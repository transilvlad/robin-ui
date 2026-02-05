import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '@shared/shared.module';
import { DomainListComponent } from './components/domain-list/domain-list.component';
import { DomainWizardComponent } from './components/domain-wizard/domain-wizard.component';
import { DomainDetailComponent } from './components/domain-detail/domain-detail.component';
import { DnsRecordDialogComponent } from './components/dns-record-dialog/dns-record-dialog.component';
import { DnssecDialogComponent } from './components/dnssec-dialog/dnssec-dialog.component';

const routes: Routes = [
  { path: '', component: DomainListComponent },
  { path: 'new', component: DomainWizardComponent },
  { path: ':id', component: DomainDetailComponent }
];

@NgModule({
  declarations: [
    DomainListComponent,
    DomainWizardComponent,
    DomainDetailComponent,
    DnsRecordDialogComponent,
    DnssecDialogComponent
  ],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class DomainModule { }
