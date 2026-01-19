import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ServerConfigComponent } from './server/server-config.component';
import { UserListComponent } from './users/user-list.component';

const routes: Routes = [
  { path: 'server', component: ServerConfigComponent },
  { path: 'users', component: UserListComponent },
  { path: '', redirectTo: 'server', pathMatch: 'full' },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class SettingsRoutingModule {}
