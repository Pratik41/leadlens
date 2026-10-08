package com.leadlens.quality;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.time.Duration;
import java.util.Hashtable;
import java.util.List;

/**
 * MX lookup through the JDK's built-in DNS provider (no SMTP probing: that is slow, often
 * blocked, and looks like spam to the receiving server).
 *
 * A domain with no MX but an A record still accepts mail (RFC 5321 section 5.1), so both are checked.
 * Answers are cached per domain: one export usually has many addresses at the same company.
 */
@Component
public class DnsMailDomainChecker implements MailDomainChecker {
    private static final Logger log = LoggerFactory.getLogger(DnsMailDomainChecker.class);

    private final Cache<String, Result> cache;
    private final String timeoutMs;
    /** Tried in order: "" = the OS resolver, then the configured fallbacks (the JDK resolver cannot use some OS setups, e.g. link-local IPv6 DNS on Windows). */
    private final List<String> servers;
    private volatile boolean osResolverBroken;

    public DnsMailDomainChecker(@Value("${leadlens.dns.timeout-ms:3000}") long timeoutMs,
                                @Value("${leadlens.dns.fallback-servers:1.1.1.1,8.8.8.8}") List<String> fallbackServers) {
        this.servers = new java.util.ArrayList<>(List.of(""));
        fallbackServers.stream().filter(s -> !s.isBlank()).forEach(s -> servers.add("dns://" + s.trim()));
        this.timeoutMs = String.valueOf(timeoutMs);
        this.cache = Caffeine.newBuilder().maximumSize(20_000).expireAfterWrite(Duration.ofHours(24)).build();
    }

    @Override
    public Result check(String domain) {
        if (domain == null || domain.isBlank()) {
            return Result.UNKNOWN;
        }
        Result cached = cache.getIfPresent(domain);
        if (cached != null) {
            return cached;
        }
        Result result = Result.UNKNOWN;
        for (int i = osResolverBroken ? 1 : 0; i < servers.size(); i++) {
            result = lookup(domain, servers.get(i));
            if (result != Result.UNKNOWN) {
                // The OS resolver failed where a fallback answered: stop paying its timeout on every domain
                if (i > 0 && !osResolverBroken) {
                    osResolverBroken = true;
                    log.info("System DNS resolver is unusable from Java; using {} for MX lookups", servers.subList(1, servers.size()));
                }
                break;
            }
        }
        if (result != Result.UNKNOWN) {
            cache.put(domain, result);   // don't cache a network hiccup
        }
        return result;
    }

    private Result lookup(String domain, String server) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", timeoutMs);
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        if (!server.isEmpty()) {
            env.put("java.naming.provider.url", server);
        }
        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            // One record type per query: asking JNDI for several at once sends a DNS "ANY" query,
            // which modern resolvers refuse or answer minimally (RFC 8482), producing false "no MX" results.
            for (String type : new String[] {"MX", "A", "AAAA"}) {
                Attributes attrs = ctx.getAttributes(domain, new String[] {type});
                Attribute records = attrs.get(type);
                if (hasValues(records)) {
                    // "0 ." is a null MX (RFC 7505): the domain explicitly accepts no mail
                    if (type.equals("MX") && records.size() == 1 && String.valueOf(records.get()).trim().endsWith(" .")) {
                        return Result.NO_MAIL;
                    }
                    return Result.ACCEPTS_MAIL;
                }
            }
            return Result.NO_MAIL;
        } catch (NameNotFoundException e) {
            return Result.NO_MAIL;
        } catch (NamingException e) {
            log.debug("DNS lookup for {} failed: {}", domain, e.toString());
            return Result.UNKNOWN;
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                    // closing a DNS context has nothing to release
                }
            }
        }
    }

    private static boolean hasValues(Attribute attribute) {
        return attribute != null && attribute.size() > 0;
    }
}
