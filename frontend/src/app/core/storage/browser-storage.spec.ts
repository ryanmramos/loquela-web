import { TestBed } from '@angular/core/testing';

import { BrowserStorage } from './browser-storage';

describe('BrowserStorage', () => {
  it('round-trips an allowlisted non-sensitive preference', () => {
    const storage = TestBed.inject(BrowserStorage);

    storage.write('dismissed-whatsapp-link-tip', 'true');

    expect(storage.read('dismissed-whatsapp-link-tip')).toBe('true');
    storage.remove('dismissed-whatsapp-link-tip');
    expect(storage.read('dismissed-whatsapp-link-tip')).toBeNull();
  });
});
