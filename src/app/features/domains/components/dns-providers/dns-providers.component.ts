import { Component, OnInit } from '@angular/core';
import { DnsProviderService, CreateDnsProviderRequest } from '../../services/dns-provider.service';
import { DnsProvider } from '../../models/domain.models';

@Component({
  selector: 'app-dns-providers',
  templateUrl: './dns-providers.component.html',
  styleUrls: ['./dns-providers.component.scss'],
  standalone: false,
})
export class DnsProvidersComponent implements OnInit {
  providers: DnsProvider[] = [];
  loading = false;
  testing: number | null = null;
  error: string | null = null;
  successMessage: string | null = null;

  showForm = false;
  editingProvider: DnsProvider | null = null;

  formData: CreateDnsProviderRequest = {
    name: '',
    type: 'CLOUDFLARE',
    credentials: '',
  };

  providerTypes: Array<{ value: 'CLOUDFLARE' | 'AWS_ROUTE53'; label: string }> = [
    { value: 'CLOUDFLARE',  label: 'Cloudflare' },
    { value: 'AWS_ROUTE53', label: 'AWS Route 53' },
  ];

  constructor(private dnsProviderService: DnsProviderService) {}

  ngOnInit(): void {
    this.loadProviders();
  }

  loadProviders(): void {
    this.loading = true;
    this.error = null;
    this.dnsProviderService.getProviders().subscribe(result => {
      this.loading = false;
      if (result.ok) {
        this.providers = result.value;
      } else {
        this.error = 'Failed to load DNS providers';
      }
    });
  }

  openAddForm(): void {
    this.editingProvider = null;
    this.formData = { name: '', type: 'CLOUDFLARE', credentials: '' };
    this.showForm = true;
  }

  editProvider(provider: DnsProvider): void {
    this.editingProvider = provider;
    this.formData = { name: provider.name, type: provider.type, credentials: '' };
    this.showForm = true;
  }

  cancelForm(): void {
    this.showForm = false;
    this.editingProvider = null;
  }

  saveProvider(): void {
    if (!this.formData.name || !this.formData.credentials) return;

    if (this.editingProvider) {
      this.dnsProviderService.updateProvider(this.editingProvider.id, this.formData).subscribe(result => {
        if (result.ok) {
          this.providers = this.providers.map(p => p.id === result.value.id ? result.value : p);
          this.cancelForm();
        } else {
          this.error = 'Failed to update provider';
        }
      });
    } else {
      this.dnsProviderService.createProvider(this.formData).subscribe(result => {
        if (result.ok) {
          this.providers = [...this.providers, result.value];
          this.cancelForm();
        } else {
          this.error = 'Failed to create provider';
        }
      });
    }
  }

  deleteProvider(provider: DnsProvider): void {
    if (!confirm(`Delete DNS provider "${provider.name}"?`)) return;
    this.dnsProviderService.deleteProvider(provider.id).subscribe(result => {
      if (result.ok) {
        this.providers = this.providers.filter(p => p.id !== provider.id);
      } else {
        this.error = 'Failed to delete provider';
      }
    });
  }

  testConnection(provider: DnsProvider): void {
    this.testing = provider.id;
    this.successMessage = null;
    this.dnsProviderService.testConnection(provider.id).subscribe(result => {
      this.testing = null;
      if (result.ok) {
        this.successMessage = result.value.success
          ? `Connection to "${provider.name}" successful`
          : `Connection test failed: ${result.value.message}`;
      } else {
        this.error = 'Connection test failed';
      }
    });
  }

  getProviderLabel(type: string): string {
    return this.providerTypes.find(t => t.value === type)?.label ?? type;
  }

  getCredentialsLabel(type: 'CLOUDFLARE' | 'AWS_ROUTE53'): string {
    return type === 'CLOUDFLARE' ? 'API Token' : 'Access Key JSON';
  }

  getCredentialsPlaceholder(type: 'CLOUDFLARE' | 'AWS_ROUTE53'): string {
    return type === 'CLOUDFLARE' ? 'Cloudflare API token' : '{"accessKeyId":"...","secretAccessKey":"..."}';
  }
}
