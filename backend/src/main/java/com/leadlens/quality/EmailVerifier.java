package com.leadlens.quality;

import com.leadlens.domain.EmailStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Grades an email for outreach: is it an address, can its domain receive mail,
 * and does it reach a person or a shared inbox.
 */
@Component
public class EmailVerifier {

    private static final Pattern SYNTAX = Pattern.compile(
        "^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");

    private static final Set<String> ROLE_MAILBOXES = Set.of(
        "info", "sales", "contact", "contactus", "hello", "office", "admin", "support", "service", "services",
        "team", "enquiries", "inquiries", "enquiry", "inquiry", "help", "billing", "accounts", "accounting",
        "marketing", "hr", "jobs", "careers", "noreply", "no-reply", "donotreply", "mail", "email", "frontdesk",
        "reception", "booking", "bookings", "appointments", "orders", "customerservice", "general", "webmaster");

    private static final Set<String> DISPOSABLE = Set.of(
        "mailinator.com", "guerrillamail.com", "10minutemail.com", "tempmail.com", "temp-mail.org",
        "yopmail.com", "trashmail.com", "getnada.com", "dispostable.com", "sharklasers.com", "throwawaymail.com",
        "maildrop.cc", "fakeinbox.com", "mintemail.com");

    public record Verdict(String email, EmailStatus status) {
    }

    private final MailDomainChecker mailDomains;

    public EmailVerifier(MailDomainChecker mailDomains) {
        this.mailDomains = mailDomains;
    }

    /** Syntax and mailbox type only: no network. */
    public static Verdict syntaxOnly(String raw) {
        String email = normalise(raw);
        if (email == null) {
            return new Verdict(null, EmailStatus.MISSING);
        }
        if (!SYNTAX.matcher(email).matches()) {
            return new Verdict(raw.strip(), EmailStatus.INVALID);
        }
        String domain = domainOf(email);
        if (DISPOSABLE.contains(domain)) {
            return new Verdict(email, EmailStatus.DISPOSABLE);
        }
        return new Verdict(email, isRole(email) ? EmailStatus.ROLE : EmailStatus.VALID);
    }

    public Verdict verify(String raw) {
        Verdict syntax = syntaxOnly(raw);
        if (syntax.status() != EmailStatus.VALID && syntax.status() != EmailStatus.ROLE) {
            return syntax;
        }
        String domain = domainOf(syntax.email());
        if (Normalizer.isReservedDomain(domain)) {
            return new Verdict(syntax.email(), EmailStatus.UNVERIFIED);
        }
        return switch (mailDomains.check(domain)) {
            case ACCEPTS_MAIL -> syntax;
            case NO_MAIL -> new Verdict(syntax.email(), EmailStatus.NO_MX);
            case UNKNOWN -> new Verdict(syntax.email(), EmailStatus.UNVERIFIED);
        };
    }

    public static String normalise(String raw) {
        String e = Normalizer.clean(raw);
        if (e == null) {
            return null;
        }
        e = e.toLowerCase(Locale.ROOT).replaceFirst("^mailto:", "").replaceAll("[<>\"]", "").trim();
        return e.isEmpty() ? null : e;
    }

    public static String domainOf(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        return at < 0 ? null : email.substring(at + 1);
    }

    public static boolean isRole(String email) {
        int at = email.indexOf('@');
        String local = at < 0 ? email : email.substring(0, at);
        return ROLE_MAILBOXES.contains(local.replace(".", ""));
    }
}
