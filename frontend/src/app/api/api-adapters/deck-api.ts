import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Api } from '@api/generated/api';
import { createBrowserDeck } from '@api/generated/fn/browser-deck-controller/create-browser-deck';
import { createBrowserDeckExport } from '@api/generated/fn/browser-deck-controller/create-browser-deck-export';
import { deleteBrowserDeck } from '@api/generated/fn/browser-deck-controller/delete-browser-deck';
import { getBrowserExport } from '@api/generated/fn/browser-deck-controller/get-browser-export';
import { listBrowserDecks } from '@api/generated/fn/browser-deck-controller/list-browser-decks';
import { listBrowserExports } from '@api/generated/fn/browser-deck-controller/list-browser-exports';
import { updateBrowserDeck } from '@api/generated/fn/browser-deck-controller/update-browser-deck';
import { BrowserDeckDto, BrowserExportDto, DeckMutationRequestDto } from '@api/generated/models';

export type Deck = BrowserDeckDto;
export type DeckExport = BrowserExportDto;
export type DeckMutation = DeckMutationRequestDto;

@Injectable({ providedIn: 'root' })
export class DeckApi {
  private readonly api = inject(Api);

  list(): Observable<Deck[]> {
    return this.api.invoke(listBrowserDecks);
  }

  create(body: DeckMutation): Observable<Deck> {
    return this.api.invoke(createBrowserDeck, { body });
  }

  update(reference: string, body: DeckMutation): Observable<Deck> {
    return this.api.invoke(updateBrowserDeck, { reference, body });
  }

  delete(reference: string): Observable<void> {
    return this.api.invoke(deleteBrowserDeck, { reference });
  }

  export(reference: string): Observable<DeckExport> {
    return this.api.invoke(createBrowserDeckExport, { reference });
  }

  exports(): Observable<DeckExport[]> {
    return this.api.invoke(listBrowserExports);
  }

  exportStatus(reference: string): Observable<DeckExport> {
    return this.api.invoke(getBrowserExport, { reference });
  }

  downloadUrl(reference: string): string {
    return `/api/v1/exports/${encodeURIComponent(reference)}/download`;
  }
}
