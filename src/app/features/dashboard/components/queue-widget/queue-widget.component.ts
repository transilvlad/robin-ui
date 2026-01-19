import { Component, Input } from '@angular/core';

@Component({
    selector: 'app-queue-widget',
    templateUrl: './queue-widget.component.html',
    styleUrls: ['./queue-widget.component.scss'],
    standalone: false
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
