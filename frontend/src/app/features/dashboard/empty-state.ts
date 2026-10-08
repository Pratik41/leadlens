import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { LeadStore } from '../../core/lead-store';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="empty">
      <h1>Your SaaSquatch export, ranked and ready to work</h1>
      <p class="lede">Drop in a raw lead export. LeadLens merges duplicates, verifies every email and phone, reads each
        company's website for acquisition and buying signals, scores every lead against <em>your</em> buy box with the
        reasons shown, and drafts the first outreach. Then it exports to HubSpot or Salesforce.</p>
      <div class="empty-grid">
        <button class="empty-card" (click)="store.importOpen.set('csv')"><span class="ico">⬆</span><b>Upload a CSV</b>
          <span>SaaSquatch, Apollo, Google Maps scrapes or any sheet. Columns are matched automatically.</span></button>
        <button class="empty-card" (click)="store.importOpen.set('websites')"><span class="ico">🌐</span><b>Paste websites</b>
          <span>Just a list of domains: LeadLens builds each lead from the company's own site.</span></button>
        <button class="empty-card accent" [disabled]="loading()" (click)="sample()"><span class="ico">▶</span>
          <b>{{ loading() ? 'Importing…' : 'Try the sample' }}</b>
          <span>36 messy rows with duplicates, bad emails and a franchise. See the clean-up in 5 seconds.</span></button>
      </div>
    </section>`,
})
export class EmptyState {
  protected readonly store = inject(LeadStore);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  protected readonly loading = signal(false);

  protected async sample(): Promise<void> {
    this.loading.set(true);
    try {
      const b = await this.api.importSample();
      this.toast.show(`${b.totalRows} rows → ${b.newLeads} companies, ${b.duplicatesMerged} duplicates merged. Verifying…`, 4500);
      await this.store.refresh();
    } catch (e) {
      this.toast.show((e as Error).message);
    } finally {
      this.loading.set(false);
    }
  }
}
