import { Routes } from '@angular/router';

/**
 * Auth Feature Routes
 *
 * Standalone route configuration for authentication features.
 * All components are lazy-loaded and standalone.
 */
export const AUTH_ROUTES: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./login/login.component').then(m => m.LoginComponent),
    title: 'Login - Robin MTA'
  },
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  }
];
