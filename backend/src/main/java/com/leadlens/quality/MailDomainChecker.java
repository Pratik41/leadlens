package com.leadlens.quality;

/** Answers "can this domain receive mail?". A seam so tests don't need the network. */
public interface MailDomainChecker {

    enum Result { ACCEPTS_MAIL, NO_MAIL, UNKNOWN }

    Result check(String domain);
}
