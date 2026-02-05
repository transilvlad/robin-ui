import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { HealthResponse } from '@core/models/health.model';

@Component({
    selector: 'app-health-widget',
    templateUrl: './health-widget.component.html',
    styleUrls: ['./health-widget.component.scss'],
    standalone: false,
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class HealthWidgetComponent {
  @Input() health?: HealthResponse;
}
