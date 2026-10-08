import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { LeadStore } from '../../core/lead-store';
import { ExportFormat } from '../../core/models';

@Component({
  selector: 'app-topbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:click)': 'menuOpen.set(false)' },
  template: `
    <header class="topbar">
      <div class="brand">
        <svg class="brand-mark" viewBox="0 0 32 32" aria-hidden="true"><rect width="32" height="32" rx="8"/><circle cx="14" cy="14" r="7"/><path d="M19 19l6 6"/></svg>
        <div>
          <div class="brand-name">LeadLens</div>
          <div class="brand-sub">Qualify SaaSquatch exports: clean · verify · enrich · rank · act</div>
        </div>
      </div>
      <div class="top-actions">
        <button class="thesis-pill" (click)="store.thesisOpen.set(true)" title="Edit what a great lead looks like">
          <span class="dot"></span><span>{{ thesisLabel() }}</span>
        </button>
        <div class="menu-wrap">
          <button class="btn" aria-haspopup="true" [attr.aria-expanded]="menuOpen()"
                  (click)="$event.stopPropagation(); menuOpen.set(!menuOpen())">Export ▾</button>
          @if (menuOpen()) {
            <div class="menu" role="menu">
              <div class="menu-head">Exports what the table shows (excluded leads left out)</div>
              <button role="menuitem" (click)="export('hubspot')">HubSpot CSV <small>contacts + companies import</small></button>
              <button role="menuitem" (click)="export('salesforce')">Salesforce CSV <small>Lead import wizard</small></button>
              <button role="menuitem" (click)="export('full')">Full CSV <small>every field, reasons, next action</small></button>
              <button role="menuitem" (click)="menuOpen.set(false); store.webhookOpen.set(true)">Send to CRM webhook… <small>Zapier · Make · n8n · HubSpot workflow</small></button>
            </div>
          }
        </div>
        <button class="btn primary" (click)="store.importOpen.set('csv')">+ Import leads</button>
      </div>
    </header>`,
})
export class Topbar {
  protected readonly store = inject(LeadStore);
  private readonly api = inject(ApiService);
  protected readonly menuOpen = signal(false);

  protected readonly thesisLabel = computed(() => {
    const t = this.store.thesis();
    if (!t) return 'Buy box';
    const parts = [
      `${t.industries.length || 'any'} industries`,
      t.locations.length ? t.locations.slice(0, 4).join('/') + (t.locations.length > 4 ? '…' : '') : 'anywhere',
    ];
    if (t.minEmployees != null || t.maxEmployees != null) parts.push(`${t.minEmployees ?? 0}–${t.maxEmployees ?? '∞'} staff`);
    return `${t.mode === 'ACQUISITION' ? 'Buy box' : 'ICP'}: ${parts.join(' · ')}`;
  });

  protected export(format: ExportFormat): void {
    this.menuOpen.set(false);
    location.href = this.api.exportUrl(format, this.store.filters());
  }
}
