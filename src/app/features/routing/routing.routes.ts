import { Routes } from '@angular/router';
import { RelayConfigComponent } from './relay/relay-config.component';
import { WebhooksComponent } from './webhooks/webhooks.component';

export const ROUTING_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'relay',
    pathMatch: 'full',
  },
  {
    path: 'relay',
    component: RelayConfigComponent,
  },
  {
    path: 'webhooks',
    component: WebhooksComponent,
  },
];
