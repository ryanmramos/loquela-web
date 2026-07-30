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

@Injectable({ providedIn: 'root' })
export class SettingsApi {
  private readonly api = inject(Api);

  getLanguages(): Observable<LanguageSettings> {
    return this.api.invoke(getBrowserLanguageSettings).pipe(map(toLanguageSettings));
  }

  updateLanguages(languagePair: LanguagePair): Observable<LanguageSettings> {
    return this.api
      .invoke(updateBrowserLanguageSettings, { body: languagePair })
      .pipe(map(toLanguageSettings));
  }
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
