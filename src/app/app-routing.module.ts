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
      import('./features/dashboard/dashboard.module').then(
        (m) => m.DashboardModule
      ),
    canActivate: [authGuard],
  },
  {
    path: 'email',
    loadChildren: () =>
      import('./features/email/email.module').then((m) => m.EmailModule),
    canActivate: [authGuard],
  },
  {
    path: 'security',
    loadChildren: () =>
      import('./features/security/security.module').then(
        (m) => m.SecurityModule
      ),
    canActivate: [authGuard],
  },
  {
    path: 'routing',
    loadChildren: () =>
      import('./features/routing/routing.module').then((m) => m.RoutingModule),
    canActivate: [authGuard],
  },
  {
    path: 'monitoring',
    loadChildren: () =>
      import('./features/monitoring/monitoring.module').then(
        (m) => m.MonitoringModule
      ),
    canActivate: [authGuard],
  },
  {
    path: 'settings',
    loadChildren: () =>
      import('./features/settings/settings.module').then(
        (m) => m.SettingsModule
      ),
    canActivate: [authGuard],
  },
  {
    path: 'domains',
    loadChildren: () =>
      import('./features/domains/domains.module').then((m) => m.DomainsModule),
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
