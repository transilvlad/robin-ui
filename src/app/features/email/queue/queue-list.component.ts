import { Component, OnInit } from '@angular/core';
import { QueueService } from '../services/queue.service';
import { QueueItem } from '@core/models/queue.model';
import { NotificationService } from '@core/services/notification.service';

@Component({
    selector: 'app-queue-list',
    templateUrl: './queue-list.component.html',
    styleUrls: ['./queue-list.component.scss'],
    standalone: false
})
export class QueueListComponent implements OnInit {
  items: QueueItem[] = [];
  loading = true;
  currentPage = 0;
  pageSize = 50;
  totalCount = 0;
  Math = Math;

  constructor(
    private queueService: QueueService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.loadQueue();
  }

  loadQueue(): void {
    this.loading = true;
    this.queueService.getQueue(this.currentPage, this.pageSize).subscribe({
      next: (response) => {
        this.items = response.items;
        this.totalCount = response.totalCount;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  retryItem(item: QueueItem): void {
    this.queueService.retryItem(item.uid).subscribe({
      next: () => {
        this.notificationService.success('Item queued for retry');
        this.loadQueue();
      },
    });
  }

  deleteItem(item: QueueItem): void {
    if (confirm(`Are you sure you want to delete item ${item.uid}?`)) {
      this.queueService.deleteItem(item.uid).subscribe({
        next: () => {
          this.notificationService.success('Item deleted');
          this.loadQueue();
        },
      });
    }
  }

  nextPage(): void {
    if ((this.currentPage + 1) * this.pageSize < this.totalCount) {
      this.currentPage++;
      this.loadQueue();
    }
  }

  previousPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadQueue();
    }
  }
}
