import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Tier } from '../core/models';
import { TIER_COLOR } from './format';

/** Circular 0–100 score in the tier's colour. */
@Component({
  selector: 'app-score-ring',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="ring" [class.lg]="large()" [style.--p]="score()" [style.--c]="color()"
      [attr.aria-label]="'Score ' + score() + ' of 100'"><span>{{ score() }}</span></div>`,
  styles: `.ring.lg { width: 56px; height: 56px; } .ring.lg span { width: 46px; height: 46px; font-size: 17px; }`,
})
export class ScoreRing {
  readonly score = input.required<number>();
  readonly tier = input.required<Tier>();
  readonly large = input(false);
  protected readonly color = computed(() => TIER_COLOR[this.tier()]);
}
