import { Routes } from '@angular/router';
import { ClamavConfigComponent } from './clamav/clamav-config.component';
import { RspamdConfigComponent } from './rspamd/rspamd-config.component';
import { BlocklistComponent } from './blocklist/blocklist.component';

export const SECURITY_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'clamav',
    pathMatch: 'full',
  },
  {
    path: 'clamav',
    component: ClamavConfigComponent,
  },
  {
    path: 'rspamd',
    component: RspamdConfigComponent,
  },
  {
    path: 'blocklist',
    component: BlocklistComponent,
  },
];
