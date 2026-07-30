import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Api } from '@api/generated/api';
import { createBrowserCard } from '@api/generated/fn/browser-card-controller/create-browser-card';
import { delete$ } from '@api/generated/fn/browser-card-controller/delete';
import { getBrowserCard } from '@api/generated/fn/browser-card-controller/get-browser-card';
import { listBrowserCards } from '@api/generated/fn/browser-card-controller/list-browser-cards';
import { updateBrowserCard } from '@api/generated/fn/browser-card-controller/update-browser-card';
import { BrowserCardDto, BrowserCardListDto, CardMutationRequestDto } from '@api/generated/models';

export type { BrowserCardDto, CardMutationRequestDto } from '@api/generated/models';

@Injectable({ providedIn: 'root' })
export class CardApi {
  private readonly api = inject(Api);

  list(search?: string, deckReference?: string): Observable<BrowserCardListDto> {
    return this.api.invoke(listBrowserCards, { page: 0, pageSize: 50, search, deckReference });
  }

  get(reference: string): Observable<BrowserCardDto> {
    return this.api.invoke(getBrowserCard, { reference });
  }

  create(body: CardMutationRequestDto): Observable<BrowserCardDto> {
    return this.api.invoke(createBrowserCard, { body });
  }

  update(reference: string, body: CardMutationRequestDto): Observable<BrowserCardDto> {
    return this.api.invoke(updateBrowserCard, { reference, body });
  }

  delete(reference: string): Observable<void> {
    return this.api.invoke(delete$, { reference });
  }
}
