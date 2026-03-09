import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '@environments/environment';
import { DnsProvider } from '../models/domain.models';
import { Ok, Err, Result } from '@core/models/auth.model';

export interface CreateDnsProviderRequest {
  name: string;
  type: 'CLOUDFLARE' | 'AWS_ROUTE53';
  credentials: string;
}

export interface TestConnectionResponse {
  success: boolean;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class DnsProviderService {
  private readonly base = environment.apiUrl + environment.endpoints.dnsProviders;

  constructor(private http: HttpClient) {}

  getProviders(): Observable<Result<DnsProvider[], Error>> {
    return this.http.get<DnsProvider[]>(this.base).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  getProvider(id: number): Observable<Result<DnsProvider, Error>> {
    return this.http.get<DnsProvider>(`${this.base}/${id}`).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  createProvider(req: CreateDnsProviderRequest): Observable<Result<DnsProvider, Error>> {
    return this.http.post<DnsProvider>(this.base, req).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  updateProvider(id: number, req: Partial<CreateDnsProviderRequest>): Observable<Result<DnsProvider, Error>> {
    return this.http.put<DnsProvider>(`${this.base}/${id}`, req).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  deleteProvider(id: number): Observable<Result<void, Error>> {
    return this.http.delete<void>(`${this.base}/${id}`).pipe(
      map(() => Ok(undefined as void)),
      catchError(e => of(Err(e)))
    );
  }

  testConnection(id: number): Observable<Result<TestConnectionResponse, Error>> {
    return this.http.post<TestConnectionResponse>(`${this.base}/${id}/test`, {}).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }
}
