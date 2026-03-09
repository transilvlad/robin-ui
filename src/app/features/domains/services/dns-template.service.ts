import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '@environments/environment';
import { DnsTemplate } from '../models/domain.models';
import { Ok, Err, Result } from '@core/models/auth.model';

export interface CreateDnsTemplateRequest {
  name: string;
  description?: string;
  records: string;
}

@Injectable({ providedIn: 'root' })
export class DnsTemplateService {
  private readonly base = environment.apiUrl + environment.endpoints.dnsTemplates;

  constructor(private http: HttpClient) {}

  getTemplates(): Observable<Result<DnsTemplate[], Error>> {
    return this.http.get<DnsTemplate[]>(this.base).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  getTemplate(id: number): Observable<Result<DnsTemplate, Error>> {
    return this.http.get<DnsTemplate>(`${this.base}/${id}`).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  createTemplate(req: CreateDnsTemplateRequest): Observable<Result<DnsTemplate, Error>> {
    return this.http.post<DnsTemplate>(this.base, req).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  updateTemplate(id: number, req: Partial<CreateDnsTemplateRequest>): Observable<Result<DnsTemplate, Error>> {
    return this.http.put<DnsTemplate>(`${this.base}/${id}`, req).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  deleteTemplate(id: number): Observable<Result<void, Error>> {
    return this.http.delete<void>(`${this.base}/${id}`).pipe(
      map(() => Ok(undefined as void)),
      catchError(e => of(Err(e)))
    );
  }
}
