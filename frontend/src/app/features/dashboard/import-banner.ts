import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ImportBatch } from '../../core/models';

/** The data-quality report for the latest import, with live progress while it enriches. */
@Component({
  selector: 'app-import-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let b = batch();
    <div class="batch" [class.failed]="b.status === 'FAILED'">
      <div>
        <div class="title">@if (running()) {<span class="spinner"></span> }{{ b.sourceName }}</div>
        <div class="facts">
          <span><b>{{ b.totalRows }}</b> rows</span>
          <span>→ <b>{{ b.newLeads }}</b> new companies</span>
          <span><b>{{ b.duplicatesMerged }}</b> duplicates merged</span>
          <span><b>{{ b.emptyRows }}</b> empty/unusable skipped</span>
          @switch (b.status) {
            @case ('RUNNING') { <span>verifying &amp; enriching <b>{{ b.processed }}/{{ b.toProcess }}</b></span> }
            @case ('FAILED') { <span>{{ b.error || 'Failed' }}</span> }
            @default { <span>✓ verified &amp; enriched</span> }
          }
        </div>
        @if (b.unmappedColumns) { <div class="note">Not imported: {{ b.unmappedColumns }}</div> }
      </div>
      @if (running()) { <div class="bar"><i [style.width.%]="progress()"></i></div> }
    </div>`,
})
export class ImportBanner {
  readonly batch = input.required<ImportBatch>();
  protected readonly running = computed(() => this.batch().status === 'RUNNING');
  protected readonly progress = computed(() => {
    const b = this.batch();
    return b.toProcess ? Math.round((b.processed / b.toProcess) * 100) : 100;
  });
}
