# 2-minute walkthrough script

Target length: 1:50–2:00. Screen-record the app at http://localhost:8080 with an empty workspace.

---

**0:00 – 0:15 · The problem**
> "SaaSquatch is great at finding companies. But a raw export is messy: the same business on three rows, emails that
> bounce, franchises and companies ten times too big. And nothing tells you which owner might actually want to sell.
> Reps lose hours in spreadsheets before the first call. I built LeadLens, the step between *scraped* and *contacted*."

**0:15 – 0:40 · Import and clean** *(click "Try the sample")*
> "Here's a 36-row SaaSquatch-style export. In a couple of seconds LeadLens mapped the columns automatically, merged
> three duplicates (including one row with no website, matched by name and state), skipped the empty row, and told me
> which columns it didn't use. Every email is graded: verified, shared inbox, throwaway or bouncing. Phones are
> validated too."

**0:40 – 1:05 · Rank with reasons** *(open the top lead)*
> "Every lead is scored against my buy box. I'm in acquisition mode, so it rewards what a searcher cares about: 38
> years in business, family-owned, recurring maintenance revenue, an owner I can name. The score isn't a black box:
> here are the four components and every reason. The franchise and the 2,400-person company are excluded, and it says
> why." *(click the Excluded tab briefly)*

**1:05 – 1:25 · Act** *(scroll to the brief, click "Write the brief")*
> "For the lead I want, LeadLens writes a one-screen brief: why now, talking points, a call opener and a first email
> built only from verified facts. With an API key it uses Claude with schema-checked output. I mark it contacted, and
> export to HubSpot or Salesforce with their native column names plus score, tier and next step."

**1:25 – 1:45 · Live enrichment and ethics** *(Import → Paste websites → two real domains)*
> "Paste a list of websites and it builds the lead from the company's own site: emails, phone, founding year, owner,
> signals, each with the sentence it came from. It identifies itself, obeys robots.txt, never solves CAPTCHAs, and
> never probes mailboxes."

**1:45 – 2:00 · Architecture and close**
> "It's an Angular 22 frontend on a Spring Boot / Java 17 API, with PostgreSQL on Neon, Caffeine caching for DNS, robots and crawls, a bounded worker
> pool, Flyway migrations and a Docker deploy to Render. The value: reps start every morning with a short, verified,
> ranked list, plus the reason and the first message for each lead."
