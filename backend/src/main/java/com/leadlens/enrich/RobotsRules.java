package com.leadlens.enrich;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal robots.txt evaluation: picks the group for our user agent (else "*") and applies
 * the longest matching Allow/Disallow prefix, as Google and RFC 9309 do. Wildcards are
 * supported for "*" and the "$" end anchor.
 */
public final class RobotsRules {

    public static final RobotsRules ALLOW_ALL = new RobotsRules(List.of(), List.of());
    /** Used when robots.txt answers 5xx: the site may be struggling, so stay out for now (RFC 9309 2.3.1.4). */
    public static final RobotsRules DISALLOW_ALL = new RobotsRules(List.of(), List.of("/"));

    private final List<String> allow;
    private final List<String> disallow;

    private RobotsRules(List<String> allow, List<String> disallow) {
        this.allow = allow;
        this.disallow = disallow;
    }

    public static RobotsRules parse(String body, String agentToken) {
        String agent = agentToken.toLowerCase(Locale.ROOT);
        List<String> specificAllow = new ArrayList<>(), specificDisallow = new ArrayList<>();
        List<String> starAllow = new ArrayList<>(), starDisallow = new ArrayList<>();
        boolean sawSpecific = false;
        List<String> currentAgents = new ArrayList<>();
        boolean lastWasAgent = false;
        for (String rawLine : body.split("\\r?\\n")) {
            String line = rawLine.replaceAll("#.*$", "").trim();
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                if (!lastWasAgent) currentAgents.clear();
                currentAgents.add(value.toLowerCase(Locale.ROOT));
                lastWasAgent = true;
                continue;
            }
            lastWasAgent = false;
            if (!key.equals("allow") && !key.equals("disallow")) continue;
            boolean specific = currentAgents.stream().anyMatch(a -> !a.equals("*") && agent.contains(a));
            boolean star = currentAgents.contains("*");
            if (specific) sawSpecific = true;
            if (key.equals("disallow") && value.isEmpty()) continue; // "Disallow:" with no path allows everything
            if (specific) (key.equals("allow") ? specificAllow : specificDisallow).add(value);
            if (star) (key.equals("allow") ? starAllow : starDisallow).add(value);
        }
        return sawSpecific ? new RobotsRules(specificAllow, specificDisallow) : new RobotsRules(starAllow, starDisallow);
    }

    public boolean allows(String path) {
        String p = path == null || path.isEmpty() ? "/" : path;
        int bestAllow = longestMatch(allow, p), bestDisallow = longestMatch(disallow, p);
        return bestDisallow < 0 || bestAllow >= bestDisallow;
    }

    private static int longestMatch(List<String> rules, String path) {
        int best = -1;
        for (String r : rules) {
            if (matches(r, path) && r.length() > best) best = r.length();
        }
        return best;
    }

    static boolean matches(String rule, String path) {
        boolean anchored = rule.endsWith("$");
        String r = anchored ? rule.substring(0, rule.length() - 1) : rule;
        StringBuilder regex = new StringBuilder("^");
        for (String part : r.split("\\*", -1)) {
            if (regex.length() > 1) regex.append(".*");
            regex.append(java.util.regex.Pattern.quote(part));
        }
        if (anchored) regex.append("$");
        return java.util.regex.Pattern.compile(regex.toString()).matcher(path).find();
    }
}
