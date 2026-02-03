import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { DomainService } from '@core/services/domain.service';
import { ProviderService } from '@core/services/provider.service';

@Component({
  selector: 'app-domain-wizard',
  standalone: false,
  template: `
    <div class="p-10 min-h-screen bg-background text-foreground">
      <div class="max-w-[800px] mx-auto space-y-8">
        
        <!-- Header -->
        <div class="flex flex-col gap-2">
          <h1 class="text-3xl font-bold tracking-tight">Add New Domain</h1>
          <p class="text-muted-foreground">Enter the domain name you want to configure with Robin MTA</p>
        </div>

        <!-- Wizard Card -->
        <div class="card">
          <div class="card-header">
            <h2 class="card-title">Domain Configuration</h2>
            <p class="card-description">This will initialize the domain and generate required DNS records</p>
          </div>
          <div class="card-content">
            <form [formGroup]="domainForm" (ngSubmit)="onSubmit()" class="space-y-6">
              
              <div class="space-y-2">
                <label class="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70" for="domain">
                  Domain Name
                </label>
                <input class="input"
                       id="domain" type="text" placeholder="e.g., example.com" formControlName="domain">
                <p *ngIf="domainForm.get('domain')?.invalid && domainForm.get('domain')?.touched" class="text-destructive text-xs font-medium">
                  Please enter a valid domain name (e.g., domain.com).
                </p>
              </div>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div class="space-y-2">
                  <label class="text-sm font-medium">DNS Provider</label>
                  <select class="input" formControlName="dnsProviderId">
                    <option [ngValue]="null">Manual (Display records only)</option>
                    <option *ngFor="let p of providers" [value]="p.id">{{ p.name }} ({{ p.type }})</option>
                  </select>
                </div>
                <div class="space-y-2">
                  <label class="text-sm font-medium">Registrar Provider</label>
                  <select class="input" formControlName="registrarProviderId">
                    <option [ngValue]="null">None / Manual</option>
                    <option *ngFor="let p of providers" [value]="p.id">{{ p.name }} ({{ p.type }})</option>
                  </select>
                </div>
              </div>

              <div class="pt-4 flex items-center gap-3">
                <button class="btn-primary btn-md"
                        type="submit" [disabled]="domainForm.invalid">
                  Add Domain
                </button>
                <a routerLink=".." class="btn-outline btn-md">
                  Cancel
                </a>
              </div>
            </form>
          </div>
        </div>

        <div class="alert alert-default bg-blue-500/5 border-blue-500/20 text-blue-700 dark:text-blue-400">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="h-4 w-4"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>
          <div class="ml-2">
            <h5 class="font-medium leading-none tracking-tight mb-1 text-sm">Pro Tip</h5>
            <p class="text-xs opacity-90">After adding the domain, visit the "Security" tab to enable DNSSEC, MTA-STS, and DANE for maximum email deliverability.</p>
          </div>
        </div>

      </div>
    </div>
  `
})
export class DomainWizardComponent implements OnInit {
  domainForm: FormGroup;
  providers: any[] = [];

  constructor(
    private fb: FormBuilder,
    private domainService: DomainService,
    private providerService: ProviderService,
    private router: Router
  ) {
    this.domainForm = this.fb.group({
      domain: ['', [Validators.required, Validators.pattern('^[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]\.[a-zA-Z]{2,}$')]],
      dnsProviderId: [null],
      registrarProviderId: [null]
    });
  }

  ngOnInit(): void {
    this.providerService.getProviders(0, 100).subscribe(data => {
      this.providers = data.content;
    });
  }

  onSubmit(): void {
    if (this.domainForm.valid) {
      this.domainService.createDomain(this.domainForm.value).subscribe({
        next: (domain) => {
          this.router.navigate(['/domains', domain.id]);
        },
        error: (err) => {
          console.error('Error creating domain', err);
          alert('Failed to create domain. It might already exist.');
        }
      });
    }
  }
}