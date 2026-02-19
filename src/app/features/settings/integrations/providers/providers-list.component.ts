import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ProviderService, ProviderConfig } from '@core/services/provider.service';
import { Page } from '@core/services/domain.service';

@Component({
  selector: 'app-providers-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="p-10 min-h-screen bg-background text-foreground">
      <div class="max-w-[1400px] mx-auto space-y-8">
        
        <!-- Header -->
        <div class="flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div>
            <h1 class="text-3xl font-bold tracking-tight">Providers</h1>
            <p class="text-muted-foreground mt-1">Manage credentials for external services (DNS, Registrars)</p>
          </div>
          <button (click)="openCreateModal()" class="btn-primary btn-md">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="mr-2"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
            Add Provider
          </button>
        </div>

        <!-- Providers Grid -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <div *ngFor="let provider of providers?.content" class="card hover:border-primary/50 transition-colors">
            <div class="card-header pb-4">
              <div class="flex items-center justify-between">
                <div class="h-10 w-10 rounded-full bg-muted flex items-center justify-center">
                  <!-- Icons based on type -->
                  <svg *ngIf="provider.type === 'CLOUDFLARE'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="h-6 w-6 text-orange-500"><path d="M5.5 8.5 9 12l-3.5 3.5L2 12l3.5-3.5Z"/><path d="m12 2 3.5 3.5L12 9 8.5 5.5 12 2Z"/><path d="M18.5 8.5 22 12l-3.5 3.5L15 12l3.5-3.5Z"/><path d="m12 15 3.5 3.5L12 22l-3.5-3.5L12 15Z"/></svg>
                  <svg *ngIf="provider.type === 'AWS_ROUTE53'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="h-6 w-6 text-yellow-500"><path d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z"/><path d="m12 15-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z"/><path d="M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0"/><path d="M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5"/></svg>
                  <svg *ngIf="provider.type === 'GODADDY'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="h-6 w-6 text-green-500"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/></svg>
                  <svg *ngIf="provider.type === 'EMAIL'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="h-6 w-6 text-blue-500"><rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
                </div>
                <div class="flex gap-1">
                    <button (click)="openEditModal(provider)" class="btn-ghost btn-icon text-muted-foreground hover:text-primary" aria-label="Edit provider">
                      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg>
                    </button>
                    <button (click)="deleteProvider(provider)" class="btn-ghost btn-icon text-muted-foreground hover:text-destructive" aria-label="Delete provider">
                      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18"></path><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path></svg>
                    </button>
                </div>
              </div>
              <h3 class="card-title text-lg mt-4">{{ provider.name }}</h3>
              <p class="text-xs text-muted-foreground">{{ provider.type }}</p>
            </div>
            <div class="card-content pt-0">
              <div class="flex items-center gap-2 text-xs text-muted-foreground">
                <span class="inline-block h-2 w-2 rounded-full bg-green-500"></span>
                Active
              </div>
            </div>
          </div>
          
          <!-- Empty State -->
          <div *ngIf="providers?.content?.length === 0" class="col-span-full py-12 text-center border-2 border-dashed rounded-lg border-muted">
            <h3 class="text-lg font-medium text-muted-foreground">No providers configured</h3>
            <p class="text-sm text-muted-foreground/70 mt-1">Add a provider to start syncing your domains.</p>
            <button (click)="openCreateModal()" class="btn-outline btn-sm mt-4">Add Provider</button>
          </div>
        </div>

        <!-- Modal -->
        <div *ngIf="isModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm">
          <div class="bg-card border shadow-lg rounded-lg max-w-lg w-full p-6 space-y-6">
            <h2 class="text-2xl font-bold">{{ editingProvider ? 'Edit Provider' : 'Add Provider' }}</h2>
            
            <form [formGroup]="providerForm" (ngSubmit)="onSubmit()" class="space-y-4">
              <div class="space-y-2">
                <label class="text-sm font-medium">Name</label>
                <input class="input" formControlName="name" placeholder="My Cloudflare">
              </div>
              
              <div class="space-y-2">
                <label class="text-sm font-medium">Type</label>
                <select class="input" formControlName="type" (change)="onTypeChange()">
                  <option value="CLOUDFLARE">Cloudflare</option>
                  <option value="AWS_ROUTE53">AWS Route53</option>
                  <option value="GODADDY">GoDaddy</option>
                  <option value="EMAIL">Email Provider</option>
                </select>
              </div>

              <!-- Dynamic Fields -->
              <div [ngSwitch]="providerForm.get('type')?.value" class="space-y-4 pt-2 border-t">
                
                <!-- Cloudflare -->
                <div *ngSwitchCase="'CLOUDFLARE'" class="space-y-4">
                  <div class="space-y-2">
                    <label class="text-sm font-medium">API Token</label>
                    <input class="input" formControlName="apiToken" type="password" [placeholder]="editingProvider ? 'Leave empty to keep current' : 'Cloudflare API Token'">
                  </div>
                  <div class="space-y-2">
                    <label class="text-sm font-medium">Zone ID (Optional Default)</label>
                    <input class="input" formControlName="zoneId" placeholder="Zone ID">
                  </div>
                </div>

                <!-- AWS -->
                <div *ngSwitchCase="'AWS_ROUTE53'" class="space-y-4">
                  <div class="space-y-2">
                    <label class="text-sm font-medium">Access Key ID</label>
                    <input class="input" formControlName="accessKey" placeholder="AKIA...">
                  </div>
                  <div class="space-y-2">
                    <label class="text-sm font-medium">Secret Access Key</label>
                    <input class="input" formControlName="secretKey" type="password" [placeholder]="editingProvider ? 'Leave empty to keep current' : 'Secret Key'">
                  </div>
                </div>

                 <!-- GoDaddy -->
                 <div *ngSwitchCase="'GODADDY'" class="space-y-4">
                  <div class="space-y-2">
                    <label class="text-sm font-medium">API Key</label>
                    <input class="input" formControlName="apiKey" placeholder="Key">
                  </div>
                  <div class="space-y-2">
                    <label class="text-sm font-medium">API Secret</label>
                    <input class="input" formControlName="apiSecret" type="password" [placeholder]="editingProvider ? 'Leave empty to keep current' : 'Secret'">
                  </div>
                </div>

                <!-- Email -->
                <div *ngSwitchCase="'EMAIL'" class="space-y-4">
                    <p class="text-sm text-muted-foreground italic">Email providers only require a name at this moment.</p>
                </div>

              </div>

              <div class="flex justify-end gap-3 pt-4">
                <button type="button" (click)="isModalOpen = false" class="btn-ghost">Cancel</button>
                <button type="submit" class="btn-primary" [disabled]="providerForm.invalid">
                    {{ editingProvider ? 'Update Provider' : 'Save Provider' }}
                </button>
              </div>
            </form>
          </div>
        </div>

      </div>
    </div>
  `
})
export class ProvidersListComponent implements OnInit, OnDestroy {
  providers: Page<ProviderConfig> | null = null;
  isModalOpen = false;
  editingProvider: ProviderConfig | null = null;
  providerForm: FormGroup;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private providerService: ProviderService,
    private fb: FormBuilder
  ) {
    this.providerForm = this.fb.group({
      name: ['', Validators.required],
      type: ['CLOUDFLARE', Validators.required],
      // Dynamic fields
      apiToken: [''],
      zoneId: [''],
      accessKey: [''],
      secretKey: [''],
      apiKey: [''],
      apiSecret: ['']
    });
  }

  ngOnInit(): void {
    this.loadProviders();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadProviders(): void {
    this.providerService.getProviders()
      .pipe(takeUntil(this.destroy$))
      .subscribe(data => this.providers = data);
  }

  openCreateModal(): void {
    this.editingProvider = null;
    this.isModalOpen = true;
    this.providerForm.reset({ type: 'CLOUDFLARE' });
  }

  openEditModal(provider: ProviderConfig): void {
      this.editingProvider = provider;
      this.isModalOpen = true;
      
      const creds = provider.credentials || {};
      
      this.providerForm.reset({
          name: provider.name,
          type: provider.type,
          apiToken: '', // Keep empty for sensitive
          zoneId: creds['zoneId'] || '', // Pre-fill non-sensitive
          accessKey: creds['accessKey'] || '',
          secretKey: '',
          apiKey: creds['apiKey'] || '',
          apiSecret: ''
      });
  }

  onTypeChange(): void {
    // Logic to reset validators based on type could go here
  }

  onSubmit(): void {
    if (this.providerForm.valid) {
      const val = this.providerForm.value;
      const config: ProviderConfig = {
        name: val.name,
        type: val.type,
        credentials: {}
      };

      // Map fields to credentials map
      const creds: Record<string, string> = {};
      if (val.type === 'CLOUDFLARE') {
        if (val.apiToken) creds['apiToken'] = val.apiToken;
        if (val.zoneId) creds['zoneId'] = val.zoneId;
      } else if (val.type === 'AWS_ROUTE53') {
        if (val.accessKey) creds['accessKey'] = val.accessKey;
        if (val.secretKey) creds['secretKey'] = val.secretKey;
      } else if (val.type === 'GODADDY') {
        if (val.apiKey) creds['apiKey'] = val.apiKey;
        if (val.apiSecret) creds['apiSecret'] = val.apiSecret;
      }
      
      config.credentials = creds;

      const request = this.editingProvider
        ? this.providerService.updateProvider(this.editingProvider.id!, config)
        : this.providerService.createProvider(config);

      request
        .pipe(takeUntil(this.destroy$))
        .subscribe(() => {
          this.isModalOpen = false;
          this.loadProviders();
        });
    }
  }

  deleteProvider(provider: ProviderConfig): void {
    if (confirm(`Delete provider ${provider.name}?`)) {
      this.providerService.deleteProvider(provider.id!)
        .pipe(takeUntil(this.destroy$))
        .subscribe(() => this.loadProviders());
    }
  }
}