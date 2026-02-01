import { Component, OnInit, OnDestroy, inject, ViewChild, ElementRef } from '@angular/core';
import { Subject, takeUntil } from 'rxjs';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MonitoringService } from '../../../core/services/monitoring.service';
import {
  TimeRange,
  TimeRangeLabels,
  MetricsResponse,
  SystemStats,
  QueueStats,
  MetricType,
  formatBytes,
  formatUptime,
} from '../../../core/models/monitoring.model';

// Register Chart.js components
Chart.register(...registerables);

@Component({
  selector: 'app-metrics-dashboard',
  templateUrl: './metrics-dashboard.component.html',
  styleUrls: ['./metrics-dashboard.component.scss'],
  standalone: false
})
export class MetricsDashboardComponent implements OnInit, OnDestroy {
  private readonly monitoringService = inject(MonitoringService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroy$ = new Subject<void>();

  @ViewChild('queueChart') queueChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('messagesChart') messagesChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('connectionsChart') connectionsChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('cpuChart') cpuChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('memoryChart') memoryChartRef!: ElementRef<HTMLCanvasElement>;

  // Charts
  private queueChart?: Chart;
  private messagesChart?: Chart;
  private connectionsChart?: Chart;
  private cpuChart?: Chart;
  private memoryChart?: Chart;

  // State
  loading = false;
  autoRefresh = true;
  selectedTimeRange: TimeRange = TimeRange.ONE_HOUR;
  timeRanges = Object.values(TimeRange);
  timeRangeLabels = TimeRangeLabels;

  // Stats
  systemStats: SystemStats | null = null;
  queueStats: QueueStats | null = null;

  // Metrics data
  metricsData: MetricsResponse | null = null;

  ngOnInit(): void {
    this.loadMetrics();
    this.loadSystemStats();
    this.loadQueueStats();

    if (this.autoRefresh) {
      this.startAutoRefresh();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.destroyCharts();
  }

  private loadMetrics(): void {
    this.loading = true;

    this.monitoringService.getMetrics(this.selectedTimeRange).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (data) => {
        this.metricsData = data;
        this.updateCharts(data);
        this.loading = false;
      },
      error: (error) => {
        console.error('Failed to load metrics:', error);
        this.snackBar.open('Failed to load metrics', 'Close', {
          duration: 5000,
          panelClass: ['error-snackbar']
        });
        this.loading = false;
      }
    });
  }

