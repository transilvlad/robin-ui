import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '@environments/environment';
import { MtaStsWorker } from '../models/domain.models';
import { Ok, Err, Result } from '@core/models/auth.model';

export interface DeployMtaStsRequest {
  policyMode: 'testing' | 'enforce' | 'none';
}

@Injectable({ providedIn: 'root' })
export class MtaStsService {
  private readonly base = environment.apiUrl + environment.endpoints.domains;

  constructor(private http: HttpClient) {}

  getWorker(domainId: number): Observable<Result<MtaStsWorker, Error>> {
    return this.http.get<MtaStsWorker>(`${this.base}/${domainId}/mta-sts`).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  deploy(domainId: number, req: DeployMtaStsRequest): Observable<Result<MtaStsWorker, Error>> {
    return this.http.post<MtaStsWorker>(`${this.base}/${domainId}/mta-sts/deploy`, req).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  updatePolicy(domainId: number, policyMode: 'testing' | 'enforce' | 'none'): Observable<Result<MtaStsWorker, Error>> {
    return this.http.put<MtaStsWorker>(`${this.base}/${domainId}/mta-sts`, { policyMode }).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }
}
