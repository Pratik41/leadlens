package com.leadlens.web;

import com.leadlens.domain.EmailStatus;
import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadStatus;
import com.leadlens.domain.Tier;

/** The one thing to do next with a lead, so a rep can work the list top to bottom without thinking. */
public record NextAction(String channel, String label) {

    public static NextAction of(Lead l) {
        String who = l.getOwnerName() != null ? l.getOwnerName().split("\\s+")[0] : null;
        if (l.getTier() == Tier.X) {
            return new NextAction("skip", "Skip: " + (l.getExcludedReason() == null ? "excluded" : l.getExcludedReason()));
        }
        if (l.getStatus() == LeadStatus.DISQUALIFIED) return new NextAction("skip", "Disqualified");
        if (l.getStatus() == LeadStatus.REPLIED) return new NextAction("call", "Book the intro call");
        if (l.getStatus() == LeadStatus.CONTACTED) {
            return new NextAction(l.isPhoneValid() ? "call" : "email",
                l.isPhoneValid() ? "Follow up by phone" : "Send a follow-up email");
        }
        if (l.getEmailStatus() == EmailStatus.VALID) {
            return new NextAction("email", who != null ? "Email " + who + " directly" : "Send the intro email");
        }
        if (l.isPhoneValid()) {
            return new NextAction("call", who != null ? "Call and ask for " + who : "Call the main line");
        }
        if (l.getEmailStatus() == EmailStatus.ROLE || l.getEmailStatus() == EmailStatus.UNVERIFIED) {
            return new NextAction("email", who != null ? "Email, addressed to " + who : "Email the shared inbox");
        }
        if (l.getLinkedinUrl() != null) return new NextAction("linkedin", "Connect on LinkedIn");
        return new NextAction("research", "Find a contact first");
    }
}
