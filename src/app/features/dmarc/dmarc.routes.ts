import { Routes } from '@angular/router';
import { authGuard } from '@core/guards/auth.guard';
import { dmarcLicenseGuard } from './guards/dmarc-license.guard';

export const DMARC_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full',
  },
  {
    path: 'dashboard',
    canActivate: [authGuard, dmarcLicenseGuard],
    loadComponent: () =>
      import('./components/dmarc-dashboard/dmarc-dashboard.component').then(
        m => m.DmarcDashboardComponent
      ),
    title: 'DMARC Dashboard - Robin MTA',
  },
  {
    path: 'reports',
    canActivate: [authGuard, dmarcLicenseGuard],
    loadComponent: () =>
      import('./components/dmarc-reports/dmarc-reports.component').then(
        m => m.DmarcReportsComponent
      ),
    title: 'DMARC Reports - Robin MTA',
  },
  {
    path: 'reports/:id',
    canActivate: [authGuard, dmarcLicenseGuard],
    loadComponent: () =>
      import('./components/dmarc-report-detail/dmarc-report-detail.component').then(
        m => m.DmarcReportDetailComponent
      ),
    title: 'DMARC Report Detail - Robin MTA',
  },
  {
    path: 'sources',
    canActivate: [authGuard, dmarcLicenseGuard],
    loadComponent: () =>
      import('./components/dmarc-sources/dmarc-sources.component').then(
        m => m.DmarcSourcesComponent
      ),
    title: 'DMARC Sources - Robin MTA',
  },
];
