import { Component, OnInit } from '@angular/core';
import { DashboardService } from './services/dashboard.service';
import { HealthResponse } from '@core/models/health.model';

@Component({
    selector: 'app-dashboard',
    templateUrl: './dashboard.component.html',
    styleUrls: ['./dashboard.component.scss'],
    standalone: false
})
export class DashboardComponent implements OnInit {
  health?: HealthResponse;
  loading = true;

  constructor(private dashboardService: DashboardService) {}

  ngOnInit(): void {
    this.loadHealth();
  }

  loadHealth(): void {
    this.loading = true;
    this.dashboardService.getHealth().subscribe({
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
