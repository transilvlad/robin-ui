import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DmarcLicenseService } from '../../services/dmarc-license.service';
import { DmarcLicense } from '../../models/dmarc.models';

@Component({
  selector: 'app-dmarc-license',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dmarc-license.component.html',
})
export class DmarcLicenseComponent implements OnInit {
  protected readonly licenseService = inject(DmarcLicenseService);

  loading = true;
  license: DmarcLicense | null = null;

  ngOnInit(): void {
    this.licenseService.check().then(() => {
      this.license = this.licenseService.license();
      this.loading = false;
    });
  }

  get daysUntilExpiry(): number | null {
    if (!this.license?.expires_at) return null;
    const ms = new Date(this.license.expires_at).getTime() - Date.now();
    return Math.max(0, Math.floor(ms / 86_400_000));
  }

  get expiryClass(): string {
    const d = this.daysUntilExpiry;
    if (d === null) return '';
    if (d <= 7)  return 'text-destructive';
    if (d <= 30) return 'text-orange-500';
    return 'text-green-600';
  }

  get statusBadgeClass(): string {
    const map: Record<string, string> = {
      active:  'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
      expired: 'bg-red-100   text-red-800   dark:bg-red-900/30   dark:text-red-400',
      missing: 'bg-muted/40  text-muted-foreground',
      invalid: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-400',
    };
    return map[this.license?.status ?? 'missing'] ?? map['missing'];
  }

  featureLabel(key: PropertyKey): string {
    const normalized = String(key);
    const labels: Record<string, string> = {
      max_domains:      'Max Domains',
      forensic_reports: 'Forensic Reports',
      api_access:       'API Access',
      threat_detection: 'Threat Detection',
    };
    return labels[normalized] ?? normalized;
  }
}
