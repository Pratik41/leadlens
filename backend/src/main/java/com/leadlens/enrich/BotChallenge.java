package com.leadlens.enrich;

import java.util.List;
import java.util.Locale;

/**
 * Recognises bot-check and CAPTCHA interstitials (Cloudflare, reCAPTCHA, hCaptcha, Akamai, Sucuri...).
 * The site owner has asked automated clients to stop, so we record PROTECTED and move on;
 * the lead keeps whatever the import already had.
 */
public final class BotChallenge {

    private BotChallenge() {
    }

    private static final List<String> MARKERS = List.of(
        "cf-chl-", "challenge-platform", "just a moment...", "attention required! | cloudflare",
        "checking your browser before accessing", "g-recaptcha", "hcaptcha.com/1/api.js", "h-captcha",
        "verify you are human", "are you a robot", "px-captcha", "_incapsula_resource", "sucuri website firewall",
        "access denied | ", "ddos protection by");

    public static boolean looksLikeChallenge(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        String h = html.length() > 200_000 ? html.substring(0, 200_000) : html;
        h = h.toLowerCase(Locale.ROOT);
        for (String marker : MARKERS) {
            if (h.contains(marker)) {
                // A normal page can embed reCAPTCHA on a contact form; a challenge page is short.
                return !marker.equals("g-recaptcha") || h.length() < 15_000;
            }
        }
        return false;
    }
}
