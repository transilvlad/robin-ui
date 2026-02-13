import { Routes } from '@angular/router';
import { MetricsDashboardComponent } from './metrics/metrics-dashboard.component';
import { LogViewerComponent } from './logs/log-viewer.component';

export const MONITORING_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'metrics',
    pathMatch: 'full',
  },
  {
    path: 'metrics',
    component: MetricsDashboardComponent,
  },
  {
    path: 'logs',
    component: LogViewerComponent,
  },
];
