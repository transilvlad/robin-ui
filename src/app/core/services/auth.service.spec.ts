import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';
import {
  LoginRequest,
  AuthResponse,
  AuthTokens,
  User,
  UserRole,
  Permission,
  AuthErrorCode,
  AccessToken,
  RefreshToken,
  UserId,
} from '../models/auth.model';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const mockUser: User = {
    id: '123e4567-e89b-12d3-a456-426614174000' as UserId,
    username: 'testuser',
    email: 'test@example.com',
    firstName: 'Test',
    lastName: 'User',
    roles: [UserRole.USER],
    permissions: [Permission.VIEW_DASHBOARD, Permission.VIEW_QUEUE],
    createdAt: new Date('2024-01-01'),
    lastLoginAt: new Date('2024-01-02'),
  };

  const mockTokens: AuthTokens = {
    accessToken: 'mock_access_token' as AccessToken,
    refreshToken: 'mock_refresh_token' as RefreshToken,
    expiresIn: 3600,
    tokenType: 'Bearer' as const,
  };

  const mockAuthResponse: AuthResponse = {
    user: mockUser,
    tokens: mockTokens,
    permissions: [Permission.VIEW_DASHBOARD, Permission.VIEW_QUEUE],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AuthService],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('login', () => {
    const credentials: LoginRequest = {
      username: 'testuser',
      password: 'password123',
      rememberMe: false,
    };

    it('should return Ok with AuthResponse on successful login', (done) => {
      service.login(credentials).subscribe((result) => {
        expect(result.ok).toBe(true);
        if (result.ok) {
          expect(result.value.user.username).toBe('testuser');
          expect(result.value.tokens.accessToken).toBe(mockTokens.accessToken);
        }
        done();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(credentials);
      req.flush(mockAuthResponse);
    });

    it('should return Err on failed login', (done) => {
      service.login(credentials).subscribe((result) => {
        expect(result.ok).toBe(false);
        if (!result.ok) {
          expect(result.error.code).toBe(AuthErrorCode.INVALID_CREDENTIALS);
          expect(result.error.message).toBeTruthy();
        }
        done();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
      req.flush(
        { message: 'Invalid credentials' },
        { status: 401, statusText: 'Unauthorized' }
      );
    });

    it('should return Err on network error', (done) => {
      service.login(credentials).subscribe((result) => {
        expect(result.ok).toBe(false);
        if (!result.ok) {
          expect(result.error.code).toBe(AuthErrorCode.NETWORK_ERROR);
        }
        done();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
      req.error(new ProgressEvent('Network error'));
    });

    it('should validate response with Zod schema', (done) => {
      const invalidResponse = {
        user: {
          id: 'invalid-uuid', // Invalid UUID
          username: 'test',
          email: 'invalid-email', // Invalid email
        },
        tokens: mockTokens,
        permissions: [],
      };

      service.login(credentials).subscribe((result) => {
        expect(result.ok).toBe(false);
        if (!result.ok) {
          expect(result.error.message).toContain('validation');
        }
        done();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
      req.flush(invalidResponse);
    });
  });

  describe('logout', () => {
    it('should call logout endpoint', (done) => {
      service.logout().subscribe(() => {
        done();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/logout`);
      expect(req.request.method).toBe('POST');
      req.flush({});
    });

    it('should handle logout failure gracefully', (done) => {
      service.logout().subscribe({
        error: (error) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/logout`);
      req.flush(null, { status: 500, statusText: 'Server Error' });
    });
  });

  describe('refreshToken', () => {
    it('should return Ok with new tokens on successful refresh', (done) => {
      service.refreshToken().subscribe((result) => {
        expect(result.ok).toBe(true);
        if (result.ok) {
          expect(result.value.accessToken).toBe(mockTokens.accessToken);
          expect(result.value.expiresIn).toBe(3600);
        }
        done();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
      expect(req.request.method).toBe('POST');
      req.flush(mockTokens);
    });

    it('should return Err on refresh failure', (done) => {
      service.refreshToken().subscribe((result) => {
        expect(result.ok).toBe(false);
        if (!result.ok) {
          expect(result.error.code).toBe(AuthErrorCode.TOKEN_EXPIRED);
        }
        done();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
      req.flush(null, { status: 401, statusText: 'Unauthorized' });
    });

    it('should not send refresh token in body (uses HttpOnly cookie)', () => {
      service.refreshToken().subscribe();

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
      expect(req.request.body).toEqual({});
      req.flush(mockTokens);
    });
  });

  describe('verifyToken', () => {
    it('should return true for valid token', (done) => {
      service.verifyToken().subscribe((isValid) => {
        expect(isValid).toBe(true);
        done();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/verify`);
      expect(req.request.method).toBe('GET');
      req.flush({ valid: true });
    });

    it('should return false for invalid token', (done) => {
      service.verifyToken().subscribe((isValid) => {
        expect(isValid).toBe(false);
        done();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/verify`);
      req.flush({ valid: false });
    });

    it('should return false on error', (done) => {
      service.verifyToken().subscribe((isValid) => {
        expect(isValid).toBe(false);
        done();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/verify`);
      req.flush(null, { status: 401, statusText: 'Unauthorized' });
    });
  });

  describe('getCurrentUser', () => {
    it('should return current user', (done) => {
      service.getCurrentUser().subscribe((user) => {
        expect(user.username).toBe('testuser');
        expect(user.email).toBe('test@example.com');
        done();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/me`);
      expect(req.request.method).toBe('GET');
      req.flush(mockUser);
    });

    it('should handle error when fetching user', (done) => {
      service.getCurrentUser().subscribe({
        error: (error) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/me`);
      req.flush(null, { status: 401, statusText: 'Unauthorized' });
    });
  });

  describe('JWT Token Utilities', () => {
    describe('decodeToken', () => {
      it('should decode valid JWT token', () => {
        // Valid JWT token (header.payload.signature)
        const token =
          'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjNlNDU2Ny1lODliLTEyZDMtYTQ1Ni00MjY2MTQxNzQwMDAiLCJ1c2VybmFtZSI6InRlc3R1c2VyIiwicm9sZXMiOlsiVVNFUiJdLCJleHAiOjE3MDYzNzEyMDAsImlhdCI6MTcwNjM2NzYwMH0.signature';

        const payload = service.decodeToken(token);

        expect(payload).toBeTruthy();
        expect(payload?.sub).toBe('123e4567-e89b-12d3-a456-426614174000');
        expect(payload?.username).toBe('testuser');
        expect(payload?.roles).toContain('USER');
      });

      it('should return null for invalid JWT format', () => {
        const invalidToken = 'not.a.valid.jwt.token';
        const payload = service.decodeToken(invalidToken);
        expect(payload).toBeNull();
      });

      it('should return null for malformed JWT', () => {
        const malformedToken = 'header.invalid_base64.signature';
        const payload = service.decodeToken(malformedToken);
        expect(payload).toBeNull();
      });

      it('should return null for empty token', () => {
        const payload = service.decodeToken('');
        expect(payload).toBeNull();
      });
    });

    describe('isTokenExpired', () => {
      it('should return false for valid token', () => {
        // Token expires in the future (2050-01-01)
        const futureToken =
          'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjMiLCJleHAiOjI1MjQ2MDgwMDB9.signature';
        expect(service.isTokenExpired(futureToken)).toBe(false);
      });

      it('should return true for expired token', () => {
        // Token expired in the past (2020-01-01)
        const expiredToken =
          'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjMiLCJleHAiOjE1Nzc4MzY4MDB9.signature';
        expect(service.isTokenExpired(expiredToken)).toBe(true);
      });

      it('should return true for token without exp claim', () => {
        const tokenWithoutExp =
          'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjMifQ.signature';
        expect(service.isTokenExpired(tokenWithoutExp)).toBe(true);
      });

      it('should return true for invalid token', () => {
        expect(service.isTokenExpired('invalid_token')).toBe(true);
      });
    });

    describe('getTokenExpirationDate', () => {
      it('should return expiration date for valid token', () => {
        // Token expires at Unix timestamp 2524608000 (2050-01-01)
        const token =
          'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjMiLCJleHAiOjI1MjQ2MDgwMDB9.signature';
        const expirationDate = service.getTokenExpirationDate(token);

        expect(expirationDate).toBeTruthy();
        expect(expirationDate instanceof Date).toBe(true);
        if (expirationDate) {
          expect(expirationDate.getTime()).toBe(2524608000 * 1000);
        }
      });

      it('should return null for token without exp claim', () => {
        const tokenWithoutExp =
          'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjMifQ.signature';
        const expirationDate = service.getTokenExpirationDate(tokenWithoutExp);
        expect(expirationDate).toBeNull();
      });

      it('should return null for invalid token', () => {
        const expirationDate = service.getTokenExpirationDate('invalid_token');
        expect(expirationDate).toBeNull();
      });
    });
  });

  describe('Error Handling', () => {
    it('should map 401 to INVALID_CREDENTIALS error', (done) => {
      service
        .login({ username: 'test', password: 'test', rememberMe: false })
        .subscribe((result) => {
          expect(result.ok).toBe(false);
          if (!result.ok) {
            expect(result.error.code).toBe(AuthErrorCode.INVALID_CREDENTIALS);
          }
          done();
        });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
      req.flush({ error: 'Invalid credentials' }, { status: 401, statusText: 'Unauthorized' });
    });

    it('should map 403 to FORBIDDEN error', (done) => {
      service.getCurrentUser().subscribe({
        error: (error) => {
          expect(error.code).toBe(AuthErrorCode.FORBIDDEN);
          done();
        },
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/me`);
      req.flush({ error: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
    });

    it('should map network errors to NETWORK_ERROR', (done) => {
      service
        .login({ username: 'test', password: 'test', rememberMe: false })
        .subscribe((result) => {
          expect(result.ok).toBe(false);
          if (!result.ok) {
            expect(result.error.code).toBe(AuthErrorCode.NETWORK_ERROR);
          }
          done();
        });

      const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
      req.error(new ProgressEvent('error'));
    });
  });
});
