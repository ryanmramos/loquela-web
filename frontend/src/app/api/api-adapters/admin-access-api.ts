import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type AccessRole = 'USER' | 'ADMIN';
export type AccessStatus = 'ACTIVE' | 'REVOKED';

export interface AdminAccessEntry {
  readonly reference: string;
  readonly email: string;
  readonly role: AccessRole;
  readonly status: AccessStatus;
  readonly version: number;
  readonly createdByUserId: string | null;
  readonly updatedByUserId: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface AdminAccessEntryPage {
  readonly items: AdminAccessEntry[];
  readonly page: number;
  readonly pageSize: number;
  readonly totalItems: number;
  readonly totalPages: number;
}

export interface AdminAccessQuery {
  readonly search?: string;
  readonly role?: AccessRole;
  readonly status?: AccessStatus;
  readonly page: number;
  readonly pageSize: number;
}

@Injectable({ providedIn: 'root' })
export class AdminAccessApi {
  private readonly http = inject(HttpClient);

  list(query: AdminAccessQuery): Observable<AdminAccessEntryPage> {
    let params = new HttpParams().set('page', query.page).set('pageSize', query.pageSize);
    if (query.search) params = params.set('search', query.search);
    if (query.role) params = params.set('role', query.role);
    if (query.status) params = params.set('status', query.status);
    return this.http.get<AdminAccessEntryPage>('/api/v1/admin/access-entries', { params });
  }

  create(request: {
    email: string;
    role: AccessRole;
    confirmationEmail?: string;
  }): Observable<AdminAccessEntry> {
    return this.http.post<AdminAccessEntry>('/api/v1/admin/access-entries', request);
  }

  update(
    entry: AdminAccessEntry,
    request: { role: AccessRole; status: AccessStatus; confirmationEmail?: string },
  ): Observable<AdminAccessEntry> {
    return this.http.patch<AdminAccessEntry>(
      `/api/v1/admin/access-entries/${encodeURIComponent(entry.reference)}`,
      { ...request, version: entry.version },
    );
  }
}
