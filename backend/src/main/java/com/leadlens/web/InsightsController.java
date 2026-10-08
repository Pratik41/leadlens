package com.leadlens.web;

import com.leadlens.ai.AskService;
import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadRepository;
import com.leadlens.export.WebhookService;
import com.leadlens.service.InsightsService;
import com.leadlens.service.LeadScoring;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reporting, natural-language search and CRM push: the layers on top of the scored list. */
@RestController
public class InsightsController {

    private final InsightsService insights;
    private final AskService ask;
    private final WebhookService webhook;
    private final LeadRepository leads;
    private final LeadScoring scoring;

    public InsightsController(InsightsService insights, AskService ask, WebhookService webhook, LeadRepository leads,
                              LeadScoring scoring) {
        this.insights = insights;
        this.ask = ask;
        this.webhook = webhook;
        this.leads = leads;
        this.scoring = scoring;
    }

    @GetMapping("/api/insights")
    public InsightsService.Insights insights() {
        return insights.build();
    }

    public record Question(String question) {
    }

    /** Returns the filters the question means; the client applies them to the normal lead list. */
    @PostMapping("/api/leads/ask")
    public AskService.Answer ask(@RequestBody Question body) {
        return ask.ask(body.question());
    }

    @GetMapping("/api/integrations/webhook")
    public Map<String, String> webhook() {
        Map<String, String> m = new HashMap<>();
        m.put("url", webhook.url());
        return m;
    }

    public record WebhookConfig(String url) {
    }

    @PutMapping("/api/integrations/webhook")
    public Map<String, String> saveWebhook(@RequestBody WebhookConfig config) {
        webhook.saveUrl(config.url());
        return webhook();
    }

    /** Sends the leads matching the given filters (same parameters as the lead list; excluded leads skipped). */
    @PostMapping("/api/integrations/webhook/send")
    public WebhookService.Result send(LeadQuery filters) {
        LeadQuery query = filters.tier() == null || filters.tier().isBlank() ? filters.withTier("A,B,C") : filters;
        List<Lead> rows = leads.findAll(query.spec(), query.sortOrder());
        return webhook.send(rows, scoring::breakdown);
    }
}
