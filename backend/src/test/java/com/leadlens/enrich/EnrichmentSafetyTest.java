package com.leadlens.enrich;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class EnrichmentSafetyTest {

    @Test
    void addressGuardBlocksInternalTargets() {
        assertThat(AddressGuard.rejectReason(URI.create("http://localhost:8080/actuator"))).isNotNull();
        assertThat(AddressGuard.rejectReason(URI.create("http://169.254.169.254/latest/meta-data"))).isNotNull();
        assertThat(AddressGuard.rejectReason(URI.create("http://10.0.0.5/"))).isNotNull();
        assertThat(AddressGuard.rejectReason(URI.create("ftp://acme.com/"))).isNotNull();
        assertThat(AddressGuard.rejectReason(URI.create("https://acme.com:8443/"))).isNotNull();
    }

    @Test
    void robotsRulesUseOurGroupAndLongestMatch() {
        RobotsRules rules = RobotsRules.parse("""
            User-agent: *
            Disallow: /

            User-agent: LeadLensBot
            Disallow: /private
            Allow: /private/about
            """, "LeadLensBot");
        assertThat(rules.allows("/")).isTrue();
        assertThat(rules.allows("/private/team")).isFalse();
        assertThat(rules.allows("/private/about")).isTrue();
        assertThat(RobotsRules.parse("User-agent: *\nDisallow: /\n", "LeadLensBot").allows("/about")).isFalse();
        assertThat(RobotsRules.parse("User-agent: *\nDisallow:\n", "LeadLensBot").allows("/about")).isTrue();
    }

    @Test
    void rateLimitsHonourRetryAfter() {
        assertThat(SafeFetcher.retryAfterMs("3")).isEqualTo(3000);
        assertThat(SafeFetcher.retryAfterMs(null)).isEqualTo(2000);
        assertThat(SafeFetcher.retryAfterMs("soon")).isEqualTo(-1);
        assertThat(SafeFetcher.retryAfterMs("Wed, 21 Oct 2015 07:28:00 GMT")).isZero();
    }

    @Test
    void botChallengePagesAreRecognised() {
        assertThat(BotChallenge.looksLikeChallenge("<title>Just a moment...</title><div id=cf-chl-widget>")).isTrue();
        assertThat(BotChallenge.looksLikeChallenge("<html><body><h1>Acme Plumbing</h1></body></html>")).isFalse();
    }

    @Test
    void extractsContactsSignalsAndEvidenceFromAPage() {
        Document doc = Jsoup.parse("""
            <html><head><title>Acme Heating | Austin HVAC</title>
            <meta name="description" content="Heating and cooling for Austin homes."></head>
            <body>
              <p>Family owned and operated since 1987. Ask about our maintenance plans.</p>
              <p>Meet Ray Delgado, Owner. Call (512) 555-0142.</p>
              <a href="mailto:ray@acme.com">Email Ray</a>
              <a href="https://www.linkedin.com/company/acme-heating">LinkedIn</a>
              <a href="/about-us">About us</a> <a href="/careers">Careers</a>
              <footer>© 2019 Acme Heating</footer>
            </body></html>""", "https://acme.com/");
        PageExtractor.PageFacts f = PageExtractor.extract(doc, "acme.com");

        assertThat(f.siteName()).isEqualTo("Acme Heating");
        assertThat(f.description()).isEqualTo("Heating and cooling for Austin homes.");
        assertThat(f.emails()).contains("ray@acme.com");
        assertThat(f.phones()).isNotEmpty();
        assertThat(f.linkedin()).contains("linkedin.com/company/acme-heating");
        assertThat(f.foundedYear()).isEqualTo(1987);
        assertThat(f.copyrightYear()).isEqualTo(2019);
        assertThat(f.ownerName()).isEqualTo("Ray Delgado");
        assertThat(f.ownerTitle()).isEqualTo("Owner");
        assertThat(f.signals()).containsKeys("FAMILY_OWNED", "RECURRING_REVENUE", "HIRING", "FOUNDED");
        assertThat(f.signals().get("FAMILY_OWNED").evidence()).contains("Family owned and operated since 1987");
        assertThat(f.internalLinks()).contains("https://acme.com/about-us");
    }

    @Test
    void descriptionTextYieldsTheSameSignals() {
        assertThat(PageExtractor.textSignals("Second-generation family business. Founder planning retirement.", "Export: "))
            .containsKeys("FAMILY_OWNED", "MULTI_GENERATION", "RETIREMENT");
    }
}
