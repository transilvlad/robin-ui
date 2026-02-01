import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import JSON5 from 'json5';
import { environment } from '../../../environments/environment';
import {
  ClamAVConfig,
  ClamAVStatus,
  RspamdConfig,
  RspamdStatus,
  BlocklistEntry,
  CreateBlocklistEntry,
  ScannerTestResult,
  ClamAVConfigSchema,
  RspamdConfigSchema,
  BlocklistEntrySchema,
  ScannerTestResultSchema,
  ClamAVStatusSchema,
  RspamdStatusSchema,
} from '../models/security.model';

/**
 * Security service for managing ClamAV, Rspamd, and blocklist configurations.
 */
@Injectable({
  providedIn: 'root'
})
export class SecurityService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/config`;
  private readonly scannersUrl = `${environment.apiUrl}/scanners`;
  private readonly blocklistUrl = `${environment.apiUrl}/blocklist`;

  // ==========================================
  // ClamAV Operations
  // ==========================================

  /**
   * Get ClamAV configuration
   */
  getClamAVConfig(): Observable<ClamAVConfig> {
    return this.http.get(`${this.baseUrl}/clamav`, { 
      responseType: 'text',
      headers: new HttpHeaders({ 'Accept': 'application/json' })
    }).pipe(
      map(data => {
        const parsed = JSON5.parse(data);
        return ClamAVConfigSchema.parse(parsed);
      }),
      catchError(err => {
        console.error('Failed to get ClamAV config:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Update ClamAV configuration
   */
  updateClamAVConfig(config: ClamAVConfig): Observable<ClamAVConfig> {
    // Validate before sending
    const validated = ClamAVConfigSchema.parse(config);

    return this.http.put(`${this.baseUrl}/clamav`, validated, { responseType: 'text' }).pipe(
      map(data => {
        const parsed = JSON5.parse(data);
        return ClamAVConfigSchema.parse(parsed);
      }),
      catchError(err => {
        console.error('Failed to update ClamAV config:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Test ClamAV connection
   */
  testClamAV(config?: ClamAVConfig): Observable<ScannerTestResult> {
    const body = config ? { config } : undefined;

    return this.http.post(`${this.scannersUrl}/clamav/test`, body).pipe(
      map(data => ScannerTestResultSchema.parse(data)),
      catchError(err => {
        console.error('ClamAV test failed:', err);
        return of({
          success: false,
          status: 'ERROR' as const,
          message: err.message || 'Connection test failed',
        });
      })
    );
  }

  /**
   * Get ClamAV status
   */
  getClamAVStatus(): Observable<ClamAVStatus> {
    return this.http.get(`${this.scannersUrl}/clamav/status`).pipe(
      map(data => ClamAVStatusSchema.parse(data)),
      catchError(err => {
        console.error('Failed to get ClamAV status:', err);
        return of({
          status: 'UNKNOWN' as const,
          error: err.message || 'Failed to fetch status',
        });
      })
    );
  }

  // ==========================================
  // Rspamd Operations
  // ==========================================

  /**
   * Get Rspamd configuration
   */
  getRspamdConfig(): Observable<RspamdConfig> {
    return this.http.get(`${this.baseUrl}/rspamd`, { 
      responseType: 'text',
      headers: new HttpHeaders({ 'Accept': 'application/json' })
    }).pipe(
      map(data => {
        const parsed = JSON5.parse(data);
        return RspamdConfigSchema.parse(parsed);
      }),
      catchError(err => {
        console.error('Failed to get Rspamd config:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Update Rspamd configuration
   */
  updateRspamdConfig(config: RspamdConfig): Observable<RspamdConfig> {
    // Validate before sending
    const validated = RspamdConfigSchema.parse(config);

    return this.http.put(`${this.baseUrl}/rspamd`, validated, { responseType: 'text' }).pipe(
      map(data => {
        const parsed = JSON5.parse(data);
        return RspamdConfigSchema.parse(parsed);
      }),
      catchError(err => {
        console.error('Failed to update Rspamd config:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Test Rspamd connection
   */
  testRspamd(config?: RspamdConfig): Observable<ScannerTestResult> {
    const body = config ? { config } : undefined;

    return this.http.post(`${this.scannersUrl}/rspamd/test`, body).pipe(
      map(data => ScannerTestResultSchema.parse(data)),
      catchError(err => {
        console.error('Rspamd test failed:', err);
        return of({
          success: false,
          status: 'ERROR' as const,
          message: err.message || 'Connection test failed',
        });
      })
    );
  }

  /**
   * Get Rspamd status
   */
  getRspamdStatus(): Observable<RspamdStatus> {
    return this.http.get(`${this.scannersUrl}/rspamd/status`).pipe(
      map(data => RspamdStatusSchema.parse(data)),
      catchError(err => {
        console.error('Failed to get Rspamd status:', err);
        return of({
          status: 'UNKNOWN' as const,
          error: err.message || 'Failed to fetch status',
        });
      })
    );
  }

  // ==========================================
  // Blocklist Operations
  // ==========================================

  /**
   * Get all blocklist entries
   */
  getBlocklistEntries(params?: {
    page?: number;
    limit?: number;
    type?: string;
    active?: boolean;
  }): Observable<{ items: BlocklistEntry[]; total: number }> {
    let httpParams = new HttpParams();

    if (params?.page !== undefined) {
      httpParams = httpParams.set('page', params.page.toString());
    }
    if (params?.limit !== undefined) {
      httpParams = httpParams.set('limit', params.limit.toString());
    }
    if (params?.type) {
      httpParams = httpParams.set('type', params.type);
    }
    if (params?.active !== undefined) {
      httpParams = httpParams.set('active', params.active.toString());
    }

    return this.http.get<{ items: unknown[]; totalCount: number }>(this.blocklistUrl, { params: httpParams }).pipe(
      map(response => {
        console.log('Blocklist raw response:', response);
        return {
          items: response.items.map(item => BlocklistEntrySchema.parse(item)),
          total: response.totalCount,
        };
      }),
      catchError(err => {
        console.error('Failed to get blocklist entries:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Create a new blocklist entry
   */
  createBlocklistEntry(entry: CreateBlocklistEntry): Observable<BlocklistEntry> {
    return this.http.post(this.blocklistUrl, entry).pipe(
      map(data => BlocklistEntrySchema.parse(data)),
      catchError(err => {
        console.error('Failed to create blocklist entry:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Delete a blocklist entry
   */
  deleteBlocklistEntry(id: string): Observable<void> {
    return this.http.delete<void>(`${this.blocklistUrl}/${id}`).pipe(
      catchError(err => {
        console.error('Failed to delete blocklist entry:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Update blocklist entry (e.g., activate/deactivate)
   */
  updateBlocklistEntry(id: string, updates: Partial<BlocklistEntry>): Observable<BlocklistEntry> {
    return this.http.patch(`${this.blocklistUrl}/${id}`, updates).pipe(
      map(data => BlocklistEntrySchema.parse(data)),
      catchError(err => {
        console.error('Failed to update blocklist entry:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Import blocklist entries from file (CSV/JSON)
   */
  importBlocklist(file: File): Observable<{ imported: number; failed: number; errors: string[] }> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<{ imported: number; failed: number; errors: string[] }>(
      `${this.blocklistUrl}/import`,
      formData
    ).pipe(
      catchError(err => {
        console.error('Failed to import blocklist:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Export blocklist entries to CSV
   */
  exportBlocklist(format: 'csv' | 'json' = 'csv'): Observable<Blob> {
    return this.http.get(`${this.blocklistUrl}/export`, {
      params: { format },
      responseType: 'blob'
    }).pipe(
      catchError(err => {
        console.error('Failed to export blocklist:', err);
        return throwError(() => err);
      })
    );
  }
}
