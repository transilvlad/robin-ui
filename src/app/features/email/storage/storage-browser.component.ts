import { Component, OnInit } from '@angular/core';
import { StorageService } from '../services/storage.service';
import { StorageItem, BreadcrumbItem } from '@core/models/storage.model';

@Component({
    selector: 'app-storage-browser',
    templateUrl: './storage-browser.component.html',
    styleUrls: ['./storage-browser.component.scss'],
    standalone: false
})
export class StorageBrowserComponent implements OnInit {
  items: StorageItem[] = [];
  breadcrumbs: BreadcrumbItem[] = [];
  currentPath = '/';
  loading = true;

  constructor(private storageService: StorageService) {}

  ngOnInit(): void {
    this.loadPath(this.currentPath);
  }

  loadPath(path: string): void {
    this.loading = true;
    this.currentPath = path;

    this.storageService.getItems(path).subscribe({
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
