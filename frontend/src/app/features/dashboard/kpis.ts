import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { LeadStore } from '../../core/lead-store';
import { Stats } from '../../core/models';

/** Headline numbers; the clickable ones apply the matching table filter. */
@Component({
  selector: 'app-kpis',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let s = stats();
    <div class="kpis">
      <button class="kpi hero click" (click)="store.setFilter({ tier: 'A', contact: '' })">
        <div class="label">Tier A · work these first</div><div class="value">{{ t().A }}</div>
        <div class="tierbar"><i [style.width.%]="pct(t().A)"></i><i [style.width.%]="pct(t().B)"></i><i [style.width.%]="pct(t().C)"></i><i [style.width.%]="pct(t().X)"></i></div>
        <div class="sub">{{ t().B }} B · {{ t().C }} C · {{ t().X }} excluded</div>
      </button>
      <button class="kpi click" (click)="store.setFilter({ tier: 'A,B', contact: 'email' })">
        <div class="label">Ready to contact</div><div class="value">{{ s.readyToContact }}</div><div class="sub">A/B with a working email or phone</div>
      </button>
      <div class="kpi"><div class="label">Companies</div><div class="value">{{ s.total }}</div><div class="sub">avg score {{ s.averageScore }}</div></div>
      <div class="kpi"><div class="label">Duplicates merged</div><div class="value">{{ s.duplicatesMerged }}</div><div class="sub">rows folded into one company</div></div>
      <button class="kpi click" (click)="store.setFilter({ tier: '', contact: 'none' })">
        <div class="label">Bad contacts caught</div><div class="value">{{ s.badContactsCaught }}</div><div class="sub">bouncing, throwaway or invalid emails</div>
      </button>
      <div class="kpi"><div class="label">Enriched from web</div><div class="value">{{ s.enriched }}</div><div class="sub">{{ s.websites.LIVE ?? 0 }} sites read live</div></div>
    </div>`,
  styles: `button.kpi { text-align: left; font: inherit; color: inherit; width: 100%; }`,
})
export class Kpis {
  protected readonly store = inject(LeadStore);
  readonly stats = input.required<Stats>();
  protected readonly t = computed(() => {
    const t = this.stats().tiers;
    return { A: t.A ?? 0, B: t.B ?? 0, C: t.C ?? 0, X: t.X ?? 0 };
  });

  protected pct(n: number): number {
    const total = this.stats().total;
    return total ? (n / total) * 100 : 0;
  }
}
