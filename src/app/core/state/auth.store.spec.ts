import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthStore } from './auth.store';
import { AuthService } from '../services/auth.service';
import { TokenStorageService } from '../services/token-storage.service';
import { NotificationService } from '../services/notification.service';
import {
  User,
  UserRole,
  Permission,
  AuthResponse,
  AuthTokens,
  LoginRequest,
  AuthErrorCode,
  Ok,
  Err,
  UserId,
  AccessToken,
  RefreshToken,
} from '../models/auth.model';

describe('AuthStore', () => {
  let store: typeof AuthStore.prototype;
  let authServiceMock: jasmine.SpyObj<AuthService>;
  let tokenStorageMock: jasmine.SpyObj<TokenStorageService>;
  let routerMock: jasmine.SpyObj<Router>;
  let notificationServiceMock: jasmine.SpyObj<NotificationService>;

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
    authServiceMock = jasmine.createSpyObj('AuthService', [
      'login',
      'logout',
      'refreshToken',
      'verifyToken',
      'getCurrentUser',
    ]);
    tokenStorageMock = jasmine.createSpyObj('TokenStorageService', [
      'setAccessToken',
      'getAccessToken',
      'setUser',
      'getUser',
      'clear',
    ]);
    routerMock = jasmine.createSpyObj('Router', ['navigate', 'navigateByUrl'], {
      routerState: {
        snapshot: {
          root: {
            queryParams: {},
          },
        },
      },
    });
    notificationServiceMock = jasmine.createSpyObj('NotificationService', [
      'success',
      'error',
      'info',
    ]);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: TokenStorageService, useValue: tokenStorageMock },
        { provide: Router, useValue: routerMock },
        { provide: NotificationService, useValue: notificationServiceMock },
      ],
    });

    store = TestBed.inject(AuthStore);
  });

  describe('Initial State', () => {
    it('should have correct initial state', () => {
      expect(store.user()).toBeNull();
      expect(store.accessToken()).toBeNull();
      expect(store.permissions()).toEqual([]);
      expect(store.isAuthenticated()).toBe(false);
      expect(store.loading()).toBe(false);
      expect(store.error()).toBeNull();
      expect(store.sessionExpiresAt()).toBeNull();
      expect(store.lastActivity()).toBeNull();
    });

    it('should have empty computed signals initially', () => {
      expect(store.userRoles()).toEqual([]);
      expect(store.username()).toBe('');
      expect(store.hasValidSession()).toBe(false);
    });
  });

  describe('Login', () => {
    const credentials: LoginRequest = {
      username: 'testuser',
      password: 'password123',
      rememberMe: false,
    };

    it('should handle successful login', async () => {
      authServiceMock.login.and.returnValue(
        Promise.resolve(Ok(mockAuthResponse))
      );

      await store.login(credentials);

      expect(authServiceMock.login).toHaveBeenCalledWith(credentials);
      expect(tokenStorageMock.setAccessToken).toHaveBeenCalledWith(
        mockTokens.accessToken
      );
      expect(tokenStorageMock.setUser).toHaveBeenCalledWith(mockUser);
      expect(store.user()).toEqual(mockUser);
      expect(store.accessToken()).toBe(mockTokens.accessToken);
      expect(store.isAuthenticated()).toBe(true);
      expect(store.loading()).toBe(false);
      expect(store.permissions()).toEqual(mockAuthResponse.permissions);
      expect(notificationServiceMock.success).toHaveBeenCalledWith('Login successful');
      expect(routerMock.navigateByUrl).toHaveBeenCalled();
    });

    it('should redirect to returnUrl after successful login', async () => {
      routerMock.routerState.snapshot.root.queryParams = { returnUrl: '/settings' };
      authServiceMock.login.and.returnValue(
        Promise.resolve(Ok(mockAuthResponse))
      );

      await store.login(credentials);

      expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/settings');
    });

    it('should redirect to dashboard when no returnUrl', async () => {
      authServiceMock.login.and.returnValue(
        Promise.resolve(Ok(mockAuthResponse))
      );

      await store.login(credentials);

      expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    });

    it('should handle login failure', async () => {
      const error = {
        code: AuthErrorCode.INVALID_CREDENTIALS,
        message: 'Invalid username or password',
      };
      authServiceMock.login.and.returnValue(Promise.resolve(Err(error)));

      await store.login(credentials);

      expect(store.isAuthenticated()).toBe(false);
      expect(store.loading()).toBe(false);
      expect(store.error()).toBe(error.message);
      expect(notificationServiceMock.error).toHaveBeenCalledWith(
        'Invalid username or password'
      );
      expect(routerMock.navigateByUrl).not.toHaveBeenCalled();
    });

    it('should set loading state during login', async () => {
      authServiceMock.login.and.returnValue(
        new Promise((resolve) => {
          expect(store.loading()).toBe(true);
          resolve(Ok(mockAuthResponse));
        })
      );

      await store.login(credentials);
      expect(store.loading()).toBe(false);
    });

    it('should set sessionExpiresAt based on token expiresIn', async () => {
      const beforeLogin = Date.now();
      authServiceMock.login.and.returnValue(
        Promise.resolve(Ok(mockAuthResponse))
      );

      await store.login(credentials);

      const sessionExpires = store.sessionExpiresAt();
      expect(sessionExpires).toBeTruthy();
      if (sessionExpires) {
        const expiresAtTime = sessionExpires.getTime();
        const expectedTime = beforeLogin + mockTokens.expiresIn * 1000;
        // Allow 1 second tolerance for test execution time
        expect(Math.abs(expiresAtTime - expectedTime)).toBeLessThan(1000);
      }
    });
  });

  describe('Logout', () => {
    beforeEach(async () => {
      // Set up authenticated state
      authServiceMock.login.and.returnValue(
        Promise.resolve(Ok(mockAuthResponse))
      );
      await store.login({
        username: 'test',
        password: 'password123',
        rememberMe: false,
      });
    });

    it('should handle successful logout', async () => {
      authServiceMock.logout.and.returnValue(Promise.resolve());

      await store.logout();

      expect(authServiceMock.logout).toHaveBeenCalled();
      expect(tokenStorageMock.clear).toHaveBeenCalled();
      expect(store.user()).toBeNull();
      expect(store.accessToken()).toBeNull();
      expect(store.isAuthenticated()).toBe(false);
      expect(routerMock.navigate).toHaveBeenCalledWith(['/auth/login']);
      expect(notificationServiceMock.info).toHaveBeenCalledWith(
        'You have been logged out'
      );
    });

    it('should handle logout API failure gracefully', async () => {
      authServiceMock.logout.and.returnValue(
        Promise.reject(new Error('Network error'))
      );

      await store.logout();

      // Should still clear client-side state
      expect(tokenStorageMock.clear).toHaveBeenCalled();
      expect(store.isAuthenticated()).toBe(false);
      expect(routerMock.navigate).toHaveBeenCalledWith(['/auth/login']);
    });
  });

  describe('Auto Login', () => {
    it('should restore session from storage', async () => {
      tokenStorageMock.getAccessToken.and.returnValue(
        'stored_token' as AccessToken
      );
      tokenStorageMock.getUser.and.returnValue(mockUser);

      await store.autoLogin();

      expect(store.user()).toEqual(mockUser);
      expect(store.accessToken()).toBe('stored_token');
      expect(store.isAuthenticated()).toBe(true);
      expect(store.permissions()).toEqual(mockUser.permissions);
    });

    it('should try refresh token when no access token in storage', async () => {
      tokenStorageMock.getAccessToken.and.returnValue(null);
      tokenStorageMock.getUser.and.returnValue(null);
      authServiceMock.refreshToken.and.returnValue(
        Promise.resolve(Ok(mockTokens))
      );

      await store.autoLogin();

      expect(authServiceMock.refreshToken).toHaveBeenCalled();
      expect(store.accessToken()).toBe(mockTokens.accessToken);
      expect(store.isAuthenticated()).toBe(true);
    });

    it('should handle refresh token failure silently', async () => {
      tokenStorageMock.getAccessToken.and.returnValue(null);
      tokenStorageMock.getUser.and.returnValue(null);
      authServiceMock.refreshToken.and.returnValue(
        Promise.reject(new Error('Refresh failed'))
      );

      await store.autoLogin();

      expect(store.isAuthenticated()).toBe(false);
      // Should not show error notifications during silent auto-login
      expect(notificationServiceMock.error).not.toHaveBeenCalled();
    });
  });

  describe('Update Tokens', () => {
    it('should update access token and session expiration', () => {
      const newTokens = {
        accessToken: 'new_token' as AccessToken,
        expiresIn: 1800,
      };

      const beforeUpdate = Date.now();
      store.updateTokens(newTokens);

      expect(tokenStorageMock.setAccessToken).toHaveBeenCalledWith(
        newTokens.accessToken
      );
      expect(store.accessToken()).toBe(newTokens.accessToken);

      const sessionExpires = store.sessionExpiresAt();
      expect(sessionExpires).toBeTruthy();
      if (sessionExpires) {
        const expiresAtTime = sessionExpires.getTime();
        const expectedTime = beforeUpdate + newTokens.expiresIn * 1000;
        expect(Math.abs(expiresAtTime - expectedTime)).toBeLessThan(1000);
      }
    });
  });

  describe('Update Last Activity', () => {
    it('should update last activity timestamp', () => {
      const beforeUpdate = Date.now();
      store.updateLastActivity();

      const lastActivity = store.lastActivity();
      expect(lastActivity).toBeTruthy();
      if (lastActivity) {
        const activityTime = lastActivity.getTime();
        expect(Math.abs(activityTime - beforeUpdate)).toBeLessThan(100);
      }
    });
  });

  describe('Permission Checks', () => {
    beforeEach(async () => {
      authServiceMock.login.and.returnValue(
        Promise.resolve(Ok(mockAuthResponse))
      );
      await store.login({
        username: 'test',
        password: 'password123',
        rememberMe: false,
      });
    });

    it('should check if user has specific permission', () => {
      expect(store.hasPermission(Permission.VIEW_DASHBOARD)).toBe(true);
      expect(store.hasPermission(Permission.VIEW_QUEUE)).toBe(true);
      expect(store.hasPermission(Permission.MANAGE_USERS)).toBe(false);
    });

    it('should check if user has all permissions (AND)', () => {
      expect(
        store.hasAllPermissions(Permission.VIEW_DASHBOARD, Permission.VIEW_QUEUE)
      ).toBe(true);
      expect(
        store.hasAllPermissions(
          Permission.VIEW_DASHBOARD,
          Permission.MANAGE_USERS
        )
      ).toBe(false);
    });

    it('should check if user has any permission (OR)', () => {
      expect(
        store.hasAnyPermission(Permission.VIEW_DASHBOARD, Permission.MANAGE_USERS)
      ).toBe(true);
      expect(
        store.hasAnyPermission(
          Permission.MANAGE_USERS,
          Permission.MANAGE_SERVER_CONFIG
        )
      ).toBe(false);
    });
  });

  describe('Role Checks', () => {
    beforeEach(async () => {
      authServiceMock.login.and.returnValue(
        Promise.resolve(Ok(mockAuthResponse))
      );
      await store.login({
        username: 'test',
        password: 'password123',
        rememberMe: false,
      });
    });

    it('should check if user has specific role', () => {
      expect(store.hasRole(UserRole.USER)).toBe(true);
      expect(store.hasRole(UserRole.ADMIN)).toBe(false);
    });

    it('should return false for role check when not authenticated', async () => {
      await store.logout();
      expect(store.hasRole(UserRole.USER)).toBe(false);
    });
  });

  describe('Computed Signals', () => {
    beforeEach(async () => {
      authServiceMock.login.and.returnValue(
        Promise.resolve(Ok(mockAuthResponse))
      );
      await store.login({
        username: 'test',
        password: 'password123',
        rememberMe: false,
      });
    });

    it('should compute user roles', () => {
      expect(store.userRoles()).toEqual([UserRole.USER]);
    });

    it('should compute username', () => {
      expect(store.username()).toBe('testuser');
    });

    it('should compute hasValidSession', () => {
      expect(store.hasValidSession()).toBe(true);
    });

    it('should detect expired session', async () => {
      // Manually set expired session
      store.updateTokens({
        accessToken: 'token' as AccessToken,
        expiresIn: -1, // Already expired
      });

      // Wait a tick for computed signal to update
      await new Promise((resolve) => setTimeout(resolve, 0));

      expect(store.hasValidSession()).toBe(false);
    });
  });

  describe('Error Messages', () => {
    it('should return correct error message for each error code', () => {
      const testCases = [
        {
          code: AuthErrorCode.INVALID_CREDENTIALS,
          expected: 'Invalid username or password',
        },
        {
          code: AuthErrorCode.TOKEN_EXPIRED,
          expected: 'Your session has expired. Please login again.',
        },
        {
          code: AuthErrorCode.NETWORK_ERROR,
          expected: 'Network error. Please check your connection.',
        },
        {
          code: AuthErrorCode.UNAUTHORIZED,
          expected: 'You are not authorized to perform this action.',
        },
        { code: AuthErrorCode.FORBIDDEN, expected: 'Access forbidden.' },
      ];

      testCases.forEach(({ code, expected }) => {
        expect(store.getErrorMessage(code)).toBe(expected);
      });
    });

    it('should return default message for unknown error code', () => {
      const unknownCode = 'UNKNOWN_ERROR' as AuthErrorCode;
      expect(store.getErrorMessage(unknownCode)).toBe(
        'An unexpected error occurred'
      );
    });
  });
});
