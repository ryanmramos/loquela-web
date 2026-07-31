import { provideRouter } from '@angular/router';
import { ViewportScroller } from '@angular/common';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { BrowserSettings, SettingsApi } from '@api/api-adapters/settings-api';
import { SessionFacade } from '@core/auth/session-facade';
import { provideRuntimeConfig } from '@core/config/runtime-config';

import { SettingsPage } from './settings-page';

@Component({ template: '' })
class EmptyRoute {}

const settings: BrowserSettings = {
  profile: { displayName: 'Ryan Ramos', email: 'ryan@example.com' },
  linkedAccounts: { google: true, googleLinkedAt: '2026-07-18T00:00:00Z', whatsapp: false },
  languagePair: { sourceLanguage: 'es', definitionLanguage: 'en' },
  supportedPairs: [
    { sourceLanguage: 'es', definitionLanguage: 'en' },
    { sourceLanguage: 'es', definitionLanguage: 'es' },
    { sourceLanguage: 'en', definitionLanguage: 'es' },
  ],
  automaticCaptionFallback: true,
  exportDeckPrefix: 'Loquela — ',
  exportIncludeProvenance: true,
};

describe('SettingsPage', () => {
  const get = vi.fn(() => of(settings));
  const update = vi.fn(() => of(settings));
  const deleteAccount = vi.fn(() => of(undefined));
  const markDeleting = vi.fn();
  const updateInterfaceLanguage = vi.fn(() => of(undefined));
  const scrollToAnchor = vi.fn();
  const setScrollOffset = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [
        provideRouter([{ path: 'account-deleting', component: EmptyRoute }]),
        provideRuntimeConfig({
          apiBasePath: '/api/v1',
          googleLoginPath: '/api/v1/auth/google',
          localLoginEnabled: true,
          supportUrl: '/support',
          environmentName: 'test',
          telemetryEnabled: false,
        }),
        { provide: SettingsApi, useValue: { get, update, deleteAccount } },
        {
          provide: ViewportScroller,
          useValue: { scrollToAnchor, setOffset: setScrollOffset },
        },
        {
          provide: SessionFacade,
          useValue: { updateInterfaceLanguage, markDeleting },
        },
      ],
    });
  });

  it('loads Google metadata and starts each preference form pristine', () => {
    const component = TestBed.createComponent(SettingsPage).componentInstance;

    expect(component.settings()?.profile.displayName).toBe('Ryan Ramos');
    expect(component.languageForm.pristine).toBe(true);
    expect(component.captionForm.pristine).toBe(true);
    expect(component.exportForm.pristine).toBe(true);
  });

  it('renders linked provider icons and scrolls from the section navigation', () => {
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    const sectionButtons = [...element.querySelectorAll<HTMLButtonElement>('.settings-nav button')];
    const providerIcons = element.querySelectorAll('.provider-mark svg');
    const toggles = element.querySelectorAll<HTMLInputElement>('.toggle-input');

    expect(sectionButtons.map((button) => button.textContent?.trim())).toEqual([
      'Profile',
      'Linked accounts',
      'Language & lookup',
      'Delivery',
      'Export defaults',
      'Privacy & data',
    ]);
    expect(providerIcons).toHaveLength(1);
    expect(toggles).toHaveLength(2);
    expect([...toggles].every((toggle) => toggle.getAttribute('role') === 'switch')).toBe(true);
    expect(element.textContent).not.toContain('WhatsApp');
    expect(element.textContent).not.toContain('Daily study reminder');
    expect(element.textContent).not.toContain('Export my data');

    sectionButtons[3]?.click();
    expect(setScrollOffset).toHaveBeenCalledWith([0, 88]);
    expect(scrollToAnchor).toHaveBeenCalledWith('delivery');
    expect(fixture.componentInstance.activeSection()).toBe('delivery');
  });

  it('does not add a persistent saved message after changing interface language', () => {
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const select = element.querySelector<HTMLSelectElement>(
      'select[aria-label="Interface language"]',
    );
    expect(select).not.toBeNull();
    if (!select) {
      return;
    }
    select.value = 'es';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(updateInterfaceLanguage).toHaveBeenCalledWith('es');
    expect(element.querySelector('.save-status .success')).toBeNull();
  });

  it('keeps unsupported source and definition combinations from submission', () => {
    const component = TestBed.createComponent(SettingsPage).componentInstance;
    component.languageForm.setValue({ sourceLanguage: 'en', definitionLanguage: 'en' });
    component.languageForm.markAsDirty();

    component.saveLanguages();

    expect(component.languageError()).toBe(true);
    expect(update).not.toHaveBeenCalled();
  });

  it('tracks caption save state independently', () => {
    const component = TestBed.createComponent(SettingsPage).componentInstance;
    component.captionForm.controls.automaticCaptionFallback.setValue(false);
    component.captionForm.markAsDirty();

    component.saveCaptions();

    expect(update).toHaveBeenCalledWith(
      expect.objectContaining({ automaticCaptionFallback: false }),
    );
    expect(component.captionSaved()).toBe(true);
    expect(component.languageSaved()).toBe(false);
  });

  it('preserves dirty export edits when saving fails', () => {
    update.mockReturnValueOnce(throwError(() => new Error('offline')));
    const component = TestBed.createComponent(SettingsPage).componentInstance;
    component.exportForm.controls.exportDeckPrefix.setValue('Mine — ');
    component.exportForm.markAsDirty();

    component.saveExports();

    expect(component.exportError()).toBe(true);
    expect(component.exportForm.dirty).toBe(true);
  });

  it('requires the exact destructive confirmation phrase', () => {
    const component = TestBed.createComponent(SettingsPage).componentInstance;
    component.deletionForm.controls.confirmationPhrase.setValue('delete');
    component.deleteAccount();
    expect(deleteAccount).not.toHaveBeenCalled();

    component.deletionForm.controls.confirmationPhrase.setValue('DELETE MY ACCOUNT');
    component.deleteAccount();
    expect(deleteAccount).toHaveBeenCalledWith('DELETE MY ACCOUNT');
    expect(markDeleting).toHaveBeenCalled();
  });
});
