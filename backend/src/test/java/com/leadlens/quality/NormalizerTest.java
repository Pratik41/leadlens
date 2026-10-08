package com.leadlens.quality;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizerTest {

    @Test
    void canonicalDomainStripsSchemeWwwPathAndCase() {
        assertThat(Normalizer.canonicalDomain("https://www.Acme-HVAC.com/about?x=1")).isEqualTo("acme-hvac.com");
        assertThat(Normalizer.canonicalDomain("acme.com")).isEqualTo("acme.com");
        assertThat(Normalizer.canonicalDomain("not a site")).isNull();
        assertThat(Normalizer.canonicalDomain("http://10.0.0.1/")).isNull();
    }

    @Test
    void companyKeyIgnoresLegalSuffixesPunctuationAndAmpersand() {
        assertThat(Normalizer.companyKey("The Acme Heating & Air, LLC."))
            .isEqualTo(Normalizer.companyKey("acme heating and air"));
        assertThat(Normalizer.companyKey("Alamo Electric Co")).isEqualTo(Normalizer.companyKey("Alamo Electric Company"));
    }

    @Test
    void parsesRangesAndUnits() {
        assertThat(Normalizer.parseEmployees("11-50")).isEqualTo(31);
        assertThat(Normalizer.parseEmployees("1,200")).isEqualTo(1200);
        assertThat(Normalizer.parseRevenue("$4M-$6M")).isEqualTo(5_000_000L);
        assertThat(Normalizer.parseRevenue("2.1 million")).isEqualTo(2_100_000L);
        assertThat(Normalizer.parseRevenue("$650K")).isEqualTo(650_000L);
        assertThat(Normalizer.parseYear("Est. 1987")).isEqualTo(1987);
        assertThat(Normalizer.parseYear("3025")).isNull();
    }

    @Test
    void normalizesStatesAndLocations() {
        assertThat(Normalizer.state("texas")).isEqualTo("TX");
        assertThat(Normalizer.state("tx")).isEqualTo("TX");
        assertThat(Normalizer.splitLocation("123 Main St, Austin, TX 78701")).containsExactly("Austin", "TX", null);
    }

    @Test
    void validatesPhonesWithTheLeadsCountry() {
        Normalizer.Phone p = Normalizer.phone("512-555-0142", "USA");
        assertThat(p).isNotNull();
        assertThat(p.e164()).isEqualTo("+15125550142");
        assertThat(Normalizer.phone("12345", null).valid()).isFalse();
    }

    @Test
    void reservedDomainsAreRecognised() {
        assertThat(Normalizer.isReservedDomain("acme.example")).isTrue();
        assertThat(Normalizer.isReservedDomain("example.com")).isTrue();
        assertThat(Normalizer.isReservedDomain("acme.com")).isFalse();
    }
}
