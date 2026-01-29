import { inject } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptorFn,
  HttpErrorResponse,
  HttpHandlerFn,
} from '@angular/common/http';
import { Observable, throwError, BehaviorSubject, filter, take, switchMap, catchError } from 'rxjs';
import { AuthStore } from '../state/auth.store';
import { AuthService } from '../services/auth.service';

/**
 * Token Refresh State Management
 *
 * Prevents duplicate refresh requests by queueing requests
 * while a refresh is in progress.
 */
let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

/**
 * Authentication Interceptor (Functional)
 *
 * Modern Angular 21+ functional interceptor using SignalStore.
 * Handles:
 * - Adding Bearer token to requests
 * - Skipping auth for public routes
 * - Token refresh on 401 errors
 * - Request queuing during refresh
 *
 * Usage in app.config.ts:
 * ```typescript
 * providers: [
 *   provideHttpClient(
 *     withInterceptors([authInterceptor])
 *   )
 * ]
 * ```
 */
export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
): Observable<HttpEvent<unknown>> => {
  const authStore = inject(AuthStore);
  const authService = inject(AuthService);

  // Skip auth for public endpoints
  if (isPublicEndpoint(req.url)) {
    return next(req);
  }

  // Add Bearer token to request
  const token = authStore.accessToken();
  if (token) {
    req = addAuthHeader(req, token);
  }

  // Handle request and catch 401 errors
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && token) {
        return handle401Error(req, next, authStore, authService);
      }
      return throwError(() => error);
    })
  );
};

/**
 * Check if endpoint is public (no auth required)
 */
function isPublicEndpoint(url: string): boolean {
  const publicEndpoints = [
    '/api/v1/auth/login',
    '/api/v1/auth/refresh',
    '/api/v1/auth/register',
    '/health/aggregate',
    '/health/public',
    '/actuator/health',
  ];

  return publicEndpoints.some(endpoint => url.includes(endpoint)) ||
         url.includes('/actuator/');
}

/**
 * Add Authorization header with Bearer token
 */
function addAuthHeader(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`
    }
  });
}

/**
 * Handle 401 Unauthorized error with token refresh
 * Implements request queuing to prevent duplicate refresh calls
 */
function handle401Error(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  authStore: InstanceType<typeof AuthStore>,
  authService: AuthService
): Observable<HttpEvent<unknown>> {
  if (!isRefreshing) {
    isRefreshing = true;
    refreshTokenSubject.next(null);

    // Attempt token refresh
    return authService.refreshToken().pipe(
      switchMap(result => {
        if (result.ok) {
          const tokens = result.value;
          isRefreshing = false;

          // Update store with new token
          authStore.updateTokens({
            accessToken: tokens.accessToken,
            expiresIn: tokens.expiresIn
          });

          refreshTokenSubject.next(tokens.accessToken);

          // Retry original request with new token
          return next(addAuthHeader(req, tokens.accessToken));
        } else {
          // Refresh failed - logout user
          isRefreshing = false;
          authStore.logout();
          return throwError(() => new Error('Token refresh failed'));
        }
      }),
      catchError(error => {
        isRefreshing = false;
        authStore.logout();
        return throwError(() => error);
      })
    );
  } else {
    // Queue request until refresh completes
    return refreshTokenSubject.pipe(
      filter(token => token !== null),
      take(1),
      switchMap(token => next(addAuthHeader(req, token as string)))
    );
  }
}

/**
 * Legacy Class-Based Interceptor (for backward compatibility)
 * Use functional authInterceptor instead in new code
 */
export class AuthInterceptor {
  private authStore = inject(AuthStore);
  private authService = inject(AuthService);
  private isRefreshing = false;
  private refreshTokenSubject = new BehaviorSubject<string | null>(null);

  intercept(
    request: HttpRequest<unknown>,
    next: HttpHandler
  ): Observable<HttpEvent<unknown>> {
    // Skip auth for public endpoints
    if (this.isPublicEndpoint(request.url)) {
      return next.handle(request);
    }

    // Add Bearer token
    const token = this.authStore.accessToken();
    if (token) {
      request = this.addAuthHeader(request, token);
    }

    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401 && token) {
          return this.handle401Error(request, next);
        }
        return throwError(() => error);
      })
    );
  }

  private isPublicEndpoint(url: string): boolean {
    const publicEndpoints = [
      '/api/v1/auth/login',
      '/api/v1/auth/refresh',
      '/api/v1/auth/register',
      '/health/aggregate',
      '/health/public',
      '/actuator/health',
    ];
    return publicEndpoints.some(endpoint => url.includes(endpoint)) ||
           url.includes('/actuator/');
  }

  private addAuthHeader(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
    return req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  private handle401Error(
    req: HttpRequest<unknown>,
    next: HttpHandler
  ): Observable<HttpEvent<unknown>> {
    if (!this.isRefreshing) {
      this.isRefreshing = true;
      this.refreshTokenSubject.next(null);

      return this.authService.refreshToken().pipe(
        switchMap(result => {
          if (result.ok) {
            const tokens = result.value;
            this.isRefreshing = false;

            this.authStore.updateTokens({
              accessToken: tokens.accessToken,
              expiresIn: tokens.expiresIn
            });

            this.refreshTokenSubject.next(tokens.accessToken);
            return next.handle(this.addAuthHeader(req, tokens.accessToken));
          } else {
            this.isRefreshing = false;
            this.authStore.logout();
            return throwError(() => new Error('Token refresh failed'));
          }
        }),
        catchError(error => {
          this.isRefreshing = false;
          this.authStore.logout();
          return throwError(() => error);
        })
      );
    } else {
      return this.refreshTokenSubject.pipe(
        filter(token => token !== null),
        take(1),
        switchMap(token => next.handle(this.addAuthHeader(req, token as string)))
      );
    }
  }
}
