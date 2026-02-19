import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';
import { DashboardService } from './services/dashboard.service';
import { HealthResponse } from '@core/models/health.model';

@Component({
    selector: 'app-dashboard',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './dashboard.component.html',
    styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  health?: HealthResponse;
  loading = true;

  constructor(private dashboardService: DashboardService) {}

  ngOnInit(): void {
    this.loadHealth();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadHealth(): void {
    this.loading = true;
    this.dashboardService.getHealth()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (health) => {
          this.health = health;
          this.loading = false;
        },
        error: () => {
          this.health = undefined;
          this.loading = false;
        },
      });
  }

  refresh(): void {
    this.loadHealth();
  }

  hasRetries(): boolean {
    const histogram = this.health?.queue?.retryHistogram;
    return histogram ? Object.keys(histogram).length > 0 : false;
  }

  getRetryEntries(): Array<{key: string, value: number}> {
    const histogram = this.health?.queue?.retryHistogram;
    if (!histogram) return [];
    return Object.entries(histogram)
      .map(([key, value]) => ({ key, value }))
      .sort((a, b) => parseInt(a.key) - parseInt(b.key));
  }

  getRetryPercentage(value: number): number {
    const size = this.health?.queue?.size;
    if (!size || size === 0) return 0;
    return (value / size) * 100;
  }
}
