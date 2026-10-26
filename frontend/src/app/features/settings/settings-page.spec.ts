import { provideRouter } from '@angular/router';
import { ViewportScroller } from '@angular/common';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { BrowserSettings, SettingsApi } from '@api/api-adapters/settings-api';
import { SessionFacade } from '@core/auth/session-facade';
import { provideRuntimeConfig } from '@core/config/runtime-config';
import { TranslationService } from '@core/i18n/translation.service';

import { SettingsPage } from './settings-page';

@Component({ template: '' })
class EmptyRoute {}

const settings: BrowserSettings = {
  profile: { displayName: 'Test User', email: 'test@example.com' },
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

    expect(component.settings()?.profile.displayName).toBe('Test User');
    expect(component.languageForm.pristine).toBe(true);
    expect(component.captionForm.pristine).toBe(true);
    expect(component.exportForm.pristine).toBe(true);
  });

  it('renders linked account details and scrolls from the section navigation', () => {
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    const sectionButtons = [...element.querySelectorAll<HTMLButtonElement>('.settings-nav button')];
    const toggles = element.querySelectorAll<HTMLInputElement>('.toggle-input');

    expect(sectionButtons.map((button) => button.textContent?.trim())).toEqual([
      'Profile',
      'Linked accounts',
      'Language & lookup',
      'Delivery',
      'Export defaults',
      'Privacy & data',
    ]);
    expect(element.querySelector('.account-details span')?.textContent?.trim()).toBe(
      'test@example.com',
    );
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
    const select = element.querySelector<HTMLElement>('p-select[inputid="interface-language"]');
    expect(select).not.toBeNull();
    if (!select) {
      return;
    }
    fixture.componentInstance.changeInterfaceLanguage('es');
    fixture.detectChanges();

    expect(updateInterfaceLanguage).toHaveBeenCalledWith('es');
    expect(element.querySelector('.save-status .success')).toBeNull();
  });

  it('initializes the interface language selector from the active translation language', () => {
    TestBed.inject(TranslationService).setLanguage('es');
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    const select = element.querySelector<HTMLElement>('p-select[inputid="interface-language"]');

    expect(select).not.toBeNull();
    expect(fixture.componentInstance.translations.language()).toBe('es');
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

  it('stages account deletion behind an explicit acknowledgement', () => {
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('#deletion-acknowledgement')).toBeNull();
    fixture.componentInstance.startDeletion();
    fixture.detectChanges();
    expect(element.querySelector('#deletion-acknowledgement')).not.toBeNull();

    const component = TestBed.createComponent(SettingsPage).componentInstance;
    component.startDeletion();
    component.deleteAccount();
    expect(deleteAccount).not.toHaveBeenCalled();

    component.deletionForm.controls.acknowledged.setValue(true);
    component.deleteAccount();
    expect(deleteAccount).toHaveBeenCalledWith('DELETE MY ACCOUNT');
    expect(markDeleting).toHaveBeenCalled();
  });
});
