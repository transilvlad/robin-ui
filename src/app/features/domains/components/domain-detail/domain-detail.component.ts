import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup } from '@angular/forms';
import { DomainService, Domain } from '@core/services/domain.service';
import { ProviderService, ProviderConfig } from '@core/services/provider.service';

@Component({
  selector: 'app-domain-detail',
  standalone: false,
  template: `
    <div class="p-10 min-h-screen bg-background text-foreground" *ngIf="domain">
      <div class="max-w-[1400px] mx-auto space-y-8">
        
        <!-- Header -->
        <div class="flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div class="flex items-center gap-4">
            <a routerLink=".." class="btn-ghost btn-sm h-10 w-10 p-0 rounded-full" title="Back to list">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline></svg>
            </a>
            <div>
              <h1 class="text-3xl font-bold tracking-tight">{{ domain.domain }}</h1>
              <div class="flex items-center gap-2 mt-1">
                <span class="badge" [ngClass]="{
                  'bg-green-500/10 text-green-600 border-green-500/20': domain.status === 'ACTIVE' || domain.status === 'VERIFIED',
                  'bg-yellow-500/10 text-yellow-600 border-yellow-500/20': domain.status === 'PENDING',
                  'bg-red-500/10 text-red-600 border-red-500/20': domain.status === 'FAILED'
                }">{{ domain.status }}</span>
                <span class="text-xs text-muted-foreground">ID: {{ domain.id }}</span>
              </div>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <button (click)="sync()" class="btn-primary btn-md">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="mr-2"><path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"></path><path d="M21 3v5h-5"></path><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"></path><path d="M8 16H3v5"></path></svg>
              Sync Configuration
            </button>
          </div>
        </div>

        <!-- Status Cards -->
        <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div class="card">
            <div class="card-header pb-2">
              <h3 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">DNS Provider</h3>
            </div>
            <div class="card-content">
              <div class="text-2xl font-bold">{{ domain.dnsProviderType }}</div>
              <p class="text-xs text-muted-foreground mt-1">{{ domain.dnsProvider?.name || 'No specific provider linked' }}</p>
            </div>
          </div>
          <div class="card">
            <div class="card-header pb-2">
              <h3 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">Registrar</h3>
            </div>
            <div class="card-content">
              <div class="text-2xl font-bold">{{ domain.registrarProviderType }}</div>
              <p class="text-xs text-muted-foreground mt-1" *ngIf="domain.renewalDate">Renews: {{ domain.renewalDate }}</p>
              <p class="text-xs text-muted-foreground mt-1" *ngIf="!domain.renewalDate">{{ domain.registrarProvider?.name || 'No specific provider linked' }}</p>
            </div>
          </div>
          <div class="card">
            <div class="card-header pb-2">
              <h3 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">Email Protocol Health</h3>
            </div>
            <div class="card-content flex gap-2">
              <div class="h-2 flex-1 rounded-full bg-green-500" title="SPF Active"></div>
              <div class="h-2 flex-1 rounded-full bg-green-500" title="DKIM Active"></div>
              <div class="h-2 flex-1 rounded-full bg-green-500" title="DMARC Active"></div>
              <div class="h-2 flex-1 rounded-full" [ngClass]="domain.mtaStsEnabled ? 'bg-green-500' : 'bg-muted'" title="MTA-STS"></div>
              <div class="h-2 flex-1 rounded-full" [ngClass]="domain.daneEnabled ? 'bg-green-500' : 'bg-muted'" title="DANE"></div>
            </div>
          </div>
        </div>

        <!-- Tabs -->
        <div class="card">
          <div class="border-b border-border bg-muted/30 px-6 pt-2">
            <nav class="-mb-px flex gap-6">
              <button (click)="activeTab = 'overview'" 
                      [class.border-primary]="activeTab === 'overview'" 
                      [class.text-primary]="activeTab === 'overview'" 
                      class="border-b-2 border-transparent py-4 font-medium text-sm transition-colors hover:text-primary">Overview</button>
              <button (click)="activeTab = 'dns'" 
                      [class.border-primary]="activeTab === 'dns'" 
                      [class.text-primary]="activeTab === 'dns'" 
                      class="border-b-2 border-transparent py-4 font-medium text-sm text-muted-foreground transition-colors hover:text-primary">DNS Records</button>
              <button (click)="activeTab = 'security'" 
                      [class.border-primary]="activeTab === 'security'" 
                      [class.text-primary]="activeTab === 'security'" 
                      class="border-b-2 border-transparent py-4 font-medium text-sm text-muted-foreground transition-colors hover:text-primary">Security Protocols</button>
              <button (click)="activeTab = 'settings'" 
                      [class.border-primary]="activeTab === 'settings'" 
                      [class.text-primary]="activeTab === 'settings'" 
                      class="border-b-2 border-transparent py-4 font-medium text-sm text-muted-foreground transition-colors hover:text-primary">Settings</button>
            </nav>
          </div>
          <div class="card-content pt-6">
            
            <!-- Overview Tab -->
            <div *ngIf="activeTab === 'overview'" class="space-y-6">
               <div>
                 <h3 class="text-lg font-semibold mb-2">Domain Configuration</h3>
                 <p class="text-muted-foreground">Robin is configured to handle outgoing and incoming mail for <strong>{{ domain.domain }}</strong>. Ensure your MX records are correctly pointed to this server.</p>
               </div>
               
               <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                 <div class="p-4 border rounded-lg bg-muted/10">
                   <h4 class="font-medium text-sm mb-1">DKIM Selector</h4>
                   <p class="text-2xl font-mono">{{ domain.dkimSelectorPrefix }}</p>
                 </div>
                 <div class="p-4 border rounded-lg bg-muted/10">
                   <h4 class="font-medium text-sm mb-1">MTA-STS Mode</h4>
                   <p class="text-2xl font-mono">{{ domain.mtaStsMode }}</p>
                 </div>
               </div>
            </div>

            <!-- DNS Tab -->
            <div *ngIf="activeTab === 'dns'" class="space-y-4">
              <div class="flex justify-between items-center">
                <h3 class="text-lg font-semibold">Generated Records</h3>
                <span class="text-xs text-muted-foreground italic">Records are updated locally every time you sync.</span>
              </div>
              <div class="rounded-md border overflow-hidden">
                <table class="table">
                  <thead class="table-header bg-muted/50">
                    <tr class="table-row">
                      <th class="table-head w-20">Type</th>
                      <th class="table-head">Host/Name</th>
                      <th class="table-head">Value/Content</th>
                      <th class="table-head">Purpose</th>
                    </tr>
                  </thead>
                  <tbody class="table-body">
                    <tr *ngFor="let record of records" class="table-row hover:bg-muted/30">
                      <td class="table-cell font-bold">{{ record.type }}</td>
                      <td class="table-cell font-mono text-xs">{{ record.name }}</td>
                      <td class="table-cell font-mono text-xs">
                        <div class="max-w-md truncate" [title]="record.content">{{ record.content }}</div>
                      </td>
                      <td class="table-cell whitespace-nowrap">
                        <span class="badge badge-outline text-[10px] uppercase">{{ record.purpose }}</span>
                      </td>
                    </tr>
                    <tr *ngIf="records.length === 0">
                      <td colspan="4" class="table-cell text-center py-10 text-muted-foreground">
                        No records generated yet. Click "Sync" to generate them.
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Security Tab -->
            <div *ngIf="activeTab === 'security'" class="space-y-6">
              <div>
                <h3 class="text-lg font-semibold mb-2">Advanced Security Protocols</h3>
                <p class="text-muted-foreground text-sm">Enable these protocols to protect your domain from spoofing and ensure secure transport.</p>
              </div>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                <!-- DNSSEC -->
                <div class="flex items-start justify-between p-4 border rounded-lg transition-colors hover:bg-muted/5">
                  <div class="space-y-1">
                    <div class="flex items-center gap-2">
                      <h4 class="font-semibold">DNSSEC</h4>
                      <span class="badge" [ngClass]="domain.dnssecEnabled ? 'bg-green-500/10 text-green-600 border-green-500/20' : 'bg-muted text-muted-foreground'">
                        {{ domain.dnssecEnabled ? 'Active' : 'Inactive' }}
                      </span>
                    </div>
                    <p class="text-xs text-muted-foreground">Protects against DNS spoofing by signing your records.</p>
                  </div>
                  <button class="btn-outline btn-sm">Configure</button>
                </div>

                <!-- MTA-STS -->
                <div class="flex items-start justify-between p-4 border rounded-lg transition-colors hover:bg-muted/5">
                  <div class="space-y-1">
                    <div class="flex items-center gap-2">
                      <h4 class="font-semibold">MTA-STS</h4>
                      <span class="badge" [ngClass]="domain.mtaStsEnabled ? 'bg-green-500/10 text-green-600 border-green-500/20' : 'bg-muted text-muted-foreground'">
                        {{ domain.mtaStsEnabled ? domain.mtaStsMode : 'Inactive' }}
                      </span>
                    </div>
                    <p class="text-xs text-muted-foreground">Enforces TLS for incoming emails via HTTPS policy.</p>
                  </div>
                  <button class="btn-outline btn-sm">Configure</button>
                </div>
              </div>
            </div>

            <!-- Settings Tab -->
            <div *ngIf="activeTab === 'settings'" class="space-y-8">
              <form [formGroup]="settingsForm" (ngSubmit)="onSaveSettings()" class="space-y-6">
                <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <div class="space-y-4">
                    <h4 class="text-sm font-bold uppercase tracking-wider text-muted-foreground">Providers</h4>
                    
                    <div class="space-y-2">
                      <label class="text-sm font-medium">DNS Provider Type</label>
                      <select class="input" formControlName="dnsProviderType">
                        <option value="MANUAL">Manual</option>
                        <option value="CLOUDFLARE">Cloudflare</option>
                        <option value="AWS_ROUTE53">AWS Route53</option>
                      </select>
                    </div>

                    <div class="space-y-2" *ngIf="settingsForm.get('dnsProviderType')?.value !== 'MANUAL'">
                      <label class="text-sm font-medium">Link DNS Provider Config</label>
                      <select class="input" formControlName="dnsProviderId">
                        <option [ngValue]="null">Select a provider...</option>
                        <option *ngFor="let p of providers" [value]="p.id">{{ p.name }} ({{ p.type }})</option>
                      </select>
                    </div>

                    <div class="space-y-2">
                      <label class="text-sm font-medium">Registrar Provider Type</label>
                      <select class="input" formControlName="registrarProviderType">
                        <option value="NONE">None / Manual</option>
                        <option value="CLOUDFLARE">Cloudflare</option>
                        <option value="AWS_ROUTE53">AWS Route53</option>
                        <option value="GODADDY">GoDaddy</option>
                      </select>
                    </div>

                    <div class="space-y-2" *ngIf="settingsForm.get('registrarProviderType')?.value !== 'NONE'">
                      <label class="text-sm font-medium">Link Registrar Provider Config</label>
                      <select class="input" formControlName="registrarProviderId">
                        <option [ngValue]="null">Select a provider...</option>
                        <option *ngFor="let p of providers" [value]="p.id">{{ p.name }} ({{ p.type }})</option>
                      </select>
                    </div>
                  </div>

                  <div class="space-y-4">
                    <h4 class="text-sm font-bold uppercase tracking-wider text-muted-foreground">Protocols</h4>
                    
                    <div class="flex items-center justify-between p-3 border rounded-lg">
                      <div class="space-y-0.5">
                        <label class="text-sm font-medium">Enable MTA-STS</label>
                        <p class="text-xs text-muted-foreground">Enforce TLS for incoming mail</p>
                      </div>
                      <input type="checkbox" formControlName="mtaStsEnabled">
                    </div>

                    <div class="space-y-2" *ngIf="settingsForm.get('mtaStsEnabled')?.value">
                      <label class="text-sm font-medium">MTA-STS Mode</label>
                      <select class="input" formControlName="mtaStsMode">
                        <option value="TESTING">Testing (Log violations)</option>
                        <option value="ENFORCE">Enforce (Reject non-TLS)</option>
                      </select>
                    </div>

                    <div class="flex items-center justify-between p-3 border rounded-lg">
                      <div class="space-y-0.5">
                        <label class="text-sm font-medium">Enable DANE</label>
                        <p class="text-xs text-muted-foreground">Use TLSA records</p>
                      </div>
                      <input type="checkbox" formControlName="daneEnabled">
                    </div>
                  </div>
                </div>

                <div class="pt-6 border-t flex justify-end">
                  <button type="submit" class="btn-primary" [disabled]="settingsForm.pristine">Save Changes</button>
                </div>
              </form>

              <div class="pt-8 border-t">
                <h4 class="text-sm font-bold uppercase tracking-wider text-destructive mb-4">Danger Zone</h4>
                <div class="p-4 border border-destructive/20 bg-destructive/5 rounded-lg flex items-center justify-between">
                  <div>
                    <p class="font-medium">Delete Domain</p>
                    <p class="text-xs text-muted-foreground">This will remove the domain and all its records from Robin. This action cannot be undone.</p>
                  </div>
                  <button (click)="onDelete()" class="btn-destructive btn-sm">Delete Domain</button>
                </div>
              </div>
            </div>

          </div>
        </div>

      </div>
    </div>
  `
})
export class DomainDetailComponent implements OnInit {
  domain: Domain | null = null;
  records: any[] = [];
  providers: ProviderConfig[] = [];
  activeTab = 'overview';
  settingsForm: FormGroup;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private domainService: DomainService,
    private providerService: ProviderService,
    private fb: FormBuilder
  ) {
    this.settingsForm = this.fb.group({
      dnsProviderType: ['MANUAL'],
      dnsProviderId: [null],
      registrarProviderType: ['NONE'],
      registrarProviderId: [null],
      mtaStsEnabled: [false],
      mtaStsMode: ['NONE'],
      daneEnabled: [false]
    });
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) {
      this.loadDomain(id);
    }
    this.loadProviders();
  }

  loadDomain(id: number): void {
    this.domainService.getDomain(id).subscribe(data => {
      this.domain = data;
      this.loadRecords(id);
      this.updateForm(data);
    });
  }

  updateForm(domain: Domain): void {
    this.settingsForm.patchValue({
      dnsProviderType: domain.dnsProviderType,
      dnsProviderId: domain.dnsProviderId,
      registrarProviderType: domain.registrarProviderType,
      registrarProviderId: domain.registrarProviderId,
      mtaStsEnabled: domain.mtaStsEnabled,
      mtaStsMode: domain.mtaStsMode,
      daneEnabled: domain.daneEnabled
    });
  }

  loadRecords(id: number): void {
    this.domainService.getRecords(id).subscribe(data => {
      this.records = data;
    });
  }

  loadProviders(): void {
    this.providerService.getProviders(0, 100).subscribe(data => {
      this.providers = data.content;
    });
  }

  sync(): void {
    if (this.domain?.id) {
      this.domainService.syncDomain(this.domain.id).subscribe(() => {
        alert('Sync started');
        this.loadDomain(this.domain!.id!);
      });
    }
  }

  onSaveSettings(): void {
    if (this.domain?.id) {
      const update: Partial<Domain> = this.settingsForm.value;
      const payload = {
        ...update,
        dnsProvider: update.dnsProviderId ? { id: update.dnsProviderId } : null,
        registrarProvider: update.registrarProviderId ? { id: update.registrarProviderId } : null
      };

      this.domainService.updateDomain(this.domain.id, payload as Domain).subscribe(() => {
        alert('Settings saved');
        this.loadDomain(this.domain!.id!);
      });
    }
  }

  onDelete(): void {
    if (this.domain?.id && confirm('Are you sure you want to delete ' + this.domain.domain + '? This action cannot be undone.')) {
      this.domainService.deleteDomain(this.domain.id).subscribe(() => {
        this.router.navigate(['/domains']);
      });
    }
  }
}