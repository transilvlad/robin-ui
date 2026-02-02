import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import JSON5 from 'json5';
import { environment } from '../../../environments/environment';

/**
 * Service for managing generic configuration sections (e.g. dovecot, relay, server).
 */
@Injectable({
  providedIn: 'root'
})
export class ConfigService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/config`;

  /**
   * Get configuration for a specific section
   */
  getConfig<T>(section: string): Observable<T> {
    return this.http.get(`${this.baseUrl}/${section}`, { 
      responseType: 'text',
      headers: new HttpHeaders({ 'Accept': 'application/json' })
    }).pipe(
      map(data => {
        // Parse JSON5 to handle comments/flexible syntax if backend returns file content directly
        try {
            return JSON5.parse(data) as T;
        } catch (e) {
            // If it's standard JSON (or handled by spring message converter to json), 
            // the responseType: 'text' might be overkill if we trust the backend to always return valid JSON.
            // But since we use JSON5 on backend storage, this is safer.
            console.error(`Failed to parse config for ${section}:`, e);
            throw e;
        }
      }),
      catchError(err => {
        console.error(`Failed to get config for ${section}:`, err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Update configuration for a specific section
   */
  updateConfig<T>(section: string, config: T): Observable<T> {
    return this.http.put(`${this.baseUrl}/${section}`, config, { responseType: 'text' }).pipe(
      map(data => {
        return JSON5.parse(data) as T;
      }),
      catchError(err => {
        console.error(`Failed to update config for ${section}:`, err);
        return throwError(() => err);
      })
    );
  }
}
