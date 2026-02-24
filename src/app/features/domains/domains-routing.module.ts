import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DomainListComponent } from './components/domain-list/domain-list.component';
import { DomainDetailComponent } from './components/domain-detail/domain-detail.component';
import { DnsProvidersComponent } from './components/dns-providers/dns-providers.component';
import { DnsTemplatesComponent } from './components/dns-templates/dns-templates.component';

const routes: Routes = [
  { path: '',                component: DomainListComponent },
  { path: 'dns-providers',   component: DnsProvidersComponent },
  { path: 'dns-templates',   component: DnsTemplatesComponent },
  { path: ':id',             component: DomainDetailComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class DomainsRoutingModule {}
