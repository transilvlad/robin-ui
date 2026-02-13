import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-queue-widget',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './queue-widget.component.html',
    styleUrls: ['./queue-widget.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class QueueWidgetComponent {
  @Input() queueSize = 0;
  @Input() retryHistogram: Record<number, number> = {};

  get retryData(): { retry: number; count: number }[] {
    return Object.entries(this.retryHistogram).map(([retry, count]) => ({
      retry: Number(retry),
      count: Number(count),
    }));
  }
}
