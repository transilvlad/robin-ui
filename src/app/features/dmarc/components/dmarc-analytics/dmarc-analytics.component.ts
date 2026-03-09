import { Component, OnInit, OnDestroy, inject, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { DmarcApiService } from '../../services/dmarc-api.service';
import { DmarcDailyAnalytics, DmarcCompliance } from '../../models/dmarc.models';

Chart.register(...registerables);

@Component({
  selector: 'app-dmarc-analytics',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './dmarc-analytics.component.html',
})
export class DmarcAnalyticsComponent implements OnInit, OnDestroy {
  private readonly api = inject(DmarcApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy$ = new Subject<void>();

  @ViewChild('trendChart')    trendChartRef!:    ElementRef<HTMLCanvasElement>;
  @ViewChild('dkimSpfChart')  dkimSpfChartRef!:  ElementRef<HTMLCanvasElement>;
  @ViewChild('volumeChart')   volumeChartRef!:   ElementRef<HTMLCanvasElement>;

  private trendChart?:   Chart;
  private dkimSpfChart?: Chart;
  private volumeChart?:  Chart;

  loading   = true;
  domain    = '';
  days      = 30;
  daily:    DmarcDailyAnalytics | null = null;
  compliance: DmarcCompliance | null = null;
  error:    string | null = null;

  readonly dayOptions = [7, 14, 30, 60, 90];

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.domain = params.get('domain') ?? '';
      this.days   = Number(params.get('days') ?? 30);
      this.load();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.trendChart?.destroy();
    this.dkimSpfChart?.destroy();
    this.volumeChart?.destroy();
  }

  load(): void {
    this.loading = true;
    this.error   = null;

    const range = { days: this.days };

    this.api.getDailyStats(this.domain, range).pipe(takeUntil(this.destroy$)).subscribe(result => {
      if (result.ok) {
        this.daily = result.value;
        setTimeout(() => this.renderCharts(), 50);
      } else {
        this.error = 'Failed to load daily analytics.';
      }
      this.loading = false;
    });

    this.api.getCompliance(this.domain, range).pipe(takeUntil(this.destroy$)).subscribe(result => {
      if (result.ok) this.compliance = result.value;
    });
  }

  private renderCharts(): void {
    const pts = this.daily?.points ?? [];
    const labels = pts.map(p => p.date.slice(0, 10));

    this.renderTrend(labels, pts);
    this.renderDkimSpf(labels, pts);
    this.renderVolume(labels, pts);
  }

  private renderTrend(labels: string[], pts: DmarcDailyAnalytics['points']): void {
    if (!this.trendChartRef) return;
    const pass = pts.map(p => p.dmarcPass);
    const fail = pts.map(p => p.dmarcFail);
    const pct  = pts.map(p => p.compliancePercent);

    if (this.trendChart) {
      this.trendChart.data.labels = labels;
      this.trendChart.data.datasets[0].data = pass;
      this.trendChart.data.datasets[1].data = fail;
      this.trendChart.data.datasets[2].data = pct;
      this.trendChart.update();
      return;
    }

    const ctx = this.trendChartRef.nativeElement.getContext('2d');
    if (!ctx) return;
    this.trendChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [
          { label: 'DMARC Pass',    data: pass, yAxisID: 'count', borderColor: 'rgb(34,197,94)',  backgroundColor: 'rgba(34,197,94,0.1)',  fill: true, tension: 0.4 },
          { label: 'DMARC Fail',    data: fail, yAxisID: 'count', borderColor: 'rgb(239,68,68)',   backgroundColor: 'rgba(239,68,68,0.1)',   fill: true, tension: 0.4 },
          { label: 'Compliance %',  data: pct,  yAxisID: 'pct',   borderColor: 'rgb(99,102,241)',  backgroundColor: 'rgba(99,102,241,0)',    borderDash: [4,3], tension: 0.4 },
        ]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: { title: { display: true, text: 'DMARC Pass / Fail Trend', font: { size: 15, weight: 'bold' } }, legend: { position: 'top' }, tooltip: { mode: 'index', intersect: false } },
        scales: {
          count: { type: 'linear', position: 'left',  beginAtZero: true, title: { display: true, text: 'Messages' } },
          pct:   { type: 'linear', position: 'right', beginAtZero: true, max: 100, title: { display: true, text: 'Compliance %' }, grid: { drawOnChartArea: false } },
          x: { ticks: { maxTicksLimit: 10 } },
        }
      } as ChartConfiguration['options']
    });
  }

  private renderDkimSpf(labels: string[], pts: DmarcDailyAnalytics['points']): void {
    if (!this.dkimSpfChartRef) return;
    const dkim = pts.map(p => p.dkimAligned);
    const spf  = pts.map(p => p.spfAligned);
    const tot  = pts.map(p => p.totalMessages);

    if (this.dkimSpfChart) {
      this.dkimSpfChart.data.labels = labels;
      this.dkimSpfChart.data.datasets[0].data = dkim;
      this.dkimSpfChart.data.datasets[1].data = spf;
      this.dkimSpfChart.data.datasets[2].data = tot;
      this.dkimSpfChart.update();
      return;
    }

    const ctx = this.dkimSpfChartRef.nativeElement.getContext('2d');
    if (!ctx) return;
    this.dkimSpfChart = new Chart(ctx, {
      type: 'bar',
      data: {
        labels,
        datasets: [
          { label: 'DKIM Aligned',      data: dkim, backgroundColor: 'rgba(59,130,246,0.75)', stack: 'auth' },
          { label: 'SPF Aligned',       data: spf,  backgroundColor: 'rgba(168,85,247,0.75)', stack: 'auth' },
          { label: 'Total Messages',    data: tot,  backgroundColor: 'rgba(100,116,139,0.3)',  stack: 'total' },
        ]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: { title: { display: true, text: 'DKIM / SPF Alignment Breakdown', font: { size: 15, weight: 'bold' } }, legend: { position: 'top' }, tooltip: { mode: 'index', intersect: false } },
        scales: { x: { stacked: true, ticks: { maxTicksLimit: 10 } }, y: { beginAtZero: true, stacked: false } }
      } as ChartConfiguration['options']
    });
  }

  private renderVolume(labels: string[], pts: DmarcDailyAnalytics['points']): void {
    if (!this.volumeChartRef) return;
    const vol = pts.map(p => p.totalMessages);

    if (this.volumeChart) {
      this.volumeChart.data.labels = labels;
      this.volumeChart.data.datasets[0].data = vol;
      this.volumeChart.update();
      return;
    }

    const ctx = this.volumeChartRef.nativeElement.getContext('2d');
    if (!ctx) return;
    this.volumeChart = new Chart(ctx, {
      type: 'bar',
      data: {
        labels,
        datasets: [{ label: 'Total Messages', data: vol, backgroundColor: 'rgba(99,102,241,0.7)', borderColor: 'rgb(99,102,241)', borderWidth: 1 }]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: { title: { display: true, text: 'Daily Message Volume', font: { size: 15, weight: 'bold' } }, legend: { display: false }, tooltip: { mode: 'index', intersect: false } },
        scales: { x: { ticks: { maxTicksLimit: 10 } }, y: { beginAtZero: true } }
      } as ChartConfiguration['options']
    });
  }

  get compliancePct(): number { return this.compliance?.compliancePercent ?? 0; }
  get complianceClass(): string {
    const p = this.compliancePct;
    if (p >= 95) return 'text-green-600';
    if (p >= 75) return 'text-orange-500';
    return 'text-destructive';
  }
}
