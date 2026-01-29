import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import {
  LoginRequest,
  AuthResponse,
  AuthTokens,
  User,
  Result,
  Ok,
  Err,
  AuthError,
  AuthErrorCode,
  createAuthError,
  TokenPayload,
  AuthResponseSchema,
  AuthTokensSchema,
  UserSchema,
  AccessToken,
} from '../models/auth.model';
import { environment } from '../../../environments/environment';

/**
 * Authentication Service
 *
 * Handles all authentication-related operations including login, logout,
 * token management, and JWT operations. Integrates with Robin Gateway
 * authentication endpoints.
 *
 * Uses Result<T, E> pattern for explicit error handling.
 */
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  /**
   * Authenticate user with credentials
   * @param credentials - Login request with username and password
   * @returns Observable of Result containing auth response or error
   */
  login(credentials: LoginRequest): Observable<Result<AuthResponse, AuthError>> {
    return this.http.post<any>(`${this.baseUrl}/api/v1/auth/login`, credentials, {
      withCredentials: true, // Required for HttpOnly cookies
    }).pipe(
      map(response => {
        try {
          // Validate response with Zod
          const validated = AuthResponseSchema.parse(response);
          return Ok(validated);
        } catch (error) {
          return Err(createAuthError(
            AuthErrorCode.INVALID_TOKEN,
            'Invalid response from server',
            error
          ));
        }
      }),
      catchError((error: HttpErrorResponse) => {
        return of(Err(this.handleHttpError(error)));
      })
    );
  }

  /**
   * Logout user and invalidate session
   * Clears HttpOnly refresh token cookie on backend
   * @returns Observable of Result containing void or error
   */
  logout(): Observable<Result<void, AuthError>> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/auth/logout`, {}, {
      withCredentials: true, // Required to send HttpOnly cookie
    }).pipe(
      map(() => Ok(undefined)),
      catchError((error: HttpErrorResponse) => {
        // Even if logout fails on backend, we consider it successful on client
        // Client-side cleanup happens regardless
        return of(Ok(undefined));
      })
    );
  }

  /**
   * Refresh access token using HttpOnly refresh token cookie
   * @returns Observable of Result containing new tokens or error
   */
  refreshToken(): Observable<Result<AuthTokens, AuthError>> {
    return this.http.post<any>(`${this.baseUrl}/api/v1/auth/refresh`, {}, {
      withCredentials: true, // Required to send HttpOnly cookie
    }).pipe(
      map(response => {
        try {
          const validated = AuthTokensSchema.parse(response);
          return Ok(validated);
        } catch (error) {
          return Err(createAuthError(
            AuthErrorCode.REFRESH_FAILED,
            'Invalid token refresh response',
            error
          ));
        }
      }),
      catchError((error: HttpErrorResponse) => {
        return of(Err(this.handleHttpError(error, AuthErrorCode.REFRESH_FAILED)));
      })
    );
  }

  /**
   * Verify if current access token is valid
   * @returns Observable of boolean indicating token validity
   */
  verifyToken(): Observable<boolean> {
    return this.http.get<{ valid: boolean }>(`${this.baseUrl}/api/v1/auth/verify`).pipe(
      map(response => response.valid),
      catchError(() => of(false))
    );
  }

  /**
   * Get current authenticated user information
   * @returns Observable of Result containing user or error
   */
  getCurrentUser(): Observable<Result<User, AuthError>> {
    return this.http.get<any>(`${this.baseUrl}/api/v1/auth/me`).pipe(
      map(response => {
        try {
          const validated = UserSchema.parse(response);
          return Ok(validated);
        } catch (error) {
          return Err(createAuthError(
            AuthErrorCode.INVALID_TOKEN,
            'Invalid user response',
            error
          ));
        }
      }),
      catchError((error: HttpErrorResponse) => {
        return of(Err(this.handleHttpError(error)));
      })
    );
  }

  /**
   * Decode JWT token to extract payload
   * @param token - JWT access token
   * @returns Token payload or null if invalid
   */
  decodeToken(token: string): TokenPayload | null {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) {
        return null;
      }

      // Decode base64url (JWT uses base64url encoding)
      const payload = parts[1];
      const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
      const decoded = decodeURIComponent(
        atob(base64)
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );

      return JSON.parse(decoded) as TokenPayload;
    } catch (error) {
      console.error('Failed to decode token', error);
      return null;
    }
  }

  /**
   * Check if token is expired
   * @param token - JWT access token
   * @returns True if token is expired
   */
  isTokenExpired(token: string): boolean {
    const payload = this.decodeToken(token);
    if (!payload || !payload.exp) {
      return true;
    }

    const expirationDate = new Date(payload.exp * 1000);
    return expirationDate < new Date();
  }

  /**
   * Get token expiration date
   * @param token - JWT access token
   * @returns Expiration date or null if invalid
   */
  getTokenExpirationDate(token: string): Date | null {
    const payload = this.decodeToken(token);
    if (!payload || !payload.exp) {
      return null;
    }

    return new Date(payload.exp * 1000);
  }

  /**
   * Check if token will expire soon (within buffer time)
   * @param token - JWT access token
   * @param bufferSeconds - Number of seconds before expiry to consider "expiring soon"
   * @returns True if token expires within buffer time
   */
  isTokenExpiringSoon(token: string, bufferSeconds: number = 60): boolean {
    const payload = this.decodeToken(token);
    if (!payload || !payload.exp) {
      return true;
    }

    const expirationDate = new Date(payload.exp * 1000);
    const bufferDate = new Date(Date.now() + bufferSeconds * 1000);
    return expirationDate < bufferDate;
  }

  /**
   * Handle HTTP errors and convert to AuthError
   * @param error - HTTP error response
   * @param defaultCode - Default error code if not determinable
   * @returns AuthError object
   */
  private handleHttpError(
    error: HttpErrorResponse,
    defaultCode: AuthErrorCode = AuthErrorCode.NETWORK_ERROR
  ): AuthError {
    if (error.status === 0) {
      // Network error
      return createAuthError(
        AuthErrorCode.NETWORK_ERROR,
        'Network error. Please check your connection.',
        error
      );
    }

    if (error.status === 401) {
      // Unauthorized
      return createAuthError(
        AuthErrorCode.INVALID_CREDENTIALS,
        error.error?.message || 'Invalid credentials',
        error
      );
    }

    if (error.status === 403) {
      // Forbidden
      return createAuthError(
        AuthErrorCode.FORBIDDEN,
        error.error?.message || 'Access forbidden',
        error
      );
    }

    // Default error
    return createAuthError(
      defaultCode,
      error.error?.message || 'An unexpected error occurred',
      error
    );
  }
}
