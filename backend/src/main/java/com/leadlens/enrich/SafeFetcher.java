package com.leadlens.enrich;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * The only way LeadLens fetches the web. Polite and safe by construction:
 *  - identifies itself with a descriptive User-Agent and a link to the project,
 *  - obeys robots.txt (cached per host for 6 hours),
 *  - refuses private/internal addresses on every hop (AddressGuard),
 *  - recognises CAPTCHA / bot-check pages and stops there (no solving, no evasion),
 *  - on HTTP 429/503 honours Retry-After once (up to 10 s) instead of retrying blindly or rotating IPs,
 *  - caps time (connect + read) and size (pages over maxBytes are cut, not buffered),
 *  - only reads HTML.
 */
@Component
public class SafeFetcher {

    public enum Outcome { OK, ROBOTS_DISALLOWED, BLOCKED_ADDRESS, CHALLENGED, HTTP_ERROR, NOT_HTML, UNREACHABLE, TIMEOUT }

    public record Result(Outcome outcome, URI finalUri, int status, byte[] body, String note) {
        public boolean ok() {
            return outcome == Outcome.OK;
        }
    }

    private static final int MAX_REDIRECTS = 4;
    private static final long MAX_RETRY_AFTER_MS = 10_000;
    /** Used when a 429/503 has no Retry-After header. */
    private static final long DEFAULT_RETRY_MS = 2_000;

