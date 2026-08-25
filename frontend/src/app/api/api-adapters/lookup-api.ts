import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { Api } from '@api/generated/api';
import { createBrowserLookup } from '@api/generated/fn/browser-lookup-controller/create-browser-lookup';
import { getBrowserLookup } from '@api/generated/fn/browser-lookup-controller/get-browser-lookup';
import { listBrowserLookupDecks } from '@api/generated/fn/browser-lookup-controller/list-browser-lookup-decks';
import { saveBrowserLookupContext } from '@api/generated/fn/browser-lookup-controller/save-browser-lookup-context';
import {
  BrowserDeckOptionListDto,
  BrowserLookupAcceptedDto,
  BrowserLookupDto,
  BrowserLookupSaveDto,
} from '@api/generated/models';

export type { BrowserDeckOptionDto, BrowserLookupDto, ContextDto } from '@api/generated/models';
export type DeckOptionPage = BrowserDeckOptionListDto;

export interface BrowserLookupAccepted {
  readonly reference: string;
  readonly expiresAt?: string;
}

@Injectable({ providedIn: 'root' })
export class LookupApi {
  private readonly api = inject(Api);
  create(
    term: string,
    sourceReferences: string[],
    sourceKinds: string[],
  ): Observable<BrowserLookupAccepted> {
    return this.api
      .invoke(createBrowserLookup, {
        body: { term, sourceReferences, sourceKinds },
      })
      .pipe(
        map((accepted: BrowserLookupAcceptedDto) => {
          if (!accepted.reference) {
            throw new Error('Lookup acceptance response is missing its reference.');
          }
          return { reference: accepted.reference, expiresAt: accepted.expiresAt };
        }),
      );
  }
  get(reference: string): Observable<BrowserLookupDto> {
    return this.api.invoke(getBrowserLookup, { lookupReference: reference });
  }
  decks(search?: string, page = 0, pageSize = 20): Observable<BrowserDeckOptionListDto> {
    return this.api.invoke(listBrowserLookupDecks, { search, page, pageSize });
  }
  save(
    reference: string,
    contextNumber: number,
    deckReference: string,
  ): Observable<BrowserLookupSaveDto> {
    return this.api.invoke(saveBrowserLookupContext, {
      lookupReference: reference,
      contextNumber,
      body: { deckReference },
    });
  }
}
