import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { DmarcApiService } from '../../services/dmarc-api.service';
import { DmarcReportDetail } from '../../models/dmarc.models';

@Component({
  selector: 'app-dmarc-report-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dmarc-report-detail.component.html',
})
export class DmarcReportDetailComponent implements OnInit, OnDestroy {
  private readonly api = inject(DmarcApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  loading = true;
  report: DmarcReportDetail | null = null;
  error: string | null = null;

  readonly skeletonRows = [1, 2, 3, 4, 5];

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) {
      this.error = 'Invalid report ID.';
      this.loading = false;
      return;
    }
    this.loadReport(parseInt(idParam, 10));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadReport(id: number): void {
    this.loading = true;
    this.error = null;

    this.api
      .getReport(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => {
          if (result.ok) {
            this.report = result.value;
          } else {
            this.error = 'Report not found or could not be loaded.';
          }
          this.loading = false;
        },
        error: () => {
          this.error = 'Unexpected error loading report.';
          this.loading = false;
        },
      });
  }

  goBack(): void {
    this.router.navigate(['/dmarc/reports']);
  }

  formatDate(isoString: string): string {
    return isoString.slice(0, 10);
  }
}
