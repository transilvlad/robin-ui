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
      case 'WARN':    return '⚠';
      case 'ERROR':   return '✕';
      default:        return '?';
    }
  }

  getStatusCardClass(status: string): string {
    switch (status) {
      case 'OK':      return 'border-success/20';
      case 'WARN':    return 'border-warning/20';
      case 'ERROR':   return 'border-error/20';
      default:        return 'border-muted';
    }
  }

  getStatusIconClass(status: string): string {
    switch (status) {
      case 'OK':      return 'text-white bg-success';
      case 'WARN':    return 'text-white bg-warning';
      case 'ERROR':   return 'text-white bg-error';
      default:        return 'text-muted-foreground bg-muted';
    }
  }

  getStatusBadgeClass(status: string): string {
    switch (status) {
      case 'OK':      return 'text-success bg-success/10';
      case 'WARN':    return 'text-warning bg-warning/10';
      case 'ERROR':   return 'text-error bg-error/10';
      default:        return 'text-muted-foreground bg-muted';
    }
  }
}
