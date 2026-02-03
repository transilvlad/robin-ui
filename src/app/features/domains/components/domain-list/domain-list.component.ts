import { Component, OnInit } from '@angular/core';
import { DomainService, Domain, Page } from '../../../../core/services/domain.service';

@Component({
  selector: 'app-domain-list',
  standalone: false,
  template: `
    <div class="p-10 min-h-screen bg-background text-foreground">
      <div class="max-w-[1400px] mx-auto space-y-8">
        
        <!-- Header -->
        <div class="flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div>
            <h1 class="text-3xl font-bold tracking-tight">Domains</h1>
            <p class="text-muted-foreground mt-1">Manage email domains, DNS records, and security protocols</p>
          </div>
          <div class="flex items-center gap-3">
            <a routerLink="new" class="btn-primary btn-md">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="mr-2"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
              Add Domain
            </a>
          </div>
        </div>

        <!-- Domains Table Card -->
        <div class="card">
          <div class="card-header">
            <h2 class="card-title">Configured Domains</h2>
            <p class="card-description">A list of all domains managed by this Robin instance</p>
          </div>
          <div class="card-content">
            <div class="rounded-md border">
              <table class="table">
                <thead class="table-header bg-muted/50">
                  <tr class="table-row">
                    <th class="table-head">Domain</th>
                    <th class="table-head">Status</th>
                    <th class="table-head">DNS Provider</th>
                    <th class="table-head">Registrar</th>
                    <th class="table-head">Renewal Date</th>
                    <th class="table-head text-right">Actions</th>
                  </tr>
                </thead>
                <tbody class="table-body">
                  <tr *ngFor="let domain of domains?.content" class="table-row hover:bg-muted/30">
                    <td class="table-cell font-medium">{{ domain.domain }}</td>
                    <td class="table-cell">
                      <span class="badge"
                        [ngClass]="{
                          'bg-green-500/10 text-green-600 border-green-500/20': domain.status === 'ACTIVE' || domain.status === 'VERIFIED',
                          'bg-yellow-500/10 text-yellow-600 border-yellow-500/20': domain.status === 'PENDING',
                          'bg-red-500/10 text-red-600 border-red-500/20': domain.status === 'FAILED'
                        }">
                        {{ domain.status }}
                      </span>
                    </td>
                    <td class="table-cell text-muted-foreground">{{ domain.dnsProviderType }}</td>
                    <td class="table-cell text-muted-foreground">{{ domain.registrarProviderType }}</td>
                    <td class="table-cell text-muted-foreground">{{ domain.renewalDate || '-' }}</td>
                    <td class="table-cell text-right">
                      <div class="flex items-center justify-end gap-2">
                        <a [routerLink]="[domain.id]" class="btn-ghost btn-sm h-8 w-8 p-0" title="View Details">
                          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"></path><circle cx="12" cy="12" r="3"></circle></svg>
                        </a>
                        <button (click)="deleteDomain(domain)" class="btn-ghost btn-sm h-8 w-8 p-0 text-destructive hover:text-destructive hover:bg-destructive/10" title="Delete Domain">
                          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"></path><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>
                        </button>
                      </div>
                    </td>
                  </tr>
                  <tr *ngIf="domains?.content?.length === 0">
                    <td colspan="6" class="table-cell text-center py-10 text-muted-foreground italic">
                      No domains found. Click "Add Domain" to get started.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class DomainListComponent implements OnInit {
  domains: Page<Domain> | null = null;

  constructor(private domainService: DomainService) {}

  ngOnInit(): void {
    this.loadDomains();
  }

  loadDomains(): void {
    this.domainService.getDomains().subscribe(data => {
      this.domains = data;
    });
  }

  deleteDomain(domain: Domain): void {
    if (confirm(`Are you sure you want to delete ${domain.domain}?`)) {
      this.domainService.deleteDomain(domain.id!).subscribe(() => {
        this.loadDomains();
      });
    }
  }
}
