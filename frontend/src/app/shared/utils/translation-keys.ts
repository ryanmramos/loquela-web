import type { TranslationKey } from '@core/i18n/translation.service';

export const SOURCE_KIND_TRANSLATION_KEYS = {
  YOUTUBE_VIDEO: 'lookup.videoType',
  TIMED_TRANSCRIPT: 'lookup.timedTranscriptType',
  PASTED_TEXT: 'lookup.pastedType',
  TXT: 'lookup.txtSourceType',
  PDF: 'lookup.pdfSourceType',
  EPUB: 'lookup.epubSourceType',
} satisfies Record<string, TranslationKey>;

export type SourceKindTranslationCode = keyof typeof SOURCE_KIND_TRANSLATION_KEYS;

export function isSourceKindTranslationCode(value: string): value is SourceKindTranslationCode {
  return Object.hasOwn(SOURCE_KIND_TRANSLATION_KEYS, value);
}
