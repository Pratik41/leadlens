import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { LeadStore } from '../../core/lead-store';
import { CallList, Lead, LeadStatus } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { EmailBadge } from '../../shared/email-badge';
import { CHANNEL_ICON, highlights, place } from '../../shared/format';
import { ScoreRing } from '../../shared/score-ring';

/**
 * The daily work queue: follow-ups that are due, then the best reachable leads nobody has touched.
 * One click logs the touch; the follow-up date is scheduled automatically.
 */
@Component({
  selector: 'app-today-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ScoreRing, EmailBadge, NgTemplateOutlet],
  template: `
    <section class="today">
      <div class="page-head">
        <div>
          <div class="eyebrow">Today</div>
          <h1>Your call list</h1>
          <p class="meta">Follow-ups that are due come first, then the highest-scoring reachable leads nobody has
            contacted yet. Logging a touch schedules the next follow-up 3 business days out.</p>
        </div>
        <button class="btn sm" (click)="load()">↻ Refresh</button>
      </div>

      @if (list(); as l) {
        <h2 class="section">Follow-ups due <span class="count">{{ l.followUpsDue.length }}</span></h2>
        @if (l.followUpsDue.length) {
          @for (lead of l.followUpsDue; track lead.id) { <ng-container *ngTemplateOutlet="row; context: { $implicit: lead, due: true }" /> }
        } @else {
          <p class="empty-line">Nothing due.{{ l.followUpsLater ? ' ' + l.followUpsLater + ' follow-up' + (l.followUpsLater === 1 ? ' is' : 's are') + ' scheduled for later.' : '' }}</p>
        }

        <h2 class="section">Start here <span class="count">{{ l.startHere.length }}</span></h2>
        @if (l.startHere.length) {
          @for (lead of l.startHere; track lead.id) { <ng-container *ngTemplateOutlet="row; context: { $implicit: lead, due: false }" /> }
        } @else {
          <p class="empty-line">No uncontacted Tier A/B leads with a working email or phone. Import more, or loosen the buy box.</p>
        }
      } @else {
        <p class="meta"><span class="spinner"></span> Building today's list…</p>
      }
    </section>

    <ng-template #row let-lead let-due="due">
      <div class="call" [class.due]="due">
        <app-score-ring [score]="lead.score" [tier]="lead.tier" />
        <button class="call-main" (click)="store.open(lead.id)">
          <b>{{ lead.company }}</b>
          <small>{{ subtitle(lead) }}</small>
          <span class="call-why">{{ why(lead) }}</span>
        </button>
        <div class="call-contact">
          @if (lead.ownerName) { <b>{{ lead.ownerName }}</b> }
          @if (lead.phone && lead.phoneValid) { <a [href]="'tel:' + lead.phone">☎ {{ lead.phone }}</a> }
          @if (lead.email) { <span><app-email-badge [status]="lead.emailStatus" /> {{ lead.email }}</span> }
        </div>
        <div class="call-actions">
          <span class="next"><span class="ch">{{ iconFor(lead) }}</span> {{ lead.nextAction.label }}</span>
          <div class="btns">
            <button class="btn sm primary" (click)="mark(lead, 'CONTACTED')">{{ due ? 'Logged follow-up' : 'Contacted' }}</button>
            <button class="btn sm" (click)="mark(lead, 'REPLIED')">Replied</button>
            @if (due) { <button class="btn sm" (click)="snooze(lead, 7)">+1 week</button> }
            @else { <button class="btn sm" (click)="mark(lead, 'DISQUALIFIED')">Not a fit</button> }
          </div>
        </div>
      </div>
    </ng-template>`,
})
export class TodayPage implements OnInit {
  protected readonly store = inject(LeadStore);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  protected readonly list = signal<CallList | null>(null);
  protected iconFor(l: Lead): string {
    return CHANNEL_ICON[l.nextAction.channel];
  }

  ngOnInit(): void {
    this.load();
  }

  protected async load(): Promise<void> {
    try {
      this.list.set(await this.api.today(15));
    } catch (e) {
      this.toast.show((e as Error).message);
    }
  }

  protected async mark(lead: Lead, status: LeadStatus): Promise<void> {
    const updated = await this.api.updateLead(lead.id, { status });
    this.store.patchLead(updated);
    this.toast.show(status === 'CONTACTED'
      ? `Logged. Next follow-up: ${new Date(updated.followUpAt!).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })}`
      : `${lead.company}: ${status.toLowerCase()}`);
    await this.load();
    this.store.refresh();
  }

  protected async snooze(lead: Lead, days: number): Promise<void> {
    this.store.patchLead(await this.api.updateLead(lead.id, { snoozeDays: days }));
    this.toast.show(`Snoozed ${lead.company} for ${days} days`);
    await this.load();
  }

  protected subtitle(l: Lead): string {
    return [l.industry, place(l)].filter(Boolean).join(' · ');
  }

  protected why(l: Lead): string {
    return highlights(l, 3).join(' · ');
  }
}
