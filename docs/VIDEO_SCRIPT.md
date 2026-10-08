# 2-minute walkthrough script

Target length: 1:50–2:00. Screen-record the app (http://localhost:8080, or :4300 with `ng serve`) starting from an
empty workspace (**Reset workspace** at the bottom of the Leads tab).

---

**0:00 – 0:12 · The problem**
> "SaaSquatch finds companies. But a raw export is messy: duplicates, emails that bounce, franchises, companies ten
> times too big, and nothing tells you which owner might sell. I built LeadLens, the step between *scraped* and
> *contacted*."

**0:12 – 0:35 · Clean and verify** *(click "Try the sample")*
> "Thirty-six raw rows. LeadLens mapped the columns, merged three duplicates (one with no website, matched by name
> and state), skipped the empty row, and graded every email: verified, shared inbox, throwaway or bouncing."

**0:35 – 0:55 · Rank with reasons** *(open Lone Star Comfort)*
> "Every lead is scored against my buy box. In acquisition mode it rewards what a searcher cares about: 38 years in
> business, family-owned, recurring maintenance revenue, a named owner. Here's every reason behind the 91. The
> franchise and the 2,400-person company are excluded, and it says why."

**0:55 – 1:12 · Ask your list** *(type: "family-owned HVAC in Texas over 20 years, not contacted")*
> "I can ask in plain English. Claude turns the question into exact filters (it never touches the data itself), so
> the answer is precise, removable and exportable."

**1:12 – 1:30 · Act** *(Write the brief → Contacted → Today tab)*
> "One click writes a brief and first email from verified facts only. I mark it contacted, and LeadLens schedules
> the follow-up three business days out. The Today tab is my call list: follow-ups due first, then the best
> untouched leads."

**1:30 – 1:45 · Insights and CRM** *(Insights tab → click a recommendation → Export menu)*
> "Insights shows the funnel, where Tier A leads concentrate, data quality, and recommendations that open the
> matching leads. Everything exports to HubSpot or Salesforce, or pushes to Zapier through a webhook."

**1:45 – 2:00 · Architecture and close**
> "Angular 22 on a Spring Boot / Java 17 API, PostgreSQL on Neon, Caffeine caching, a bounded crawler that obeys
> robots.txt and never bypasses CAPTCHAs, 32 tests including a 5,000-row benchmark, and CI building one Docker image.
> The result: every morning starts with a short, verified, ranked list, the reason for each lead, and the first message."
