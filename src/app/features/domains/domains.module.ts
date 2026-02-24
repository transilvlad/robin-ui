import { NgModule } from '@angular/core';
import { SharedModule } from '@shared/shared.module';
import { DomainsRoutingModule } from './domains-routing.module';
import { DomainListComponent } from './components/domain-list/domain-list.component';
import { DomainDetailComponent } from './components/domain-detail/domain-detail.component';
import { DnsRecordsComponent } from './components/dns-records/dns-records.component';
import { DkimManagementComponent } from './components/dkim-management/dkim-management.component';
import { DomainHealthComponent } from './components/domain-health/domain-health.component';
import { MtaStsStatusComponent } from './components/mta-sts-status/mta-sts-status.component';
import { DnsProvidersComponent } from './components/dns-providers/dns-providers.component';
import { DnsTemplatesComponent } from './components/dns-templates/dns-templates.component';

@NgModule({
  declarations: [
    DomainListComponent,
    DomainDetailComponent,
    DnsRecordsComponent,
    DkimManagementComponent,
    DomainHealthComponent,
    MtaStsStatusComponent,
    DnsProvidersComponent,
    DnsTemplatesComponent,
  ],
  imports: [SharedModule, DomainsRoutingModule],
})
export class DomainsModule {}
