import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@environments/environment';
import { DmarcReport, DmarcReportList, DmarcValidationResult, DnsResult } from '@features/dmarc/models';

@Injectable({
  providedIn: 'root',
})
export class ApiService {
  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // Service endpoint methods (routed through gateway)
  getHealth(): Observable<any> {
    return this.http.get(`${this.apiUrl}${environment.endpoints.health}`);
  }

  getConfig(): Observable<any> {
    return this.http.get(`${this.apiUrl}${environment.endpoints.config}`);
  }

  getMetrics(metricName?: string): Observable<any> {
    const url = metricName
      ? `${this.apiUrl}${environment.endpoints.metrics}/${metricName}`
      : `${this.apiUrl}${environment.endpoints.metrics}`;
    return this.http.get(url);
  }

  // API endpoint methods (port 8090)
  getQueue(page = 0, size = 50): Observable<any> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get(`${this.apiUrl}${environment.endpoints.queue}/json`, {
      params,
    });
  }

  performQueueAction(uid: string, action: string): Observable<any> {
    return this.http.post(
      `${this.apiUrl}${environment.endpoints.queue}/${uid}/${action}`,
      {}
    );
  }

  deleteQueueItem(uid: string): Observable<any> {
    return this.http.delete(
      `${this.apiUrl}${environment.endpoints.queue}/${uid}`
    );
  }

  getStorageItems(path = '/'): Observable<any> {
    const params = new HttpParams().set('path', path);
    return this.http.get(`${this.apiUrl}${environment.endpoints.store}/`, {
      params,
    });
  }

  getStorageFile(path: string): Observable<any> {
    const params = new HttpParams().set('path', path);
    return this.http.get(`${this.apiUrl}${environment.endpoints.store}/file`, {
      params,
    });
  }

  getLogs(lines = 100, level?: string): Observable<any> {
    let params = new HttpParams().set('lines', lines);
    if (level) {
      params = params.set('level', level);
    }
    return this.http.get(`${this.apiUrl}${environment.endpoints.logs}`, {
      params,
    });
  }

  // DMARC API methods
  getDmarcReports(page = 0, size = 20): Observable<DmarcReportList> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<DmarcReportList>(`${this.apiUrl}/dmarc/reports`, { params });
  }

  getDmarcReport(id: string): Observable<DmarcReport> {
    return this.http.get<DmarcReport>(`${this.apiUrl}/dmarc/reports/${id}`);
  }

  ingestDmarcXml(xml: string): Observable<DmarcReport> {
    return this.http.post<DmarcReport>(`${this.apiUrl}/dmarc/ingest/xml`, xml);
  }

  ingestDmarcEmail(file: File): Observable<DmarcReport> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<DmarcReport>(`${this.apiUrl}/dmarc/ingest/email`, file);
  }

  validateDmarcDomain(domain: string): Observable<DmarcValidationResult> {
    const params = new HttpParams().set('domain', domain);
    return this.http.get<DmarcValidationResult>(`${this.apiUrl}/dmarc/validate`, { params });
  }

  dnsLookup(domain: string, type: string): Observable<DnsResult> {
    const params = new HttpParams()
      .set('domain', domain)
      .set('type', type);
    return this.http.get<DnsResult>(`${this.apiUrl}/dmarc/dns`, { params });
  }

  // Generic HTTP methods for custom endpoints
  get<T>(endpoint: string, params?: HttpParams): Observable<T> {
    return this.http.get<T>(`${this.apiUrl}${endpoint}`, { params });
  }

  post<T>(endpoint: string, body: unknown): Observable<T> {
    return this.http.post<T>(`${this.apiUrl}${endpoint}`, body);
  }

  put<T>(endpoint: string, body: unknown): Observable<T> {
    return this.http.put<T>(`${this.apiUrl}${endpoint}`, body);
  }

  patch<T>(endpoint: string, body: unknown): Observable<T> {
    return this.http.patch<T>(`${this.apiUrl}${endpoint}`, body);
  }

  delete<T>(endpoint: string): Observable<T> {
    return this.http.delete<T>(`${this.apiUrl}${endpoint}`);
  }
}
