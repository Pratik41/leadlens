import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'system' | 'light' | 'dark';
const KEY = 'leadlens.theme';
const ORDER: ThemeMode[] = ['system', 'light', 'dark'];

/**
 * Light / dark / follow-the-OS. The choice is a per-browser convenience, so it lives in localStorage
 * (guarded: storage can be unavailable in private windows). index.html applies it before Angular
 * boots so there is no flash of the wrong theme.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly mode = signal<ThemeMode>(read());

  constructor() {
    apply(this.mode());
  }

  cycle(): void {
    this.set(ORDER[(ORDER.indexOf(this.mode()) + 1) % ORDER.length]);
  }

  set(mode: ThemeMode): void {
    this.mode.set(mode);
    apply(mode);
    try {
      if (mode === 'system') localStorage.removeItem(KEY); else localStorage.setItem(KEY, mode);
    } catch {
      // storage blocked: the choice still applies for this visit
    }
  }
}

function read(): ThemeMode {
  try {
    const v = localStorage.getItem(KEY);
    return v === 'light' || v === 'dark' ? v : 'system';
  } catch {
    return 'system';
  }
}

function apply(mode: ThemeMode): void {
  const root = document.documentElement;
  if (mode === 'system') root.removeAttribute('data-theme'); else root.setAttribute('data-theme', mode);
}
