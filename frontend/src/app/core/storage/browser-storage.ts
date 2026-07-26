import { inject, Injectable } from '@angular/core';
import { DOCUMENT } from '@angular/common';

export type BrowserPreferenceKey = 'dismissed-whatsapp-link-tip';

/**
 * The only reviewed browser-storage boundary. It accepts explicit non-sensitive preference keys;
 * API responses, identity data, source text, cards, and credentials must remain in memory.
 */
@Injectable({ providedIn: 'root' })
export class BrowserStorage {
  private readonly storage = inject(DOCUMENT).defaultView?.localStorage;

  read(key: BrowserPreferenceKey): string | null {
    return this.storage?.getItem(key) ?? null;
  }

  write(key: BrowserPreferenceKey, value: string): void {
    this.storage?.setItem(key, value);
  }

  remove(key: BrowserPreferenceKey): void {
    this.storage?.removeItem(key);
  }
}
