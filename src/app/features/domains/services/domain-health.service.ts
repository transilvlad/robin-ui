import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '@environments/environment';
import { DomainHealth } from '../models/domain.models';
import { Ok, Err, Result } from '@core/models/auth.model';

@Injectable({ providedIn: 'root' })
export class DomainHealthService {
  private readonly base = environment.apiUrl + environment.endpoints.domains;

  constructor(private http: HttpClient) {}

  getHealth(domainId: number): Observable<Result<DomainHealth[], Error>> {
    return this.http.get<DomainHealth[]>(`${this.base}/${domainId}/health`).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  triggerVerification(domainId: number): Observable<Result<DomainHealth[], Error>> {
    return this.http.post<DomainHealth[]>(`${this.base}/${domainId}/health/verify`, {}).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }
}
