import { NgModule } from '@angular/core';
import { SharedModule } from '@shared/shared.module';
import { SettingsRoutingModule } from './settings-routing.module';
import { ServerConfigComponent } from './server/server-config.component';
import { UserListComponent } from './users/user-list.component';
import { DovecotConfigComponent } from './dovecot/dovecot-config.component';

@NgModule({
  declarations: [ServerConfigComponent, UserListComponent, DovecotConfigComponent],
  imports: [SharedModule, SettingsRoutingModule],
})
export class SettingsModule {}
