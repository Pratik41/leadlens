import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { LeadStore } from '../../core/lead-store';
import { Lead, LeadStatus } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { EmailBadge } from '../../shared/email-badge';
import { CHANNEL_ICON, STATUS_LABEL, highlights, place } from '../../shared/format';
import { ScoreRing } from '../../shared/score-ring';

@Component({
  selector: 'app-lead-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ScoreRing, EmailBadge],
  host: { '(document:keydown)': 'onKey($event)' },
  template: `
    @if (store.selected().size > 0) {
      <div class="bulkbar">
        <span>{{ store.selected().size }} selected</span>
        <button class="btn sm" (click)="bulk('QUALIFIED')">Mark qualified</button>
        <button class="btn sm" (click)="bulk('CONTACTED')">Mark contacted</button>
        <button class="btn sm" (click)="bulk('DISQUALIFIED')">Disqualify</button>
        <button class="btn sm" (click)="exportSelected()">Export selected (HubSpot)</button>
        <button class="link" (click)="store.clearSelection()">Clear</button>
      </div>
    }

    @if (store.page(); as page) {
      <div class="table-wrap">
        <table class="leads">
          <thead>
            <tr>
              <th class="chk"><input type="checkbox" aria-label="Select all on this page" [checked]="allChecked()"
                                     (change)="toggleAll($any($event.target).checked)"></th>
              <th>Score</th><th>Company</th><th class="hide-sm">Owner</th><th>Contact</th>
              <th class="hide-md">Why</th><th class="hide-sm">Next step</th><th class="hide-md">Status</th>
            </tr>
          </thead>
          <tbody>
            @for (l of page.items; track l.id) {
              <tr [class.excluded]="l.tier === 'X'" [class.active]="store.openLead()?.id === l.id" (click)="store.open(l.id)">
                <td class="chk" (click)="$event.stopPropagation()">
                  <input type="checkbox" [attr.aria-label]="'Select ' + l.company" [checked]="store.selected().has(l.id)"
                         (change)="store.toggleSelected(l.id, $any($event.target).checked)">
                </td>
                <td><div class="score"><app-score-ring [score]="l.score" [tier]="l.tier" />
                  <span class="tier" [class]="'tier ' + l.tier">{{ l.tier === 'X' ? '✕' : l.tier }}</span></div></td>
                <td class="co">
                  <b>{{ l.company }}</b>
                  <small>{{ subtitle(l) }}</small>
                  @if (l.processing) { <span class="proc"><span class="spinner"></span> verifying…</span> }
                </td>
                <td class="owner hide-sm">
                  @if (l.ownerName) { <b>{{ l.ownerName }}</b><small>{{ l.ownerTitle }}</small> } @else { <small>Not identified</small> }
                </td>
                <td><div class="contact">
                  <app-email-badge [status]="l.email ? l.emailStatus : 'MISSING'" />
                  @if (l.phone) {
                    <span class="badge" [class]="'badge ' + (l.phoneValid ? 'good' : 'bad')">{{ l.phoneValid ? '☎ ' + l.phone : 'Bad phone' }}</span>
                  }
                </div></td>
                <td class="why hide-md">
                  @if (l.tier === 'X') { <span class="x">{{ l.excludedReason || 'Excluded' }}</span> }
                  @else { {{ why(l) }} }
                </td>
                <td class="next hide-sm"><span class="ch">{{ icon[l.nextAction.channel] }}</span> {{ l.nextAction.label }}</td>
                <td class="hide-md"><span class="status-pill" [class]="'status-pill ' + l.status">{{ statusLabel[l.status] }}</span></td>
              </tr>
            }
          </tbody>
        </table>
        @if (page.items.length === 0) { <div class="no-results">No leads match these filters.</div> }
      </div>
      <div class="pager">
        <span>{{ range() }}</span>
        <span class="btns">
          <button class="btn sm" [disabled]="page.page === 0" (click)="goPage(-1)">← Prev</button>
          <button class="btn sm" [disabled]="(page.page + 1) * page.size >= page.total" (click)="goPage(1)">Next →</button>
        </span>
      </div>
    }`,
})
export class LeadTable {
  protected readonly store = inject(LeadStore);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  protected readonly icon = CHANNEL_ICON;
  protected readonly statusLabel = STATUS_LABEL;

  protected readonly allChecked = computed(() => {
    const items = this.store.page()?.items ?? [];
    return items.length > 0 && items.every((l) => this.store.selected().has(l.id));
  });

  protected readonly range = computed(() => {
    const p = this.store.page();
    if (!p || !p.total) return '0 leads';
    return `${p.page * p.size + 1}–${Math.min(p.total, (p.page + 1) * p.size)} of ${p.total} leads`;
  });

  protected subtitle(l: Lead): string {
    return [l.industry, place(l)].filter(Boolean).join(' · ') || l.domain || '';
  }

  protected why(l: Lead): string {
    return highlights(l).join(' · ') || '—';
  }

  protected toggleAll(on: boolean): void {
    for (const l of this.store.page()?.items ?? []) this.store.toggleSelected(l.id, on);
  }

  protected goPage(delta: number): void {
    this.store.filters.update((f) => ({ ...f, page: f.page + delta }));
    this.store.loadLeads();
  }

  protected async bulk(status: LeadStatus): Promise<void> {
    try {
      const r = await this.api.bulkStatus([...this.store.selected()], status);
      this.toast.show(`${r.updated} leads marked ${STATUS_LABEL[status].toLowerCase()}`);
      this.store.clearSelection();
      await this.store.refresh();
    } catch (e) {
      this.toast.show((e as Error).message);
    }
  }

  protected exportSelected(): void {
    location.href = this.api.exportUrl('hubspot', { tier: 'A,B,C,X' }, [...this.store.selected()]);
  }

  /** j / k move through the list and open the next lead, like an inbox. */
  protected onKey(e: KeyboardEvent): void {
    if (e.key !== 'j' && e.key !== 'k') return;
    if (/INPUT|TEXTAREA|SELECT/.test((document.activeElement as HTMLElement | null)?.tagName ?? '')) return;
    const items = this.store.page()?.items ?? [];
    if (!items.length) return;
    const i = items.findIndex((l) => l.id === this.store.openLead()?.id);
    const next = items[Math.max(0, Math.min(items.length - 1, i + (e.key === 'j' ? 1 : -1)))];
    this.store.open(next.id);
  }
}
