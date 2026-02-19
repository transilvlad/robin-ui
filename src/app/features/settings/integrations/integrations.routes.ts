import { Routes } from '@angular/router';
import { ProvidersListComponent } from './providers/providers-list.component';

export const INTEGRATIONS_ROUTES: Routes = [
  {
    path: 'providers',
    component: ProvidersListComponent,
  },
];
