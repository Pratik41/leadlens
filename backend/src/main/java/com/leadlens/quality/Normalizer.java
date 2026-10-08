package com.leadlens.quality;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the free-text values found in lead exports into comparable values:
 * canonical domains, dedup keys, numbers from "11-50" or "$1M-$5M", US state codes
 * and E.164 phone numbers. Pure functions, no I/O.
 */
public final class Normalizer {

    private Normalizer() {
    }

    private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();

    private static final Set<String> LEGAL_SUFFIXES = Set.of(
        "llc", "l l c", "inc", "incorporated", "co", "company", "corp", "corporation", "ltd", "limited",
        "pllc", "plc", "lp", "llp", "pc", "pa", "the", "group", "holdings", "enterprises");

    /** Mailbox providers: an address there says nothing about the company's own website. */
    public static final Set<String> FREE_MAIL = Set.of(
        "gmail.com", "googlemail.com", "yahoo.com", "hotmail.com", "outlook.com", "live.com", "msn.com",
        "aol.com", "icloud.com", "me.com", "mac.com", "comcast.net", "att.net", "sbcglobal.net",
        "verizon.net", "bellsouth.net", "cox.net", "charter.net", "proton.me", "protonmail.com",
        "ymail.com", "gmx.com", "mail.com", "zoho.com");

