import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DomainService } from '../../services/domain.service';
import { Domain, DomainLookupResult, DnsProvider } from '../../models/domain.models';

@Component({
  selector: 'app-domain-list',
  templateUrl: './domain-list.component.html',
  styleUrls: ['./domain-list.component.scss'],
  standalone: false,
})
export class DomainListComponent implements OnInit {
  domains: Domain[] = [];
  loading = false;
  showAddModal = false;
  newDomainName = '';
  error: string | null = null;

  // Add-domain modal state
  lookupLoading = false;
  lookupError: string | null = null;
  lookupResult: DomainLookupResult | null = null;
  selectedDnsProviderId: number | null = null;
  selectedNsProviderId: number | null = null;
  isExistingDomain = true;

  constructor(
    private domainService: DomainService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadDomains();
  }

  loadDomains(): void {
    this.loading = true;
    this.error = null;
    this.domainService.getDomains().subscribe(result => {
      this.loading = false;
      if (result.ok) {
        this.domains = result.value.content;
      } else {
        this.error = 'Failed to load domains';
      }
    });
  }

  viewDomain(domain: Domain): void {
    this.router.navigate(['/domains', domain.id]);
  }

  openAddModal(): void {
    this.showAddModal = true;
    this.newDomainName = '';
    this.lookupResult = null;
    this.lookupError = null;
    this.lookupLoading = false;
    this.selectedDnsProviderId = null;
    this.selectedNsProviderId = null;
    this.isExistingDomain = true;
  }

  closeAddModal(): void {
    this.showAddModal = false;
    this.lookupResult = null;
  }

  detectDns(): void {
    const domain = this.newDomainName.trim();
    if (!domain) return;
    this.lookupLoading = true;
    this.lookupError = null;
    this.lookupResult = null;
    this.domainService.lookupDomain(domain).subscribe(result => {
      this.lookupLoading = false;
      if (result.ok) {
        this.lookupResult = result.value;
        // Pre-select suggested provider for both DNS and NS management
        const sugId = result.value.suggestedProvider?.id ?? null;
        this.selectedDnsProviderId = sugId;
        this.selectedNsProviderId = sugId;
      } else {
        this.lookupError = 'Could not look up DNS records. You can still add the domain manually.';
      }
    });
  }

  skipDetection(): void {
    this.lookupResult = { domain: this.newDomainName.trim(), nsRecords: [], mxRecords: [],
      spfRecords: [], dmarcRecords: [], mtaStsRecords: [], smtpTlsRecords: [],
      detectedNsProviderType: 'UNKNOWN', suggestedProvider: null, availableProviders: [] };
  }

  addDomain(): void {
    const domain = this.newDomainName.trim();
    if (!domain) return;
    this.domainService.createDomain(
      domain,
      this.selectedDnsProviderId ?? undefined,
      this.selectedNsProviderId ?? undefined
    ).subscribe(result => {
      if (result.ok) {
        this.domains = [...this.domains, result.value];
        this.closeAddModal();
      } else {
        this.error = 'Failed to create domain';
      }
    });
  }

  deleteDomain(domain: Domain, event: Event): void {
    event.stopPropagation();
    if (!confirm(`Delete domain ${domain.domain}?`)) return;
    this.domainService.deleteDomain(domain.id).subscribe(result => {
      if (result.ok) {
        this.domains = this.domains.filter(d => d.id !== domain.id);
      }
    });
  }

  getStatusClass(status?: string): string {
    switch (status) {
      case 'ACTIVE':  return 'badge-success';
      case 'ERROR':   return 'badge-destructive';
      case 'PENDING_VERIFICATION': return 'badge-warning';
      default:        return 'badge-secondary';
    }
  }

  getProviderLabel(type: string): string {
    switch (type) {
      case 'CLOUDFLARE':  return 'Cloudflare';
      case 'AWS_ROUTE53': return 'AWS Route 53';
      default:            return 'Unknown';
    }
  }

  hasDetectedRecords(): boolean {
    if (!this.lookupResult) return false;
    const r = this.lookupResult;
    return r.mxRecords.length > 0 || r.spfRecords.length > 0 ||
           r.dmarcRecords.length > 0 || r.nsRecords.length > 0;
  }
}
