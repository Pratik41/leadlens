import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { LeadStore } from '../../core/lead-store';
import { ToastService } from '../../core/toast.service';

const EXAMPLES = [
  'Family-owned HVAC in Texas over 20 years, not contacted',
  'Owners thinking about retirement',
  'Best plumbing leads with a verified email',
  'Oldest businesses in Florida I can call',
];

const SIGNAL_LABEL: Record<string, string> = {
  FAMILY_OWNED: 'Family-owned', OWNER_OPERATED: 'Owner-operated', MULTI_GENERATION: 'Multi-generation',
  RECURRING_REVENUE: 'Recurring revenue', RETIREMENT: 'Retirement / succession', MULTI_LOCATION: 'Multi-location',
  COMMERCIAL_CLIENTS: 'Commercial clients', HIRING: 'Hiring', STALE_WEBSITE: 'Stale website',
};

/** "Ask your list": a plain-English question becomes exact, removable filters on the table. */
@Component({
  selector: 'app-ask-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="ask" (submit)="$event.preventDefault(); ask(input.value)">
      <span class="ask-ico" aria-hidden="true">✦</span>
      <input #input class="ask-input" [value]="question()" (input)="question.set(input.value)"
             placeholder="Ask your list…  e.g. “{{ example }}”" aria-label="Ask your list in plain English">
      <button class="btn primary sm" type="submit" [disabled]="busy() || !question().trim()">
        @if (busy()) { <span class="spinner"></span> } @else { Ask }
      </button>
    </form>
    @if (chips().length || store.askExplanation()) {
      <div class="ask-result">
        @if (store.askExplanation(); as e) {
          <span class="ask-expl">{{ e.text }} <small>· {{ providerLabel(e.provider) }}</small></span>
        }
        @for (c of chips(); track c.key) {
          <button class="chip" (click)="remove(c.key)" [attr.aria-label]="'Remove filter ' + c.label">{{ c.label }} ✕</button>
        }
        <button class="link" (click)="clear()">Clear all</button>
      </div>
    }`,
})
export class AskBar {
  protected readonly store = inject(LeadStore);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  protected readonly question = signal('');
  protected readonly busy = signal(false);
  protected readonly example = EXAMPLES[Math.floor(Math.random() * EXAMPLES.length)];

  /** Filters the tier tabs and dropdowns don't show; these appear as chips. */
  protected readonly chips = computed(() => {
    const f = this.store.filters();
    const out: { key: 'state' | 'industry' | 'minYears' | 'signal' | 'q'; label: string }[] = [];
    if (f.q) out.push({ key: 'q', label: `“${f.q}”` });
    if (f.industry) out.push({ key: 'industry', label: f.industry });
    if (f.state) out.push({ key: 'state', label: f.state });
    if (f.minYears) out.push({ key: 'minYears', label: `${f.minYears}+ years` });
    if (f.signal) out.push({ key: 'signal', label: SIGNAL_LABEL[f.signal] ?? f.signal });
    return out;
  });

  protected async ask(text: string): Promise<void> {
    if (!text.trim()) return;
    this.busy.set(true);
    try {
      const { plan, provider } = await this.api.ask(text);
      const { explanation, ...filters } = plan;
      this.store.applyPlan(filters, { text: explanation, provider });
    } catch (e) {
      this.toast.show((e as Error).message);
    } finally {
      this.busy.set(false);
    }
  }

  protected providerLabel(p: string): string {
    return p === 'rules' ? 'rule parser' : p === 'insights' ? 'from Insights' : 'interpreted by Claude';
  }

  protected remove(key: 'state' | 'industry' | 'minYears' | 'signal' | 'q'): void {
    this.store.setFilter({ [key]: key === 'minYears' ? null : '' });
  }

  protected clear(): void {
    this.question.set('');
    this.store.clearFilters();
  }
}
