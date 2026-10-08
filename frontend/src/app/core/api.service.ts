import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, firstValueFrom, throwError } from 'rxjs';
import {
  ExportFormat, ImportBatch, Lead, LeadFilters, LeadPage, LeadStatus, RescoreOutcome, Stats, Thesis,
} from './models';

/** Thin typed client for the LeadLens REST API. Errors surface the server's {"error": "..."} message. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  stats(): Promise<Stats> {
    return this.call(this.http.get<Stats>('/api/stats'));
  }

  leads(f: LeadFilters): Promise<LeadPage> {
    return this.call(this.http.get<LeadPage>('/api/leads', { params: params(f) }));
  }

  lead(id: number): Promise<Lead> {
    return this.call(this.http.get<Lead>(`/api/leads/${id}`));
  }

  updateLead(id: number, change: { status?: LeadStatus; notes?: string }): Promise<Lead> {
    return this.call(this.http.patch<Lead>(`/api/leads/${id}`, change));
  }

  bulkStatus(ids: number[], status: LeadStatus): Promise<{ updated: number }> {
    return this.call(this.http.post<{ updated: number }>('/api/leads/bulk-status', { ids, status }));
  }

  writeBrief(id: number): Promise<Lead> {
    return this.call(this.http.post<Lead>(`/api/leads/${id}/brief`, null));
  }

  refreshLead(id: number): Promise<Lead> {
    return this.call(this.http.post<Lead>(`/api/leads/${id}/refresh`, null));
  }

  reset(): Promise<void> {
    return this.call(this.http.delete<void>('/api/leads'));
  }

  uploadCsv(file: File): Promise<ImportBatch> {
    const body = new FormData();
    body.append('file', file);
    return this.call(this.http.post<ImportBatch>('/api/imports', body));
  }

  importWebsites(text: string): Promise<ImportBatch> {
    return this.call(this.http.post<ImportBatch>('/api/imports/websites', { text }));
  }

  importSample(): Promise<ImportBatch> {
    return this.call(this.http.post<ImportBatch>('/api/imports/sample', null));
  }

  thesis(): Promise<Thesis> {
    return this.call(this.http.get<Thesis>('/api/thesis'));
  }

  presets(): Promise<Record<'acquisition' | 'sales', Thesis>> {
    return this.call(this.http.get<Record<'acquisition' | 'sales', Thesis>>('/api/thesis/presets'));
  }

  saveThesis(t: Thesis): Promise<{ thesis: Thesis; rescored: RescoreOutcome }> {
    return this.call(this.http.put<{ thesis: Thesis; rescored: RescoreOutcome }>('/api/thesis', t));
  }

  /** Browser download URL: the export is a streamed CSV, so it is fetched by navigation, not XHR. */
  exportUrl(format: ExportFormat, f: Partial<LeadFilters>, ids: number[] = []): string {
    let p = params({ ...f, page: undefined, size: undefined }).set('format', format);
    ids.forEach((id) => (p = p.append('ids', id)));
    return `/api/leads/export?${p.toString()}`;
  }

  private call<T>(request: Observable<T>): Promise<T> {
    return firstValueFrom(request.pipe(catchError((e: HttpErrorResponse) =>
      throwError(() => new Error(e.error?.error ?? (e.status === 0 ? 'The server is not reachable.' : `Request failed (${e.status})`))))));
  }
}

function params(f: Partial<LeadFilters>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(f)) {
    if (v !== '' && v !== null && v !== undefined) p = p.set(k, String(v));
  }
  return p;
}
