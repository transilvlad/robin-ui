import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProviderConfig } from './provider.service';

export interface Domain {
  id?: number;
  domain: string;
  status: 'PENDING' | 'VERIFIED' | 'FAILED' | 'ACTIVE';
  dnsProviderType: 'MANUAL' | 'CLOUDFLARE' | 'AWS_ROUTE53';
  dnsProviderId?: number;
  dnsProvider?: ProviderConfig | null;
  registrarProviderType: 'NONE' | 'MANUAL' | 'CLOUDFLARE' | 'AWS_ROUTE53' | 'GODADDY';
  registrarProviderId?: number;
  registrarProvider?: ProviderConfig | null;
  emailProviderId?: number;
  emailProvider?: ProviderConfig | null;
  renewalDate?: string;
  nameservers?: string;
  dnssecEnabled?: boolean;
  mtaStsEnabled?: boolean;
  mtaStsMode?: 'NONE' | 'TESTING' | 'ENFORCE';
  daneEnabled?: boolean;
  bimiSelector?: string;
  bimiLogoUrl?: string;
  dkimSelectorPrefix?: string;
  
  // DMARC
  dmarcPolicy?: string;
  dmarcSubdomainPolicy?: string;
  dmarcPercentage?: number;
  dmarcAlignment?: string;
  dmarcReportingEmail?: string;
  
  // SPF
  spfIncludes?: string;
  spfSoftFail?: boolean;

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

export interface DiscoveryResult {
  discoveredRecords: DnsRecord[];
  proposedRecords: DnsRecord[];
  configuration: Domain;
}

export interface CreateDomainRequest {
  domain: string;
  dnsProviderId?: number | null;
  registrarProviderId?: number | null;
  emailProviderId?: number | null;
  config?: Partial<Domain>;
  initialRecords?: DnsRecord[] | null;
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

  createDomain(request: CreateDomainRequest): Observable<Domain> {
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

  updateRecord(id: number, record: Partial<DnsRecord>): Observable<DnsRecord> {
    return this.http.put<DnsRecord>(`${environment.apiUrl}/dns-records/${id}`, record);
  }

  deleteRecord(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/dns-records/${id}`);
  }

  syncDomain(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/sync`, {});
  }
  
  discover(domain: string, dnsProviderId?: number): Observable<DiscoveryResult> {
    return this.http.post<DiscoveryResult>(`${this.apiUrl}/discover`, { domain, dnsProviderId });
  }

  getDnssecStatus(id: number): Observable<DnsRecord[]> {
    return this.http.get<DnsRecord[]>(`${this.apiUrl}/${id}/dnssec`);
  }

  enableDnssec(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/dnssec/enable`, {});
  }

  disableDnssec(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/dnssec/disable`, {});
  }
}
