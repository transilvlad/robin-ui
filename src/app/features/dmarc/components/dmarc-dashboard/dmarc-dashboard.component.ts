import { Component, OnInit, OnDestroy, inject, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { Subject, takeUntil, forkJoin } from 'rxjs';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { DmarcApiService } from '../../services/dmarc-api.service';
import { DmarcSummary, DmarcDailyAnalytics } from '../../models/dmarc.models';

// Register Chart.js components
Chart.register(...registerables);

@Component({
  selector: 'app-dmarc-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dmarc-dashboard.component.html',
})
export class DmarcDashboardComponent implements OnInit, OnDestroy {
  private readonly api = inject(DmarcApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy$ = new Subject<void>();

  @ViewChild('trendChart') trendChartRef!: ElementRef<HTMLCanvasElement>;

  private trendChart?: Chart;

  loading = true;
  domain = '';
  summary: DmarcSummary | null = null;
  dailyStats: DmarcDailyAnalytics | null = null;
  error: string | null = null;

  ngOnInit(): void {
    this.domain = this.route.snapshot.queryParamMap.get('domain') ?? '';
    this.loadData();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.trendChart?.destroy();
  }

  private loadData(): void {
    this.loading = true;
    this.error = null;

    forkJoin({
      summary: this.api.getSummary(this.domain, { days: 30 }),
      daily: this.api.getDailyStats(this.domain, { days: 30 }),
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: ({ summary, daily }) => {
          this.summary = summary.ok ? summary.value : null;
          this.dailyStats = daily.ok ? daily.value : null;

          if (!summary.ok) {
            this.error = 'Failed to load DMARC summary.';
          }

          this.loading = false;
          setTimeout(() => this.buildTrendChart(), 100);
        },
        error: () => {
          this.error = 'Unexpected error loading DMARC data.';
          this.loading = false;
        },
      });
  }

  private buildTrendChart(): void {
    if (!this.trendChartRef || !this.dailyStats?.points?.length) return;

    const points = this.dailyStats.points;
    const labels = points.map(p => p.date.slice(0, 10));
    const passData = points.map(p => p.dmarcPass);
    const failData = points.map(p => p.dmarcFail);

    if (this.trendChart) {
      this.trendChart.data.labels = labels;
      this.trendChart.data.datasets[0].data = passData;
      this.trendChart.data.datasets[1].data = failData;
      this.trendChart.update();
      return;
    }

    const ctx = this.trendChartRef.nativeElement.getContext('2d');
    if (!ctx) return;

    const options: ChartConfiguration['options'] = {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: true, position: 'top' },
        tooltip: { mode: 'index', intersect: false },
      },
      scales: {
        y: { beginAtZero: true },
        x: { ticks: { maxRotation: 45, minRotation: 45 } },
      },
    };

    this.trendChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: 'DMARC Pass',
            data: passData,
            borderColor: 'rgb(34, 197, 94)',
            backgroundColor: 'rgba(34, 197, 94, 0.1)',
            fill: true,
            tension: 0.4,
          },
          {
            label: 'DMARC Fail',
            data: failData,
            borderColor: 'rgb(239, 68, 68)',
            backgroundColor: 'rgba(239, 68, 68, 0.1)',
            fill: true,
            tension: 0.4,
          },
        ],
      },
      options,
    });
  }

  get complianceColor(): string {
    const pct = this.summary?.compliancePercent ?? 0;
    if (pct >= 95) return 'text-green-600';
    if (pct >= 75) return 'text-orange-500';
    return 'text-destructive';
  }
}
