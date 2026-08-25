import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, mergeMap, throwError } from 'rxjs';

import { RUNTIME_CONFIG } from '@core/config/runtime-config';
import { ApiFieldError, ApiProblem, ApiProblemError } from '@core/errors/api-problem';
import type { ApiProblemDto } from '@api/generated/models/api-problem-dto';

import { isApiRequest } from './api-request';
import { CORRELATION_ID_HEADER } from './correlation-id.interceptor';

const UNKNOWN_CORRELATION_ID = 'unavailable';

export const apiProblemInterceptor: HttpInterceptorFn = (request, next) => {
  const config = inject(RUNTIME_CONFIG);

  if (!isApiRequest(request, config)) {
    return next(request);
  }

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof ApiProblemError) return throwError(() => error);

      const correlationId = request.headers.get(CORRELATION_ID_HEADER);
      if (error instanceof HttpErrorResponse) {
        const blobBody = blobProblemBody(error);
        if (blobBody) {
          return from(blobBody.text()).pipe(
            mergeMap((body) =>
              throwError(() => mapApiProblemBody(error, parseProblemBody(body), correlationId)),
            ),
          );
        }
      }

      return throwError(() => mapApiProblem(error, correlationId));
    }),
  );
};

export function mapApiProblem(
  error: unknown,
  requestCorrelationId: string | null = null,
): ApiProblemError {
  if (!(error instanceof HttpErrorResponse)) {
    return new ApiProblemError(fallbackProblem(0, requestCorrelationId));
  }

  return mapApiProblemBody(
    error,
    isRecord(error.error) ? error.error : undefined,
    requestCorrelationId,
  );
}

function mapApiProblemBody(
  error: HttpErrorResponse,
  body: Readonly<Record<string, unknown>> | undefined,
  requestCorrelationId: string | null,
): ApiProblemError {
  const responseCorrelationId = error.headers.get(CORRELATION_ID_HEADER);

  if (!body || !isCompleteProblem(body)) {
    return new ApiProblemError(
      fallbackProblem(error.status, responseCorrelationId ?? requestCorrelationId),
    );
  }

  const correlationId =
    body.correlationId.length > 0
      ? body.correlationId
      : (responseCorrelationId ?? requestCorrelationId ?? UNKNOWN_CORRELATION_ID);
  const detail = body.detail;
  const fieldErrors = body.fieldErrors;
  const problem: ApiProblem = {
    type: body.type,
    title: body.title,
    status: body.status,
    code: body.code,
    correlationId,
    ...(optionalString(detail) ? { detail } : {}),
    ...(isFieldErrors(fieldErrors) ? { fieldErrors } : {}),
  };

  return new ApiProblemError(problem);
}

function blobProblemBody(error: unknown): Blob | undefined {
  if (!(error instanceof HttpErrorResponse) || typeof Blob === 'undefined') return undefined;

  const body: unknown = error.error;
  return body instanceof Blob ? body : undefined;
}

function parseProblemBody(value: string): Readonly<Record<string, unknown>> | undefined {
  try {
    const parsed: unknown = JSON.parse(value);
    return isRecord(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

function fallbackProblem(status: number, correlationId: string | null): ApiProblem {
  return {
    type: 'about:blank',
    title: 'Request failed',
    status,
    code: status === 401 ? 'SESSION_EXPIRED' : 'UNEXPECTED_API_ERROR',
    detail:
      status === 401
        ? 'Your session has expired. Sign in and try again.'
        : 'The request could not be completed. Try again.',
    correlationId: correlationId ?? UNKNOWN_CORRELATION_ID,
  };
}

function isCompleteProblem(
  value: Readonly<Record<string, unknown>>,
): value is Readonly<Record<string, unknown>> & ApiProblemDto {
  return (
    typeof value['type'] === 'string' &&
    typeof value['title'] === 'string' &&
    typeof value['status'] === 'number' &&
    typeof value['code'] === 'string' &&
    typeof value['correlationId'] === 'string' &&
    isFieldErrors(value['fieldErrors'])
  );
}

function isFieldErrors(value: unknown): value is readonly ApiFieldError[] {
  return (
    Array.isArray(value) &&
    value.every(
      (item: unknown) =>
        isRecord(item) &&
        typeof item['field'] === 'string' &&
        typeof item['code'] === 'string' &&
        typeof item['message'] === 'string',
    )
  );
}

function optionalString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0;
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
