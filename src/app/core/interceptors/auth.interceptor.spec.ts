import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { HTTP_INTERCEPTORS, HttpClient, HttpErrorResponse } from '@angular/common/http';
import { authInterceptor } from './auth.interceptor';
import { AuthStore } from '../state/auth.store';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';
import { AccessToken, AuthTokens, RefreshToken, Ok, Err, AuthErrorCode } from '../models/auth.model';
import { TokenStorageService } from '../services/token-storage.service';
import { NotificationService } from '../services/notification.service';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authStoreMock: jasmine.SpyObj<typeof AuthStore.prototype>;
  let authServiceMock: jasmine.SpyObj<AuthService>;
  let routerMock: jasmine.SpyObj<Router>;

  const mockToken = 'mock_access_token' as AccessToken;
  const mockNewTokens: AuthTokens = {
    accessToken: 'new_access_token' as AccessToken,
    refreshToken: 'new_refresh_token' as RefreshToken,
    expiresIn: 3600,
    tokenType: 'Bearer' as const,
  };

  beforeEach(() => {
    authStoreMock = jasmine.createSpyObj(
      'AuthStore',
      ['updateTokens', 'logout'],
      {
        accessToken: jasmine.createSpy().and.returnValue(mockToken),
        isAuthenticated: jasmine.createSpy().and.returnValue(true),
      }
    );

    authServiceMock = jasmine.createSpyObj('AuthService', ['refreshToken']);
    routerMock = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        { provide: AuthStore, useValue: authStoreMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock },
        {
          provide: TokenStorageService,
          useValue: jasmine.createSpyObj('TokenStorageService', ['clear']),
        },
        {
          provide: NotificationService,
          useValue: jasmine.createSpyObj('NotificationService', ['info']),
        },
        {
          provide: HTTP_INTERCEPTORS,
          useValue: authInterceptor,
          multi: true,
        },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('Bearer Token Addition', () => {
    it('should add Bearer token to request headers', () => {
      Object.defineProperty(authStoreMock, 'accessToken', {
        get: () => mockToken,
      });

      httpClient.get('/api/test').subscribe();

      const req = httpMock.expectOne('/api/test');
      expect(req.request.headers.has('Authorization')).toBe(true);
      expect(req.request.headers.get('Authorization')).toBe(
        `Bearer ${mockToken}`
      );
      req.flush({});
    });

    it('should not add token when user is not authenticated', () => {
      Object.defineProperty(authStoreMock, 'accessToken', {
        get: () => null,
      });
      Object.defineProperty(authStoreMock, 'isAuthenticated', {
        get: () => false,
      });

      httpClient.get('/api/test').subscribe();

      const req = httpMock.expectOne('/api/test');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });
  });

  describe('Public Endpoints', () => {
    const publicEndpoints = [
      '/auth/login',
      '/auth/refresh',
      '/auth/register',
      '/auth/forgot-password',
      '/auth/reset-password',
    ];

    publicEndpoints.forEach((endpoint) => {
      it(`should skip auth header for ${endpoint}`, () => {
        Object.defineProperty(authStoreMock, 'accessToken', {
          get: () => mockToken,
        });

        httpClient.post(endpoint, {}).subscribe();

        const req = httpMock.expectOne(endpoint);
        expect(req.request.headers.has('Authorization')).toBe(false);
        req.flush({});
      });
    });

    it('should add auth header for protected endpoints', () => {
      Object.defineProperty(authStoreMock, 'accessToken', {
        get: () => mockToken,
      });

      const protectedEndpoints = [
        '/api/users',
        '/api/settings',
        '/api/dashboard',
        '/auth/verify', // Not in public list
      ];

      protectedEndpoints.forEach((endpoint) => {
        httpClient.get(endpoint).subscribe();
        const req = httpMock.expectOne(endpoint);
        expect(req.request.headers.get('Authorization')).toBe(
          `Bearer ${mockToken}`
        );
        req.flush({});
      });
    });
  });

  describe('Token Refresh on 401', () => {
    it('should refresh token and retry request on 401 error', (done) => {
      Object.defineProperty(authStoreMock, 'accessToken', {
        get: () => mockToken,
      });

      authServiceMock.refreshToken.and.returnValue(
        Promise.resolve(Ok(mockNewTokens))
      );

      httpClient.get('/api/protected').subscribe({
        next: (data) => {
          expect(data).toEqual({ success: true });
          expect(authServiceMock.refreshToken).toHaveBeenCalled();
          expect(authStoreMock.updateTokens).toHaveBeenCalledWith({
            accessToken: mockNewTokens.accessToken,
            expiresIn: mockNewTokens.expiresIn,
          });
          done();
        },
      });

      // First request fails with 401
      const firstReq = httpMock.expectOne('/api/protected');
      firstReq.flush({ error: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

      // After token refresh, request should be retried
      setTimeout(() => {
        const retryReq = httpMock.expectOne('/api/protected');
        expect(retryReq.request.headers.get('Authorization')).toBe(
          `Bearer ${mockNewTokens.accessToken}`
        );
        retryReq.flush({ success: true });
      }, 100);
    });

    it('should logout user when token refresh fails', (done) => {
      Object.defineProperty(authStoreMock, 'accessToken', {
        get: () => mockToken,
      });

      authServiceMock.refreshToken.and.returnValue(
        Promise.resolve(
          Err({
            code: AuthErrorCode.TOKEN_EXPIRED,
            message: 'Refresh token expired',
          })
        )
      );

      httpClient.get('/api/protected').subscribe({
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(401);
          expect(authStoreMock.logout).toHaveBeenCalled();
          done();
        },
      });

      const req = httpMock.expectOne('/api/protected');
      req.flush({ error: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
    });

    it('should queue multiple requests during token refresh', (done) => {
      Object.defineProperty(authStoreMock, 'accessToken', {
        get: () => mockToken,
      });

      let refreshResolve: (value: any) => void;
      const refreshPromise = new Promise((resolve) => {
        refreshResolve = resolve;
      });
      authServiceMock.refreshToken.and.returnValue(refreshPromise as any);

      const responses: any[] = [];
      let completedCount = 0;

      // Make 3 simultaneous requests
      for (let i = 0; i < 3; i++) {
        httpClient.get(`/api/protected/${i}`).subscribe({
          next: (data) => {
            responses.push(data);
            completedCount++;
            if (completedCount === 3) {
              expect(authServiceMock.refreshToken).toHaveBeenCalledTimes(1);
              expect(responses).toEqual([
                { id: 0 },
                { id: 1 },
                { id: 2 },
              ]);
              done();
            }
          },
        });
      }

      // All initial requests fail with 401
      for (let i = 0; i < 3; i++) {
        const req = httpMock.expectOne(`/api/protected/${i}`);
        req.flush({ error: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
      }

      // Resolve refresh token
      setTimeout(() => {
        refreshResolve(Ok(mockNewTokens));

        // Wait for refresh to complete, then respond to retried requests
        setTimeout(() => {
          for (let i = 0; i < 3; i++) {
            const retryReq = httpMock.expectOne(`/api/protected/${i}`);
            retryReq.flush({ id: i });
          }
        }, 50);
      }, 50);
    });

    it('should not refresh token for public endpoints returning 401', () => {
      httpClient.post('/auth/login', { username: 'test', password: 'test' }).subscribe({
        error: () => {
          expect(authServiceMock.refreshToken).not.toHaveBeenCalled();
        },
      });

      const req = httpMock.expectOne('/auth/login');
      req.flush({ error: 'Invalid credentials' }, { status: 401, statusText: 'Unauthorized' });
    });
  });

  describe('Error Handling', () => {
    it('should pass through non-401 errors', (done) => {
      Object.defineProperty(authStoreMock, 'accessToken', {
        get: () => mockToken,
      });

      httpClient.get('/api/test').subscribe({
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(500);
          expect(authServiceMock.refreshToken).not.toHaveBeenCalled();
          done();
        },
      });

      const req = httpMock.expectOne('/api/test');
      req.flush({ error: 'Server error' }, { status: 500, statusText: 'Internal Server Error' });
    });

    it('should handle 403 Forbidden errors without refresh', (done) => {
      Object.defineProperty(authStoreMock, 'accessToken', {
        get: () => mockToken,
      });

      httpClient.get('/api/admin').subscribe({
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(403);
          expect(authServiceMock.refreshToken).not.toHaveBeenCalled();
          done();
        },
      });

      const req = httpMock.expectOne('/api/admin');
      req.flush({ error: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
    });

    it('should handle network errors', (done) => {
      Object.defineProperty(authStoreMock, 'accessToken', {
        get: () => mockToken,
      });

      httpClient.get('/api/test').subscribe({
        error: (error) => {
          expect(error).toBeTruthy();
          expect(authServiceMock.refreshToken).not.toHaveBeenCalled();
          done();
        },
      });

      const req = httpMock.expectOne('/api/test');
      req.error(new ProgressEvent('Network error'));
    });
  });

  describe('Special Cases', () => {
    it('should handle requests without response body', () => {
      Object.defineProperty(authStoreMock, 'accessToken', {
        get: () => mockToken,
      });

      httpClient.delete('/api/resource/123').subscribe();

      const req = httpMock.expectOne('/api/resource/123');
      expect(req.request.headers.get('Authorization')).toBe(
        `Bearer ${mockToken}`
      );
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('should handle requests with custom headers', () => {
      Object.defineProperty(authStoreMock, 'accessToken', {
        get: () => mockToken,
      });

      httpClient
        .get('/api/test', {
          headers: {
            'X-Custom-Header': 'custom-value',
          },
        })
        .subscribe();

      const req = httpMock.expectOne('/api/test');
      expect(req.request.headers.get('Authorization')).toBe(
        `Bearer ${mockToken}`
      );
      expect(req.request.headers.get('X-Custom-Header')).toBe('custom-value');
      req.flush({});
    });

    it('should work with different HTTP methods', () => {
      Object.defineProperty(authStoreMock, 'accessToken', {
        get: () => mockToken,
      });

      const methods: Array<{
        method: string;
        fn: (url: string, body?: any) => any;
      }> = [
        { method: 'GET', fn: (url) => httpClient.get(url) },
        { method: 'POST', fn: (url, body) => httpClient.post(url, body) },
        { method: 'PUT', fn: (url, body) => httpClient.put(url, body) },
        { method: 'PATCH', fn: (url, body) => httpClient.patch(url, body) },
        { method: 'DELETE', fn: (url) => httpClient.delete(url) },
      ];

      methods.forEach(({ method, fn }) => {
        fn('/api/test', {}).subscribe();
        const req = httpMock.expectOne('/api/test');
        expect(req.request.method).toBe(method);
        expect(req.request.headers.get('Authorization')).toBe(
          `Bearer ${mockToken}`
        );
        req.flush({});
      });
    });
  });
});
