import { Routes } from '@angular/router';
import { authGuard } from '@core/guards/auth.guard';
import { dmarcLicenseGuard } from './guards/dmarc-license.guard';

export const DMARC_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard, dmarcLicenseGuard],
    loadComponent: () =>
      import('./dmarc-overview/dmarc-overview.component').then(m => m.DmarcOverviewComponent),
    title: 'DMARC - Robin MTA',
  },
];
