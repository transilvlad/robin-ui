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
}
