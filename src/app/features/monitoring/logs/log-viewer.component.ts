import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Subject, takeUntil, debounceTime, distinctUntilChanged } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MonitoringService } from '../../../core/services/monitoring.service';
import {
  LogEntry,
  LogLevel,
  LogFilterParams,
  getLogLevelColor,
  getLogLevelBgColor,
} from '../../../core/models/monitoring.model';

@Component({
  selector: 'app-log-viewer',
  templateUrl: './log-viewer.component.html',
  styleUrls: ['./log-viewer.component.scss'],
  standalone: false
})
export class LogViewerComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly monitoringService = inject(MonitoringService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroy$ = new Subject<void>();

  // State
  logs: LogEntry[] = [];
  loading = false;
  autoRefresh = true;
  totalLogs = 0;
  hasMore = false;

  // Filters
  filterForm!: FormGroup;
  logLevels = Object.values(LogLevel);
  availableLoggers: string[] = [];

  // Pagination
  pageSize = 100;
  currentOffset = 0;

  // Virtual scroll
  itemSize = 80; // Approximate height of each log entry in pixels

  // Expansion state
  expandedStackTraces = new Set<string>();
  expandedContexts = new Set<string>();

  // Make Object and Math available in template
  Object = Object;
  Math = Math;

  toggleStackTrace(log: LogEntry): void {
    const id = this.trackByLogId(0, log);
    if (this.expandedStackTraces.has(id)) {
      this.expandedStackTraces.delete(id);
    } else {
      this.expandedStackTraces.add(id);
    }
  }

  isStackTraceExpanded(log: LogEntry): boolean {
    return this.expandedStackTraces.has(this.trackByLogId(0, log));
  }

  toggleContext(log: LogEntry): void {
    const id = this.trackByLogId(0, log);
    if (this.expandedContexts.has(id)) {
      this.expandedContexts.delete(id);
    } else {
      this.expandedContexts.add(id);
    }
  }

  isContextExpanded(log: LogEntry): boolean {
    return this.expandedContexts.has(this.trackByLogId(0, log));
  }

  ngOnInit(): void {
    this.initializeFilterForm();
    this.loadLoggers();
    this.loadLogs();

    // Watch for filter changes
    this.filterForm.valueChanges.pipe(
      debounceTime(500),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.currentOffset = 0;
      this.loadLogs();
    });

    if (this.autoRefresh) {
      this.startAutoRefresh();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeFilterForm(): void {
    this.filterForm = this.fb.group({
      level: [''],
      search: [''],
      logger: [''],
      startTime: [''],
      endTime: [''],
    });
  }

  private loadLoggers(): void {
    this.monitoringService.getLoggers().subscribe({
      next: (loggers) => {
        this.availableLoggers = loggers;
      },
      error: (error) => {
        console.error('Failed to load loggers:', error);
      }
    });
  }

  private loadLogs(): void {
    this.loading = true;

    const filters: LogFilterParams = {
      limit: this.pageSize,
      offset: this.currentOffset,
    };

    if (this.filterForm.value.level) {
      filters.level = this.filterForm.value.level;
    }
    if (this.filterForm.value.search) {
      filters.search = this.filterForm.value.search;
    }
    if (this.filterForm.value.logger) {
      filters.logger = this.filterForm.value.logger;
    }
    if (this.filterForm.value.startTime) {
      filters.startTime = new Date(this.filterForm.value.startTime).toISOString();
    }
    if (this.filterForm.value.endTime) {
      filters.endTime = new Date(this.filterForm.value.endTime).toISOString();
    }

    this.monitoringService.getLogs(filters).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (response) => {
        if (this.currentOffset === 0) {
          this.logs = response.entries;
        } else {
          this.logs = [...this.logs, ...response.entries];
        }
        this.totalLogs = response.total;
        this.hasMore = response.hasMore || false;
        this.loading = false;
      },
      error: (error) => {
        console.error('Failed to load logs:', error);
        this.snackBar.open('Failed to load logs', 'Close', {
          duration: 5000,
          panelClass: ['error-snackbar']
        });
        this.loading = false;
      }
    });
  }

  private startAutoRefresh(): void {
    const filters: LogFilterParams = {
      limit: this.pageSize,
      offset: 0,
    };

    if (this.filterForm.value.level) {
      filters.level = this.filterForm.value.level;
    }
    if (this.filterForm.value.search) {
      filters.search = this.filterForm.value.search;
    }
    if (this.filterForm.value.logger) {
      filters.logger = this.filterForm.value.logger;
    }

    this.monitoringService.getLogsStream(filters, 5000).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (response) => {
        if (this.currentOffset === 0) {
          this.logs = response.entries;
          this.totalLogs = response.total;
          this.hasMore = response.hasMore || false;
        }
      },
      error: (error) => {
        console.error('Auto-refresh failed:', error);
      }
    });
  }

  toggleAutoRefresh(): void {
    this.autoRefresh = !this.autoRefresh;

    if (this.autoRefresh) {
      this.startAutoRefresh();
    } else {
      this.loadLogs();
    }
  }

  refreshLogs(): void {
    this.currentOffset = 0;
    this.loadLogs();
  }

  loadMoreLogs(): void {
    if (this.hasMore && !this.loading) {
      this.currentOffset += this.pageSize;
      this.loadLogs();
    }
  }

  clearFilters(): void {
    this.filterForm.reset({
      level: '',
      search: '',
      logger: '',
      startTime: '',
      endTime: '',
    });
  }

  downloadLogs(format: 'txt' | 'json'): void {
    const filters: LogFilterParams = {};

    if (this.filterForm.value.level) {
      filters.level = this.filterForm.value.level;
    }
    if (this.filterForm.value.search) {
      filters.search = this.filterForm.value.search;
    }
    if (this.filterForm.value.logger) {
      filters.logger = this.filterForm.value.logger;
    }
    if (this.filterForm.value.startTime) {
      filters.startTime = new Date(this.filterForm.value.startTime).toISOString();
    }
    if (this.filterForm.value.endTime) {
      filters.endTime = new Date(this.filterForm.value.endTime).toISOString();
    }

    this.monitoringService.downloadLogs(filters, format).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `logs-${new Date().toISOString()}.${format}`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);

        this.snackBar.open(`✓ Logs exported as ${format.toUpperCase()}`, 'Close', {
          duration: 3000,
          panelClass: ['success-snackbar']
        });
      },
      error: (error) => {
        this.snackBar.open('✗ Failed to download logs', 'Close', {
          duration: 5000,
          panelClass: ['error-snackbar']
        });
      }
    });
  }

  formatTimestamp(timestamp: string): string {
    return new Date(timestamp).toLocaleString();
  }

  getLogLevelColor(level: LogLevel): string {
    return getLogLevelColor(level);
  }

  getLogLevelBgColor(level: LogLevel): string {
    return getLogLevelBgColor(level);
  }

  trackByLogId(index: number, log: LogEntry): string {
    return log.id || `${log.timestamp}-${index}`;
  }
}
