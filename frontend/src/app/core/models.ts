/** Types mirroring the Spring Boot API (backend/src/main/java/com/leadlens/web). */

export type Tier = 'A' | 'B' | 'C' | 'X';
export type LeadStatus = 'NEW' | 'QUALIFIED' | 'CONTACTED' | 'REPLIED' | 'DISQUALIFIED';
export type EmailStatus = 'VALID' | 'ROLE' | 'UNVERIFIED' | 'NO_MX' | 'DISPOSABLE' | 'INVALID' | 'MISSING';
export type WebsiteStatus = 'UNCHECKED' | 'LIVE' | 'DEAD' | 'ROBOTS_BLOCKED' | 'PROTECTED' | 'SKIPPED';
export type ExportFormat = 'hubspot' | 'salesforce' | 'full';

export interface Signal { code: string; label: string; evidence: string; }
export interface ScoreComponent { key: string; label: string; score: number; weightPercent: number; }
export interface Reason { positive: boolean; text: string; }
export interface NextAction { channel: 'email' | 'call' | 'linkedin' | 'research' | 'skip' | 'wait'; label: string; }

export interface Brief {
  headline: string;
  summary: string;
  whyNow: string;
  talkingPoints: string[];
  emailSubject: string;
  emailBody: string;
  callOpener?: string;
  risks: string[];
}

export interface Lead {
  id: number;
  company: string;
  domain?: string;
  website?: string;
  industry?: string;
  city?: string;
  state?: string;
  country?: string;
  employees?: number;
  revenueUsd?: number;
  foundedYear?: number;
  yearsInBusiness?: number;
  ownerName?: string;
  ownerTitle?: string;
  email?: string;
  emailStatus: EmailStatus;
  phone?: string;
  phoneValid: boolean;
  linkedinUrl?: string;
  websiteStatus: WebsiteStatus;
  websiteNote?: string;
  description?: string;
  signals: Signal[];
  enrichedFields: string[];
  sourceRows: number;
  score: number;
  tier: Tier;
  excludedReason?: string;
  components: ScoreComponent[];
  reasons: Reason[];
  status: LeadStatus;
  notes?: string;
  contactedAt?: string;
  followUpAt?: string;
  brief?: Brief;
  briefProvider?: string;
  processing: boolean;
  nextAction: NextAction;
  updatedAt: string;
}

export interface LeadPage { items: Lead[]; total: number; page: number; size: number; }

export interface ImportBatch {
  id: number;
  sourceName: string;
  status: 'RUNNING' | 'DONE' | 'FAILED';
  totalRows: number;
  emptyRows: number;
  newLeads: number;
  duplicatesMerged: number;
  toProcess: number;
  processed: number;
  unmappedColumns?: string;
  error?: string;
  createdAt: string;
  finishedAt?: string;
}

export interface Stats {
  total: number;
  tiers: Partial<Record<Tier, number>>;
  statuses: Partial<Record<LeadStatus, number>>;
  emails: Partial<Record<EmailStatus, number>>;
  websites: Partial<Record<WebsiteStatus, number>>;
  readyToContact: number;
  badContactsCaught: number;
  duplicatesMerged: number;
  enriched: number;
  averageScore: number;
  latestImport?: ImportBatch;
  claudeEnabled: boolean;
}

export interface Thesis {
  mode: 'ACQUISITION' | 'SALES';
  industries: string[];
  locations: string[];
  minEmployees: number | null;
  maxEmployees: number | null;
  minRevenue: number | null;
  maxRevenue: number | null;
  minYearsInBusiness: number | null;
  excludeKeywords: string[];
}

export interface RescoreOutcome {
  leads: number;
  before: Partial<Record<Tier, number>>;
  after: Partial<Record<Tier, number>>;
}

export interface LeadFilters {
  q: string;
  tier: string;
  contact: string;
  status: string;
  sort: string;
  page: number;
  size: number;
  /** Set by "Ask your list" (or an Insights recommendation); shown as removable chips. */
  state: string;
  industry: string;
  minYears: number | null;
  signal: string;
}

export interface CallList { followUpsDue: Lead[]; startHere: Lead[]; followUpsLater: number; }

export interface QueryPlan {
  q?: string | null;
  tier?: string | null;
  status?: string | null;
  contact?: string | null;
  state?: string | null;
  industry?: string | null;
  minYears?: number | null;
  signal?: string | null;
  sort?: string | null;
  explanation: string;
}
export interface AskAnswer { plan: QueryPlan; provider: string; }

export interface FunnelStep { label: string; count: number; detail: string; }
export interface Segment { name: string; leads: number; tierA: number; tierAB: number; avgScore: number; reachable: number; }
export interface Quality { label: string; count: number; total: number; }
export interface Recommendation { kind: 'focus' | 'opportunity' | 'tactic' | 'quality' | 'thesis'; title: string; detail: string; filter: Record<string, string>; }
export interface Insights {
  generatedAt: string;
  mode: 'ACQUISITION' | 'SALES';
  funnel: FunnelStep[];
  industries: Segment[];
  states: Segment[];
  quality: Quality[];
  exclusions: Record<string, number>;
  recommendations: Recommendation[];
}
export interface WebhookResult { sent: number; batches: number; lastStatus: number; target: string; }
