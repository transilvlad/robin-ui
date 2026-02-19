import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { DomainService, DiscoveryResult, DnsRecord, CreateDomainRequest } from '@core/services/domain.service';
import { ProviderService, ProviderConfig } from '@core/services/provider.service';
import { NotificationService } from '@core/services/notification.service';
import { LoggingService } from '@core/services/logging.service';

@Component({
  selector: 'app-domain-wizard',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  template: `
    <div class="p-10 min-h-screen bg-background text-foreground">
      <div class="max-w-[800px] mx-auto space-y-8">
        
        <!-- Header -->
        <div class="flex flex-col gap-2">
          <h1 class="text-3xl font-bold tracking-tight">Add Domain</h1>
          <p class="text-muted-foreground">Configure a new or existing domain with Robin MTA</p>
        </div>

        <!-- Mode Selection -->
        <div class="card p-6 space-y-4" *ngIf="!mode">
             <h2 class="text-lg font-semibold">How would you like to add the domain?</h2>
             <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                 <button class="btn btn-outline h-auto py-8 flex flex-col items-center gap-3 hover:bg-muted/50 transition-colors" (click)="setMode('new')">
                    <span class="text-2xl">✨</span>
                    <span class="font-bold text-lg">Add New Domain</span>
                    <span class="text-sm text-muted-foreground text-center px-4">I want to register/configure a fresh domain. Records will be auto-generated.</span>
                 </button>
                 <button class="btn btn-outline h-auto py-8 flex flex-col items-center gap-3 hover:bg-muted/50 transition-colors" (click)="setMode('existing')">
                    <span class="text-2xl">🔍</span>
                    <span class="font-bold text-lg">Add Existing Domain</span>
                    <span class="text-sm text-muted-foreground text-center px-4">I have a domain with existing records. We'll discover and import them.</span>
                 </button>
             </div>
             <div class="flex justify-end pt-4">
                <a routerLink=".." class="btn btn-ghost">Cancel</a>
             </div>
        </div>

        <!-- Wizard Card -->
        <div class="card" *ngIf="mode">
          <div class="card-header flex justify-between items-start">
            <div>
                <h2 class="card-title">{{ mode === 'new' ? 'New Domain Configuration' : 'Existing Domain Discovery' }}</h2>
                <p class="card-description mt-1" *ngIf="mode === 'new'">Initialize domain and generate required DNS records</p>
                <p class="card-description mt-1" *ngIf="mode === 'existing' && step === 1">Enter domain to discover existing DNS records</p>
                <p class="card-description mt-1" *ngIf="mode === 'existing' && step === 2">Review discovered configuration</p>
            </div>
            <button class="btn btn-ghost btn-sm text-muted-foreground" (click)="reset()">Change Mode</button>
          </div>
          
          <div class="card-content">
            <!-- Loading -->
            <div *ngIf="loading" class="py-12 flex flex-col items-center justify-center space-y-4">
                <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                <p class="text-sm text-muted-foreground">
                    {{ mode === 'existing' ? 'Discovering DNS records...' : 'Creating domain...' }}
                </p>
            </div>

            <!-- Form -->
            <form [formGroup]="domainForm" (ngSubmit)="onSubmit()" class="space-y-6" *ngIf="!loading">
              
              <!-- Step 1: Input (Common) -->
              <div class="space-y-4" *ngIf="step === 1">
                <div class="space-y-2">
                    <label class="text-sm font-medium leading-none" for="domain">Domain Name</label>
                    <input class="input" id="domain" type="text" placeholder="e.g., example.com" formControlName="domain">
                    <p *ngIf="domainForm.get('domain')?.invalid && domainForm.get('domain')?.touched" class="text-destructive text-xs font-medium">
                      Please enter a valid domain name (e.g., domain.com).
                    </p>
                </div>
                
                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div class="space-y-2">
                      <label class="text-sm font-medium">DNS Provider (Recommended)</label>
                      <select class="input" formControlName="dnsProviderId">
                        <option [ngValue]="null">Manual / Public Lookup</option>
                        <option *ngFor="let p of dnsProviders" [value]="p.id">{{ p.name }} ({{ p.type }})</option>
                      </select>
                      <p class="text-xs text-muted-foreground">Linking your provider allows Robin to discover unproxied origin IPs.</p>
                    </div>
                    <div class="space-y-2">
                      <label class="text-sm font-medium">Registrar Provider</label>
                      <select class="input" formControlName="registrarProviderId">
                        <option [ngValue]="null">None / Manual</option>
                        <option *ngFor="let p of registrarProviders" [value]="p.id">{{ p.name }} ({{ p.type }})</option>
                      </select>
                    </div>
                </div>

                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div class="space-y-2">
                      <label class="text-sm font-medium">Email Provider (Optional)</label>
                      <select class="input" formControlName="emailProviderId">
                        <option [ngValue]="null">None</option>
                        <option *ngFor="let p of emailProviders" [value]="p.id">{{ p.name }}</option>
                      </select>
                    </div>
                </div>
              </div>

              <!-- Step 2: Review (Existing Only) -->
              <div *ngIf="mode === 'existing' && step === 2" class="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
                
                <div class="space-y-4">
                    <div class="flex items-center gap-2">
                        <h3 class="font-medium text-lg">Discovered Configuration</h3>
                        <span class="badge badge-outline text-xs">Auto-detected</span>
                    </div>
                    
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div class="p-4 bg-muted/50 rounded-lg border">
                            <span class="text-xs font-mono uppercase tracking-wider text-muted-foreground mb-2 block">SPF Configuration</span>
                            <div class="space-y-1">
                                <div class="text-sm"><span class="text-muted-foreground">Includes:</span> <span class="font-medium">{{ discoveredConfig?.configuration?.spfIncludes || 'None' }}</span></div>
                                <div class="text-sm"><span class="text-muted-foreground">SoftFail:</span> <span class="font-medium">{{ discoveredConfig?.configuration?.spfSoftFail ? 'Yes (~all)' : 'No (-all)' }}</span></div>
                            </div>
                        </div>
                         <div class="p-4 bg-muted/50 rounded-lg border">
                            <span class="text-xs font-mono uppercase tracking-wider text-muted-foreground mb-2 block">DMARC Configuration</span>
                            <div class="space-y-1">
                                <div class="text-sm"><span class="text-muted-foreground">Policy:</span> <span class="font-medium">{{ discoveredConfig?.configuration?.dmarcPolicy || 'none' }}</span></div>
                                <div class="text-sm"><span class="text-muted-foreground">Subdomain:</span> <span class="font-medium">{{ discoveredConfig?.configuration?.dmarcSubdomainPolicy || 'none' }}</span></div>
                                <div class="text-sm"><span class="text-muted-foreground">Percentage:</span> <span class="font-medium">{{ discoveredConfig?.configuration?.dmarcPercentage }}%</span></div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="space-y-2">
                    <h3 class="font-medium text-lg">Existing DNS Records</h3>
                    <div class="border rounded-md overflow-hidden bg-background">
                        <table class="w-full text-sm">
                            <thead class="bg-muted text-muted-foreground">
                                <tr>
                                    <th class="px-4 py-3 text-left font-medium">Type</th>
                                    <th class="px-4 py-3 text-left font-medium">Name</th>
                                    <th class="px-4 py-3 text-left font-medium">Content</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y">
                                <tr *ngFor="let rec of discoveredConfig?.discoveredRecords">
                                    <td class="px-4 py-3 font-medium">{{ rec.type }}</td>
                                    <td class="px-4 py-3">{{ rec.name }}</td>
                                    <td class="px-4 py-3 font-mono text-xs break-all text-muted-foreground">{{ rec.content }}</td>
                                </tr>
                                <tr *ngIf="!discoveredConfig?.discoveredRecords?.length">
                                    <td colspan="3" class="px-4 py-8 text-center text-muted-foreground">
                                        No relevant DNS records found.
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>

                <div class="space-y-4">
                    <div class="flex items-center justify-between">
                        <div class="flex items-center gap-2">
                            <h3 class="font-medium text-lg">Proposed DNS Records</h3>
                            <span class="badge bg-primary/10 text-primary border-primary/20 text-[10px]">ROBIN RECOMMENDED</span>
                        </div>
                        <div class="flex items-center gap-4">
                            <span class="text-xs text-muted-foreground">{{ selectedProposedIndices.size }} of {{ discoveredConfig?.proposedRecords?.length }} accepted</span>
                            <button type="button" class="text-xs font-medium text-primary hover:underline" (click)="toggleAllProposed()">
                                {{ selectedProposedIndices.size === discoveredConfig?.proposedRecords?.length ? 'Deselect All' : 'Accept All' }}
                            </button>
                        </div>
                    </div>
                    <p class="text-xs text-muted-foreground">Select the records you want Robin to configure. We've pre-selected recommended security records (SPF, DMARC, DKIM).</p>
                    <div class="border rounded-md overflow-hidden bg-background shadow-sm">
                        <table class="w-full text-sm">
                            <thead class="bg-muted/50 text-muted-foreground">
                                <tr>
                                    <th class="px-4 py-2 text-left font-medium w-[80px]">Accept</th>
                                    <th class="px-4 py-2 text-left font-medium">Type</th>
                                    <th class="px-4 py-2 text-left font-medium">Name</th>
                                    <th class="px-4 py-2 text-left font-medium">Purpose</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y">
                                <tr *ngFor="let rec of discoveredConfig?.proposedRecords; let i = index" 
                                    [class.bg-primary/5]="isProposedSelected(i)"
                                    class="hover:bg-muted/30 cursor-pointer transition-colors"
                                    (click)="toggleProposedRecord(i)">
                                    <td class="px-4 py-3 text-center">
                                        <input type="checkbox" 
                                               class="w-5 h-5 cursor-pointer rounded border-gray-300 text-primary focus:ring-primary"
                                               [checked]="isProposedSelected(i)" 
                                               (click)="$event.stopPropagation(); toggleProposedRecord(i)">
                                    </td>
                                    <td class="px-4 py-3 font-bold">{{ rec.type }}</td>
                                    <td class="px-4 py-3 font-mono text-xs">{{ rec.name }}</td>
                                    <td class="px-4 py-3 whitespace-nowrap">
                                        <span class="badge badge-outline text-[10px] uppercase tracking-tighter">{{ rec.purpose }}</span>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
                
                <div class="alert alert-info flex gap-2">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="mt-0.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>
                    <div class="text-sm">
                        <p class="font-medium">Ready to import?</p>
                        <p class="opacity-90">We will import these settings. On the next screen, you can review missing records (like DKIM) that we'll generate for you.</p>
                    </div>
                </div>

              </div>

              <!-- Actions -->
              <div class="pt-6 flex items-center gap-3 border-t">
                <button class="btn btn-primary btn-md min-w-[120px]"
                        type="submit" [disabled]="domainForm.invalid">
                  {{ (mode === 'existing' && step === 1) ? 'Discover Records' : 'Add Domain' }}
                </button>
                
                <button type="button" class="btn btn-outline btn-md" *ngIf="step === 2" (click)="step = 1">
                  Back
                </button>
                
                <a routerLink=".." class="btn btn-ghost btn-md" *ngIf="step === 1">
                  Cancel
                </a>
              </div>
            </form>
          </div>
        </div>

      </div>
    </div>
  `
})
export class DomainWizardComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  domainForm: FormGroup;

  mode: 'new' | 'existing' | null = null;
  step: number = 1;
  loading: boolean = false;
  discoveredConfig: DiscoveryResult | null = null;
  selectedProposedIndices: Set<number> = new Set();

  allProviders: ProviderConfig[] = [];
  dnsProviders: ProviderConfig[] = [];
  registrarProviders: ProviderConfig[] = [];
  emailProviders: ProviderConfig[] = [];

  constructor(
    private fb: FormBuilder,
    private domainService: DomainService,
    private providerService: ProviderService,
    private router: Router,
    private notificationService: NotificationService,
    private loggingService: LoggingService
  ) {
    this.domainForm = this.fb.group({
      domain: ['', [Validators.required, Validators.pattern('^[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]\.[a-zA-Z]{2,}$')]],
      dnsProviderId: [null],
      registrarProviderId: [null],
      emailProviderId: [null]
    });
  }

  ngOnInit(): void {
    this.providerService.getProviders(0, 100)
      .pipe(takeUntil(this.destroy$))
      .subscribe(data => {
        this.allProviders = data.content;
        this.dnsProviders = this.allProviders.filter(p => ['CLOUDFLARE', 'AWS_ROUTE53'].includes(p.type));
        this.registrarProviders = this.allProviders.filter(p => ['CLOUDFLARE', 'AWS_ROUTE53', 'GODADDY'].includes(p.type));
        this.emailProviders = this.allProviders.filter(p => p.type === 'EMAIL');
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  setMode(mode: 'new' | 'existing'): void {
    this.mode = mode;
    this.step = 1;
    this.discoveredConfig = null;
    this.selectedProposedIndices.clear();
    this.domainForm.reset({
        domain: '',
        dnsProviderId: null,
        registrarProviderId: null,
        emailProviderId: null
    });
  }

  reset(): void {
      this.mode = null;
      this.step = 1;
      this.discoveredConfig = null;
      this.selectedProposedIndices.clear();
  }

  onSubmit(): void {
    if (this.domainForm.invalid) return;

    if (this.mode === 'existing' && this.step === 1) {
       this.discover();
    } else {
       this.createDomain();
    }
  }

  toggleProposedRecord(index: number): void {
      if (this.selectedProposedIndices.has(index)) {
          this.selectedProposedIndices.delete(index);
      } else {
          this.selectedProposedIndices.add(index);
      }
  }

  isProposedSelected(index: number): boolean {
      return this.selectedProposedIndices.has(index);
  }

  toggleAllProposed(): void {
      if (this.selectedProposedIndices.size === this.discoveredConfig?.proposedRecords?.length) {
          this.selectedProposedIndices.clear();
      } else {
          this.discoveredConfig?.proposedRecords?.forEach((_, i) => this.selectedProposedIndices.add(i));
      }
  }

  discover(): void {
    const domain = this.domainForm.get('domain')?.value;
    const dnsProviderId = this.domainForm.get('dnsProviderId')?.value;
    if (!domain) return;

    this.loading = true;
    this.domainService.discover(domain, dnsProviderId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (res) => {
          this.discoveredConfig = res;
          this.loading = false;
          this.step = 2;
          // Default select all proposed
          this.selectedProposedIndices.clear();
          res.proposedRecords?.forEach((_, i) => this.selectedProposedIndices.add(i));
        },
        error: (err: HttpErrorResponse) => {
          this.loading = false;
          this.loggingService.error('Domain discovery failed', err);
          if (err.status === 500 && err.error?.message?.includes('already exists')) {
            this.notificationService.error(`The domain "${domain}" is already registered in Robin.`);
          } else {
            this.notificationService.error('Discovery failed. Please check the domain name and try again.');
          }
        }
      });
  }

  createDomain(): void {
    this.loading = true;

    const initialRecords: DnsRecord[] = [];
    if (this.mode === 'existing' && this.discoveredConfig) {
      // Start with discovered records
      initialRecords.push(...this.discoveredConfig.discoveredRecords);

      // Add selected proposed records only if they don't conflict with existing ones
      this.discoveredConfig.proposedRecords?.forEach((proposed, i) => {
        if (this.selectedProposedIndices.has(i)) {
          const alreadyExists = initialRecords.some(existing =>
            existing.type === proposed.type &&
            existing.name === proposed.name &&
            existing.content === proposed.content
          );

          if (!alreadyExists) {
            initialRecords.push(proposed);
          }
        }
      });
    }

    const payload: CreateDomainRequest = {
      ...this.domainForm.value,
      config: this.discoveredConfig?.configuration,
      initialRecords: initialRecords.length > 0 ? initialRecords : null
    };

    this.domainService.createDomain(payload)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (domain) => {
          this.router.navigate(['/domains', domain.id]);
        },
        error: (err: HttpErrorResponse) => {
          this.loading = false;
          this.loggingService.error('Failed to create domain', err);

          if (err.status === 500 && err.error?.message?.includes('already exists')) {
            this.notificationService.error(`The domain "${payload.domain}" is already registered in Robin.`);
          } else {
            this.notificationService.error('Failed to create domain. Please check your settings and try again.');
          }
        }
      });
  }
}
