import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Domain {
  id?: number;
  domain: string;
  status: 'PENDING' | 'VERIFIED' | 'FAILED' | 'ACTIVE';
  dnsProviderType: 'MANUAL' | 'CLOUDFLARE' | 'AWS_ROUTE53';
  dnsProviderId?: number;
  dnsProvider?: any; // ProviderConfig
  registrarProviderType: 'NONE' | 'MANUAL' | 'CLOUDFLARE' | 'AWS_ROUTE53' | 'GODADDY';
  registrarProviderId?: number;
  registrarProvider?: any; // ProviderConfig
  renewalDate?: string;
  nameservers?: string;
  dnssecEnabled?: boolean;
  mtaStsEnabled?: boolean;
  mtaStsMode?: 'NONE' | 'TESTING' | 'ENFORCE';
  daneEnabled?: boolean;
  bimiSelector?: string;
  bimiLogoUrl?: string;
  dkimSelectorPrefix?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DnsRecord {
  id?: number;
  domainId: number;
  type: string;
  name: string;
  content: string;
  ttl: number;
  priority?: number;
  purpose: string;
  syncStatus: 'PENDING' | 'SYNCED' | 'ERROR';
  lastSyncedAt?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

@Injectable({
  providedIn: 'root'
})
export class DomainService {
  private apiUrl = `${environment.apiUrl}/domains`;

  constructor(private http: HttpClient) {}

  getDomains(page: number = 0, size: number = 10): Observable<Page<Domain>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<Page<Domain>>(this.apiUrl, { params });
  }

  getDomain(id: number): Observable<Domain> {
    return this.http.get<Domain>(`${this.apiUrl}/${id}`);
  }

  createDomain(request: any): Observable<Domain> {
    return this.http.post<Domain>(this.apiUrl, request);
  }

  updateDomain(id: number, domain: Domain): Observable<Domain> {
    return this.http.put<Domain>(`${this.apiUrl}/${id}`, domain);
  }

  deleteDomain(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getRecords(domainId: number): Observable<DnsRecord[]> {
    return this.http.get<DnsRecord[]>(`${this.apiUrl}/${domainId}/records`);
  }

  syncDomain(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/sync`, {});
  }
}
