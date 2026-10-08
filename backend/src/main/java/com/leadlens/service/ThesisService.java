package com.leadlens.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadlens.domain.AppSetting;
import com.leadlens.domain.AppSettingRepository;
import com.leadlens.scoring.Thesis;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** The active buy box / ICP, stored as JSON in app_setting so it survives restarts. */
@Service
public class ThesisService {

    private static final String KEY = "thesis";

    private final AppSettingRepository settings;
    private final ObjectMapper json;
    private volatile Thesis cached;

    public ThesisService(AppSettingRepository settings, ObjectMapper json) {
        this.settings = settings;
        this.json = json;
    }

    public Thesis current() {
        Thesis t = cached;
        if (t != null) {
            return t;
        }
        t = settings.findById(KEY).map(s -> read(s.getValue())).orElseGet(Thesis::defaults);
        cached = t;
        return t;
    }

    public Thesis save(Thesis thesis) {
        try {
            String value = json.writeValueAsString(thesis);
            AppSetting s = settings.findById(KEY).orElseGet(() -> new AppSetting(KEY, value));
            s.setValue(value);
            settings.save(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        cached = thesis;
        return thesis;
    }

    /** Starting points in the settings panel; the user edits from there. */
    public static Map<String, Thesis> presets() {
        return Map.of(
            "acquisition", Thesis.defaults(),
            "sales", new Thesis(Thesis.Mode.SALES,
                List.of("HVAC", "plumbing", "electrical", "roofing", "landscaping", "dental", "accounting", "law",
                    "construction", "manufacturing"),
                List.of(), 5, 500, 500_000L, 50_000_000L, 0, List.of("government", "nonprofit")));
    }

    private Thesis read(String value) {
        try {
            return json.readValue(value, Thesis.class);
        } catch (JsonProcessingException e) {
            return Thesis.defaults();
        }
    }
}
