import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { DmarcApiService } from '../../services/dmarc-api.service';
import { DmarcReportItem, DmarcReportsPage } from '../../models/dmarc.models';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-dmarc-reports',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dmarc-reports.component.html',
})
export class DmarcReportsComponent implements OnInit, OnDestroy {
  private readonly api = inject(DmarcApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  loading = true;
  domain = '';
  page = 0;
  reportsPage: DmarcReportsPage | null = null;
  error: string | null = null;

  readonly skeletonRows = [1, 2, 3, 4, 5];

  ngOnInit(): void {
    this.domain = this.route.snapshot.queryParamMap.get('domain') ?? '';
    const pageParam = this.route.snapshot.queryParamMap.get('page');
    this.page = pageParam ? parseInt(pageParam, 10) : 0;
    this.loadReports();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadReports(): void {
    this.loading = true;
    this.error = null;

    this.api
      .getReports({ domain: this.domain, page: this.page, size: PAGE_SIZE })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => {
          if (result.ok) {
            this.reportsPage = result.value;
          } else {
            this.error = 'Failed to load reports.';
          }
          this.loading = false;
        },
        error: () => {
          this.error = 'Unexpected error loading reports.';
          this.loading = false;
        },
      });
  }

  goToPage(newPage: number): void {
    this.page = newPage;
    this.router.navigate([], {
      queryParams: { domain: this.domain, page: newPage },
      queryParamsHandling: 'merge',
    });
    this.loadReports();
  }

  openReport(item: DmarcReportItem): void {
    this.router.navigate(['/dmarc/reports', item.id]);
  }

  formatDate(isoString: string): string {
    return isoString.slice(0, 10);
  }

  get hasMore(): boolean {
    return this.reportsPage?.hasMore ?? false;
  }

  get isFirstPage(): boolean {
    return this.page === 0;
  }
}
