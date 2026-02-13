import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject, takeUntil } from 'rxjs';
import { StorageService } from '../services/storage.service';
import { StorageItem, BreadcrumbItem } from '@core/models/storage.model';
import { BytesPipe } from '@shared/pipes/bytes.pipe';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';

@Component({
    selector: 'app-storage-browser',
    templateUrl: './storage-browser.component.html',
    styleUrls: ['./storage-browser.component.scss'],
    standalone: true,
    imports: [
      CommonModule,
      MatButtonModule,
      MatIconModule,
      MatTableModule,
      MatProgressSpinnerModule,
      BytesPipe,
      RelativeTimePipe,
    ]
})
export class StorageBrowserComponent implements OnInit, OnDestroy {
  items: StorageItem[] = [];
  breadcrumbs: BreadcrumbItem[] = [];
  currentPath = '/';
  loading = true;
  private destroy$ = new Subject<void>();

  constructor(private storageService: StorageService) {}

  ngOnInit(): void {
    this.loadPath(this.currentPath);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadPath(path: string): void {
    this.loading = true;
    this.currentPath = path;

    this.storageService.getItems(path)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.items = response.items;
          this.breadcrumbs = response.breadcrumbs;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        },
      });
  }

  navigate(item: StorageItem): void {
    if (item.type === 'directory') {
      this.loadPath(item.path);
    }
  }

  navigateToBreadcrumb(breadcrumb: BreadcrumbItem): void {
    this.loadPath(breadcrumb.path);
  }
}
