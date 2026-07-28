import { inject, Injectable } from '@angular/core';
import { from, map, Observable, switchMap } from 'rxjs';

import { Api } from '@api/generated/api';
import { createPastedTextSource } from '@api/generated/fn/browser-source-controller/create-pasted-text-source';
import { createYouTubeSource } from '@api/generated/fn/browser-source-controller/create-you-tube-source';
import { deleteSource } from '@api/generated/fn/browser-source-controller/delete-source';
import { getSource } from '@api/generated/fn/browser-source-controller/get-source';
import {
  listSources,
  ListSources$Params,
} from '@api/generated/fn/browser-source-controller/list-sources';
import { renameSource } from '@api/generated/fn/browser-source-controller/rename-source';
import { retrySource } from '@api/generated/fn/browser-source-controller/retry-source';
import { completeSourceUpload } from '@api/generated/fn/browser-source-upload-controller/complete-source-upload';
import { createSourceUpload } from '@api/generated/fn/browser-source-upload-controller/create-source-upload';
import {
  AcceptedSourceDto,
  BrowserSourceDetailsDto,
  BrowserSourceSummaryDto,
  SourceListDto,
  SourceMutationDto,
} from '@api/generated/models';

export interface AcceptedPastedTextSource {
  readonly sourceId: string;
  readonly reference: string;
  readonly status: 'RECEIVED';
  readonly characterCount: number;
}

export type SourceListQuery = ListSources$Params;
export type SourcePage = SourceListDto;
export type SourceSummary = BrowserSourceSummaryDto;
export type SourceDetails = BrowserSourceDetailsDto;
export type AcceptedSource = AcceptedSourceDto;
export type SourceMutation = SourceMutationDto;

@Injectable({ providedIn: 'root' })
export class SourceApi {
  private readonly api = inject(Api);

  createPastedText(text: string): Observable<AcceptedPastedTextSource> {
    return this.api
      .invoke(createPastedTextSource, { body: { text } })
      .pipe(map((response) => ({ ...response })));
  }

  createYouTube(url: string): Observable<AcceptedSource> {
    return this.api.invoke(createYouTubeSource, { body: { url } });
  }

  list(query: SourceListQuery): Observable<SourcePage> {
    return this.api.invoke(listSources, query);
  }

  get(reference: string): Observable<SourceDetails> {
    return this.api.invoke(getSource, { sourceReference: reference });
  }

  rename(reference: string, displayName: string, version: number): Observable<SourceSummary> {
    return this.api.invoke(renameSource, {
      sourceReference: reference,
      body: { displayName, version },
    });
  }

  retry(reference: string): Observable<SourceMutation> {
    return this.api.invoke(retrySource, { sourceReference: reference });
  }

  delete(reference: string): Observable<SourceMutation> {
    return this.api.invoke(deleteSource, { sourceReference: reference });
  }

  upload(file: File): Observable<AcceptedSource> {
    return from(file.arrayBuffer()).pipe(
      switchMap((bytes) => from(crypto.subtle.digest('SHA-256', bytes))),
      switchMap((bytes) =>
        this.api.invoke(createSourceUpload, {
          body: {
            fileName: file.name,
            mediaType: file.type,
            size: file.size,
            sha256: this.hex(new Uint8Array(bytes)),
          },
        }),
      ),
      switchMap((intent) =>
        this.api.invoke(completeSourceUpload, {
          uploadReference: intent.uploadReference,
          body: { file },
        }),
      ),
    );
  }

  private hex(bytes: Uint8Array): string {
    // The checksum binds the one-time intent to the exact selected bytes.
    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  }
}
