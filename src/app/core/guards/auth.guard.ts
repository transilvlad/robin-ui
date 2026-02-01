import { inject } from '@angular/core';
import { Router, CanActivateFn, ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthStore } from '../state/auth.store';
import { UserRole, Permission } from '../models/auth.model';

/**
 * Authentication Guard
 *
 * Functional guard using Angular 21+ patterns with SignalStore.
 * Protects routes from unauthenticated access and redirects to login
 * with return URL preservation.
 *
 * Supports role-based and permission-based route protection via route data:
 * - `roles`: Array of allowed roles (OR logic - user must have at least one)
 * - `permissions`: Array of required permissions (AND logic - user must have all)
 *
 * Usage:
 * ```typescript
 * // Basic authentication
 * {
 *   path: 'dashboard',
 *   canActivate: [authGuard],
 *   loadComponent: () => import('./dashboard.component')
 * }
 *
 * // Role-based protection
 * {
 *   path: 'admin',
 *   canActivate: [authGuard],
 *   data: { roles: [UserRole.ADMIN] },
 *   loadComponent: () => import('./admin.component')
 * }
 *
 * // Permission-based protection
 * {
 *   path: 'users',
 *   canActivate: [authGuard],
 *   data: { permissions: [Permission.MANAGE_USERS] },
 *   loadComponent: () => import('./users.component')
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
  if (!authStore.isAuthenticated()) {
    // Redirect to login with return URL
    return router.createUrlTree(['/auth/login'], {
      queryParams: { returnUrl: state.url }
    });
  }

  // Check token validity
  if (!authStore.hasValidSession()) {
    // Session expired - redirect to login
    return router.createUrlTree(['/auth/login'], {
      queryParams: { returnUrl: state.url }
    });
  }

  // Check role-based access (OR logic)
  const requiredRoles = route.data['roles'] as UserRole[];
  if (requiredRoles && requiredRoles.length > 0) {
    if (!authStore.hasAnyRole(...requiredRoles)) {
      // User doesn't have required role
      return router.createUrlTree(['/unauthorized']);
    }
  }

  // Check permission-based access (AND logic)
  const requiredPermissions = route.data['permissions'] as Permission[];
  if (requiredPermissions && requiredPermissions.length > 0) {
    if (!authStore.hasAllPermissions(...requiredPermissions)) {
      // User doesn't have required permissions
      return router.createUrlTree(['/unauthorized']);
    }
  }

  return true;
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
