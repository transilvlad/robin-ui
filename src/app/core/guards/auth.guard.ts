import { inject } from '@angular/core';
import { Router, CanActivateFn, ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthStore } from '../state/auth.store';

/**
 * Authentication Guard
 *
 * Functional guard using Angular 21+ patterns with SignalStore.
 * Protects routes from unauthenticated access and redirects to login
 * with return URL preservation.
 *
 * Usage:
 * ```typescript
 * {
 *   path: 'dashboard',
 *   canActivate: [authGuard],
 *   loadComponent: () => import('./dashboard.component')
 * }
 * ```
 */
export const authGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
): boolean | UrlTree => {
  const authStore = inject(AuthStore);
  const router = inject(Router);

  // Check if user is authenticated
  if (authStore.isAuthenticated()) {
    return true;
  }

  // Redirect to login with return URL
  return router.createUrlTree(['/auth/login'], {
    queryParams: { returnUrl: state.url }
  });
};

/**
 * Legacy class-based guard for backward compatibility
 * Use functional authGuard instead in new code
 */
export class AuthGuard {
  private authStore = inject(AuthStore);
  private router = inject(Router);

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): boolean | UrlTree {
    if (this.authStore.isAuthenticated()) {
      return true;
    }

    return this.router.createUrlTree(['/auth/login'], {
      queryParams: { returnUrl: state.url }
    });
  }
}
