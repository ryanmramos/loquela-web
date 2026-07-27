import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { Api } from '@api/generated/api';
import { createPastedTextSource } from '@api/generated/fn/browser-source-controller/create-pasted-text-source';

export interface AcceptedPastedTextSource {
  readonly sourceId: string;
  readonly reference: string;
  readonly status: 'RECEIVED';
  readonly characterCount: number;
}

@Injectable({ providedIn: 'root' })
export class SourceApi {
  private readonly api = inject(Api);

  createPastedText(text: string): Observable<AcceptedPastedTextSource> {
    return this.api
      .invoke(createPastedTextSource, { body: { text } })
      .pipe(map((response) => ({ ...response })));
  }
}
