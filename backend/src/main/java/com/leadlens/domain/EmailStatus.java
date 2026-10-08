package com.leadlens.domain;

/** Outcome of email verification, from most to least useful for outreach. */
public enum EmailStatus {
    /** Personal mailbox, syntax fine and the domain accepts mail (MX record). */
    VALID,
    /** Deliverable but generic (info@, sales@...): reaches a shared inbox, not the owner. */
    ROLE,
    /** Syntax fine, but the mail servers could not be checked (DNS unavailable or a reserved demo domain). */
    UNVERIFIED,
    /** The domain has no mail servers: mail will bounce. */
    NO_MX,
    /** Throwaway mailbox provider. */
    DISPOSABLE,
    /** Not an email address. */
    INVALID,
    MISSING;

    public boolean isReachable() {
        return this == VALID || this == ROLE || this == UNVERIFIED;
    }
}
