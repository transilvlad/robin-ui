import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ServerConfigComponent } from './server/server-config.component';
import { UserListComponent } from './users/user-list.component';
import { DovecotConfigComponent } from './dovecot/dovecot-config.component';

const routes: Routes = [
  { path: 'server', component: ServerConfigComponent },
  { path: 'users', component: UserListComponent },
  { path: 'dovecot', component: DovecotConfigComponent },
  { path: '', redirectTo: 'server', pathMatch: 'full' },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class SettingsRoutingModule {}
