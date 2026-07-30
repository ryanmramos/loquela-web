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
    'settings.saveError': 'Could not save. Try again.',
    'settings.learningLanguages': 'Source and definition languages',
    'settings.learningLanguagesHelp':
      'Choose the language of your source material and the language used for definitions and learning help.',
    'settings.sourceLanguage': 'Source language',
    'settings.sourceLanguageHelp': 'The language used in sources you add and terms you look up.',
    'settings.definitionLanguage': 'Definition language',
    'settings.definitionLanguageHelp':
      'The language used for definitions, translations, and card help.',
    'settings.futureWorkOnlyTitle': 'Changes apply to future work',
    'settings.futureWorkOnlyHelp':
      'Existing sources, lookup results, and cards keep their original language snapshots and are not rewritten.',
    'settings.languageLoadErrorTitle': 'Language settings are unavailable',
    'settings.languageLoadErrorHelp':
      'We could not load the supported language combinations. Your current settings are unchanged.',
    'settings.languageSaveErrorTitle': 'Language settings were not saved',
    'settings.languageSaveErrorHelp':
      'The selected combination may no longer be supported. Reload the settings and try again.',
    'settings.tryAgain': 'Try again',
    'settings.saveLanguages': 'Save languages',
    'settings.languagesSaved': 'Language settings saved',
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
    'settings.saveError': 'No se pudo guardar. Inténtalo de nuevo.',
    'settings.learningLanguages': 'Idiomas de origen y definición',
    'settings.learningLanguagesHelp':
      'Elige el idioma de tu material de origen y el idioma de las definiciones y ayudas de aprendizaje.',
    'settings.sourceLanguage': 'Idioma de origen',
    'settings.sourceLanguageHelp': 'El idioma de las fuentes que añades y los términos que buscas.',
    'settings.definitionLanguage': 'Idioma de definición',
    'settings.definitionLanguageHelp':
      'El idioma de las definiciones, traducciones y ayudas de las tarjetas.',
    'settings.futureWorkOnlyTitle': 'Los cambios se aplican al trabajo futuro',
    'settings.futureWorkOnlyHelp':
      'Las fuentes, búsquedas y tarjetas existentes conservan sus idiomas originales y no se modifican.',
    'settings.languageLoadErrorTitle': 'La configuración de idiomas no está disponible',
    'settings.languageLoadErrorHelp':
      'No pudimos cargar las combinaciones compatibles. Tu configuración actual no ha cambiado.',
    'settings.languageSaveErrorTitle': 'No se guardó la configuración de idiomas',
    'settings.languageSaveErrorHelp':
      'Es posible que la combinación seleccionada ya no sea compatible. Recarga e inténtalo de nuevo.',
    'settings.tryAgain': 'Intentar de nuevo',
    'settings.saveLanguages': 'Guardar idiomas',
    'settings.languagesSaved': 'Configuración de idiomas guardada',
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
