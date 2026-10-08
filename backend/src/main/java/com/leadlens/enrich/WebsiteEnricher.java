package com.leadlens.enrich;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.leadlens.domain.WebsiteStatus;
import com.leadlens.quality.Normalizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a company's own public website: the home page plus up to two "about"/"contact" pages,
 * with a pause between pages. Results are cached per domain for 24 hours, so re-imports and
 * duplicate rows never crawl the same site twice.
 */
@Component
public class WebsiteEnricher {

    private final SafeFetcher fetcher;
    private final int extraPages;
    private final long politenessDelayMs;
    private final Cache<String, Enrichment> cache =
        Caffeine.newBuilder().maximumSize(20_000).expireAfterWrite(Duration.ofHours(24)).build();

    public WebsiteEnricher(SafeFetcher fetcher,
                           @Value("${leadlens.crawler.extra-pages:2}") int extraPages,
                           @Value("${leadlens.crawler.delay-ms:400}") long politenessDelayMs) {
        this.fetcher = fetcher;
        this.extraPages = extraPages;
        this.politenessDelayMs = politenessDelayMs;
    }

    public Enrichment enrich(String domain) {
        if (domain == null) {
            return Enrichment.notCrawled(WebsiteStatus.SKIPPED, "No website");
        }
        if (Normalizer.isReservedDomain(domain)) {
            return Enrichment.notCrawled(WebsiteStatus.SKIPPED, "Reserved demo domain: not crawled");
        }
        return cache.get(domain, this::crawl);
    }

    public void evict(String domain) {
        if (domain != null) {
            cache.invalidate(domain);
        }
    }

    private Enrichment crawl(String domain) {
        SafeFetcher.Result home = fetcher.fetchPage(URI.create("https://" + domain + "/"));
        boolean https = true;
        if (home.outcome() == SafeFetcher.Outcome.UNREACHABLE || home.outcome() == SafeFetcher.Outcome.TIMEOUT) {
            SafeFetcher.Result plain = fetcher.fetchPage(URI.create("http://" + domain + "/"));
            if (plain.ok()) {
                home = plain;
                https = "https".equals(plain.finalUri().getScheme());
            }
        }
        if (!home.ok()) {
            return Enrichment.notCrawled(statusFor(home.outcome()), home.note());
        }
        Document homeDoc = parse(home);
        if (homeDoc == null) {
            return Enrichment.notCrawled(WebsiteStatus.DEAD, "Page could not be parsed");
        }
        if (BotChallenge.looksLikeChallenge(homeDoc.outerHtml())) {
            return Enrichment.notCrawled(WebsiteStatus.PROTECTED, "Behind a bot check / CAPTCHA; not bypassed");
        }

        List<String> pagesRead = new ArrayList<>(List.of(home.finalUri().getPath().isEmpty() ? "/" : home.finalUri().getPath()));
        PageExtractor.PageFacts first = PageExtractor.extract(homeDoc, domain);
        List<PageExtractor.PageFacts> facts = new ArrayList<>(List.of(first));

        for (String link : first.internalLinks().stream().limit(extraPages).toList()) {
            sleep();
            SafeFetcher.Result page = fetcher.fetchPage(URI.create(link));
            if (page.ok()) {
                Document doc = parse(page);
                if (doc != null && !BotChallenge.looksLikeChallenge(doc.outerHtml())) {
                    facts.add(PageExtractor.extract(doc, domain));
                    pagesRead.add(page.finalUri().getPath());
                }
            }
        }
        return merge(facts, pagesRead, https, domain);
    }

    private Enrichment merge(List<PageExtractor.PageFacts> pages, List<String> pagesRead, boolean https, String domain) {
        PageExtractor.PageFacts home = pages.get(0);
        Set<String> emails = new LinkedHashSet<>();
        Set<String> phones = new LinkedHashSet<>();
        Map<String, Enrichment.Signal> signals = new LinkedHashMap<>();
        String linkedin = null;
        Integer founded = null;
        String owner = null;
        String ownerTitle = null;
        String description = home.description();
        Integer copyright = null;

        for (PageExtractor.PageFacts p : pages) {
            emails.addAll(p.emails());
            phones.addAll(p.phones());
            p.signals().forEach(signals::putIfAbsent);
            linkedin = linkedin != null ? linkedin : p.linkedin();
            if (p.foundedYear() != null && (founded == null || p.foundedYear() < founded)) {
                founded = p.foundedYear();
            }
            if (owner == null && p.ownerName() != null) {
                owner = p.ownerName();
                ownerTitle = p.ownerTitle();
            }
            if (description == null) {
                description = p.description();
            }
            if (p.copyrightYear() != null) {
                copyright = copyright == null ? p.copyrightYear() : Math.max(copyright, p.copyrightYear());
            }
        }

        if (copyright != null && copyright <= Year.now().getValue() - 4) {
            signals.put("STALE_WEBSITE", new Enrichment.Signal("STALE_WEBSITE", "Website not updated since " + copyright,
                "Latest copyright year on the site is " + copyright));
        }
        if (!https) {
            signals.put("NO_HTTPS", new Enrichment.Signal("NO_HTTPS", "Website has no HTTPS", "Only reachable over http://"));
        }

        // Prefer addresses at the company's own domain; free-mail addresses go last
        List<String> sortedEmails = new ArrayList<>(emails);
        sortedEmails.sort((a, b) -> Boolean.compare(!a.endsWith("@" + domain), !b.endsWith("@" + domain)));

        return new Enrichment(WebsiteStatus.LIVE, "Read " + pagesRead.size() + " page" + (pagesRead.size() == 1 ? "" : "s"),
            home.siteName(), description, sortedEmails, new ArrayList<>(phones), linkedin, founded, owner, ownerTitle,
            new ArrayList<>(signals.values()), pagesRead);
    }

    private static WebsiteStatus statusFor(SafeFetcher.Outcome outcome) {
        return switch (outcome) {
            case ROBOTS_DISALLOWED -> WebsiteStatus.ROBOTS_BLOCKED;
            case CHALLENGED -> WebsiteStatus.PROTECTED;
            case BLOCKED_ADDRESS -> WebsiteStatus.SKIPPED;
            default -> WebsiteStatus.DEAD;
        };
    }

    private static Document parse(SafeFetcher.Result result) {
        try {
            return Jsoup.parse(new ByteArrayInputStream(result.body()), null, result.finalUri().toString());
        } catch (IOException e) {
            return null;
        }
    }

    private void sleep() {
        try {
            Thread.sleep(politenessDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
