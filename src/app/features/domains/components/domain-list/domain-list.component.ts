import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DomainService } from '../../services/domain.service';
import { DnsProviderService, CreateDnsProviderRequest } from '../../services/dns-provider.service';
import { Domain, DomainLookupResult, DnsProvider } from '../../models/domain.models';

interface NewProviderForm {
  name: string;
  type: 'CLOUDFLARE' | 'AWS_ROUTE53';
  cfApiToken: string;
  r53AccessKeyId: string;
  r53SecretAccessKey: string;
  r53Region: string;
}

@Component({
  selector: 'app-domain-list',
  templateUrl: './domain-list.component.html',
  styleUrls: ['./domain-list.component.scss'],
  standalone: false,
})
export class DomainListComponent implements OnInit {
  domains: Domain[] = [];
  loading = false;
  error: string | null = null;

  // ── Modal state ────────────────────────────────────────────────
  showAddModal = false;
  newDomainName = '';

  lookupLoading = false;
  lookupError: string | null = null;
  lookupResult: DomainLookupResult | null = null;

  availableProviders: DnsProvider[] = [];
  selectedDnsProviderId: number | null = null;
  selectedNsProviderId: number | null = null;

  // Inline new-provider form: 'dns' | 'ns' | null indicates which dropdown triggered it
  newProviderTarget: 'dns' | 'ns' | null = null;
  newProviderLoading = false;
  newProviderError: string | null = null;
  newProviderForm: NewProviderForm = this.emptyProviderForm();

  constructor(
    private domainService: DomainService,
    private dnsProviderService: DnsProviderService,
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

  // ── Modal open/close ────────────────────────────────────────────
  openAddModal(): void {
    this.showAddModal = true;
    this.newDomainName = '';
    this.lookupResult = null;
    this.lookupError = null;
    this.lookupLoading = false;
    this.availableProviders = [];
    this.selectedDnsProviderId = null;
    this.selectedNsProviderId = null;
    this.newProviderTarget = null;
    this.newProviderError = null;
  }

  closeAddModal(): void {
    this.showAddModal = false;
    this.lookupResult = null;
    this.newProviderTarget = null;
  }

  // ── DNS detection ───────────────────────────────────────────────
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
        this.availableProviders = result.value.availableProviders;
        const sugId = result.value.suggestedProvider?.id ?? null;
        this.selectedDnsProviderId = sugId;
        this.selectedNsProviderId = sugId;
      } else {
        this.lookupError = 'Could not look up DNS records. You can still add the domain manually.';
      }
    });
  }

  skipDetection(): void {
    this.lookupResult = {
      domain: this.newDomainName.trim(), nsRecords: [], mxRecords: [],
      spfRecords: [], dmarcRecords: [], mtaStsRecords: [], smtpTlsRecords: [],
      detectedNsProviderType: 'UNKNOWN', suggestedProvider: null, availableProviders: [], allRecords: [],
    };
    this.dnsProviderService.getProviders().subscribe(r => {
      if (r.ok) this.availableProviders = r.value;
    });
  }

  resetDetection(): void {
    this.lookupResult = null;
    this.lookupError = null;
    this.newProviderTarget = null;
  }

  // ── Inline new-provider form ────────────────────────────────────
  openNewProviderForm(target: 'dns' | 'ns'): void {
    this.newProviderTarget = target;
    this.newProviderForm = this.emptyProviderForm();
    this.newProviderError = null;
  }

  cancelNewProvider(): void {
    this.newProviderTarget = null;
    this.newProviderError = null;
  }

  saveNewProvider(): void {
    const f = this.newProviderForm;
    if (!f.name || !f.type) return;

    let credentials: Record<string, string>;
    if (f.type === 'CLOUDFLARE') {
      if (!f.cfApiToken) { this.newProviderError = 'API Token is required for Cloudflare.'; return; }
      credentials = { apiToken: f.cfApiToken };
    } else {
      if (!f.r53AccessKeyId || !f.r53SecretAccessKey) {
        this.newProviderError = 'Access Key ID and Secret are required for Route 53.'; return;
      }
      credentials = { accessKeyId: f.r53AccessKeyId, secretAccessKey: f.r53SecretAccessKey, region: f.r53Region || 'us-east-1' };
    }

    const req: CreateDnsProviderRequest = { name: f.name, type: f.type, credentials: JSON.stringify(credentials) };
    this.newProviderLoading = true;
    this.newProviderError = null;
    this.dnsProviderService.createProvider(req).subscribe(result => {
      this.newProviderLoading = false;
      if (result.ok) {
        const created = result.value;
        this.availableProviders = [...this.availableProviders, created];
        if (this.newProviderTarget === 'dns') this.selectedDnsProviderId = created.id;
        if (this.newProviderTarget === 'ns')  this.selectedNsProviderId  = created.id;
        this.cancelNewProvider();
      } else {
        this.newProviderError = 'Failed to create provider. Check your credentials and try again.';
      }
    });
  }

  // ── Add domain ──────────────────────────────────────────────────
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

  // ── Helpers ─────────────────────────────────────────────────────
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

  private emptyProviderForm(): NewProviderForm {
    return { name: '', type: 'CLOUDFLARE', cfApiToken: '', r53AccessKeyId: '', r53SecretAccessKey: '', r53Region: 'us-east-1' };
  }
}
