import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { RelayConfigComponent } from './relay/relay-config.component';
import { WebhooksComponent } from './webhooks/webhooks.component';

const routes: Routes = [
  { path: 'relay', component: RelayConfigComponent },
  { path: 'webhooks', component: WebhooksComponent },
  { path: '', redirectTo: 'relay', pathMatch: 'full' },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class RoutingRoutingModule {}
