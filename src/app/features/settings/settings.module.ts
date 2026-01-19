import { NgModule } from '@angular/core';
import { SharedModule } from '@shared/shared.module';
import { SettingsRoutingModule } from './settings-routing.module';
import { ServerConfigComponent } from './server/server-config.component';
import { UserListComponent } from './users/user-list.component';

@NgModule({
  declarations: [ServerConfigComponent, UserListComponent],
  imports: [SharedModule, SettingsRoutingModule],
})
export class SettingsModule {}
