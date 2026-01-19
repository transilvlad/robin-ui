import { NgModule } from '@angular/core';
import { SharedModule } from '@shared/shared.module';
import { MonitoringRoutingModule } from './monitoring-routing.module';
import { MetricsDashboardComponent } from './metrics/metrics-dashboard.component';
import { LogViewerComponent } from './logs/log-viewer.component';

@NgModule({
  declarations: [MetricsDashboardComponent, LogViewerComponent],
  imports: [SharedModule, MonitoringRoutingModule],
})
export class MonitoringModule {}
