import { Injectable, computed, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { Lead, LeadFilters, LeadPage, Stats, Thesis } from './models';
import { ToastService } from './toast.service';

const DEFAULT_FILTERS: LeadFilters = { q: '', tier: '', contact: '', status: '', sort: 'score,desc', page: 0, size: 50 };

/**
 * Workspace state shared by every component: dashboard stats, the current table page and filters,
 * the selection, the open lead and the thesis. While an import is verifying/enriching, it polls.
 */
@Injectable({ providedIn: 'root' })
export class LeadStore {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  readonly stats = signal<Stats | null>(null);
  readonly page = signal<LeadPage | null>(null);
  readonly filters = signal<LeadFilters>({ ...DEFAULT_FILTERS });
  readonly selected = signal<ReadonlySet<number>>(new Set());
  readonly openLead = signal<Lead | null>(null);
  readonly thesis = signal<Thesis | null>(null);
  readonly importOpen = signal<false | 'csv' | 'websites'>(false);
  readonly thesisOpen = signal(false);

  readonly hasLeads = computed(() => (this.stats()?.total ?? 0) > 0);
  readonly busy = computed(() =>
    this.stats()?.latestImport?.status === 'RUNNING' || !!this.page()?.items.some((l) => l.processing));

  private pollTimer?: ReturnType<typeof setTimeout>;
  private leadsRequest = 0;

  async refresh(): Promise<void> {
    try {
      const stats = await this.api.stats();
      this.stats.set(stats);
      if (stats.total > 0) {
        await this.loadLeads();
        const open = this.openLead();
        if (open && this.busy()) this.openLead.set(await this.api.lead(open.id));
      } else {
        this.page.set(null);
      }
    } catch (e) {
      this.toast.show((e as Error).message);
    }
    clearTimeout(this.pollTimer);
    if (this.busy()) this.pollTimer = setTimeout(() => this.refresh(), 1500);
  }

  async loadLeads(): Promise<void> {
    const request = ++this.leadsRequest;
    const page = await this.api.leads(this.filters());
    if (request === this.leadsRequest) this.page.set(page); // drop stale responses while typing
  }

  setFilter(change: Partial<LeadFilters>): void {
    this.filters.update((f) => ({ ...f, page: 0, ...change }));
    this.loadLeads().catch((e) => this.toast.show(e.message));
  }

  async open(id: number): Promise<void> {
    try {
      this.openLead.set(await this.api.lead(id));
    } catch (e) {
      this.toast.show((e as Error).message);
    }
  }

  close(): void {
    this.openLead.set(null);
  }

  /** Replace one lead in the drawer and the table after a change. */
  patchLead(lead: Lead): void {
    if (this.openLead()?.id === lead.id) this.openLead.set(lead);
    this.page.update((p) => p && { ...p, items: p.items.map((l) => (l.id === lead.id ? lead : l)) });
  }

  toggleSelected(id: number, on: boolean): void {
    this.selected.update((s) => {
      const next = new Set(s);
      if (on) next.add(id); else next.delete(id);
      return next;
    });
  }

  clearSelection(): void {
    this.selected.set(new Set());
  }

  async loadThesis(): Promise<void> {
    this.thesis.set(await this.api.thesis());
  }

  pollSoon(): void {
    clearTimeout(this.pollTimer);
    this.pollTimer = setTimeout(() => this.refresh(), 800);
  }
}
