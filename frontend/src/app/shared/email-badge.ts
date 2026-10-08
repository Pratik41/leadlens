import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { EmailStatus } from '../core/models';
import { EMAIL_BADGE } from './format';

@Component({
  selector: 'app-email-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [class]="'badge ' + info().tone" [title]="info().help">{{ info().label }}</span>`,
})
export class EmailBadge {
  readonly status = input.required<EmailStatus>();
  protected readonly info = computed(() => EMAIL_BADGE[this.status()]);
}
