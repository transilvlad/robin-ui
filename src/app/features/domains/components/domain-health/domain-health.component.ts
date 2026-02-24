import { Component, Input, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { DomainHealthService } from '../../services/domain-health.service';
import { DomainHealth } from '../../models/domain.models';

@Component({
  selector: 'app-domain-health',
  templateUrl: './domain-health.component.html',
  styleUrls: ['./domain-health.component.scss'],
  standalone: false,
})
export class DomainHealthComponent implements OnInit, OnChanges {
  @Input() domainId!: number;

  healthChecks: DomainHealth[] = [];
  loading = false;
  verifying = false;
  error: string | null = null;

  constructor(private domainHealthService: DomainHealthService) {}

  ngOnInit(): void {
    this.loadHealth();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['domainId'] && !changes['domainId'].firstChange) {
      this.loadHealth();
    }
  }

  loadHealth(): void {
    if (!this.domainId) return;
    this.loading = true;
    this.error = null;
    this.domainHealthService.getHealth(this.domainId).subscribe(result => {
      this.loading = false;
      if (result.ok) {
        this.healthChecks = result.value;
      } else {
        this.error = 'Failed to load health checks';
      }
    });
  }

  runVerification(): void {
    this.verifying = true;
    this.domainHealthService.triggerVerification(this.domainId).subscribe(result => {
      this.verifying = false;
      if (result.ok) {
        this.healthChecks = result.value;
      } else {
        this.error = 'Verification failed';
      }
    });
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'OK':      return '✓';
      case 'WARN':    return '!';
      case 'ERROR':   return '✗';
      default:        return '?';
    }
  }

  getStatusCardClass(status: string): string {
    switch (status) {
      case 'OK':      return 'border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-900/20';
      case 'WARN':    return 'border-yellow-200 dark:border-yellow-800 bg-yellow-50 dark:bg-yellow-900/20';
      case 'ERROR':   return 'border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20';
      default:        return 'border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800';
    }
  }

  getStatusIconClass(status: string): string {
    switch (status) {
      case 'OK':      return 'text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/40';
      case 'WARN':    return 'text-yellow-600 dark:text-yellow-400 bg-yellow-100 dark:bg-yellow-900/40';
      case 'ERROR':   return 'text-red-600 dark:text-red-400 bg-red-100 dark:bg-red-900/40';
      default:        return 'text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-700';
    }
  }
}
