import { NgModule } from '@angular/core';
import { SharedModule } from '@shared/shared.module';
import { RoutingRoutingModule } from './routing-routing.module';
import { RelayConfigComponent } from './relay/relay-config.component';
import { WebhooksComponent } from './webhooks/webhooks.component';

@NgModule({
  declarations: [RelayConfigComponent, WebhooksComponent],
  imports: [SharedModule, RoutingRoutingModule],
})
export class RoutingModule {}
