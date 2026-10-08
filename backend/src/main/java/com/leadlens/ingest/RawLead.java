package com.leadlens.ingest;

/** One row as it arrived, mapped to our fields but not yet cleaned. Any value may be null. */
public record RawLead(
    String company,
    String website,
    String industry,
    String city,
    String state,
    String country,
    String location,
    String employees,
    String revenue,
    String founded,
    String ownerName,
    String ownerTitle,
    String email,
    String phone,
    String linkedin,
    String description) {

    public static RawLead ofWebsite(String website) {
        return new RawLead(null, website, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public boolean isEmpty() {
        return blank(company) && blank(website) && blank(email);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
