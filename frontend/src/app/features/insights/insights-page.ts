import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { LeadStore } from '../../core/lead-store';
import { Insights, Recommendation, Segment } from '../../core/models';
import { ToastService } from '../../core/toast.service';

const KIND_LABEL: Record<Recommendation['kind'], string> = {
  focus: 'Focus', opportunity: 'Opportunity', tactic: 'Tactic', quality: 'Data quality', thesis: 'Buy box',
};

/** The list as a one-page report: funnel, where the best leads are, data quality, and what to do next. */
@Component({
  selector: 'app-insights-page',
  imports: [NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (data(); as d) {
      <section class="insights">
        <div class="page-head">
          <div>
            <div class="eyebrow">Insights · {{ d.mode === 'ACQUISITION' ? 'acquisition buy box' : 'sales ICP' }}</div>
            <h1>What this list is telling you</h1>
            <p class="meta">Generated {{ generated() }} from {{ d.funnel[1].count }} companies. Click a recommendation to open those leads.</p>
          </div>
          <div class="btns no-print">
            <button class="btn sm" (click)="load()">↻ Refresh</button>
            <button class="btn sm primary" (click)="print()">Download report (PDF)</button>
          </div>
        </div>

        <div class="card">
          <h3>Pipeline funnel</h3>
          <div class="funnel">
            @for (s of d.funnel; track s.label) {
              <div class="funnel-step">
                <div class="funnel-bar"><i [style.width.%]="pct(s.count, d.funnel[0].count)"></i></div>
                <div class="funnel-text"><b>{{ s.count }}</b> {{ s.label }} <small>{{ s.detail }}</small></div>
              </div>
            }
          </div>
        </div>

        @if (d.recommendations.length) {
          <div class="card">
            <h3>Recommendations</h3>
            <div class="recs">
              @for (r of d.recommendations; track r.title) {
                <button class="rec" [class]="'rec ' + r.kind" (click)="open(r)" [disabled]="!hasFilter(r)">
                  <span class="rec-kind">{{ kindLabel[r.kind] }}</span>
                  <b>{{ r.title }}</b>
                  <span>{{ r.detail }}</span>
                  @if (hasFilter(r)) { <span class="rec-go">Show these leads →</span> }
                </button>
              }
            </div>
          </div>
        }

        <div class="grid2">
          <div class="card">
            <h3>Best industries</h3>
            <ng-container *ngTemplateOutlet="segments; context: { $implicit: d.industries, key: 'industry' }" />
          </div>
          <div class="card">
            <h3>Best states</h3>
            <ng-container *ngTemplateOutlet="segments; context: { $implicit: d.states, key: 'state' }" />
          </div>
        </div>

        <div class="grid2">
          <div class="card">
            <h3>Data quality</h3>
            @for (q of d.quality; track q.label) {
              <div class="comp"><span>{{ q.label }}</span><div class="track"><i [style.width.%]="pct(q.count, q.total)"></i></div>
                <span class="w">{{ q.count }} · {{ pct(q.count, q.total) }}%</span></div>
            }
          </div>
          <div class="card">
            <h3>Why leads were excluded</h3>
            @if (exclusions().length) {
              @for (e of exclusions(); track e[0]) {
                <div class="excl-row"><span>{{ e[0] }}</span><b>{{ e[1] }}</b></div>
              }
              <p class="meta">Excluded leads stay visible under the “Excluded” tab and are left out of exports.</p>
            } @else { <p class="meta">Nothing excluded.</p> }
          </div>
        </div>
      </section>
    } @else {
      <p class="meta"><span class="spinner"></span> Analysing your list…</p>
    }

    <ng-template #segments let-rows let-key="key">
      <table class="seg-table">
        <thead><tr><th>{{ key === 'state' ? 'State' : 'Industry' }}</th><th>Leads</th><th>Tier A</th><th>A/B</th><th>Avg</th></tr></thead>
        <tbody>
          @for (s of $any(rows); track s.name) {
            <tr (click)="openSegment(key, s)"><td><b>{{ s.name }}</b></td><td>{{ s.leads }}</td>
              <td><span class="tier A">{{ s.tierA }}</span></td><td>{{ s.tierAB }}</td><td>{{ s.avgScore }}</td></tr>
          }
        </tbody>
      </table>
    </ng-template>`,
})
export class InsightsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly store = inject(LeadStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  protected readonly data = signal<Insights | null>(null);
  protected readonly kindLabel = KIND_LABEL;

  protected readonly exclusions = computed(() => Object.entries(this.data()?.exclusions ?? {}));
  protected readonly generated = computed(() => {
    const d = this.data();
    return d ? new Date(d.generatedAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '';
  });

  ngOnInit(): void {
    this.load();
  }

  protected async load(): Promise<void> {
    try {
      this.data.set(await this.api.insights());
    } catch (e) {
      this.toast.show((e as Error).message);
    }
  }

  protected pct(part: number, whole: number): number {
    return whole ? Math.round((part / whole) * 100) : 0;
  }

  protected hasFilter(r: Recommendation): boolean {
    return Object.keys(r.filter).length > 0;
  }

  protected open(r: Recommendation): void {
    this.store.applyPlan(r.filter, { text: r.title, provider: 'insights' });
    this.router.navigate(['/']);
  }

  protected openSegment(key: string, s: Segment): void {
    this.store.applyPlan({ [key]: s.name }, { text: `${s.name}: ${s.tierA} Tier A of ${s.leads}`, provider: 'insights' });
    this.router.navigate(['/']);
  }

  protected print(): void {
    window.print();
  }
}
