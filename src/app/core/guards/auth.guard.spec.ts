import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthStore } from '../state/auth.store';
import { AuthService } from '../services/auth.service';
import { TokenStorageService } from '../services/token-storage.service';
import { NotificationService } from '../services/notification.service';

describe('authGuard', () => {
  let authStoreMock: jasmine.SpyObj<typeof AuthStore.prototype>;
  let routerMock: jasmine.SpyObj<Router>;
  let route: ActivatedRouteSnapshot;
  let state: RouterStateSnapshot;

  beforeEach(() => {
    authStoreMock = jasmine.createSpyObj(
      'AuthStore',
      ['isAuthenticated'],
      {
        isAuthenticated: jasmine.createSpy().and.returnValue(false),
      }
    );

    routerMock = jasmine.createSpyObj('Router', ['createUrlTree']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthStore, useValue: authStoreMock },
        { provide: Router, useValue: routerMock },
        {
          provide: AuthService,
          useValue: jasmine.createSpyObj('AuthService', ['login']),
        },
        {
          provide: TokenStorageService,
          useValue: jasmine.createSpyObj('TokenStorageService', ['clear']),
        },
        {
          provide: NotificationService,
          useValue: jasmine.createSpyObj('NotificationService', ['info']),
        },
      ],
    });

    route = {} as ActivatedRouteSnapshot;
    state = { url: '/dashboard' } as RouterStateSnapshot;
  });

  it('should allow access for authenticated users', () => {
    // Mock authenticated state
    Object.defineProperty(authStoreMock, 'isAuthenticated', {
      get: () => true,
    });

    const result = TestBed.runInInjectionContext(() =>
      authGuard(route, state)
    );

    expect(result).toBe(true);
    expect(routerMock.createUrlTree).not.toHaveBeenCalled();
  });

  it('should redirect unauthenticated users to login', () => {
    // Mock unauthenticated state
    Object.defineProperty(authStoreMock, 'isAuthenticated', {
      get: () => false,
    });

    const mockUrlTree = {} as UrlTree;
    routerMock.createUrlTree.and.returnValue(mockUrlTree);

    const result = TestBed.runInInjectionContext(() =>
      authGuard(route, state)
    );

    expect(result).toBe(mockUrlTree);
    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/dashboard' },
    });
  });

  it('should preserve return URL in query params', () => {
    Object.defineProperty(authStoreMock, 'isAuthenticated', {
      get: () => false,
    });

    const mockUrlTree = {} as UrlTree;
    routerMock.createUrlTree.and.returnValue(mockUrlTree);

    // Test different URLs
    const testUrls = [
      '/dashboard',
      '/settings',
      '/email/queue',
      '/security/clamav',
    ];

    testUrls.forEach((url) => {
      state.url = url;
      TestBed.runInInjectionContext(() => authGuard(route, state));
      expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
        queryParams: { returnUrl: url },
      });
    });
  });

  it('should handle root URL correctly', () => {
    Object.defineProperty(authStoreMock, 'isAuthenticated', {
      get: () => false,
    });

    const mockUrlTree = {} as UrlTree;
    routerMock.createUrlTree.and.returnValue(mockUrlTree);

    state.url = '/';
    TestBed.runInInjectionContext(() => authGuard(route, state));

    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/' },
    });
  });

  it('should not interfere with login page access', () => {
    Object.defineProperty(authStoreMock, 'isAuthenticated', {
      get: () => false,
    });

    state.url = '/auth/login';
    const mockUrlTree = {} as UrlTree;
    routerMock.createUrlTree.and.returnValue(mockUrlTree);

    TestBed.runInInjectionContext(() => authGuard(route, state));

    // Even if redirecting to login, should preserve the URL
    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/auth/login' },
    });
  });

  it('should work with query parameters in the protected URL', () => {
    Object.defineProperty(authStoreMock, 'isAuthenticated', {
      get: () => false,
    });

    const mockUrlTree = {} as UrlTree;
    routerMock.createUrlTree.and.returnValue(mockUrlTree);

    state.url = '/settings?tab=security&id=123';
    TestBed.runInInjectionContext(() => authGuard(route, state));

    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/settings?tab=security&id=123' },
    });
  });

  it('should handle URL with fragments', () => {
    Object.defineProperty(authStoreMock, 'isAuthenticated', {
      get: () => false,
    });

    const mockUrlTree = {} as UrlTree;
    routerMock.createUrlTree.and.returnValue(mockUrlTree);

    state.url = '/dashboard#section1';
    TestBed.runInInjectionContext(() => authGuard(route, state));

    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/dashboard#section1' },
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty state URL', () => {
      Object.defineProperty(authStoreMock, 'isAuthenticated', {
        get: () => false,
      });

      const mockUrlTree = {} as UrlTree;
      routerMock.createUrlTree.and.returnValue(mockUrlTree);

      state.url = '';
      TestBed.runInInjectionContext(() => authGuard(route, state));

      expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
        queryParams: { returnUrl: '' },
      });
    });

    it('should be callable multiple times', () => {
      Object.defineProperty(authStoreMock, 'isAuthenticated', {
        get: () => true,
      });

      const result1 = TestBed.runInInjectionContext(() =>
        authGuard(route, state)
      );
      const result2 = TestBed.runInInjectionContext(() =>
        authGuard(route, state)
      );

      expect(result1).toBe(true);
      expect(result2).toBe(true);
    });

    it('should handle authentication state changes', () => {
      let isAuth = false;
      Object.defineProperty(authStoreMock, 'isAuthenticated', {
        get: () => isAuth,
      });

      const mockUrlTree = {} as UrlTree;
      routerMock.createUrlTree.and.returnValue(mockUrlTree);

      // First call: not authenticated
      const result1 = TestBed.runInInjectionContext(() =>
        authGuard(route, state)
      );
      expect(result1).toBe(mockUrlTree);

      // Change auth state
      isAuth = true;

      // Second call: authenticated
      const result2 = TestBed.runInInjectionContext(() =>
        authGuard(route, state)
      );
      expect(result2).toBe(true);
    });
  });
});
