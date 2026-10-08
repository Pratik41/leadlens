package com.leadlens.export;

import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadStatus;
import com.leadlens.domain.Tier;
import com.leadlens.scoring.ScoreResult;
import com.leadlens.web.NextAction;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * CSV files that import into a CRM without a mapping step: column names match HubSpot's and
 * Salesforce's default import fields, and LeadLens adds its score, tier, reasons and next action
 * as extra columns (HubSpot offers to create them as custom properties).
 */
public final class CrmExporter {

    public enum Format { HUBSPOT, SALESFORCE, FULL }

    private CrmExporter() {
    }

    public static void write(Format format, List<Lead> leads, Function<Lead, ScoreResult> scores, Writer out)
        throws IOException {
        List<String> header = switch (format) {
            case HUBSPOT -> List.of("First Name", "Last Name", "Email", "Phone Number", "Job Title", "Company Name",
                "Website URL", "Industry", "City", "State/Region", "Country/Region", "Number of Employees",
                "Annual Revenue", "Year Founded", "LinkedIn URL", "Lead Status", "LeadLens Score", "LeadLens Tier",
                "LeadLens Why", "LeadLens Next Action");
            case SALESFORCE -> List.of("First Name", "Last Name", "Title", "Company", "Email", "Phone", "Website",
                "Industry", "City", "State/Province", "Country", "No. of Employees", "Annual Revenue", "Lead Source",
                "Rating", "Lead Status", "Description");
            case FULL -> List.of("Company", "Domain", "Website", "Industry", "City", "State", "Country", "Employees",
                "Revenue USD", "Founded", "Owner", "Owner Title", "Email", "Email Status", "Phone", "Phone Valid",
                "LinkedIn", "Website Status", "Score", "Tier", "Excluded Reason", "Why", "Next Action", "Status",
                "Notes", "Source Rows");
        };
        try (CSVPrinter csv = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(header.toArray(String[]::new)).build())) {
            for (Lead l : leads) {
                csv.printRecord(row(format, l, scores.apply(l)).stream().map(CrmExporter::safe).toList());
            }
        }
    }

    private static List<Object> row(Format format, Lead l, ScoreResult score) {
        String[] name = splitName(l.getOwnerName());
        String why = score == null ? "" : String.join("; ", score.highlights(3));
        String next = NextAction.of(l).label();
        List<Object> r = new ArrayList<>();
        switch (format) {
            case HUBSPOT -> {
                r.add(name[0]); r.add(name[1]); r.add(l.getEmail()); r.add(l.getPhone()); r.add(l.getOwnerTitle());
                r.add(l.getCompany()); r.add(l.getWebsite()); r.add(l.getIndustry()); r.add(l.getCity());
                r.add(l.getState()); r.add(l.getCountry()); r.add(l.getEmployees()); r.add(l.getRevenueUsd());
                r.add(l.getFoundedYear()); r.add(l.getLinkedinUrl()); r.add(hubspotStatus(l.getStatus()));
                r.add(l.getScore()); r.add(l.getTier()); r.add(why); r.add(next);
            }
            case SALESFORCE -> {
                r.add(name[0]);
                r.add(name[1] == null || name[1].isBlank() ? (name[0] == null ? "Unknown" : "-") : name[1]); // required field
                r.add(l.getOwnerTitle()); r.add(l.getCompany()); r.add(l.getEmail()); r.add(l.getPhone());
                r.add(l.getWebsite()); r.add(l.getIndustry()); r.add(l.getCity()); r.add(l.getState());
                r.add(l.getCountry()); r.add(l.getEmployees()); r.add(l.getRevenueUsd()); r.add("LeadLens");
                r.add(l.getTier() == Tier.A ? "Hot" : l.getTier() == Tier.B ? "Warm" : "Cold");
                r.add(salesforceStatus(l.getStatus()));
                r.add("LeadLens score " + l.getScore() + " (" + l.getTier() + "). " + why + ". Next: " + next);
            }
            case FULL -> {
                r.add(l.getCompany()); r.add(l.getDomain()); r.add(l.getWebsite()); r.add(l.getIndustry());
                r.add(l.getCity()); r.add(l.getState()); r.add(l.getCountry()); r.add(l.getEmployees());
                r.add(l.getRevenueUsd()); r.add(l.getFoundedYear()); r.add(l.getOwnerName()); r.add(l.getOwnerTitle());
                r.add(l.getEmail()); r.add(l.getEmailStatus()); r.add(l.getPhone()); r.add(l.isPhoneValid());
                r.add(l.getLinkedinUrl()); r.add(l.getWebsiteStatus()); r.add(l.getScore()); r.add(l.getTier());
                r.add(l.getExcludedReason()); r.add(why); r.add(next); r.add(l.getStatus()); r.add(l.getNotes());
                r.add(l.getSourceRows());
            }
        }
        return r;
    }

    private static String hubspotStatus(LeadStatus s) {
        return switch (s) {
            case NEW -> "NEW";
            case QUALIFIED -> "OPEN";
            case CONTACTED -> "ATTEMPTED_TO_CONTACT";
            case REPLIED -> "CONNECTED";
            case DISQUALIFIED -> "UNQUALIFIED";
        };
    }

    private static String salesforceStatus(LeadStatus s) {
        return switch (s) {
            case NEW -> "Open - Not Contacted";
            case QUALIFIED, CONTACTED -> "Working - Contacted";
            case REPLIED -> "Closed - Converted";
            case DISQUALIFIED -> "Closed - Not Converted";
        };
    }

    static String[] splitName(String full) {
        if (full == null || full.isBlank()) return new String[] {null, null};
        String n = full.trim();
        int space = n.indexOf(' ');
        return space < 0 ? new String[] {n, null} : new String[] {n.substring(0, space), n.substring(space + 1).trim()};
    }

    /**
     * Spreadsheet formula injection: a scraped company called "=HYPERLINK(...)" must not execute
     * when someone opens the export in Excel, so cells starting with = + - @ are prefixed with '.
     */
    static Object safe(Object value) {
        if (!(value instanceof String s) || s.isEmpty()) return value;
        char c = s.charAt(0);
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r' ? "'" + s : s;
    }
}
