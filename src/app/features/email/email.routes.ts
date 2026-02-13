import { Routes } from '@angular/router';
import { QueueListComponent } from './queue/queue-list.component';
import { StorageBrowserComponent } from './storage/storage-browser.component';

export const EMAIL_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'queue',
    pathMatch: 'full',
  },
  {
    path: 'queue',
    component: QueueListComponent,
  },
  {
    path: 'storage',
    component: StorageBrowserComponent,
  },
];
