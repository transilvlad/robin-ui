import { NgModule } from '@angular/core';
import { SharedModule } from '@shared/shared.module';
import { SecurityRoutingModule } from './security-routing.module';
import { ClamavConfigComponent } from './clamav/clamav-config.component';
import { RspamdConfigComponent } from './rspamd/rspamd-config.component';
import { BlocklistComponent } from './blocklist/blocklist.component';

@NgModule({
  declarations: [
    ClamavConfigComponent,
    RspamdConfigComponent,
    BlocklistComponent,
  ],
  imports: [SharedModule, SecurityRoutingModule],
})
export class SecurityModule {}