  private loadSystemStats(): void {
    this.monitoringService.getSystemStats().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (stats) => {
        this.systemStats = stats;
      },
      error: (error) => {
        console.error('Failed to load system stats:', error);
      }
    });
  }

  private loadQueueStats(): void {
    this.monitoringService.getQueueStats().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (stats) => {
        this.queueStats = stats;
      },
      error: (error) => {
        console.error('Failed to load queue stats:', error);
      }
    });
  }

  private startAutoRefresh(): void {
    this.monitoringService.getMetricsStream(this.selectedTimeRange, 30000).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (data) => {
        this.metricsData = data;
        this.updateCharts(data);
      },
      error: (error) => {
        console.error('Auto-refresh failed:', error);
      }
    });

    // Also refresh stats
    this.monitoringService.getSystemStats().pipe(
      takeUntil(this.destroy$)
    ).subscribe(stats => this.systemStats = stats);

    this.monitoringService.getQueueStats().pipe(
      takeUntil(this.destroy$)
    ).subscribe(stats => this.queueStats = stats);
  }

  onTimeRangeChange(): void {
    this.loadMetrics();
  }

  toggleAutoRefresh(): void {
    this.autoRefresh = !this.autoRefresh;

    if (this.autoRefresh) {
      this.startAutoRefresh();
    } else {
      // Auto-refresh will stop due to takeUntil when component is destroyed
      // For now, just reload once
      this.loadMetrics();
    }
  }

  refreshMetrics(): void {
    this.loadMetrics();
    this.loadSystemStats();
    this.loadQueueStats();
  }

  exportMetrics(): void {
    this.monitoringService.exportMetrics(this.selectedTimeRange).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `metrics-${new Date().toISOString()}.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);

        this.snackBar.open('✓ Metrics exported successfully', 'Close', {
          duration: 3000,
          panelClass: ['success-snackbar']
        });
      },
      error: (error) => {
        this.snackBar.open('✗ Failed to export metrics', 'Close', {
          duration: 5000,
          panelClass: ['error-snackbar']
        });
      }
    });
  }

  private updateCharts(data: MetricsResponse): void {
    // Wait for view to initialize
    setTimeout(() => {
      this.updateQueueChart(data);
      this.updateMessagesChart(data);
      this.updateConnectionsChart(data);
      this.updateCpuChart(data);
      this.updateMemoryChart(data);
    }, 100);
  }

  private updateQueueChart(data: MetricsResponse): void {
    const queueSeries = data.series.find(s => s.metric === MetricType.QUEUE_SIZE);
    if (!queueSeries || !this.queueChartRef) return;

    const labels = queueSeries.dataPoints.map(dp => new Date(dp.timestamp).toLocaleTimeString());
    const values = queueSeries.dataPoints.map(dp => dp.value);

    if (this.queueChart) {
      this.queueChart.data.labels = labels;
      this.queueChart.data.datasets[0].data = values;
      this.queueChart.update();
    } else {
      const ctx = this.queueChartRef.nativeElement.getContext('2d');
      if (!ctx) return;

      this.queueChart = new Chart(ctx, {
        type: 'line',
        data: {
          labels,
          datasets: [{
            label: 'Queue Size',
            data: values,
            borderColor: 'rgb(59, 130, 246)',
            backgroundColor: 'rgba(59, 130, 246, 0.1)',
            fill: true,
            tension: 0.4,
          }]
        },
        options: this.getChartOptions('Queue Size Over Time', queueSeries.unit)
      });
    }
  }

  private updateMessagesChart(data: MetricsResponse): void {
    const sentSeries = data.series.find(s => s.metric === MetricType.MESSAGES_SENT);
    const receivedSeries = data.series.find(s => s.metric === MetricType.MESSAGES_RECEIVED);
    if ((!sentSeries && !receivedSeries) || !this.messagesChartRef) return;

    const labels = sentSeries?.dataPoints.map(dp => new Date(dp.timestamp).toLocaleTimeString()) || [];
    const sentValues = sentSeries?.dataPoints.map(dp => dp.value) || [];
    const receivedValues = receivedSeries?.dataPoints.map(dp => dp.value) || [];

    if (this.messagesChart) {
      this.messagesChart.data.labels = labels;
      this.messagesChart.data.datasets[0].data = sentValues;
      this.messagesChart.data.datasets[1].data = receivedValues;
      this.messagesChart.update();
    } else {
      const ctx = this.messagesChartRef.nativeElement.getContext('2d');
      if (!ctx) return;

      this.messagesChart = new Chart(ctx, {
        type: 'line',
        data: {
          labels,
          datasets: [
            {
              label: 'Messages Sent',
              data: sentValues,
              borderColor: 'rgb(34, 197, 94)',
              backgroundColor: 'rgba(34, 197, 94, 0.1)',
              fill: true,
              tension: 0.4,
            },
            {
              label: 'Messages Received',
              data: receivedValues,
              borderColor: 'rgb(168, 85, 247)',
              backgroundColor: 'rgba(168, 85, 247, 0.1)',
              fill: true,
              tension: 0.4,
            }
          ]
        },
        options: this.getChartOptions('Messages Over Time', 'messages')
      });
    }
  }

  private updateConnectionsChart(data: MetricsResponse): void {
    const connectionsSeries = data.series.find(s => s.metric === MetricType.CONNECTIONS);
    if (!connectionsSeries || !this.connectionsChartRef) return;

    const labels = connectionsSeries.dataPoints.map(dp => new Date(dp.timestamp).toLocaleTimeString());
    const values = connectionsSeries.dataPoints.map(dp => dp.value);

    if (this.connectionsChart) {
      this.connectionsChart.data.labels = labels;
      this.connectionsChart.data.datasets[0].data = values;
      this.connectionsChart.update();
    } else {
      const ctx = this.connectionsChartRef.nativeElement.getContext('2d');
      if (!ctx) return;

      this.connectionsChart = new Chart(ctx, {
        type: 'line',
        data: {
          labels,
          datasets: [{
            label: 'Active Connections',
            data: values,
            borderColor: 'rgb(251, 146, 60)',
            backgroundColor: 'rgba(251, 146, 60, 0.1)',
            fill: true,
            tension: 0.4,
          }]
        },
        options: this.getChartOptions('Connections Over Time', connectionsSeries.unit)
      });
    }
  }

  private updateCpuChart(data: MetricsResponse): void {
    const cpuSeries = data.series.find(s => s.metric === MetricType.CPU_USAGE);
    if (!cpuSeries || !this.cpuChartRef) return;

    const labels = cpuSeries.dataPoints.map(dp => new Date(dp.timestamp).toLocaleTimeString());
    const values = cpuSeries.dataPoints.map(dp => dp.value);

    if (this.cpuChart) {
      this.cpuChart.data.labels = labels;
      this.cpuChart.data.datasets[0].data = values;
      this.cpuChart.update();
    } else {
      const ctx = this.cpuChartRef.nativeElement.getContext('2d');
      if (!ctx) return;

      this.cpuChart = new Chart(ctx, {
        type: 'line',
        data: {
          labels,
          datasets: [{
            label: 'CPU Usage',
            data: values,
            borderColor: 'rgb(239, 68, 68)',
            backgroundColor: 'rgba(239, 68, 68, 0.1)',
            fill: true,
            tension: 0.4,
          }]
        },
        options: this.getChartOptions('CPU Usage Over Time', '%', 0, 100)
      });
    }
  }

  private updateMemoryChart(data: MetricsResponse): void {
    const memorySeries = data.series.find(s => s.metric === MetricType.MEMORY_USAGE);
    if (!memorySeries || !this.memoryChartRef) return;

    const labels = memorySeries.dataPoints.map(dp => new Date(dp.timestamp).toLocaleTimeString());
    const values = memorySeries.dataPoints.map(dp => dp.value);

    if (this.memoryChart) {
      this.memoryChart.data.labels = labels;
      this.memoryChart.data.datasets[0].data = values;
      this.memoryChart.update();
    } else {
      const ctx = this.memoryChartRef.nativeElement.getContext('2d');
      if (!ctx) return;

      this.memoryChart = new Chart(ctx, {
        type: 'line',
        data: {
          labels,
          datasets: [{
            label: 'Memory Usage',
            data: values,
            borderColor: 'rgb(99, 102, 241)',
            backgroundColor: 'rgba(99, 102, 241, 0.1)',
            fill: true,
            tension: 0.4,
          }]
        },
        options: this.getChartOptions('Memory Usage Over Time', '%', 0, 100)
      });
    }
  }

  private getChartOptions(title: string, unit: string, min?: number, max?: number): ChartConfiguration['options'] {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        title: {
          display: true,
          text: title,
          font: {
            size: 16,
            weight: 'bold'
          }
        },
        legend: {
          display: true,
          position: 'top',
        },
        tooltip: {
          mode: 'index',
          intersect: false,
          callbacks: {
            label: (context) => {
              const value = context.parsed.y ?? 0;
              return `${context.dataset.label}: ${value.toFixed(2)} ${unit}`;
            }
          }
        }
      },
      scales: {
        y: {
          beginAtZero: true,
          min,
          max,
          ticks: {
            callback: (value) => `${value} ${unit}`
          }
        },
        x: {
          ticks: {
            maxRotation: 45,
            minRotation: 45
          }
        }
      }
    };
  }

  private destroyCharts(): void {
    this.queueChart?.destroy();
    this.messagesChart?.destroy();
    this.connectionsChart?.destroy();
    this.cpuChart?.destroy();
    this.memoryChart?.destroy();
  }

  // Utility functions
  formatBytes = formatBytes;
  formatUptime = formatUptime;
}
