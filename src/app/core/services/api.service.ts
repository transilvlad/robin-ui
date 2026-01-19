import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@environments/environment';

@Injectable({
  providedIn: 'root',
})
export class ApiService {
  private readonly apiUrl = environment.apiUrl;
  private readonly serviceUrl = environment.serviceUrl;

  constructor(private http: HttpClient) {}

  // Service endpoint methods (port 8080)
  getHealth(): Observable<any> {
    return this.http.get(`${this.serviceUrl}${environment.endpoints.health}`);
  }

  getConfig(): Observable<any> {
    return this.http.get(`${this.serviceUrl}${environment.endpoints.config}`);
  }

  getMetrics(metricName?: string): Observable<any> {
    const url = metricName
      ? `${this.serviceUrl}${environment.endpoints.metrics}/${metricName}`
      : `${this.serviceUrl}${environment.endpoints.metrics}`;
    return this.http.get(url);
  }

  // API endpoint methods (port 8090)
  getQueue(page = 0, size = 50): Observable<any> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get(`${this.apiUrl}${environment.endpoints.queue}`, {
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
    return this.http.get(`${this.apiUrl}${environment.endpoints.store}`, {
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

  // Generic HTTP methods for custom endpoints
  get<T>(endpoint: string, params?: HttpParams): Observable<T> {
    return this.http.get<T>(`${this.apiUrl}${endpoint}`, { params });
  }

  post<T>(endpoint: string, body: any): Observable<T> {
    return this.http.post<T>(`${this.apiUrl}${endpoint}`, body);
  }

  put<T>(endpoint: string, body: any): Observable<T> {
    return this.http.put<T>(`${this.apiUrl}${endpoint}`, body);
  }

  delete<T>(endpoint: string): Observable<T> {
    return this.http.delete<T>(`${this.apiUrl}${endpoint}`);
  }
}
