import { ChangeDetectionStrategy, Component, OnInit, effect, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ApiService } from './core/api.service';
import { LeadStore } from './core/lead-store';
import { ToastService } from './core/toast.service';
import { EmptyState } from './features/dashboard/empty-state';
import { ImportDialog } from './features/import/import-dialog';
import { LeadDrawer } from './features/lead-detail/lead-drawer';
import { WebhookDialog } from './features/integrations/webhook-dialog';
import { ThesisPanel } from './features/thesis/thesis-panel';
import { Topbar } from './features/topbar/topbar';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Topbar, EmptyState, LeadDrawer, ImportDialog, ThesisPanel, WebhookDialog, RouterOutlet, RouterLink, RouterLinkActive],
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
    // (only when it actually changes: an unconditional navigate([]) at startup would race the
    // router's first navigation and send a deep link like /insights back to the root)
    effect(() => {
      const id = this.store.openLead()?.id ?? null;
      const current = new URLSearchParams(location.search).get('lead');
      if (String(id ?? '') === (current ?? '')) return;
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
    this.store.webhookOpen.set(false);
  }

  protected async reset(): Promise<void> {
    if (!confirm('Delete every lead and import? Your buy box is kept.')) return;
    await this.api.reset();
    this.store.clearSelection();
    this.store.close();
    await this.store.refresh();
  }
}
