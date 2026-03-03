import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { DmarcApiService } from '../../services/dmarc-api.service';
import { DmarcSourceItem, DmarcSourcesResponse } from '../../models/dmarc.models';

@Component({
  selector: 'app-dmarc-sources',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dmarc-sources.component.html',
})
export class DmarcSourcesComponent implements OnInit, OnDestroy {
  private readonly api = inject(DmarcApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy$ = new Subject<void>();

  loading = true;
  domain = '';
  sourcesData: DmarcSourcesResponse | null = null;
  error: string | null = null;

  readonly skeletonRows = [1, 2, 3, 4, 5];

  ngOnInit(): void {
    this.domain = this.route.snapshot.queryParamMap.get('domain') ?? '';
    this.loadSources();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadSources(): void {
    this.loading = true;
    this.error = null;

    this.api
      .getSources({ domain: this.domain, days: 30 })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => {
          if (result.ok) {
            this.sourcesData = result.value;
          } else {
            this.error = 'Failed to load sources.';
          }
          this.loading = false;
        },
        error: () => {
          this.error = 'Unexpected error loading sources.';
          this.loading = false;
        },
      });
  }

  /**
   * Converts a 2-letter ISO 3166-1 alpha-2 country code to a flag emoji
   * by mapping each letter to its Regional Indicator Symbol.
   */
  flagEmoji(cc: string): string {
    if (!cc || cc.length < 2) return '';
    return String.fromCodePoint(
      ...cc
        .toUpperCase()
        .split('')
        .map(c => 0x1f1e6 - 65 + c.charCodeAt(0))
    );
  }

  typeBadgeClass(type: string | null): string {
    switch (type?.toLowerCase()) {
      case 'esp':
        return 'bg-blue-500/10 text-blue-600';
      case 'hosting':
        return 'bg-gray-500/10 text-gray-600';
      case 'enterprise':
        return 'bg-purple-500/10 text-purple-600';
      default:
        return 'bg-muted/60 text-muted-foreground';
    }
  }

  get items(): DmarcSourceItem[] {
    return this.sourcesData?.items ?? [];
  }
}
