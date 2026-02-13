import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { authGuard } from '@core/guards/auth.guard';

const routes: Routes = [
  {
    path: 'auth',
    loadChildren: () =>
      import('./features/auth/auth.routes').then((m) => m.AUTH_ROUTES),
  },
  {
    path: 'dashboard',
    loadChildren: () =>
      import('./features/dashboard/dashboard.routes').then(
        (m) => m.DASHBOARD_ROUTES
      ),
    canActivate: [authGuard],
  },
  {
    path: 'email',
    loadChildren: () =>
      import('./features/email/email.routes').then((m) => m.EMAIL_ROUTES),
    canActivate: [authGuard],
  },
  {
    path: 'security',
    loadChildren: () =>
      import('./features/security/security.routes').then(
        (m) => m.SECURITY_ROUTES
      ),
    canActivate: [authGuard],
  },
  {
    path: 'routing',
    loadChildren: () =>
      import('./features/routing/routing.routes').then((m) => m.ROUTING_ROUTES),
    canActivate: [authGuard],
  },
  {
    path: 'monitoring',
    loadChildren: () =>
      import('./features/monitoring/monitoring.routes').then(
        (m) => m.MONITORING_ROUTES
      ),
    canActivate: [authGuard],
  },
  {
    path: 'settings',
    loadChildren: () =>
      import('./features/settings/settings.routes').then(
        (m) => m.SETTINGS_ROUTES
      ),
    canActivate: [authGuard],
  },
  {
    path: 'domains',
    loadChildren: () =>
      import('./features/domains/domain.routes').then((m) => m.DOMAIN_ROUTES),
    canActivate: [authGuard],
  },
  {
    path: '',
    redirectTo: '/dashboard',
    pathMatch: 'full',
  },
  {
    path: '**',
    redirectTo: '/dashboard',
  },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
