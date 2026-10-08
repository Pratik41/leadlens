package com.leadlens.domain;

public enum WebsiteStatus {
    UNCHECKED,
    /** Answered with a page we could read. */
    LIVE,
    /** DNS failure, connection refused, timeout or an HTTP error. */
    DEAD,
    /** robots.txt asks crawlers to stay out; we respect it and only know the site exists. */
    ROBOTS_BLOCKED,
    /** Answered, but behind a CAPTCHA or bot challenge; we do not try to bypass it. */
    PROTECTED,
    /** Not crawled on purpose: no website, a reserved demo domain, or an address we refuse to fetch. */
    SKIPPED
}
