import { Injectable, computed, signal } from '@angular/core';

export type InterfaceLanguage = 'en' | 'es';

const translations = {
  en: {
    'navigation.overview': 'Overview',
    'navigation.sources': 'Sources',
    'navigation.lookup': 'Lookup',
    'navigation.cards': 'Cards',
    'navigation.decks': 'Decks',
    'navigation.study': 'Study',
    'navigation.settings': 'Settings',
    'shell.search': 'Search your workspace…',
    'shell.activity': 'Activity',
    'shell.addSource': 'Add source',
    'shell.cardsToStudy': '{count} cards to study',
    'shell.practice': 'Start practice',
    'shell.practiceDescription': 'Practice cards saved from your personal corpus.',
    'shell.skipToContent': 'Skip to main content',
    'settings.eyebrow': 'Account preferences',
    'settings.title': 'Settings',
    'settings.interfaceLanguage': 'Interface language',
    'settings.interfaceLanguageHelp': 'Choose the language Loquela uses throughout the website.',
    'settings.english': 'English',
    'settings.spanish': 'Spanish',
    'settings.saving': 'Saving…',
    'settings.saved': 'Saved',
  },
  es: {
    'navigation.overview': 'Resumen',
    'navigation.sources': 'Fuentes',
    'navigation.lookup': 'Buscar',
    'navigation.cards': 'Tarjetas',
    'navigation.decks': 'Mazos',
    'navigation.study': 'Estudiar',
    'navigation.settings': 'Configuración',
    'shell.search': 'Busca en tu espacio…',
    'shell.activity': 'Actividad',
    'shell.addSource': 'Añadir fuente',
    'shell.cardsToStudy': '{count} tarjetas para estudiar',
    'shell.practice': 'Empezar a practicar',
    'shell.practiceDescription': 'Practica las tarjetas guardadas de tu corpus personal.',
    'shell.skipToContent': 'Saltar al contenido principal',
    'settings.eyebrow': 'Preferencias de la cuenta',
    'settings.title': 'Configuración',
    'settings.interfaceLanguage': 'Idioma de la interfaz',
    'settings.interfaceLanguageHelp': 'Elige el idioma que Loquela usa en todo el sitio web.',
    'settings.english': 'Inglés',
    'settings.spanish': 'Español',
    'settings.saving': 'Guardando…',
    'settings.saved': 'Guardado',
  },
} as const;

@Injectable({ providedIn: 'root' })
export class TranslationService {
  private readonly selectedLanguage = signal<InterfaceLanguage>(this.detectLanguage());
  readonly language = this.selectedLanguage.asReadonly();
  readonly htmlLanguage = computed(() => this.language());

  setLanguage(language: InterfaceLanguage): void {
    this.selectedLanguage.set(language);
    document.documentElement.lang = language;
  }

  translate(
    key: keyof (typeof translations)['en'],
    values: Record<string, string | number> = {},
  ): string {
    return translations[this.language()][key].replace(/\{(\w+)\}/gu, (_, name: string) =>
      String(values[name] ?? ''),
    );
  }

  private detectLanguage(): InterfaceLanguage {
    return navigator.language.toLowerCase().startsWith('es') ? 'es' : 'en';
  }
}
