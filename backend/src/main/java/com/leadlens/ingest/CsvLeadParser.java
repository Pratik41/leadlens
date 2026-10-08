package com.leadlens.ingest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads lead exports whatever their column names: SaaSquatch, Apollo, Google Maps scrapers,
 * or a hand-made spreadsheet. Headers are matched against aliases after lower-casing and removing
 * everything but letters and digits, so "Company Name", "company_name" and "COMPANY-NAME" all match.
 * The delimiter (comma, semicolon or tab) is detected from the header line.
 */
public class CsvLeadParser {

    enum Field {
        COMPANY, WEBSITE, INDUSTRY, CITY, STATE, COUNTRY, LOCATION, EMPLOYEES, REVENUE, FOUNDED,
        OWNER, FIRST_NAME, LAST_NAME, TITLE, EMAIL, PHONE, LINKEDIN, DESCRIPTION
    }

    /** Checked in order, so a more specific alias ("company name") wins over a generic one ("name"). */
    private static final Map<Field, List<String>> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put(Field.COMPANY, List.of("company", "companyname", "businessname", "business", "organization",
            "organizationname", "account", "accountname", "name"));
        ALIASES.put(Field.WEBSITE, List.of("website", "websiteurl", "companywebsite", "companydomain", "domain",
            "url", "site", "web", "homepage"));
        ALIASES.put(Field.INDUSTRY, List.of("industry", "category", "sector", "businesscategory", "vertical",
            "industrycategory", "type"));
        ALIASES.put(Field.CITY, List.of("city", "town"));
        ALIASES.put(Field.STATE, List.of("state", "stateregion", "region", "province", "stateprovince"));
        ALIASES.put(Field.COUNTRY, List.of("country", "countrycode"));
        ALIASES.put(Field.LOCATION, List.of("location", "address", "fulladdress", "hqlocation", "headquarters",
            "companyaddress"));
        ALIASES.put(Field.EMPLOYEES, List.of("employees", "employeecount", "numberofemployees", "noofemployees",
            "headcount", "companysize", "size", "staff", "employeesrange", "estemployees"));
        ALIASES.put(Field.REVENUE, List.of("revenue", "annualrevenue", "estimatedrevenue", "revenueestimate",
            "estrevenue", "revenueusd", "estimatedannualrevenue", "sales"));
        ALIASES.put(Field.FOUNDED, List.of("founded", "yearfounded", "foundedyear", "established",
            "yearestablished", "foundingyear"));
        ALIASES.put(Field.OWNER, List.of("owner", "ownername", "contactname", "fullname", "decisionmaker",
            "keycontact", "contact", "ceo", "principal"));
        ALIASES.put(Field.FIRST_NAME, List.of("firstname", "ownerfirstname", "contactfirstname", "first"));
        ALIASES.put(Field.LAST_NAME, List.of("lastname", "ownerlastname", "contactlastname", "last"));
        ALIASES.put(Field.TITLE, List.of("title", "jobtitle", "ownertitle", "contacttitle", "position", "role"));
        ALIASES.put(Field.EMAIL, List.of("email", "emailaddress", "contactemail", "owneremail", "workemail",
            "businessemail", "emails"));
        ALIASES.put(Field.PHONE, List.of("phone", "phonenumber", "companyphone", "telephone", "tel",
            "contactphone", "mobile", "phones"));
        ALIASES.put(Field.LINKEDIN, List.of("linkedin", "linkedinurl", "companylinkedin", "linkedinprofile",
            "linkedincompanyurl", "linkedinpage"));
        ALIASES.put(Field.DESCRIPTION, List.of("description", "businessdescription", "companydescription", "about",
            "summary", "overview"));
    }

    /** Fallback for headers no alias matched ("Owner's Email Address", "Est. Annual Revenue ($)"), most specific first. */
    private static final List<Map.Entry<String, Field>> KEYWORDS = List.of(
        Map.entry("linkedin", Field.LINKEDIN), Map.entry("email", Field.EMAIL), Map.entry("phone", Field.PHONE),
        Map.entry("revenue", Field.REVENUE), Map.entry("employee", Field.EMPLOYEES), Map.entry("founded", Field.FOUNDED),
        Map.entry("established", Field.FOUNDED), Map.entry("industry", Field.INDUSTRY), Map.entry("website", Field.WEBSITE),
        Map.entry("domain", Field.WEBSITE), Map.entry("firstname", Field.FIRST_NAME), Map.entry("lastname", Field.LAST_NAME),
        Map.entry("title", Field.TITLE), Map.entry("owner", Field.OWNER), Map.entry("company", Field.COMPANY),
        Map.entry("business", Field.COMPANY), Map.entry("city", Field.CITY), Map.entry("state", Field.STATE),
        Map.entry("country", Field.COUNTRY), Map.entry("description", Field.DESCRIPTION));

    public record Result(List<RawLead> leads, int totalRows, int emptyRows, List<String> unmappedColumns,
                         boolean truncated) {
    }

    private final int maxRows;

    public CsvLeadParser(int maxRows) {
        this.maxRows = maxRows;
    }

    public Result parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        reader.mark(64 * 1024);
        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new IllegalArgumentException("The file is empty.");
        }
        reader.reset();
        char delimiter = detectDelimiter(headerLine);

        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setDelimiter(delimiter)
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .setAllowMissingColumnNames(true)
            .setDuplicateHeaderMode(org.apache.commons.csv.DuplicateHeaderMode.ALLOW_ALL)
            .build();

        try (Reader r = reader; CSVParser parser = format.parse(r)) {
            List<String> headers = parser.getHeaderNames();
            Map<Field, Integer> columns = mapColumns(headers);
            if (!columns.containsKey(Field.COMPANY) && !columns.containsKey(Field.WEBSITE)) {
                throw new IllegalArgumentException(
                    "Couldn't find a company name or website column. Found: " + String.join(", ", headers));
            }
            List<String> unmapped = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                if (!columns.containsValue(i) && headers.get(i) != null && !headers.get(i).isBlank()) {
                    unmapped.add(stripBom(headers.get(i)));
                }
            }

            List<RawLead> leads = new ArrayList<>();
            int total = 0;
            int empty = 0;
            boolean truncated = false;
            for (CSVRecord record : parser) {
                if (total >= maxRows) {
                    truncated = true;
                    break;
                }
                total++;
                RawLead lead = toRawLead(record, columns);
                if (lead.isEmpty()) {
                    empty++;
                } else {
                    leads.add(lead);
                }
            }
            return new Result(leads, total, empty, unmapped, truncated);
        }
    }

    static char detectDelimiter(String headerLine) {
        int commas = count(headerLine, ',');
        int semis = count(headerLine, ';');
        int tabs = count(headerLine, '\t');
        if (tabs > commas && tabs >= semis) {
            return '\t';
        }
        return semis > commas ? ';' : ',';
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    static Map<Field, Integer> mapColumns(List<String> headers) {
        List<String> keys = headers.stream().map(CsvLeadParser::key).toList();
        Map<Field, Integer> out = new EnumMap<>(Field.class);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Map.Entry<Field, List<String>> entry : ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                int idx = indexOf(keys, alias, used);
                if (idx >= 0) {
                    out.put(entry.getKey(), idx);
                    used.add(idx);
                    break;
                }
            }
        }
        for (int i = 0; i < keys.size(); i++) {
            if (used.contains(i)) {
                continue;
            }
            for (Map.Entry<String, Field> rule : KEYWORDS) {
                if (keys.get(i).contains(rule.getKey()) && !out.containsKey(rule.getValue())) {
                    out.put(rule.getValue(), i);
                    used.add(i);
                    break;
                }
            }
        }
        return out;
    }

    private static int indexOf(List<String> keys, String alias, java.util.Set<Integer> used) {
        for (int i = 0; i < keys.size(); i++) {
            if (!used.contains(i) && keys.get(i).equals(alias)) {
                return i;
            }
        }
        return -1;
    }

    private static String key(String header) {
        return header == null ? "" : stripBom(header).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String stripBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    private static RawLead toRawLead(CSVRecord record, Map<Field, Integer> columns) {
        String owner = get(record, columns, Field.OWNER);
        String first = get(record, columns, Field.FIRST_NAME);
        String last = get(record, columns, Field.LAST_NAME);
        if (owner == null && (first != null || last != null)) {
            owner = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        }
        return new RawLead(
            get(record, columns, Field.COMPANY),
            get(record, columns, Field.WEBSITE),
            get(record, columns, Field.INDUSTRY),
            get(record, columns, Field.CITY),
            get(record, columns, Field.STATE),
            get(record, columns, Field.COUNTRY),
            get(record, columns, Field.LOCATION),
            get(record, columns, Field.EMPLOYEES),
            get(record, columns, Field.REVENUE),
            get(record, columns, Field.FOUNDED),
            owner,
            get(record, columns, Field.TITLE),
            firstOf(get(record, columns, Field.EMAIL)),
            firstOf(get(record, columns, Field.PHONE)),
            get(record, columns, Field.LINKEDIN),
            get(record, columns, Field.DESCRIPTION));
    }

    /** Some exports put several values in one cell ("a@x.com; b@x.com"); the first is the primary. */
    private static String firstOf(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split("[;|\\n]|,\\s");
        return parts.length == 0 ? null : parts[0].trim();
    }

    private static String get(CSVRecord record, Map<Field, Integer> columns, Field field) {
        Integer idx = columns.get(field);
        if (idx == null || idx >= record.size()) {
            return null;
        }
        String v = record.get(idx);
        return v == null || v.isBlank() ? null : v.trim();
    }
}
