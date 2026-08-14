import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export type DemoSourceType = 'YOUTUBE' | 'PDF' | 'PASTED_TEXT';
export type DemoSourceState = 'QUEUED' | 'PROCESSING' | 'READY' | 'FAILED';
export type DemoCardKind = 'DEFINITION' | 'CONTEXT';

export interface DemoCapabilities {
  enabled: boolean;
  ingestionEnabled: boolean;
  supportedLanguagePairs: string[];
  limits: {
    pdfBytes: number;
    pdfPages: number;
    pdfCharacters: number;
    youtubeMinutes: number;
    pastedTextCharacters: number;
    totalIndexedCharacters: number;
    uniqueLookups: number;
    cards: number;
  };
}

export interface DemoSource {
  type: DemoSourceType;
  reference: string;
  label: string;
  state: DemoSourceState;
  safeErrorCode?: string;
  youtubeVideoId?: string;
  characterCount: number;
  passageCount: number;
}

export interface DemoLookup {
  reference: string;
  term: string;
  definitions: { partOfSpeech: string; definition: string }[];
  contexts: {
    reference: string;
    text: string;
    sourceLabel: string;
    sourceType: DemoSourceType;
    location: Record<string, unknown>;
  }[];
  cached: boolean;
}

export interface DemoCard {
  reference: string;
  kind: DemoCardKind;
  term: string;
  definition: string;
  example: string;
  sourceLabel: string;
  languagePair: string;
  createdAt: string;
}

export interface DemoWorkspace {
  createdAt: string;
  inactivityExpiresAt: string;
  absoluteExpiresAt: string;
  sourceLanguage: string;
  targetLanguage: string;
  corpusRevision: number;
  sources: DemoSource[];
  indexedCharacters: number;
  latestLookup?: DemoLookup;
  cards: DemoCard[];
  deckRevision: number;
  exportReady: boolean;
}

@Injectable({ providedIn: 'root' })
export class DemoApi {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/v1/demo';

  capabilities() {
    return this.http.get<DemoCapabilities>(`${this.root}/capabilities`);
  }
  start() {
    return this.http.post<DemoWorkspace>(`${this.root}/workspace`, {});
  }
  workspace() {
    return this.http.get<DemoWorkspace>(`${this.root}/workspace`);
  }
  reset() {
    return this.http.post<DemoWorkspace>(`${this.root}/workspace/reset`, {});
  }
  exit() {
    return this.http.post<void>(`${this.root}/workspace/exit`, {});
  }
  languages(sourceLanguage: string, targetLanguage: string, confirmSourceChange: boolean) {
    return this.http.put<DemoWorkspace>(`${this.root}/languages`, {
      sourceLanguage,
      targetLanguage,
      confirmSourceChange,
    });
  }
  pastedText(text: string) {
    return this.http.post<DemoWorkspace>(`${this.root}/sources/pasted-text`, { text });
  }
  pdf(file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<DemoWorkspace>(`${this.root}/sources/pdf`, body);
  }
  youtube(url: string) {
    return this.http.post<DemoWorkspace>(`${this.root}/sources/youtube`, { url });
  }
  deleteSource(type: DemoSourceType) {
    return this.http.delete<DemoWorkspace>(`${this.root}/sources/${type}`);
  }
  retrySource(type: DemoSourceType) {
    return this.http.post<DemoWorkspace>(`${this.root}/sources/${type}/retry`, {});
  }
  lookup(term: string) {
    return this.http.post<DemoLookup>(`${this.root}/lookups`, { term });
  }
  saveCard(lookupReference: string, kind: DemoCardKind, selectionReference?: string) {
    return this.http.post<{ card: DemoCard; alreadyAdded: boolean }>(`${this.root}/cards`, {
      lookupReference,
      kind,
      ...(selectionReference ? { selectionReference } : {}),
    });
  }
  deleteCard(reference: string) {
    return this.http.delete<{ undoToken: string; undoExpiresAt: string }>(
      `${this.root}/cards/${reference}`,
    );
  }
  undoCard(reference: string, token: string) {
    return this.http.post<{ card: DemoCard; alreadyAdded: boolean }>(
      `${this.root}/cards/${reference}/undo?token=${encodeURIComponent(token)}`,
      {},
    );
  }
  exportDownload() {
    return this.http.post(`${this.root}/exports/download`, {}, { responseType: 'blob' });
  }
}
