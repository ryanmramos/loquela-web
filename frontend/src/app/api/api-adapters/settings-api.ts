import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { Api } from '@api/generated/api';
import { getBrowserLanguageSettings } from '@api/generated/fn/browser-language-settings-controller/get-browser-language-settings';
import { updateBrowserLanguageSettings } from '@api/generated/fn/browser-language-settings-controller/update-browser-language-settings';
import { BrowserLanguageSettingsDto } from '@api/generated/models/browser-language-settings-dto';

export interface LanguagePair {
  readonly sourceLanguage: string;
  readonly definitionLanguage: string;
}

export interface LanguageSettings {
  readonly languagePair: LanguagePair;
  readonly supportedPairs: readonly LanguagePair[];
}

export interface BrowserSettings extends LanguageSettings {
  readonly profile: {
    readonly displayName: string | null;
    readonly email: string | null;
  };
  readonly linkedAccounts: {
    readonly google: boolean;
    readonly googleLinkedAt: string | null;
    readonly whatsapp: boolean;
  };
  readonly automaticCaptionFallback: boolean;
  readonly exportDeckPrefix: string;
  readonly exportIncludeProvenance: boolean;
}

export interface SettingsPreferences {
  readonly sourceLanguage: string;
  readonly definitionLanguage: string;
  readonly automaticCaptionFallback: boolean;
  readonly exportDeckPrefix: string;
  readonly exportIncludeProvenance: boolean;
}

@Injectable({ providedIn: 'root' })
export class SettingsApi {
  private readonly api = inject(Api);
  private readonly http = inject(HttpClient);

  get(): Observable<BrowserSettings> {
    return this.http.get<BrowserSettingsResponse>('/api/v1/settings').pipe(map(toBrowserSettings));
  }

  update(preferences: SettingsPreferences): Observable<BrowserSettings> {
    return this.http
      .patch<BrowserSettingsResponse>('/api/v1/settings', preferences)
      .pipe(map(toBrowserSettings));
  }

  deleteAccount(confirmationPhrase: string): Observable<void> {
    return this.http
      .post('/api/v1/account-deletion-requests', { confirmationPhrase })
      .pipe(map(() => undefined));
  }

  getLanguages(): Observable<LanguageSettings> {
    return this.api.invoke(getBrowserLanguageSettings).pipe(map(toLanguageSettings));
  }

  updateLanguages(languagePair: LanguagePair): Observable<LanguageSettings> {
    return this.api
      .invoke(updateBrowserLanguageSettings, { body: languagePair })
      .pipe(map(toLanguageSettings));
  }
}

interface BrowserSettingsResponse {
  readonly profile?: { readonly displayName?: string; readonly email?: string };
  readonly linkedAccounts?: {
    readonly google?: boolean;
    readonly googleLinkedAt?: string;
    readonly whatsapp?: boolean;
  };
  readonly languagePair?: LanguagePair;
  readonly supportedPairs?: readonly LanguagePair[];
  readonly automaticCaptionFallback?: boolean;
  readonly exportDeckPrefix?: string;
  readonly exportIncludeProvenance?: boolean;
}

function toBrowserSettings(response: BrowserSettingsResponse): BrowserSettings {
  const languages = toLanguageSettings(response as BrowserLanguageSettingsDto);
  return {
    ...languages,
    profile: {
      displayName: response.profile?.displayName ?? null,
      email: response.profile?.email ?? null,
    },
    linkedAccounts: {
      google: response.linkedAccounts?.google === true,
      googleLinkedAt: response.linkedAccounts?.googleLinkedAt ?? null,
      whatsapp: response.linkedAccounts?.whatsapp === true,
    },
    automaticCaptionFallback: response.automaticCaptionFallback !== false,
    exportDeckPrefix: response.exportDeckPrefix ?? '',
    exportIncludeProvenance: response.exportIncludeProvenance !== false,
  };
}

function toLanguageSettings(response: BrowserLanguageSettingsDto): LanguageSettings {
  const sourceLanguage = response.languagePair?.sourceLanguage;
  const definitionLanguage = response.languagePair?.definitionLanguage;
  if (!sourceLanguage || !definitionLanguage) {
    throw new Error('Language settings response did not include the current language pair');
  }

  return {
    languagePair: { sourceLanguage, definitionLanguage },
    supportedPairs: (response.supportedPairs ?? []).flatMap((pair) =>
      pair.sourceLanguage && pair.definitionLanguage
        ? [
            {
              sourceLanguage: pair.sourceLanguage,
              definitionLanguage: pair.definitionLanguage,
            },
          ]
        : [],
    ),
  };
}
