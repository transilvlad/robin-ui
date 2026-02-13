import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HealthResponse } from '@core/models/health.model';
import { StatusBadgeComponent } from '@shared/components/status-badge/status-badge.component';

@Component({
    selector: 'app-health-widget',
    standalone: true,
    imports: [CommonModule, StatusBadgeComponent],
    templateUrl: './health-widget.component.html',
    styleUrls: ['./health-widget.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class HealthWidgetComponent {
  @Input() health?: HealthResponse;
}
