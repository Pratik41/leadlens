import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { LeadStore } from '../../core/lead-store';
import { ImportBanner } from '../dashboard/import-banner';
import { Kpis } from '../dashboard/kpis';
import { AskBar } from './ask-bar';
import { LeadFilters } from './lead-filters';
import { LeadTable } from './lead-table';

@Component({
  selector: 'app-leads-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ImportBanner, Kpis, AskBar, LeadFilters, LeadTable],
  template: `
    @if (store.stats(); as stats) {
      @if (stats.latestImport; as batch) { <app-import-banner [batch]="batch" /> }
      <app-kpis [stats]="stats" />
      <app-ask-bar />
      <app-lead-filters />
      <app-lead-table />
    }`,
})
export class LeadsPage {
  protected readonly store = inject(LeadStore);
}
