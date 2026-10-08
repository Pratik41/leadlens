import { ChangeDetectionStrategy, Component, ElementRef, inject, viewChild } from '@angular/core';
import { LeadStore } from '../../core/lead-store';

@Component({
  selector: 'app-lead-filters',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown./)': 'focusSearch($event)' },
  template: `
    @let f = store.filters();
    <div class="toolbar">
      <div class="search">
        <input #search type="search" placeholder="Search company, owner, city, industry…  ( / )" autocomplete="off"
               [value]="f.q" (input)="onSearch(search.value)">
      </div>
      <div class="seg" role="tablist" aria-label="Tier">
        @for (t of tiers; track t.value) {
          <button [class.on]="f.tier === t.value" (click)="store.setFilter({ tier: t.value })">
            {{ t.label }}@if (t.hint) { <small>{{ t.hint }}</small> }
          </button>
        }
      </div>
      <select aria-label="Contact filter" [value]="f.contact" (change)="store.setFilter({ contact: $any($event.target).value })">
        <option value="">Any contact</option>
        <option value="verified">Verified personal email</option>
        <option value="email">Any usable email</option>
        <option value="phone">Valid phone</option>
        <option value="none">No usable contact</option>
      </select>
      <select aria-label="Pipeline status" [value]="f.status" (change)="store.setFilter({ status: $any($event.target).value })">
        <option value="">All statuses</option>
        <option value="NEW">New</option>
        <option value="QUALIFIED">Qualified</option>
        <option value="CONTACTED">Contacted</option>
        <option value="REPLIED">Replied</option>
        <option value="DISQUALIFIED">Disqualified</option>
      </select>
      <select aria-label="Sort" [value]="f.sort" (change)="store.setFilter({ sort: $any($event.target).value })">
        <option value="score,desc">Best score first</option>
        <option value="foundedYear,asc">Oldest business first</option>
        <option value="revenueUsd,desc">Largest revenue first</option>
        <option value="employees,desc">Most employees first</option>
        <option value="company,asc">Company A–Z</option>
        <option value="updatedAt,desc">Recently updated</option>
      </select>
    </div>`,
})
export class LeadFilters {
  protected readonly store = inject(LeadStore);
  private readonly search = viewChild.required<ElementRef<HTMLInputElement>>('search');
  private timer?: ReturnType<typeof setTimeout>;

  protected readonly tiers = [
    { value: '', label: 'All', hint: '' },
    { value: 'A', label: 'A', hint: 'work first' },
    { value: 'B', label: 'B', hint: '' },
    { value: 'C', label: 'C', hint: '' },
    { value: 'X', label: 'Excluded', hint: '' },
  ];

  protected onSearch(q: string): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.store.setFilter({ q }), 220);
  }

  protected focusSearch(e: Event): void {
    if (/INPUT|TEXTAREA|SELECT/.test((document.activeElement as HTMLElement | null)?.tagName ?? '')) return;
    e.preventDefault();
    this.search().nativeElement.focus();
  }
}
