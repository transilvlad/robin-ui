import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { MetricsDashboardComponent } from './metrics/metrics-dashboard.component';
import { LogViewerComponent } from './logs/log-viewer.component';

const routes: Routes = [
  { path: 'metrics', component: MetricsDashboardComponent },
  { path: 'logs', component: LogViewerComponent },
  { path: '', redirectTo: 'metrics', pathMatch: 'full' },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class MonitoringRoutingModule {}
