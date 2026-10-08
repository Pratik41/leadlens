package com.leadlens.ingest;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvLeadParserTest {

    private final CsvLeadParser parser = new CsvLeadParser(100);

    private CsvLeadParser.Result parse(String csv) throws IOException {
        return parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void mapsSaaSquatchStyleHeadersIncludingKeywordFallbacks() throws IOException {
        CsvLeadParser.Result r = parse("""
            ﻿Company,Website,Employees Count,Owner's First Name,Owner's Last Name,Owner's Email Address,Est. Annual Revenue ($),BBB Rating
            Acme HVAC,acme.com,11-50,Jane,Doe,jane@acme.com,$2M,A+
            ,,,,,,,
            """);
        assertThat(r.totalRows()).isEqualTo(2);
        assertThat(r.emptyRows()).isEqualTo(1);
        assertThat(r.unmappedColumns()).containsExactly("BBB Rating");
        RawLead lead = r.leads().get(0);
        assertThat(lead.company()).isEqualTo("Acme HVAC");
        assertThat(lead.ownerName()).isEqualTo("Jane Doe");
        assertThat(lead.email()).isEqualTo("jane@acme.com");
        assertThat(lead.employees()).isEqualTo("11-50");
        assertThat(lead.revenue()).isEqualTo("$2M");
    }

    @Test
    void detectsSemicolonsAndTakesFirstOfMultipleEmails() throws IOException {
        CsvLeadParser.Result r = parse("Business Name;Domain;Emails\nAcme;acme.com;a@acme.com; b@acme.com\n");
        assertThat(r.leads().get(0).website()).isEqualTo("acme.com");
        assertThat(r.leads().get(0).email()).isEqualTo("a@acme.com");
    }

    @Test
    void rejectsFilesWithoutACompanyOrWebsiteColumn() {
        assertThatThrownBy(() -> parse("foo,bar\n1,2\n")).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("company name or website");
    }
}
