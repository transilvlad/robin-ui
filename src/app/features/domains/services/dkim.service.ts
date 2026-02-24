import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '@environments/environment';
import { DkimKey } from '../models/domain.models';
import { Ok, Err, Result } from '@core/models/auth.model';

export interface GenerateDkimKeyRequest {
  selector: string;
  algorithm: 'RSA_2048' | 'ED25519';
}

@Injectable({ providedIn: 'root' })
export class DkimService {
  private readonly base = environment.apiUrl + environment.endpoints.domains;

  constructor(private http: HttpClient) {}

  getKeys(domainId: number): Observable<Result<DkimKey[], Error>> {
    return this.http.get<DkimKey[]>(`${this.base}/${domainId}/dkim`).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  generateKey(domainId: number, req: GenerateDkimKeyRequest): Observable<Result<DkimKey, Error>> {
    return this.http.post<DkimKey>(`${this.base}/${domainId}/dkim`, req).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  rotateKey(domainId: number): Observable<Result<DkimKey, Error>> {
    return this.http.post<DkimKey>(`${this.base}/${domainId}/dkim/rotate`, {}).pipe(
      map(r => Ok(r)),
      catchError(e => of(Err(e)))
    );
  }

  retireKey(domainId: number, keyId: number): Observable<Result<void, Error>> {
    return this.http.post<void>(`${this.base}/${domainId}/dkim/${keyId}/retire`, {}).pipe(
      map(() => Ok(undefined as void)),
      catchError(e => of(Err(e)))
    );
  }
}
