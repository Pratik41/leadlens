import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { LeadStore } from '../../core/lead-store';
import { Thesis } from '../../core/models';
import { ToastService } from '../../core/toast.service';

/** The buy box (acquisition) or ICP (sales). Saving re-scores every lead on the server. */
@Component({
  selector: 'app-thesis-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  template: `
    @if (store.thesisOpen()) {
      <div class="scrim" (click)="store.thesisOpen.set(false)"></div>
      <aside class="drawer narrow open" aria-label="Buy box">
        <div class="drawer-inner">
          <div class="drawer-head">
            <div><div class="eyebrow">Scoring profile</div><h2>What does a great lead look like?</h2></div>
            <button class="icon-btn" aria-label="Close" (click)="store.thesisOpen.set(false)">✕</button>
          </div>
          <form class="form" [formGroup]="form" (ngSubmit)="save()">
            <fieldset class="mode">
              <legend>I'm using this list to…</legend>
              <label><input type="radio" formControlName="mode" value="ACQUISITION">
                <span><b>Acquire a business</b><small>Search fund / PE: rewards age, owner-operated, recurring revenue, succession signals</small></span></label>
              <label><input type="radio" formControlName="mode" value="SALES">
                <span><b>Sell to businesses</b><small>Sales team: rewards named decision makers, hiring, reachability</small></span></label>
            </fieldset>
            <label>Target industries <small>comma separated, matched against industry and description</small>
              <textarea rows="3" formControlName="industries"></textarea></label>
            <label>Target states or cities <small>empty = anywhere</small><input formControlName="locations"></label>
            <div class="row2">
              <label>Min employees<input type="number" min="0" formControlName="minEmployees"></label>
              <label>Max employees<input type="number" min="0" formControlName="maxEmployees"></label>
            </div>
            <div class="row2">
              <label>Min revenue ($)<input type="number" min="0" step="100000" formControlName="minRevenue"></label>
              <label>Max revenue ($)<input type="number" min="0" step="100000" formControlName="maxRevenue"></label>
            </div>
            <label>Min years in business<input type="number" min="0" formControlName="minYearsInBusiness"></label>
            <label>Exclude if it mentions <small>e.g. franchise, government</small><input formControlName="excludeKeywords"></label>
            <div class="form-actions">
              <button type="submit" class="btn primary" [disabled]="saving()">{{ saving() ? 'Re-scoring…' : 'Save & re-score all leads' }}</button>
              <button type="button" class="link" (click)="preset('acquisition')">Acquisition preset</button>
              <button type="button" class="link" (click)="preset('sales')">Sales preset</button>
            </div>
            @if (error(); as e) { <div class="modal-error">{{ e }}</div> }
          </form>
        </div>
      </aside>
    }`,
})
export class ThesisPanel {
  protected readonly store = inject(LeadStore);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    mode: 'ACQUISITION' as Thesis['mode'],
    industries: '',
    locations: '',
    minEmployees: null as number | null,
    maxEmployees: null as number | null,
    minRevenue: null as number | null,
    maxRevenue: null as number | null,
    minYearsInBusiness: null as number | null,
    excludeKeywords: '',
  });

  constructor() {
    // Load the saved thesis into the form each time the panel opens
    effect(() => {
      const t = this.store.thesis();
      if (this.store.thesisOpen() && t) {
        this.fill(t);
        this.error.set(null);
      }
    });
  }

  protected async preset(name: 'acquisition' | 'sales'): Promise<void> {
    this.fill((await this.api.presets())[name]);
  }

  protected async save(): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const list = (s: string) => s.split(',').map((x) => x.trim()).filter(Boolean);
    try {
      const r = await this.api.saveThesis({
        ...v, industries: list(v.industries), locations: list(v.locations), excludeKeywords: list(v.excludeKeywords),
      });
      this.store.thesis.set(r.thesis);
      this.store.thesisOpen.set(false);
      const d = r.rescored;
      const delta = (d.after.A ?? 0) - (d.before.A ?? 0);
      this.toast.show(`Re-scored ${d.leads} leads: ${d.after.A ?? 0} in Tier A (${delta >= 0 ? '+' : ''}${delta})`, 4500);
      await this.store.refresh();
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.saving.set(false);
    }
  }

  private fill(t: Thesis): void {
    this.form.setValue({
      mode: t.mode,
      industries: t.industries.join(', '),
      locations: t.locations.join(', '),
      minEmployees: t.minEmployees,
      maxEmployees: t.maxEmployees,
      minRevenue: t.minRevenue,
      maxRevenue: t.maxRevenue,
      minYearsInBusiness: t.minYearsInBusiness,
      excludeKeywords: t.excludeKeywords.join(', '),
    });
  }
}
