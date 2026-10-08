import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { LeadStore } from '../../core/lead-store';
import { ImportBatch } from '../../core/models';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-import-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.importOpen(); as tab) {
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="importTitle" (click)="close()">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-head">
            <h2 id="importTitle">Import leads</h2>
            <button class="icon-btn" aria-label="Close" (click)="close()">✕</button>
          </div>
          <div class="tabs" role="tablist">
            <button [class.on]="tab === 'csv'" (click)="store.importOpen.set('csv')">Upload CSV</button>
            <button [class.on]="tab === 'websites'" (click)="store.importOpen.set('websites')">Paste websites</button>
          </div>

          @if (tab === 'csv') {
            <label class="drop" [class.over]="dragging()"
                   (dragenter)="drag($event, true)" (dragover)="drag($event, true)" (dragleave)="drag($event, false)"
                   (drop)="dropped($event)">
              <input type="file" accept=".csv,.tsv,.txt" hidden (change)="upload($any($event.target).files?.[0])">
              @if (busy()) { <b><span class="spinner"></span> Importing…</b> }
              @else { <b>Drop a CSV here</b> <span>or <span class="link">browse</span></span> }
              <small>Comma, semicolon or tab separated · up to 10,000 rows · 20 MB</small>
            </label>
            <p class="hint">Columns like <code>Company</code>, <code>Website</code>, <code>Owner's Email</code>,
              <code>Employees Count</code> or <code>Year Founded</code> are recognised automatically, and anything unmapped
              is listed in the import report.</p>
          } @else {
            <textarea #sites class="websites" rows="8" placeholder="acmehvac.com&#10;https://www.example-plumbing.com&#10;…"></textarea>
            <p class="hint">One website per line (max 500). LeadLens reads each homepage plus up to two about/contact pages.</p>
            <button class="btn primary" [disabled]="busy()" (click)="websites(sites.value)">
              {{ busy() ? 'Building…' : 'Build leads' }}</button>
          }
          @if (error(); as e) { <div class="modal-error">{{ e }}</div> }
        </div>
      </div>
    }`,
})
export class ImportDialog {
  protected readonly store = inject(LeadStore);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly dragging = signal(false);

  protected close(): void {
    this.store.importOpen.set(false);
    this.error.set(null);
  }

  protected drag(e: DragEvent, over: boolean): void {
    e.preventDefault();
    this.dragging.set(over);
  }

  protected dropped(e: DragEvent): void {
    e.preventDefault();
    this.dragging.set(false);
    this.upload(e.dataTransfer?.files[0]);
  }

  protected upload(file?: File): void {
    if (file) this.run(this.api.uploadCsv(file));
  }

  protected websites(text: string): void {
    this.run(this.api.importWebsites(text));
  }

  private async run(request: Promise<ImportBatch>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      const b = await request;
      this.close();
      this.toast.show(`${b.totalRows} rows → ${b.newLeads} new companies, ${b.duplicatesMerged} duplicates merged. Verifying and enriching…`, 4500);
      this.store.filters.update((f) => ({ ...f, page: 0 }));
      await this.store.refresh();
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
}
