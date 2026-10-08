package com.leadlens.ai;

import com.leadlens.quality.Normalizer;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic fallback for "ask your list" when no model is configured. Covers the questions
 * people actually type: tiers, states, industries, business age, signals, contactability, status, sort.
 */
public final class RuleQueryParser {

    private RuleQueryParser() {
    }

    private static final Map<String, String> SIGNALS = Map.of(
        "(family|families)", "FAMILY_OWNED",
        "(owner[- ]operated|owner[- ]run|locally owned)", "OWNER_OPERATED",
        "(generation)", "MULTI_GENERATION",
        "(recurring|maintenance|service (plans?|contracts?|agreements?)|contracts?)", "RECURRING_REVENUE",
        "(retir|succession|exit|selling|for sale)", "RETIREMENT",
        "(multi(ple)?[- ]location|locations|branches)", "MULTI_LOCATION",
        "(commercial|b2b)", "COMMERCIAL_CLIENTS",
        "(hiring|growing|jobs)", "HIRING",
        "(stale|outdated|old website)", "STALE_WEBSITE");

    private static final Pattern YEARS = Pattern.compile(
        "(?:over|more than|older than|at least|>=?|\\+)\\s*(\\d{1,3})\\s*\\+?\\s*(?:years|yrs|year)|(\\d{1,3})\\s*\\+\\s*(?:years|yrs)");
    private static final Pattern BEFORE_YEAR = Pattern.compile("(?:founded |established |since |started )?before\\s+(1[89]\\d{2}|20\\d{2})");
    private static final Pattern STATE_CODE = Pattern.compile("\\b([A-Z]{2})\\b");

    public static QueryPlan parse(String question, Collection<String> knownIndustries) {
        String raw = question == null ? "" : question.trim();
        String s = " " + raw.toLowerCase(Locale.ROOT) + " ";
        List<String> understood = new ArrayList<>();

        String tier = null;
        if (s.matches(".*\\b(excluded|tier x|disqualified by score)\\b.*")) tier = "X";
        else if (s.matches(".*\\b(tier a|a[- ]tier|a leads|hottest|best|top|hot)\\b.*")) tier = "A";
        else if (s.matches(".*\\b(tier b|b leads|warm)\\b.*")) tier = "B";
        else if (s.matches(".*\\b(good|qualified leads|a and b|a/b|a or b)\\b.*")) tier = "A,B";
        if (tier != null) understood.add("tier " + tier);

        String status = null;
        if (s.matches(".*\\b(not (yet )?contacted|uncontacted|untouched|never contacted|new leads?)\\b.*")) status = "NEW";
        else if (s.matches(".*\\b(replied|responded|answered)\\b.*")) status = "REPLIED";
        else if (s.matches(".*\\b(follow[- ]?ups?|contacted|reached out)\\b.*")) status = "CONTACTED";
        else if (s.matches(".*\\b(qualified)\\b.*") && !"A,B".equals(tier)) status = "QUALIFIED";
        if (status != null) understood.add(status.toLowerCase(Locale.ROOT));

        String contact = null;
        if (s.matches(".*\\b(no (usable )?contact|unreachable|no email and no phone)\\b.*")) contact = "none";
        else if (s.matches(".*\\b(verified|personal) email.*")) contact = "verified";
        else if (s.matches(".*\\b(with|has|have) (an )?email.*") || s.contains(" emailable ")) contact = "email";
        else if (s.matches(".*\\b(phone|call|callable|ring)\\b.*")) contact = "phone";
        if (contact != null) understood.add("contact: " + contact);

        String state = stateIn(raw, s);
        if (state != null) understood.add("in " + state);

        String industry = knownIndustries.stream()
            .filter(i -> i != null && i.length() >= 3)
            .sorted(Comparator.comparingInt(String::length).reversed())
            .filter(i -> Pattern.compile("\\b" + Pattern.quote(i.toLowerCase(Locale.ROOT)) + "s?\\b").matcher(s).find())
            .findFirst().orElse(null);
        if (industry != null) understood.add(industry);

        Integer minYears = null;
        Matcher y = YEARS.matcher(s);
        if (y.find()) minYears = Integer.parseInt(y.group(1) != null ? y.group(1) : y.group(2));
        Matcher b = BEFORE_YEAR.matcher(s);
        if (minYears == null && b.find()) minYears = Year.now().getValue() - Integer.parseInt(b.group(1));
        if (minYears == null && s.matches(".*\\b(old|established|long[- ]standing|mature)\\b.*")
            && !s.contains("old website")) minYears = 20;
        if (minYears != null) understood.add(minYears + "+ years in business");

        String signal = null;
        for (Map.Entry<String, String> e : SIGNALS.entrySet()) {
            if (Pattern.compile("\\b" + e.getKey()).matcher(s).find()) {
                signal = e.getValue();
                break;
            }
        }
        if (signal != null) understood.add(signal.toLowerCase(Locale.ROOT).replace('_', ' '));

        String sort = null;
        if (s.matches(".*\\b(oldest|longest)\\b.*")) sort = "foundedYear,asc";
        else if (s.matches(".*\\b(biggest|largest|highest revenue|most revenue)\\b.*")) sort = "revenueUsd,desc";
        else if (s.matches(".*\\b(most employees|most staff)\\b.*")) sort = "employees,desc";

        String explanation = understood.isEmpty()
            ? "Searched for \"" + raw + "\""
            : "Showing " + String.join(" · ", understood);
        String q = understood.isEmpty() && !raw.isEmpty() ? raw : null;
        return new QueryPlan(q, tier, status, contact, state, industry, minYears, signal, sort, explanation);
    }

    /** Full state names anywhere; two-letter codes only when typed in capitals ("IN" vs the word "in"). */
    private static String stateIn(String raw, String lower) {
        for (String name : List.of("district of columbia", "new hampshire", "new jersey", "new mexico", "new york",
            "north carolina", "north dakota", "rhode island", "south carolina", "south dakota", "west virginia")) {
            if (lower.contains(" " + name + " ") || lower.contains(" " + name + ",")) return Normalizer.state(name);
        }
        for (String word : lower.split("[^a-z]+")) {
            String code = Normalizer.state(word);
            if (code != null && code.length() == 2 && word.length() > 2 && code.equals(code.toUpperCase(Locale.ROOT))
                && !code.equalsIgnoreCase(word)) {
                return code;
            }
        }
        Matcher m = STATE_CODE.matcher(raw);
        while (m.find()) {
            String c = Normalizer.state(m.group(1));
            if (c != null && c.length() == 2 && !List.of("OR", "IN", "ME", "OK", "HI").contains(m.group(1))) return c;
        }
        return null;
    }
}
