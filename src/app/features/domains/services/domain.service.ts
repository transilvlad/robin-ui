import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '@environments/environment';
import { Domain, DomainDnsRecord, DnsRecordEntry, DomainLookupResult, PageResponse } from '../models/domain.models';
import { Ok, Err, Result } from '@core/models/auth.model';

@Injectable({ providedIn: 'root' })
export class DomainService {
  private readonly base = environment.apiUrl + environment.endpoints.domains;

  constructor(private http: HttpClient) {}

  lookupDomain(domain: string): Observable<Result<DomainLookupResult, Error>> {
    return this.http.get<DomainLookupResult>(`${this.base}/lookup`, { params: { domain } }).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  getDomains(page = 0, size = 20): Observable<Result<PageResponse<Domain>, Error>> {    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Domain>>(this.base, { params }).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  getDomain(id: number): Observable<Result<Domain, Error>> {
    return this.http.get<Domain>(`${this.base}/${id}`).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  createDomain(domain: string, dnsProviderId?: number, nsProviderId?: number, initialDnsRecords?: DnsRecordEntry[]): Observable<Result<Domain, Error>> {
    const initialRecords = (initialDnsRecords ?? []).map(r => ({
      recordType: r.type,
      name: r.name,
      value: r.type === 'MX' ? r.value.replace(/^\d+\s+/, '') : r.value,
      priority: r.type === 'MX' ? parseInt(r.value, 10) || null : null,
    }));
    return this.http.post<Domain>(this.base, { domain, dnsProviderId, nsProviderId, initialDnsRecords: initialRecords }).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  deleteDomain(id: number): Observable<Result<void, Error>> {
    return this.http.delete<void>(`${this.base}/${id}`).pipe(
      map(() => Ok(undefined as void)),
      catchError(e => of(Err(e)))
    );
  }

  getDnsRecords(domainId: number): Observable<Result<DomainDnsRecord[], Error>> {
    return this.http.get<DomainDnsRecord[]>(`${this.base}/${domainId}/dns`).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  createDnsRecord(domainId: number, record: Partial<DomainDnsRecord>): Observable<Result<DomainDnsRecord, Error>> {
    return this.http.post<DomainDnsRecord>(`${this.base}/${domainId}/dns`, record).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  updateDnsRecord(domainId: number, recordId: number, record: Partial<DomainDnsRecord>): Observable<Result<DomainDnsRecord, Error>> {
    return this.http.put<DomainDnsRecord>(`${this.base}/${domainId}/dns/${recordId}`, record).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  deleteDnsRecord(domainId: number, recordId: number): Observable<Result<void, Error>> {
    return this.http.delete<void>(`${this.base}/${domainId}/dns/${recordId}`).pipe(
      map(() => Ok(undefined as void)),
      catchError(e => of(Err(e)))
    );
  }
}
