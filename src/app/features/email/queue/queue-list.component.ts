import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subject, takeUntil } from 'rxjs';
import { QueueService } from '../services/queue.service';
import { QueueItem } from '@core/models/queue.model';
import { NotificationService } from '@core/services/notification.service';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';

@Component({
    selector: 'app-queue-list',
    templateUrl: './queue-list.component.html',
    styleUrls: ['./queue-list.component.scss'],
    standalone: true,
    imports: [
      CommonModule,
      MatButtonModule,
      MatIconModule,
      MatTableModule,
      MatProgressSpinnerModule,
      MatTooltipModule,
      RelativeTimePipe,
    ]
})
export class QueueListComponent implements OnInit, OnDestroy {
  items: QueueItem[] = [];
  loading = true;
  currentPage = 0;
  pageSize = 50;
  totalCount = 0;
  Math = Math;
  private destroy$ = new Subject<void>();

  constructor(
    private queueService: QueueService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.loadQueue();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadQueue(): void {
    this.loading = true;
    this.queueService.getQueue(this.currentPage, this.pageSize)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
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
    this.queueService.retryItem(item.uid)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.notificationService.success('Item queued for retry');
          this.loadQueue();
        },
      });
  }

  deleteItem(item: QueueItem): void {
    if (confirm(`Are you sure you want to delete item ${item.uid}?`)) {
      this.queueService.deleteItem(item.uid)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
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
