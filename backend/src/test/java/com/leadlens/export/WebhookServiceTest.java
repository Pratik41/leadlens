package com.leadlens.export;

import com.leadlens.domain.EmailStatus;
import com.leadlens.domain.Lead;
import com.leadlens.domain.Tier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookServiceTest {

    @Test
    void webhookTargetsMustBePublicHttpUrls() {
        assertThatThrownBy(() -> WebhookService.validated("http://localhost:5678/webhook")).hasMessageContaining("not allowed");
        assertThatThrownBy(() -> WebhookService.validated("http://169.254.169.254/latest")).hasMessageContaining("not allowed");
        assertThatThrownBy(() -> WebhookService.validated("ftp://hooks.example.org/x")).hasMessageContaining("http(s)");
    }

    @Test
    void payloadIsFlatAndCrmFriendly() {
        Lead l = new Lead();
        l.setCompany("Acme HVAC");
        l.setOwnerName("Ray Delgado");
        l.setEmail("ray@acme.com");
        l.setEmailStatus(EmailStatus.VALID);
        l.setTier(Tier.A);
        l.setScore(91);
        Map<String, Object> p = WebhookService.payload(l, null);
        assertThat(p).containsEntry("firstName", "Ray").containsEntry("lastName", "Delgado")
            .containsEntry("tier", "A").containsEntry("score", 91).containsEntry("emailStatus", "VALID")
            .containsKey("nextAction");
    }
}