    /** RFC 2606 / 6761 names: they never resolve, so no network call is made for them. */
    public static boolean isReservedDomain(String domain) {
        if (domain == null) {
            return false;
        }
        String d = domain.toLowerCase(Locale.ROOT);
        return d.endsWith(".example") || d.endsWith(".test") || d.endsWith(".invalid") || d.endsWith(".localhost")
            || d.equals("example.com") || d.equals("example.org") || d.equals("example.net")
            || d.endsWith(".example.com") || d.endsWith(".example.org") || d.endsWith(".example.net");
    }

    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String v = value.replace(' ', ' ').strip();
        if (v.isEmpty() || v.equalsIgnoreCase("n/a") || v.equalsIgnoreCase("na") || v.equals("-")
            || v.equalsIgnoreCase("null") || v.equalsIgnoreCase("none") || v.equalsIgnoreCase("unknown")) {
            return null;
        }
        return v.replaceAll("\\s+", " ");
    }

    /** "https://www.Acme.com/about?x=1" and "acme.com" both become "acme.com". Null if it isn't a host name. */
    public static String canonicalDomain(String website) {
        String w = clean(website);
        if (w == null) {
            return null;
        }
        w = w.toLowerCase(Locale.ROOT);
        if (!w.contains("://")) {
            w = "http://" + w;
        }
        String host;
        try {
            host = URI.create(w.replace(" ", "")).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (host == null) {
            return null;
        }
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+") && !host.matches("[0-9.]+") ? host : null;
    }

    /** Website URL to fetch for a canonical domain. */
    public static String websiteUrl(String domain) {
        return domain == null ? null : "https://" + domain;
    }

    /** "The Acme Plumbing Co., LLC" and "ACME plumbing" both become "acme plumbing". */
    public static String companyKey(String company) {
        String c = clean(company);
        if (c == null) {
            return "";
        }
        String k = c.toLowerCase(Locale.ROOT).replace("&", " and ").replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        // Strip legal suffixes from the end (and a leading "the"), repeatedly: "acme co inc" -> "acme"
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : LEGAL_SUFFIXES) {
                if (k.endsWith(" " + suffix) && k.length() > suffix.length() + 1) {
                    k = k.substring(0, k.length() - suffix.length() - 1).trim();
                    changed = true;
                }
            }
            if (k.startsWith("the ") && k.length() > 4) {
                k = k.substring(4);
                changed = true;
            }
        }
        return k;
    }

    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:[.,]\\d+)*)\\s*([kmb]|thousand|million|billion|mm)?",
        Pattern.CASE_INSENSITIVE);

    /** "11-50" -> 30, "1,200" -> 1200, "500+" -> 500. Null if no number. */
    public static Integer parseEmployees(String text) {
        List<Double> values = numbers(text, false);
        if (values.isEmpty()) {
            return null;
        }
        double v = values.size() >= 2 ? (values.get(0) + values.get(1)) / 2 : values.get(0);
        return (int) Math.round(v);
    }

    /** "$1M-$5M" -> 3,000,000; "2.5M" -> 2,500,000; "$750K" -> 750,000; "1200000" -> 1,200,000. */
    public static Long parseRevenue(String text) {
        List<Double> values = numbers(text, true);
        if (values.isEmpty()) {
            return null;
        }
        double v = values.size() >= 2 ? (values.get(0) + values.get(1)) / 2 : values.get(0);
        return v <= 0 ? null : Math.round(v);
    }

    private static List<Double> numbers(String text, boolean money) {
        String t = clean(text);
        if (t == null) {
            return List.of();
        }
        Matcher m = NUMBER.matcher(t.replace("$", ""));
        java.util.ArrayList<Double> out = new java.util.ArrayList<>();
        String lastUnit = null;
        while (m.find() && out.size() < 2) {
            String digits = m.group(1);
            // "1,200" is a thousands separator; "2,5" (European decimal) is rare in US exports and treated as 25
            double value;
            try {
                value = new BigDecimal(digits.replace(",", "")).doubleValue();
            } catch (NumberFormatException e) {
                continue;
            }
            String unit = m.group(2) == null ? null : m.group(2).toLowerCase(Locale.ROOT);
            if (unit != null) {
                lastUnit = unit;
            }
            out.add(value * (money || unit != null ? multiplier(unit) : 1));
        }
        // "$1-5M": the unit after the second number applies to the first as well
        if (money && out.size() == 2 && lastUnit != null && out.get(0) < 1000 && out.get(1) >= 1000) {
            out.set(0, out.get(0) * multiplier(lastUnit));
        }
        return out;
    }

    private static double multiplier(String unit) {
        if (unit == null) {
            return 1;
        }
        return switch (unit) {
            case "k", "thousand" -> 1_000;
            case "m", "mm", "million" -> 1_000_000;
            case "b", "billion" -> 1_000_000_000;
            default -> 1;
        };
    }

    /** A plausible founding year (1800..this year) from "1987", "Est. 1987" or "1987-01-01". */
    public static Integer parseYear(String text) {
        String t = clean(text);
        if (t == null) {
            return null;
        }
        Matcher m = Pattern.compile("(1[89]\\d{2}|20\\d{2})").matcher(t);
        if (!m.find()) {
            return null;
        }
        int year = Integer.parseInt(m.group(1));
        return year <= java.time.Year.now().getValue() ? year : null;
    }

    public record Phone(String e164, String display, boolean valid) {
    }

    /** Parses with the lead's country (US default). Unparseable input returns null. */
    public static Phone phone(String raw, String country) {
        String p = clean(raw);
        if (p == null) {
            return null;
        }
        String region = regionFor(country);
        try {
            Phonenumber.PhoneNumber number = PHONES.parse(p, region);
            boolean valid = PHONES.isValidNumber(number);
            // Same country as the lead: "(512) 555-0142"; otherwise "+44 20 7946 0958"
            PhoneNumberUtil.PhoneNumberFormat displayFormat = region.equals(PHONES.getRegionCodeForNumber(number))
                ? PhoneNumberUtil.PhoneNumberFormat.NATIONAL
                : PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL;
            return new Phone(
                PHONES.format(number, PhoneNumberUtil.PhoneNumberFormat.E164),
                PHONES.format(number, displayFormat),
                valid);
        } catch (NumberParseException e) {
            return null;
        }
    }

    private static String regionFor(String country) {
        String c = clean(country);
        if (c == null) {
            return "US";
        }
        String u = c.toUpperCase(Locale.ROOT);
        return switch (u) {
            case "USA", "US", "UNITED STATES", "UNITED STATES OF AMERICA", "AMERICA" -> "US";
            case "CANADA", "CA" -> "CA";
            case "UK", "UNITED KINGDOM", "GB", "GREAT BRITAIN", "ENGLAND" -> "GB";
            case "INDIA", "IN" -> "IN";
            case "AUSTRALIA", "AU" -> "AU";
            default -> u.length() == 2 ? u : "US";
        };
    }

    private static final Map<String, String> STATES = Map.ofEntries(
        Map.entry("alabama", "AL"), Map.entry("alaska", "AK"), Map.entry("arizona", "AZ"), Map.entry("arkansas", "AR"),
        Map.entry("california", "CA"), Map.entry("colorado", "CO"), Map.entry("connecticut", "CT"),
        Map.entry("delaware", "DE"), Map.entry("district of columbia", "DC"), Map.entry("florida", "FL"),
        Map.entry("georgia", "GA"), Map.entry("hawaii", "HI"), Map.entry("idaho", "ID"), Map.entry("illinois", "IL"),
        Map.entry("indiana", "IN"), Map.entry("iowa", "IA"), Map.entry("kansas", "KS"), Map.entry("kentucky", "KY"),
        Map.entry("louisiana", "LA"), Map.entry("maine", "ME"), Map.entry("maryland", "MD"),
        Map.entry("massachusetts", "MA"), Map.entry("michigan", "MI"), Map.entry("minnesota", "MN"),
        Map.entry("mississippi", "MS"), Map.entry("missouri", "MO"), Map.entry("montana", "MT"),
        Map.entry("nebraska", "NE"), Map.entry("nevada", "NV"), Map.entry("new hampshire", "NH"),
        Map.entry("new jersey", "NJ"), Map.entry("new mexico", "NM"), Map.entry("new york", "NY"),
        Map.entry("north carolina", "NC"), Map.entry("north dakota", "ND"), Map.entry("ohio", "OH"),
        Map.entry("oklahoma", "OK"), Map.entry("oregon", "OR"), Map.entry("pennsylvania", "PA"),
        Map.entry("rhode island", "RI"), Map.entry("south carolina", "SC"), Map.entry("south dakota", "SD"),
        Map.entry("tennessee", "TN"), Map.entry("texas", "TX"), Map.entry("utah", "UT"), Map.entry("vermont", "VT"),
        Map.entry("virginia", "VA"), Map.entry("washington", "WA"), Map.entry("west virginia", "WV"),
        Map.entry("wisconsin", "WI"), Map.entry("wyoming", "WY"));

    /** "texas", "Texas", "TX", "tx" -> "TX". Anything else is returned cleaned, unchanged. */
    public static String state(String raw) {
        String s = clean(raw);
        if (s == null) {
            return null;
        }
        String code = STATES.get(s.toLowerCase(Locale.ROOT).replace(".", ""));
        if (code != null) {
            return code;
        }
        String upper = s.toUpperCase(Locale.ROOT).replace(".", "");
        return upper.length() == 2 && STATES.containsValue(upper) ? upper : s;
    }

    /** "Austin, TX 78701, USA" -> [Austin, TX, USA]; missing parts are null. */
    public static String[] splitLocation(String location) {
        String l = clean(location);
        String[] out = new String[3];
        if (l == null) {
            return out;
        }
        String[] parts = l.split(",");
        java.util.List<String> p = new java.util.ArrayList<>();
        for (String part : parts) {
            String c = clean(part.replaceAll("\\b\\d{5}(-\\d{4})?\\b", ""));
            if (c != null) {
                p.add(c);
            }
        }
        if (p.isEmpty()) {
            return out;
        }
        // Drop a leading street address ("123 Main St, Austin, TX")
        if (p.size() >= 3 && p.get(0).matches("^\\d+\\s.*")) {
            p.remove(0);
        }
        out[0] = p.get(0);
        if (p.size() >= 2) {
            out[1] = state(p.get(1));
        }
        if (p.size() >= 3) {
            out[2] = p.get(2);
        }
        return out;
    }

    /** "Jane Q. Doe" -> ["Jane", "Q. Doe"]. */
    public static String[] splitName(String fullName) {
        String n = clean(fullName);
        if (n == null) {
            return new String[] {null, null};
        }
        int space = n.indexOf(' ');
        return space < 0 ? new String[] {n, null} : new String[] {n.substring(0, space), n.substring(space + 1)};
    }

    /** linkedin.com/company/acme or /in/jane, normalised to https; null if it isn't a LinkedIn URL. */
    public static String linkedin(String raw) {
        String l = clean(raw);
        if (l == null || !l.toLowerCase(Locale.ROOT).contains("linkedin.com/")) {
            return null;
        }
        String url = l.contains("://") ? l : "https://" + l;
        return url.replaceFirst("^http://", "https://").replaceAll("[?#].*$", "");
    }
}
