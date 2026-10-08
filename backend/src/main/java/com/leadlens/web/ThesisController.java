package com.leadlens.web;

import com.leadlens.scoring.Thesis;
import com.leadlens.service.RescoreService;
import com.leadlens.service.ThesisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The buy box / ICP every lead is scored against. Saving it re-scores the whole list. */
@RestController
@RequestMapping("/api/thesis")
public class ThesisController {

    private final ThesisService theses;
    private final RescoreService rescore;

    public ThesisController(ThesisService theses, RescoreService rescore) {
        this.theses = theses;
        this.rescore = rescore;
    }

    @GetMapping
    public Thesis get() {
        return theses.current();
    }

    @GetMapping("/presets")
    public Map<String, Thesis> presets() {
        return ThesisService.presets();
    }

    public record Saved(Thesis thesis, RescoreService.Outcome rescored) {
    }

    @PutMapping
    public Saved save(@RequestBody Thesis thesis) {
        Thesis saved = theses.save(thesis);
        return new Saved(saved, rescore.rescoreAll(saved));
    }
}
