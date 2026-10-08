package com.leadlens.quality;

import com.leadlens.domain.EmailStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerifierTest {

    private final EmailVerifier verifier = new EmailVerifier(domain -> switch (domain) {
        case "acme.com", "gmail.com" -> MailDomainChecker.Result.ACCEPTS_MAIL;
        case "deadco.com" -> MailDomainChecker.Result.NO_MAIL;
        default -> MailDomainChecker.Result.UNKNOWN;
    });

    @Test
    void gradesAddressesForOutreach() {
        assertThat(verifier.verify("Jane.Doe@ACME.com").status()).isEqualTo(EmailStatus.VALID);
        assertThat(verifier.verify("Jane.Doe@ACME.com").email()).isEqualTo("jane.doe@acme.com");
        assertThat(verifier.verify("info@acme.com").status()).isEqualTo(EmailStatus.ROLE);
        assertThat(verifier.verify("bob@deadco.com").status()).isEqualTo(EmailStatus.NO_MX);
        assertThat(verifier.verify("bob@unknown-dns.com").status()).isEqualTo(EmailStatus.UNVERIFIED);
        assertThat(verifier.verify("owner@mailinator.com").status()).isEqualTo(EmailStatus.DISPOSABLE);
        assertThat(verifier.verify("mike@@acme.com").status()).isEqualTo(EmailStatus.INVALID);
        assertThat(verifier.verify("  ").status()).isEqualTo(EmailStatus.MISSING);
    }

    @Test
    void reservedDomainsAreNeverLookedUp() {
        EmailVerifier noNetwork = new EmailVerifier(domain -> {
            throw new AssertionError("DNS must not be queried for " + domain);
        });
        assertThat(noNetwork.verify("ray@lonestar.example").status()).isEqualTo(EmailStatus.UNVERIFIED);
    }
}
