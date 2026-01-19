import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '@core/guards/auth.guard';

const routes: Routes = [
  {
    path: 'dashboard',
    loadChildren: () =>
      import('./features/dashboard/dashboard.module').then(
        (m) => m.DashboardModule
      ),
    canActivate: [AuthGuard],
  },
  {
    path: 'email',
    loadChildren: () =>
      import('./features/email/email.module').then((m) => m.EmailModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'security',
    loadChildren: () =>
      import('./features/security/security.module').then(
        (m) => m.SecurityModule
      ),
    canActivate: [AuthGuard],
  },
  {
    path: 'routing',
    loadChildren: () =>
      import('./features/routing/routing.module').then((m) => m.RoutingModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'monitoring',
    loadChildren: () =>
      import('./features/monitoring/monitoring.module').then(
        (m) => m.MonitoringModule
      ),
    canActivate: [AuthGuard],
  },
  {
    path: 'settings',
    loadChildren: () =>
      import('./features/settings/settings.module').then(
        (m) => m.SettingsModule
      ),
    canActivate: [AuthGuard],
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
