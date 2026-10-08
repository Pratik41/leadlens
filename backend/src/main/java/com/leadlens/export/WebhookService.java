package com.leadlens.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadlens.domain.AppSetting;
import com.leadlens.domain.AppSettingRepository;
import com.leadlens.domain.Lead;
import com.leadlens.enrich.AddressGuard;
import com.leadlens.scoring.ScoreResult;
import com.leadlens.web.NextAction;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Pushes leads to any CRM automation that accepts a JSON webhook: Zapier / Make / n8n catch hooks,
 * a HubSpot workflow webhook trigger, or an internal endpoint. Leads go in batches of 100 with flat,
 * CRM-friendly fields. The target URL passes the same SSRF guard as the crawler (public https/http only).
 */
@Service
public class WebhookService {

    static final String KEY = "webhook.url";
    private static final int BATCH = 100;

    public record Result(int sent, int batches, int lastStatus, String target) {
    }

    private final AppSettingRepository settings;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    public WebhookService(AppSettingRepository settings, ObjectMapper json) {
        this.settings = settings;
        this.json = json;
    }

    public String url() {
        return settings.findById(KEY).map(AppSetting::getValue).orElse(null);
    }

    public void saveUrl(String url) {
        if (url == null || url.isBlank()) {
            settings.deleteById(KEY);
            return;
        }
        URI uri = validated(url.trim());
        AppSetting s = settings.findById(KEY).orElseGet(() -> new AppSetting(KEY, uri.toString()));
        s.setValue(uri.toString());
        settings.save(s);
    }

    public Result send(List<Lead> leads, Function<Lead, ScoreResult> scores) {
        String url = url();
        if (url == null) throw new IllegalArgumentException("Set a webhook URL first.");
        if (leads.isEmpty()) throw new IllegalArgumentException("No leads match the current view.");
        URI uri = validated(url);
        int sent = 0;
        int batches = 0;
        int status = 0;
        for (int from = 0; from < leads.size(); from += BATCH) {
            List<Lead> chunk = leads.subList(from, Math.min(leads.size(), from + BATCH));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("source", "leadlens");
            body.put("sentAt", Instant.now().toString());
            body.put("batch", batches + 1);
            body.put("leads", chunk.stream().map(l -> payload(l, scores.apply(l))).toList());
            status = post(uri, body);
            if (status >= 300) {
                throw new IllegalStateException("The webhook answered HTTP " + status + " after " + sent + " leads.");
            }
            sent += chunk.size();
            batches++;
        }
        return new Result(sent, batches, status, uri.getHost());
    }

    static Map<String, Object> payload(Lead l, ScoreResult score) {
        Map<String, Object> m = new LinkedHashMap<>();
        String[] name = CrmExporter.splitName(l.getOwnerName());
        m.put("leadlensId", l.getId());
        m.put("company", l.getCompany());
        m.put("website", l.getWebsite());
        m.put("industry", l.getIndustry());
        m.put("city", l.getCity());
        m.put("state", l.getState());
        m.put("employees", l.getEmployees());
        m.put("annualRevenue", l.getRevenueUsd());
        m.put("yearFounded", l.getFoundedYear());
        m.put("firstName", name[0]);
        m.put("lastName", name[1]);
        m.put("jobTitle", l.getOwnerTitle());
        m.put("email", l.getEmail());
        m.put("emailStatus", l.getEmailStatus().name());
        m.put("phone", l.getPhone());
        m.put("phoneValid", l.isPhoneValid());
        m.put("linkedin", l.getLinkedinUrl());
        m.put("score", l.getScore());
        m.put("tier", l.getTier().name());
        m.put("why", score == null ? List.of() : score.highlights(3));
        m.put("nextAction", NextAction.of(l).label());
        m.put("status", l.getStatus().name());
        return m;
    }

    private int post(URI uri, Map<String, Object> body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", "LeadLens-Webhook/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
            return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't reach the webhook: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted");
        }
    }

    static URI validated(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("That is not a valid URL.");
        }
        if (uri.getScheme() == null || !uri.getScheme().toLowerCase(Locale.ROOT).matches("https?")) {
            throw new IllegalArgumentException("The webhook must be an http(s) URL.");
        }
        String reject = AddressGuard.rejectReason(uri);
        if (reject != null) throw new IllegalArgumentException("Webhook not allowed: " + reject + ".");
        return uri;
    }
}
