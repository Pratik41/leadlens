package com.leadlens.export;

import com.leadlens.domain.Lead;
import com.leadlens.domain.Tier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrmExporterTest {

    @Test
    void neutralisesSpreadsheetFormulas() {
        assertThat(CrmExporter.safe("=HYPERLINK(\"http://evil\")")).isEqualTo("'=HYPERLINK(\"http://evil\")");
        assertThat(CrmExporter.safe("+1 512 555 0142")).isEqualTo("'+1 512 555 0142");
        assertThat(CrmExporter.safe("Acme")).isEqualTo("Acme");
        assertThat(CrmExporter.safe(42)).isEqualTo(42);
    }

    @Test
    void hubspotExportUsesHubspotColumnNamesAndSplitsTheOwnerName() throws IOException {
        Lead l = new Lead();
        l.setCompany("Acme HVAC");
        l.setOwnerName("Ray J. Delgado");
        l.setEmail("ray@acme.com");
        l.setTier(Tier.A);
        l.setScore(88);
        StringWriter out = new StringWriter();
        CrmExporter.write(CrmExporter.Format.HUBSPOT, List.of(l), x -> null, out);
        String[] lines = out.toString().split("\r?\n");
        assertThat(lines[0]).startsWith("First Name,Last Name,Email,Phone Number,Job Title,Company Name,Website URL");
        assertThat(lines[1]).startsWith("Ray,J. Delgado,ray@acme.com,");
        assertThat(lines[1]).contains(",88,A,");
    }
}