    private final HttpClient http;
    private final String userAgent;
    private final String robotsToken;
    private final Duration timeout;
    private final int maxBytes;
    private final Cache<String, RobotsRules> robotsCache =
        Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofHours(6)).build();

    public SafeFetcher(@Value("${leadlens.crawler.user-agent}") String userAgent,
                       @Value("${leadlens.crawler.robots-token:LeadLensBot}") String robotsToken,
                       @Value("${leadlens.crawler.timeout-ms:8000}") long timeoutMs,
                       @Value("${leadlens.crawler.max-bytes:1500000}") int maxBytes) {
        this.userAgent = userAgent;
        this.robotsToken = robotsToken;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.maxBytes = maxBytes;
        this.http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)   // we follow them ourselves, re-checking each hop
            .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 5000)))
            .build();
    }

    public Result fetchPage(URI uri) {
        URI current = uri;
        boolean retried = false;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            String reject = AddressGuard.rejectReason(current);
            if (reject != null) {
                return new Result(Outcome.BLOCKED_ADDRESS, current, 0, null, "Not fetched: " + reject);
            }
            if (!robotsFor(current).allows(pathOf(current))) {
                return new Result(Outcome.ROBOTS_DISALLOWED, current, 0, null, "robots.txt asks crawlers not to read this page");
            }
            HttpResponse<InputStream> response;
            try {
                response = http.send(request(current, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5"),
                    HttpResponse.BodyHandlers.ofInputStream());
            } catch (HttpTimeoutException e) {
                return new Result(Outcome.TIMEOUT, current, 0, null, "Timed out after " + timeout.toSeconds() + "s");
            } catch (IOException e) {
                return new Result(Outcome.UNREACHABLE, current, 0, null, describe(e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Result(Outcome.UNREACHABLE, current, 0, null, "Interrupted");
            }

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                closeQuietly(response.body());
                String location = response.headers().firstValue("location").orElse(null);
                if (location == null) {
                    return new Result(Outcome.HTTP_ERROR, current, status, null, "Redirect without a location");
                }
                try {
                    current = current.resolve(location.replace(" ", "%20"));
                } catch (IllegalArgumentException e) {
                    return new Result(Outcome.HTTP_ERROR, current, status, null, "Bad redirect target");
                }
                continue;
            }
            if (status >= 400) {
                byte[] errorBody = readQuietly(response.body(), 64 * 1024);
                if ((status == 403 || status == 429 || status == 503)
                    && (response.headers().firstValue("cf-mitigated").isPresent()
                        || BotChallenge.looksLikeChallenge(new String(errorBody, StandardCharsets.UTF_8)))) {
                    return new Result(Outcome.CHALLENGED, current, status, null,
                        "Behind a bot check / CAPTCHA (HTTP " + status + "); not bypassed");
                }
                // Rate limited: honour the site's Retry-After once (if it asks for 10 s or less), never hammer it
                if ((status == 429 || status == 503) && !retried) {
                    long waitMs = retryAfterMs(response.headers().firstValue("retry-after").orElse(null));
                    if (waitMs >= 0 && waitMs <= MAX_RETRY_AFTER_MS) {
                        retried = true;
                        sleep(waitMs);
                        hop--; // same URL again; not a redirect hop
                        continue;
                    }
                    return new Result(Outcome.HTTP_ERROR, current, status, null,
                        "Rate limited (HTTP " + status + "); asked to wait longer than we will, skipped politely");
                }
                return new Result(Outcome.HTTP_ERROR, current, status, null,
                    retried ? "Still rate limited after waiting (HTTP " + status + ")" : "HTTP " + status);
            }
            String type = response.headers().firstValue("content-type").orElse("text/html").toLowerCase(Locale.ROOT);
            if (!type.contains("html")) {
                closeQuietly(response.body());
                return new Result(Outcome.NOT_HTML, current, status, null, "Not an HTML page (" + type + ")");
            }
            try (InputStream in = response.body()) {
                return new Result(Outcome.OK, current, status, in.readNBytes(maxBytes), null);
            } catch (IOException e) {
                return new Result(Outcome.UNREACHABLE, current, status, null, describe(e));
            }
        }
        return new Result(Outcome.HTTP_ERROR, current, 0, null, "Too many redirects");
    }

    /** robots.txt per scheme+host. 4xx or unreachable = no rules; 5xx = stay out for now (RFC 9309 2.3.1). */
    RobotsRules robotsFor(URI uri) {
        String key = uri.getScheme() + "://" + uri.getHost();
        return robotsCache.get(key, k -> {
            URI robots = URI.create(k + "/robots.txt");
            if (AddressGuard.rejectReason(robots) != null) {
                return RobotsRules.DISALLOW_ALL;
            }
            try {
                HttpResponse<InputStream> response = http.send(request(robots, "text/plain"),
                    HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                try (InputStream in = response.body()) {
                    if (status >= 500) {
                        return RobotsRules.DISALLOW_ALL;
                    }
                    if (status != 200) {
                        return RobotsRules.ALLOW_ALL;   // includes redirects: rare for robots.txt, treated as absent
                    }
                    return RobotsRules.parse(new String(in.readNBytes(500_000), StandardCharsets.UTF_8), robotsToken);
                }
            } catch (IOException e) {
                return RobotsRules.ALLOW_ALL;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return RobotsRules.DISALLOW_ALL;
            }
        });
    }

    private HttpRequest request(URI uri, String accept) {
        return HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("User-Agent", userAgent)
            .header("Accept", accept)
            .header("Accept-Language", "en-US,en;q=0.8")
            .GET()
            .build();
    }

    private static String pathOf(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    /** Retry-After as delta-seconds or an HTTP date; -1 if unparseable. */
    static long retryAfterMs(String header) {
        if (header == null || header.isBlank()) return DEFAULT_RETRY_MS;
        String h = header.trim();
        try {
            return Math.max(0, Long.parseLong(h) * 1000);
        } catch (NumberFormatException ignored) {
            // not delta-seconds; try the HTTP-date form
        }
        try {
            java.time.ZonedDateTime when = java.time.ZonedDateTime.parse(h, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
            return Math.max(0, java.time.Duration.between(java.time.Instant.now(), when.toInstant()).toMillis());
        } catch (java.time.format.DateTimeParseException e) {
            return -1;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String describe(IOException e) {
        String name = e.getClass().getSimpleName();
        // HttpClient wraps DNS failures in a ConnectException, so look down the cause chain first
        for (Throwable t = e; t != null; t = t.getCause()) {
            String n = t.getClass().getSimpleName();
            if (n.contains("UnknownHost") || n.contains("UnresolvedAddress")) {
                return "Domain does not resolve";
            }
        }
        if (name.contains("ConnectException")) {
            return "Connection refused";
        }
        if (name.contains("SSL")) {
            return "TLS/certificate error";
        }
        return "Unreachable (" + name + ")";
    }

    private static byte[] readQuietly(InputStream in, int limit) {
        try (in) {
            return in.readNBytes(limit);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // nothing to do: the response is being discarded
        }
    }
}
