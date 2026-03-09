import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { DmarcApiService } from '../../services/dmarc-api.service';
import { DmarcForensicPage, DmarcForensicItem } from '../../models/dmarc.models';

@Component({
  selector: 'app-dmarc-forensic',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dmarc-forensic.component.html',
})
export class DmarcForensicComponent implements OnInit, OnDestroy {
  private readonly api    = inject(DmarcApiService);
  private readonly route  = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  loading = true;
  page    = 0;
  pageData: DmarcForensicPage | null = null;
  selected: DmarcForensicItem | null = null;
  error: string | null = null;

  /** Whether backend has forensic storage enabled (null = unknown) */
  forensicStorageEnabled: boolean | null = null;

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.page = Number(params.get('page') ?? 0);
      this.loadPage();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadPage(): void {
    this.loading  = true;
    this.selected = null;
    this.error    = null;

    this.api.getForensicReports({ page: this.page, size: 20 })
      .pipe(takeUntil(this.destroy$))
      .subscribe(result => {
        this.loading = false;
        if (result.ok) {
          this.pageData = result.value;
          // If we got an empty first page, forensic storage may be disabled
          this.forensicStorageEnabled = this.page > 0 || result.value.items.length > 0 ? true : null;
        } else {
          this.error = 'Failed to load forensic reports.';
        }
      });
  }

  openDetail(item: DmarcForensicItem): void {
    this.selected = item;
  }

  closeDetail(): void {
    this.selected = null;
  }

  prevPage(): void {
    if (this.page > 0) this.navigate(this.page - 1);
  }

  nextPage(): void {
    if (this.pageData?.hasMore) this.navigate(this.page + 1);
  }

  private navigate(page: number): void {
    this.router.navigate([], { queryParams: { page }, queryParamsHandling: 'merge' });
  }

  authFailureBadgeClass(type: string): string {
    const map: Record<string, string> = {
      dkim: 'bg-blue-100 text-blue-800',
      spf:  'bg-purple-100 text-purple-800',
      dmarc:'bg-red-100 text-red-800',
    };
    return map[type?.toLowerCase()] ?? 'bg-muted/30 text-muted-foreground';
  }
}
