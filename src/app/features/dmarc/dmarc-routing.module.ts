import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DmarcDashboardComponent } from './components/dmarc-dashboard/dmarc-dashboard.component';
import { DmarcReportListComponent } from './components/dmarc-report-list/dmarc-report-list.component';
import { DmarcReportDetailComponent } from './components/dmarc-report-detail/dmarc-report-detail.component';
import { DmarcValidatorComponent } from './components/dmarc-validator/dmarc-validator.component';
import { DmarcIngestComponent } from './components/dmarc-ingest/dmarc-ingest.component';

const routes: Routes = [
  { path: '', component: DmarcDashboardComponent },
  { path: 'reports', component: DmarcReportListComponent },
  { path: 'reports/:id', component: DmarcReportDetailComponent },
  { path: 'validate', component: DmarcValidatorComponent },
  { path: 'ingest', component: DmarcIngestComponent }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class DmarcRoutingModule { }
