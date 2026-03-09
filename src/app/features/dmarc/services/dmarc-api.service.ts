import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';
import { Ok, Err, Result } from '@core/models/auth.model';
import {
  DmarcLicense,
  DmarcSummary,
  DmarcReportsPage,
  DmarcReportDetail,
  DmarcSourcesResponse,
  DmarcCompliance,
  DmarcDailyAnalytics,
  DmarcPolicyAdvice,
  DmarcForensicPage,
  DmarcIngestResponse,
  DmarcDateRange,
  DmarcPageParams,
} from '../models/dmarc.models';

@Injectable({ providedIn: 'root' })
export class DmarcApiService {
  private readonly base = '/api/v1/dmarc';

  constructor(private http: HttpClient) {}

  getLicense(): Observable<Result<DmarcLicense, Error>> {
    return this.http.get<DmarcLicense>(`${this.base}/license`).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  getSummary(domain: string, params?: DmarcDateRange): Observable<Result<DmarcSummary, Error>> {
    const p = this.buildParams({ domain, ...params });
    return this.http.get<DmarcSummary>(`${this.base}/summary`, { params: p }).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  getReports(params?: DmarcPageParams): Observable<Result<DmarcReportsPage, Error>> {
    const p = this.buildParams(params ?? {});
    return this.http.get<DmarcReportsPage>(`${this.base}/reports`, { params: p }).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  getReport(id: number): Observable<Result<DmarcReportDetail, Error>> {
    return this.http.get<DmarcReportDetail>(`${this.base}/reports/${id}`).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  getSources(params?: DmarcDateRange & { limit?: number }): Observable<Result<DmarcSourcesResponse, Error>> {
    const p = this.buildParams(params ?? {});
    return this.http.get<DmarcSourcesResponse>(`${this.base}/sources`, { params: p }).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  getCompliance(domain: string, params?: DmarcDateRange): Observable<Result<DmarcCompliance, Error>> {
    const p = this.buildParams({ domain, ...params });
    return this.http.get<DmarcCompliance>(`${this.base}/analytics/compliance`, { params: p }).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  getDailyStats(domain: string, params?: DmarcDateRange): Observable<Result<DmarcDailyAnalytics, Error>> {
    const p = this.buildParams({ domain, ...params });
    return this.http.get<DmarcDailyAnalytics>(`${this.base}/analytics/daily`, { params: p }).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  getPolicyAdvice(domain: string): Observable<Result<DmarcPolicyAdvice, Error>> {
    const p = this.buildParams({ domain });
    return this.http.get<DmarcPolicyAdvice>(`${this.base}/analytics/policy-advice`, { params: p }).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  getForensicReports(params?: DmarcPageParams): Observable<Result<DmarcForensicPage, Error>> {
    const p = this.buildParams(params ?? {});
    return this.http.get<DmarcForensicPage>(`${this.base}/forensic`, { params: p }).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  ingest(rawEmail: string): Observable<Result<DmarcIngestResponse, Error>> {
    return this.http.post<DmarcIngestResponse>(`${this.base}/ingest`, rawEmail, {
      headers: { 'Content-Type': 'message/rfc822' }
    }).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  private buildParams(obj: DmarcDateRange & { limit?: number; page?: number; size?: number }): HttpParams {
    let p = new HttpParams();
    for (const [key, value] of Object.entries(obj)) {
      if (value !== undefined && value !== null) {
        p = p.set(key, String(value));
      }
    }
    return p;
  }
}
