import { NgModule } from '@angular/core';
import { SharedModule } from '@shared/shared.module';
import { DashboardRoutingModule } from './dashboard-routing.module';
import { DashboardComponent } from './dashboard.component';
import { HealthWidgetComponent } from './components/health-widget/health-widget.component';
import { QueueWidgetComponent } from './components/queue-widget/queue-widget.component';
import { DashboardService } from './services/dashboard.service';

@NgModule({
  declarations: [
    DashboardComponent,
    HealthWidgetComponent,
    QueueWidgetComponent,
  ],
  imports: [SharedModule, DashboardRoutingModule],
  providers: [DashboardService],
})
export class DashboardModule {}
