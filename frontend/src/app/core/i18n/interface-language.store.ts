import { Injectable, signal } from '@angular/core';

export const INTERFACE_LANGUAGES = ['en', 'es'] as const;
export type InterfaceLanguage = (typeof INTERFACE_LANGUAGES)[number];

export function isInterfaceLanguage(value: string): value is InterfaceLanguage {
  return INTERFACE_LANGUAGES.some((language) => language === value);
}

export const INTERFACE_LANGUAGE_OPTIONS = [
  { code: 'en', label: 'settings.english' },
  { code: 'es', label: 'settings.spanish' },
] as const satisfies readonly { code: InterfaceLanguage; label: string }[];

@Injectable({ providedIn: 'root' })
export class InterfaceLanguageStore {
  private readonly selectedLanguage = signal<InterfaceLanguage>(this.detectLanguage());
  readonly language = this.selectedLanguage.asReadonly();

  setLanguage(language: InterfaceLanguage): void {
    this.selectedLanguage.set(language);
    document.documentElement.lang = language;
  }

  private detectLanguage(): InterfaceLanguage {
    return navigator.language.toLowerCase().startsWith('es') ? 'es' : 'en';
  }
}
