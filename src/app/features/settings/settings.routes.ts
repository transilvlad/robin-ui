import { Routes } from '@angular/router';
import { ServerConfigComponent } from './server/server-config.component';
import { UserListComponent } from './users/user-list.component';
import { DovecotConfigComponent } from './dovecot/dovecot-config.component';
import { EmailReportingComponent } from './email-reporting/email-reporting.component';

export const SETTINGS_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'server',
    pathMatch: 'full',
  },
  {
    path: 'server',
    component: ServerConfigComponent,
  },
  {
    path: 'users',
    component: UserListComponent,
  },
  {
    path: 'dovecot',
    component: DovecotConfigComponent,
  },
  {
    path: 'reporting',
    component: EmailReportingComponent,
  },
  {
    path: 'integrations',
    loadChildren: () => import('./integrations/integrations.routes').then(m => m.INTEGRATIONS_ROUTES),
  },
];
