import { ChangeDetectionStrategy, Component, OnInit, effect, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from './core/api.service';
import { LeadStore } from './core/lead-store';
import { ToastService } from './core/toast.service';
import { EmptyState } from './features/dashboard/empty-state';
import { ImportBanner } from './features/dashboard/import-banner';
import { Kpis } from './features/dashboard/kpis';
import { ImportDialog } from './features/import/import-dialog';
import { LeadDrawer } from './features/lead-detail/lead-drawer';
import { LeadFilters } from './features/leads/lead-filters';
import { LeadTable } from './features/leads/lead-table';
import { ThesisPanel } from './features/thesis/thesis-panel';
import { Topbar } from './features/topbar/topbar';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Topbar, EmptyState, ImportBanner, Kpis, LeadFilters, LeadTable, LeadDrawer, ImportDialog, ThesisPanel],
  templateUrl: './app.html',
  host: { '(document:keydown.escape)': 'closeOverlays()' },
})
export class App implements OnInit {
  protected readonly store = inject(LeadStore);
  protected readonly toast = inject(ToastService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  constructor() {
    // Keep ?lead=<id> in the URL so a lead can be shared or reopened after a reload
    effect(() => {
      const id = this.store.openLead()?.id ?? null;
      this.router.navigate([], { queryParams: { lead: id }, queryParamsHandling: 'merge', replaceUrl: true });
    });
  }

  async ngOnInit(): Promise<void> {
    const lead = Number(new URLSearchParams(location.search).get('lead')); // read before the URL effect rewrites it
    this.store.loadThesis().catch(() => {});
    await this.store.refresh();
    if (lead) this.store.open(lead);
  }

  protected closeOverlays(): void {
    this.store.close();
    this.store.importOpen.set(false);
    this.store.thesisOpen.set(false);
  }

  protected async reset(): Promise<void> {
    if (!confirm('Delete every lead and import? Your buy box is kept.')) return;
    await this.api.reset();
    this.store.clearSelection();
    this.store.close();
    await this.store.refresh();
  }
}
