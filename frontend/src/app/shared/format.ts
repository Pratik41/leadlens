import { EmailStatus, Lead, LeadStatus, NextAction, Tier, WebsiteStatus } from '../core/models';

export const TIER_COLOR: Record<Tier, string> = {
  A: 'var(--tier-a)', B: 'var(--tier-b)', C: 'var(--tier-c)', X: 'var(--tier-x)',
};

export const EMAIL_BADGE: Record<EmailStatus, { tone: string; label: string; help: string }> = {
  VALID: { tone: 'good', label: 'Verified', help: 'Personal address and the domain accepts mail (MX record found).' },
  ROLE: { tone: 'warn', label: 'Shared inbox', help: 'Deliverable, but a shared inbox (info@, office@…), not the owner.' },
  UNVERIFIED: { tone: 'info', label: 'Unverified', help: 'Looks right, but its mail servers could not be checked (reserved demo domain or DNS unavailable).' },
  NO_MX: { tone: 'bad', label: 'Bounces', help: 'The domain has no mail servers: this would bounce.' },
  DISPOSABLE: { tone: 'bad', label: 'Throwaway', help: 'Throwaway mailbox provider.' },
  INVALID: { tone: 'bad', label: 'Invalid', help: 'Not a valid email address.' },
  MISSING: { tone: 'muted', label: 'No email', help: 'No email found.' },
};

export const SITE_BADGE: Record<WebsiteStatus, { tone: string; label: string }> = {
  LIVE: { tone: 'good', label: 'Site read' },
  DEAD: { tone: 'bad', label: 'Site down' },
  ROBOTS_BLOCKED: { tone: 'warn', label: 'robots.txt: not read' },
  PROTECTED: { tone: 'warn', label: 'Bot check: not bypassed' },
  SKIPPED: { tone: 'muted', label: 'Not crawled' },
  UNCHECKED: { tone: 'muted', label: 'Checking…' },
};

export const STATUS_LABEL: Record<LeadStatus, string> = {
  NEW: 'New', QUALIFIED: 'Qualified', CONTACTED: 'Contacted', REPLIED: 'Replied', DISQUALIFIED: 'Disqualified',
};
export const STATUSES = Object.keys(STATUS_LABEL) as LeadStatus[];

export const CHANNEL_ICON: Record<NextAction['channel'], string> = {
  email: '✉', call: '☎', linkedin: 'in', research: '🔎', skip: '⦸', wait: '⏳',
};

export function money(v?: number | null): string | null {
  if (v == null) return null;
  if (v >= 1e9) return `$${(v / 1e9).toFixed(1)}B`;
  if (v >= 1e6) return `$${(v / 1e6).toFixed(v >= 1e7 ? 0 : 1)}M`;
  if (v >= 1e3) return `$${Math.round(v / 1e3)}K`;
  return `$${v}`;
}

export function place(l: Pick<Lead, 'city' | 'state'>): string {
  return [l.city, l.state].filter(Boolean).join(', ');
}

/** Reasons most qualified leads share; the table and exports lead with what is specific to each lead. */
const GENERIC = /^(Target industry|In target geography|Employees in range|Revenue in range|Valid phone|Verified personal email|Owner identified|Decision maker named)/;

export function highlights(l: Lead, n = 2): string[] {
  return l.reasons.filter((r) => r.positive)
    .sort((a, b) => Number(GENERIC.test(a.text)) - Number(GENERIC.test(b.text)))
    .slice(0, n).map((r) => r.text);
}
