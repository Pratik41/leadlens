package com.leadlens.ai;

import com.leadlens.domain.EmailStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic brief from the same facts Claude would see. Used when no API key is configured,
 * or when the model call fails, so the feature always works and never invents facts.
 */
@Component
public class TemplateBriefWriter {


    public Brief write(LeadFacts f) {
        var lead = f.lead();
        String company = lead.getCompany();
        String hi = f.firstName() != null ? "Hi " + f.firstName() + "," : "Hello,";
        Integer years = f.yearsInBusiness();
        String industry = lead.getIndustry() == null ? "local"
            : lead.getIndustry().equals(lead.getIndustry().toUpperCase()) ? lead.getIndustry() : lead.getIndustry().toLowerCase();
        String place = f.place();

        String headline = (years != null ? years + "-year-old " : "") + industry + " business"
            + (place != null ? " in " + place : "")
            + (f.has("FAMILY_OWNED") ? ", family-owned" : f.has("OWNER_OPERATED") ? ", owner-operated" : "");
        headline = Character.toUpperCase(headline.charAt(0)) + headline.substring(1);

        StringBuilder summary = new StringBuilder(company);
        summary.append(lead.getIndustry() != null ? " is a " + industry + " company" : " is a company");
        if (place != null) summary.append(" based in ").append(place);
        if (lead.getFoundedYear() != null) summary.append(", founded in ").append(lead.getFoundedYear());
        summary.append('.');
        if (lead.getEmployees() != null) summary.append(" Roughly ").append(lead.getEmployees()).append(" employees");
        if (lead.getRevenueUsd() != null) {
            summary.append(lead.getEmployees() != null ? " and about " : " About ").append(money(lead.getRevenueUsd()))
                .append(" in annual revenue");
        }
        if (lead.getEmployees() != null || lead.getRevenueUsd() != null) summary.append('.');
        if (lead.getOwnerName() != null) {
            summary.append(' ').append(lead.getOwnerName())
                .append(lead.getOwnerTitle() != null ? " (" + lead.getOwnerTitle() + ")" : "").append(" runs the business.");
        }

        String whyNow;
        if (f.has("RETIREMENT")) {
            whyNow = "The website mentions retirement or succession, the most common trigger for an owner to sell.";
        } else if (f.acquisition() && years != null && years >= 20) {
            whyNow = years + " years in business suggests a long-tenured owner who may be thinking about succession.";
        } else if (f.has("HIRING")) {
            whyNow = "They are hiring, which usually means growth, budget and new problems to solve.";
        } else if (f.has("STALE_WEBSITE")) {
            whyNow = "The website looks unmaintained: a concrete, easy improvement to open the conversation with.";
        } else {
            whyNow = "Strong fit with your " + (f.acquisition() ? "buy box" : "ideal customer profile") + " and reachable today.";
        }

        List<String> points = new ArrayList<>();
        if (f.score() != null) {
            points.addAll(f.score().highlights(3));
        }
        if (f.acquisition()) {
            points.add("Ask what they would want for the business, the team and customers in five years");
        } else {
            points.add("Ask how they handle " + (lead.getIndustry() != null ? industry + " " : "") + "growth today");
        }

        String subject;
        String body;
        if (f.acquisition()) {
            subject = company + "'s next chapter";
            body = hi + "\n\n"
                + "I came across " + company + (years != null ? " and the " + years + " years you've spent building it" : "")
                + (place != null ? " in " + place : "") + ". I'm an operator looking to acquire and grow one great "
                + industry + " business, keeping the name, the team and the customer relationships intact.\n\n"
                + "If you've ever thought about what succession could look like, on your timeline, I'd value a "
                + "confidential 15-minute conversation. No brokers, no pressure.\n\n"
                + "Would next week work for a quick call?\n\n[Your name]";
        } else {
            subject = "Quick idea for " + company;
            body = hi + "\n\n"
                + "I work with " + industry + " businesses" + (place != null ? " around " + place : "")
                + " and noticed " + company + (f.has("HIRING") ? " is hiring" : f.has("STALE_WEBSITE")
                    ? "'s website hasn't been updated in a while" : " is well established") + ". "
                + "Companies like yours usually come to us to win more work without adding admin.\n\n"
                + "Worth a 15-minute call to see if it fits? Happy to share what's worked for similar teams.\n\n[Your name]";
        }
        String opener = (f.firstName() != null ? "Hi " + f.firstName() + ", " : "Hi, ")
            + (f.acquisition()
                ? "I'm calling because I'm looking to buy and grow one " + industry + " business, and "
                    + company + " stood out. Do you have two minutes?"
                : "I help " + industry + " companies like " + company + " grow. Is now a bad time?");

        List<String> risks = new ArrayList<>();
        if (lead.getOwnerName() == null) risks.add("Owner not identified: find the decision maker before reaching out");
        if (lead.getEmailStatus() == EmailStatus.ROLE) risks.add("Only a shared inbox: ask for the owner by name");
        if (!lead.getEmailStatus().isReachable() && !lead.isPhoneValid()) risks.add("No verified email or phone yet");
        if (lead.getRevenueUsd() == null) risks.add("Revenue unknown: confirm size early");
        if (risks.isEmpty()) risks.add("Confirm the figures from the export on the first call");

        return new Brief(headline, summary.toString(), whyNow, points, subject, body, opener,
            risks.subList(0, Math.min(3, risks.size())));
    }

    private static String money(long v) {
        if (v >= 1_000_000) return "$" + (v >= 10_000_000 ? String.valueOf(Math.round(v / 1e6)) : String.format("%.1f", v / 1e6)) + "M";
        if (v >= 1_000) return "$" + Math.round(v / 1e3) + "K";
        return "$" + v;
    }
}
