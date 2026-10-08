import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { LeadStore } from '../../core/lead-store';
import { Lead, LeadStatus } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { EmailBadge } from '../../shared/email-badge';
import { CHANNEL_ICON, SITE_BADGE, STATUSES, STATUS_LABEL, money, place } from '../../shared/format';
import { ScoreRing } from '../../shared/score-ring';
import { BriefCard } from './brief-card';

/** Everything about one lead: why it scored what it did, how to reach it, what to say, and its pipeline state. */
@Component({
  selector: 'app-lead-drawer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ScoreRing, EmailBadge, BriefCard],
  template: `
    @if (lead(); as l) {
      <div class="scrim" (click)="store.close()"></div>
      <aside class="drawer open" aria-label="Lead detail">
        <div class="drawer-inner">
          <div class="drawer-head">
            <div>
              <div class="eyebrow">{{ l.industry || 'Company' }}</div>
              <h2>{{ l.company }}</h2>
              <div class="meta">
                {{ placeOf(l) }}
                @if (l.domain) { · <a [href]="l.website" target="_blank" rel="noopener noreferrer">{{ l.domain }} ↗</a> }
                @if (l.sourceRows > 1) { · {{ l.sourceRows }} rows merged }
              </div>
            </div>
            <div class="hero-score">
              <app-score-ring [score]="l.score" [tier]="l.tier" [large]="true" />
              <span class="tier" [class]="'tier ' + l.tier">{{ l.tier === 'X' ? 'Excluded' : 'Tier ' + l.tier }}</span>
              <button class="icon-btn" aria-label="Close" (click)="store.close()">✕</button>
            </div>
          </div>

          <div class="callout" [class.skip]="l.tier === 'X'">
            <span style="font-size:20px">{{ icon[l.nextAction.channel] }}</span>
            <div><b>{{ l.nextAction.label }}</b>
              <small>{{ l.tier === 'X' ? 'Excluded leads are left out of exports.' : 'Suggested next step based on verified contact data and pipeline status.' }}</small></div>
          </div>

          <div class="pipeline" aria-label="Pipeline status">
            @for (s of statuses; track s) {
              <button [class.on]="l.status === s" (click)="setStatus(s)">{{ statusLabel[s] }}</button>
            }
          </div>

          <div class="card">
            <h3>Why this score</h3>
            @for (c of l.components; track c.key) {
              <div class="comp"><span>{{ c.label }}</span><div class="track"><i [style.width.%]="c.score"></i></div>
                <span class="w">{{ c.score }} · {{ c.weightPercent }}%</span></div>
            }
            <ul class="reasons">
              @for (r of l.reasons; track $index) {
                <li [class.excl]="r.text.startsWith('Excluded')" [class.neg]="!r.positive && !r.text.startsWith('Excluded')">{{ r.text }}</li>
              }
            </ul>
          </div>

          <div class="card">
            <h3>Contact</h3>
            <div class="facts">
              <div class="fact"><div class="k">Owner / decision maker</div><div class="v">
                @if (l.ownerName) { {{ l.ownerName }} <small>{{ l.ownerTitle }}</small> } @else { <small>Unknown</small> }
                @if (web(l, 'owner')) { <span class="src" title="Found on the company website">web</span> }
              </div></div>
              <div class="fact"><div class="k">Email</div><div class="v">
                @if (l.email) {
                  <a [href]="'mailto:' + l.email">{{ l.email }}</a> <app-email-badge [status]="l.emailStatus" />
                  <button class="copy" (click)="copy(l.email)">copy</button>
                } @else { <app-email-badge status="MISSING" /> }
                @if (web(l, 'email')) { <span class="src" title="Found on the company website">web</span> }
              </div></div>
              <div class="fact"><div class="k">Phone</div><div class="v">
                @if (l.phone) {
                  <a [href]="'tel:' + l.phone">{{ l.phone }}</a>
                  <span class="badge" [class]="'badge ' + (l.phoneValid ? 'good' : 'bad')">{{ l.phoneValid ? 'Valid' : 'Invalid' }}</span>
                  <button class="copy" (click)="copy(l.phone)">copy</button>
                } @else { <small>Unknown</small> }
                @if (web(l, 'phone')) { <span class="src" title="Found on the company website">web</span> }
              </div></div>
              <div class="fact"><div class="k">LinkedIn</div><div class="v">
                @if (l.linkedinUrl) { <a [href]="l.linkedinUrl" target="_blank" rel="noopener noreferrer">Profile ↗</a> } @else { <small>Unknown</small> }
                @if (web(l, 'linkedin')) { <span class="src" title="Found on the company website">web</span> }
              </div></div>
            </div>
          </div>

          <app-brief-card [lead]="l" />

          <div class="card">
            <h3>Company</h3>
            <div class="facts">
              <div class="fact"><div class="k">Employees</div><div class="v">{{ l.employees != null ? '~' + l.employees : 'Unknown' }}</div></div>
              <div class="fact"><div class="k">Revenue</div><div class="v">{{ revenue(l) ?? 'Unknown' }}</div></div>
              <div class="fact"><div class="k">Founded</div><div class="v">
                @if (l.foundedYear) { {{ l.foundedYear }} <small>{{ l.yearsInBusiness }} yrs</small> } @else { <small>Unknown</small> }
                @if (web(l, 'founded')) { <span class="src" title="Found on the company website">web</span> }
              </div></div>
              <div class="fact"><div class="k">Website</div><div class="v">
                <span class="badge" [class]="'badge ' + site().tone">{{ site().label }}</span>
                @if (l.websiteNote) { <small>{{ l.websiteNote }}</small> }
              </div></div>
            </div>
            @if (l.description) {
              <p class="meta" style="margin:12px 0 0">{{ l.description }}
                @if (web(l, 'description')) { <span class="src">web</span> }</p>
            }
          </div>

          @if (l.signals.length) {
            <div class="card">
              <h3>Signals</h3>
              @for (s of l.signals; track s.code) {
                <div class="signal"><b>{{ s.label }}</b><q>{{ s.evidence }}</q></div>
              }
            </div>
          }

          <div class="card">
            <h3>Notes</h3>
            <textarea class="notes" placeholder="Call notes, objections, follow-up date…" [value]="l.notes ?? ''"
                      (blur)="saveNotes($any($event.target).value)"></textarea>
          </div>
          <div class="form-actions">
            <button class="btn sm" [disabled]="l.processing" (click)="recheck()">
              @if (l.processing) { <span class="spinner"></span> Checking… } @else { ↻ Re-check email &amp; website }
            </button>
          </div>
        </div>
      </aside>
    }`,
})
export class LeadDrawer {
  protected readonly store = inject(LeadStore);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  protected readonly lead = this.store.openLead;
  protected readonly statuses = STATUSES;
  protected readonly statusLabel = STATUS_LABEL;
  protected readonly icon = CHANNEL_ICON;
  protected readonly site = computed(() => SITE_BADGE[this.lead()?.websiteStatus ?? 'UNCHECKED']);

  protected placeOf = place;
  protected revenue = (l: Lead) => money(l.revenueUsd);
  protected web = (l: Lead, field: string) => l.enrichedFields.includes(field);

  protected async setStatus(status: LeadStatus): Promise<void> {
    const l = this.lead();
    if (!l) return;
    try {
      this.store.patchLead(await this.api.updateLead(l.id, { status }));
      this.toast.show(`Marked ${STATUS_LABEL[status].toLowerCase()}`);
      this.store.refresh();
    } catch (e) {
      this.toast.show((e as Error).message);
    }
  }

  protected async saveNotes(notes: string): Promise<void> {
    const l = this.lead();
    if (!l || notes === (l.notes ?? '')) return;
    this.store.patchLead(await this.api.updateLead(l.id, { notes }));
    this.toast.show('Notes saved', 1500);
  }

  protected async recheck(): Promise<void> {
    const l = this.lead();
    if (!l) return;
    this.store.patchLead(await this.api.refreshLead(l.id));
    this.toast.show('Re-checking email and website…');
    this.store.pollSoon();
  }

  protected copy(text: string): void {
    navigator.clipboard.writeText(text).then(() => this.toast.show('Copied'));
  }
}
