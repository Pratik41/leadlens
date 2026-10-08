import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { LeadStore } from '../../core/lead-store';
import { ToastService } from '../../core/toast.service';

/** Push the current view to a CRM automation (Zapier / Make / n8n / HubSpot workflow webhook). */
@Component({
  selector: 'app-webhook-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.webhookOpen()) {
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="hookTitle" (click)="close()">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-head">
            <h2 id="hookTitle">Send to your CRM</h2>
            <button class="icon-btn" aria-label="Close" (click)="close()">✕</button>
          </div>
          <p class="hint">Paste a webhook URL from Zapier (“Catch Hook”), Make, n8n or a HubSpot workflow. LeadLens POSTs the
            leads in the current view (excluded leads left out) as JSON, in batches of 100, with name, contact,
            score, tier, reasons and next action.</p>
          <form class="form" (submit)="$event.preventDefault(); save(url.value)">
            <label>Webhook URL<input #url type="url" placeholder="https://hooks.zapier.com/hooks/catch/…" [value]="saved() ?? ''"></label>
            <div class="form-actions">
              <button type="submit" class="btn" [disabled]="busy()">Save URL</button>
              <button type="button" class="btn primary" [disabled]="busy() || !saved()" (click)="send()">
                @if (busy()) { <span class="spinner"></span> Sending… } @else { Send current view }
              </button>
            </div>
          </form>
          @if (error(); as e) { <div class="modal-error">{{ e }}</div> }
          <p class="hint"><small>Only public http(s) addresses are accepted (internal and cloud-metadata addresses are
            blocked).</small></p>
        </div>
      </div>
    }`,
})
export class WebhookDialog {
  protected readonly store = inject(LeadStore);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  protected readonly saved = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      if (this.store.webhookOpen()) {
        this.error.set(null);
        this.api.webhook().then((w) => this.saved.set(w.url)).catch(() => {});
      }
    });
  }

  protected close(): void {
    this.store.webhookOpen.set(false);
  }

  protected async save(url: string): Promise<void> {
    await this.run(async () => {
      this.saved.set((await this.api.saveWebhook(url.trim())).url);
      this.toast.show(url.trim() ? 'Webhook saved' : 'Webhook removed');
    });
  }

  protected async send(): Promise<void> {
    await this.run(async () => {
      const r = await this.api.sendWebhook(this.store.filters());
      this.toast.show(`Sent ${r.sent} leads to ${r.target} in ${r.batches} batch${r.batches === 1 ? '' : 'es'}`, 4500);
      this.close();
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
}
