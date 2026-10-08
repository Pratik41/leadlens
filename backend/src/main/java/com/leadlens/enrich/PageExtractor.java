package com.leadlens.enrich;

import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.leadlens.quality.EmailVerifier;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure HTML -> facts extraction (no network), so it is unit-testable against saved pages.
 * Structured sources (mailto:, tel:, meta tags) are preferred over regexes on body text,
 * and every signal keeps the sentence it came from as evidence.
 */
public final class PageExtractor {

    private PageExtractor() {
    }

    public record PageFacts(
        String siteName,
        String description,
        Set<String> emails,
        Set<String> phones,
        String linkedin,
        Integer foundedYear,
        String ownerName,
        String ownerTitle,
        Integer copyrightYear,
        Map<String, Enrichment.Signal> signals,
        List<String> internalLinks) {
    }

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,24}");
    private static final Pattern FOUNDED = Pattern.compile(
        "(?i)\\b(?:since|established(?:\\s+in)?|est\\.?|founded(?:\\s+in)?|in business since|serving [^.]{0,40}? since)\\s+(1[89]\\d{2}|20[0-2]\\d)\\b");
    private static final Pattern COPYRIGHT = Pattern.compile("(?i)(?:©|&copy;|copyright)\\s*(?:(?:19|20)\\d{2}\\s*[-–]\\s*)?((?:19|20)\\d{2})");
    private static final Pattern YEARS_IN_BUSINESS = Pattern.compile("(?i)\\b(over|more than)?\\s*(\\d{2,3})\\+?\\s+years\\s+(?:of\\s+)?(?:experience|in business|serving)");

    private static final Pattern FAMILY = Pattern.compile("(?i)\\bfamily[- ](?:owned|operated|run|business)\\b");
    private static final Pattern OWNER_OPERATED = Pattern.compile("(?i)\\b(?:owner[- ]operated|locally owned|independently owned)\\b");
    private static final Pattern GENERATION = Pattern.compile("(?i)\\b(?:second|third|fourth|2nd|3rd|4th)[- ]generation\\b");
    private static final Pattern RECURRING = Pattern.compile(
        "(?i)\\b(?:maintenance (?:plans?|agreements?|contracts?|programs?)|(?:service|support) (?:agreements?|contracts?|plans?)|(?:weekly|monthly|quarterly|annual) (?:[a-z]+ )?(?:service|plans?|contracts?|maintenance|bookkeeping)|membership plans?|preventive maintenance|recurring)\\b");
    private static final Pattern RETIREMENT = Pattern.compile("(?i)\\b(?:retir(?:e|ing|ement)|succession|business for sale|transition(?:ing)? ownership)\\b");
    private static final Pattern MULTI_LOCATION = Pattern.compile("(?i)\\b(?:(?:\\d+|two|three|four|five|six) (?:locations|offices|branches)|our locations|multiple locations)\\b");
    private static final Pattern COMMERCIAL = Pattern.compile("(?i)\\b(?:commercial (?:clients|customers|accounts|services)|b2b|municipal|industrial clients)\\b");
    private static final Pattern HIRING = Pattern.compile("(?i)\\b(?:we'?re hiring|now hiring|join our team|careers|open positions|job openings)\\b");

    /** "John Smith, Owner" / "Owner: John Smith" / "founded by John Smith". */
    private static final String NAME = "([A-Z][a-z]+(?:\\s[A-Z]\\.)?\\s[A-Z][a-zA-Z'’-]+)";
    private static final String TITLE = "(Owner|Founder|Co-Founder|President|CEO|Principal|Proprietor|Managing Partner)";
    private static final Pattern NAME_THEN_TITLE = Pattern.compile(NAME + ",?\\s(?:–|-|\\||,)?\\s?" + TITLE + "\\b");
    private static final Pattern TITLE_THEN_NAME = Pattern.compile(TITLE + "\\s?(?::|–|-|,)\\s?" + NAME);
    private static final Pattern FOUNDED_BY = Pattern.compile("(?i:founded by|started by|owned by)\\s" + NAME);

    private static final Pattern FOLLOW = Pattern.compile("(?i)(about|contact|team|our-story|history|leadership|who-we-are|meet)");

    public static PageFacts extract(Document doc, String domain) {
        Set<String> emails = new LinkedHashSet<>();
        Set<String> phones = new LinkedHashSet<>();
        Map<String, Enrichment.Signal> signals = new LinkedHashMap<>();
        Set<String> links = new LinkedHashSet<>();
        String linkedinCompany = null;
        String linkedinPerson = null;

        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href").trim();
            String lower = href.toLowerCase(Locale.ROOT);
            if (lower.startsWith("mailto:")) {
                String e = EmailVerifier.normalise(href.replaceAll("\\?.*$", ""));
                if (e != null && EMAIL.matcher(e).matches()) emails.add(e);
            } else if (lower.startsWith("tel:")) {
                phones.add(href.substring(4).trim());
            } else if (lower.contains("linkedin.com/company/")) {
                if (linkedinCompany == null) linkedinCompany = href;
            } else if (lower.contains("linkedin.com/in/")) {
                if (linkedinPerson == null) linkedinPerson = href;
            } else {
                String abs = a.absUrl("href").replaceAll("#.*$", "");
                if (!abs.isEmpty() && sameSite(abs, domain) && FOLLOW.matcher(abs + " " + a.text()).find()) {
                    links.add(abs);
                }
                if (HIRING.matcher(a.text() + " " + href).find()) {
                    signals.putIfAbsent("HIRING", new Enrichment.Signal("HIRING", "Hiring",
                        "Site links to \"" + clip(a.text().isBlank() ? href : a.text(), 50) + "\""));
                }
            }
        }

        String text = doc.body() == null ? "" : doc.body().text();
        Matcher em = EMAIL.matcher(text);
        while (em.find() && emails.size() < 10) {
            String e = em.group().toLowerCase(Locale.ROOT);
            if (!e.matches(".*\\.(png|jpe?g|gif|svg|webp)$") && !e.contains("sentry") && !e.contains("wixpress")
                && !e.contains("example.com")) {
                emails.add(e);
            }
        }
        if (phones.isEmpty()) {
            for (PhoneNumberMatch m : PhoneNumberUtil.getInstance().findNumbers(text, "US")) {
                phones.add(m.rawString());
                if (phones.size() >= 3) break;
            }
        }

        Integer founded = null;
        Matcher fm = FOUNDED.matcher(text);
        if (fm.find()) {
            founded = Integer.parseInt(fm.group(1));
            signals.put("FOUNDED", new Enrichment.Signal("FOUNDED", "Founded " + founded, quote(text, fm.start(), fm.end())));
        } else {
            Matcher ym = YEARS_IN_BUSINESS.matcher(text);
            if (ym.find()) {
                int years = Integer.parseInt(ym.group(2));
                if (years >= 3 && years <= 150) {
                    founded = Year.now().getValue() - years;
                    signals.put("FOUNDED", new Enrichment.Signal("FOUNDED", years + "+ years in business",
                        quote(text, ym.start(), ym.end())));
                }
            }
        }

        Integer copyright = null;
        Matcher cm = COPYRIGHT.matcher(doc.html());
        while (cm.find()) {
            int y = Integer.parseInt(cm.group(1));
            if (y <= Year.now().getValue() && (copyright == null || y > copyright)) copyright = y;
        }

        textSignals(text, "").forEach(signals::putIfAbsent);

        String ownerName = null, ownerTitle = null;
        Matcher nt = NAME_THEN_TITLE.matcher(text);
        Matcher tn = TITLE_THEN_NAME.matcher(text);
        Matcher fb = FOUNDED_BY.matcher(text);
        if (nt.find()) {
            ownerName = nt.group(1);
            ownerTitle = nt.group(2);
        } else if (tn.find()) {
            ownerName = tn.group(2);
            ownerTitle = tn.group(1);
        } else if (fb.find()) {
            ownerName = fb.group(1);
            ownerTitle = "Founder";
        }
        if (ownerName != null) {
            signals.putIfAbsent("OWNER_NAMED", new Enrichment.Signal("OWNER_NAMED", "Owner named on site",
                ownerName + (ownerTitle == null ? "" : ", " + ownerTitle)));
        }

        String siteName = meta(doc, "og:site_name");
        if (siteName == null && doc.title() != null && !doc.title().isBlank()) {
            siteName = doc.title().split("\\s[|\\-–—:]\\s")[0].trim();
        }
        String description = firstNonNull(meta(doc, "description"), meta(doc, "og:description"));

        return new PageFacts(siteName, description == null ? null : clip(description, 600), emails, phones,
            linkedinCompany != null ? linkedinCompany : linkedinPerson, founded, ownerName, ownerTitle, copyright,
            signals, new ArrayList<>(links));
    }

    /**
     * Business signals in free text: a web page, or the description column of an import.
     * {@code source} prefixes the evidence (e.g. "Export description: ") so users know where it came from.
     */
    public static Map<String, Enrichment.Signal> textSignals(String text, String source) {
        Map<String, Enrichment.Signal> signals = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return signals;
        }
        signal(signals, "FAMILY_OWNED", "Family-owned", FAMILY, text, source);
        signal(signals, "OWNER_OPERATED", "Owner-operated", OWNER_OPERATED, text, source);
        signal(signals, "MULTI_GENERATION", "Multi-generation business", GENERATION, text, source);
        signal(signals, "RECURRING_REVENUE", "Recurring revenue", RECURRING, text, source);
        signal(signals, "RETIREMENT", "Retirement / succession mention", RETIREMENT, text, source);
        signal(signals, "MULTI_LOCATION", "Multiple locations", MULTI_LOCATION, text, source);
        signal(signals, "COMMERCIAL_CLIENTS", "Commercial clients", COMMERCIAL, text, source);
        return signals;
    }

    private static void signal(Map<String, Enrichment.Signal> out, String code, String label, Pattern p, String text, String source) {
        Matcher m = p.matcher(text);
        if (m.find()) out.putIfAbsent(code, new Enrichment.Signal(code, label, source + quote(text, m.start(), m.end())));
    }

    static boolean sameSite(String url, String domain) {
        try {
            String h = URI.create(url.replace(" ", "%20")).getHost();
            if (h == null) return false;
            h = h.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            return h.equals(domain.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String meta(Document doc, String name) {
        Element e = doc.selectFirst("meta[name=" + name + "], meta[property=" + name + "]");
        if (e == null) return null;
        String c = e.attr("content").trim();
        return c.isEmpty() ? null : c;
    }

    /** The matched phrase with ~50 characters of context either side, as shown in the UI. */
    private static String quote(String text, int start, int end) {
        int s = Math.max(0, start - 50), e = Math.min(text.length(), end + 50);
        return (s > 0 ? "…" : "") + text.substring(s, e).trim() + (e < text.length() ? "…" : "");
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
