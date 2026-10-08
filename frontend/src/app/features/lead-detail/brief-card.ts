import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { LeadStore } from '../../core/lead-store';
import { Lead } from '../../core/models';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-brief-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let l = lead();
    <div class="card brief">
      @if (writing()) {
        <h3>Outreach brief</h3>
        <p class="brief-empty"><span class="spinner"></span> Writing from the verified facts…</p>
      } @else if (l.brief; as b) {
        <h3>Outreach brief <span class="provider">{{ provider() }}</span></h3>
        <p class="headline">{{ b.headline }}</p>
        <p>{{ b.summary }}</p>
        <p><b>Why now:</b> {{ b.whyNow }}</p>
        @if (b.talkingPoints.length) {
          <ul>@for (t of b.talkingPoints; track $index) { <li>{{ t }}</li> }</ul>
        }
        <div class="email">
          <div class="subj">{{ b.emailSubject }}</div>
          <pre>{{ b.emailBody }}</pre>
          <div class="email-actions">
            @if (mailto(); as href) { <a class="btn sm primary" [href]="href">Open in email</a> }
            <button class="btn sm" (click)="copy(b.emailSubject + '\\n\\n' + b.emailBody)">Copy email</button>
            <button class="btn sm" (click)="write()">↻ Regenerate</button>
          </div>
        </div>
        @if (b.callOpener) { <p><b>Call opener:</b> “{{ b.callOpener }}”</p> }
        @if (b.risks.length) { <p><b>Check first:</b> {{ b.risks.join(' · ') }}</p> }
      } @else {
        <h3>Outreach brief</h3>
        <p class="brief-empty">A one-screen brief: why this lead matters, why now, talking points and a first email
          written from the verified facts above{{ store.stats()?.claudeEnabled ? ' by Claude' : '' }}.</p>
        <button class="btn primary sm" (click)="write()">✦ Write the brief</button>
      }
    </div>`,
})
export class BriefCard {
  readonly lead = input.required<Lead>();
  protected readonly store = inject(LeadStore);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  protected readonly writing = signal(false);

  protected readonly provider = computed(() => {
    const p = this.lead().briefProvider;
    return p && p !== 'template' ? `Written by Claude (${p})` : 'Template writer · set ANTHROPIC_API_KEY for Claude';
  });

  protected readonly mailto = computed(() => {
    const l = this.lead();
    if (!l.email || !l.brief) return null;
    return `mailto:${encodeURIComponent(l.email)}?subject=${encodeURIComponent(l.brief.emailSubject)}&body=${encodeURIComponent(l.brief.emailBody)}`;
  });

  protected async write(): Promise<void> {
    this.writing.set(true);
    try {
      this.store.patchLead(await this.api.writeBrief(this.lead().id));
    } catch (e) {
      this.toast.show((e as Error).message);
    } finally {
      this.writing.set(false);
    }
  }

  protected copy(text: string): void {
    navigator.clipboard.writeText(text).then(() => this.toast.show('Copied'));
  }
}
