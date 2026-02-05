import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from './domain.service';

export interface ProviderConfig {
  id?: number;
  name: string;
  type: 'CLOUDFLARE' | 'AWS_ROUTE53' | 'GODADDY' | 'EMAIL';
  credentials?: Record<string, string>; // Only for creating
  createdAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ProviderService {
  private apiUrl = `${environment.apiUrl}/providers`;

  constructor(private http: HttpClient) {}

  getProviders(page: number = 0, size: number = 10): Observable<Page<ProviderConfig>> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http.get<Page<ProviderConfig>>(this.apiUrl, { params });
  }

  createProvider(provider: ProviderConfig): Observable<ProviderConfig> {
    return this.http.post<ProviderConfig>(this.apiUrl, provider);
  }

  updateProvider(id: number, provider: ProviderConfig): Observable<ProviderConfig> {
    return this.http.put<ProviderConfig>(`${this.apiUrl}/${id}`, provider);
  }

  deleteProvider(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
