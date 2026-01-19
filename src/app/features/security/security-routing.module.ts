import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ClamavConfigComponent } from './clamav/clamav-config.component';
import { RspamdConfigComponent } from './rspamd/rspamd-config.component';
import { BlocklistComponent } from './blocklist/blocklist.component';

const routes: Routes = [
  { path: 'clamav', component: ClamavConfigComponent },
  { path: 'rspamd', component: RspamdConfigComponent },
  { path: 'blocklist', component: BlocklistComponent },
  { path: '', redirectTo: 'clamav', pathMatch: 'full' },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class SecurityRoutingModule {}
