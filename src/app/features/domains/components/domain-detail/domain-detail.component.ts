import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import { DomainService, Domain, DnsRecord } from '@core/services/domain.service';
import { ProviderService, ProviderConfig } from '@core/services/provider.service';
import { NotificationService } from '@core/services/notification.service';
import { MatDialog } from '@angular/material/dialog';
import { DnsRecordDialogComponent } from '../dns-record-dialog/dns-record-dialog.component';
import { ConfirmationDialogComponent } from '@shared/components/confirmation-dialog/confirmation-dialog.component';
import { DnssecDialogComponent } from '../dnssec-dialog/dnssec-dialog.component';

@Component({
  selector: 'app-domain-detail',
  standalone: false,
  templateUrl: './domain-detail.component.html'
})
export class DomainDetailComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  domain: Domain | null = null;
  records: DnsRecord[] = [];
  providers: ProviderConfig[] = [];
  activeTab = 'overview';
  settingsForm: FormGroup;
  isSyncing = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private domainService: DomainService,
    private providerService: ProviderService,
    private fb: FormBuilder,
    private dialog: MatDialog,
    private notificationService: NotificationService
  ) {
    this.settingsForm = this.fb.group({
      dnsProviderType: ['MANUAL'],
      dnsProviderId: [null],
      registrarProviderType: ['NONE'],
      registrarProviderId: [null],
      emailProviderId: [null],
      mtaStsEnabled: [false],
      mtaStsMode: ['NONE'],
      daneEnabled: [false],
      // DMARC
      dmarcPolicy: ['none'],
      dmarcSubdomainPolicy: ['none'],
      dmarcPercentage: [100],
      dmarcAlignment: ['r'],
      dmarcReportingEmail: [''],
      // SPF
      spfIncludes: [''],
      spfSoftFail: [true]
    });
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) {
      this.loadDomain(id);
    }
    this.loadProviders();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private isDomainValid(domain: Domain | null): domain is Domain {
    return domain !== null && domain.id !== undefined;
  }

  loadDomain(id: number): void {
    this.domainService.getDomain(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe(data => {
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
      registrarProviderId: domain.registrarProvider?.id || domain.registrarProviderId || null,
      emailProviderId: domain.emailProvider?.id || domain.emailProviderId || null,
      mtaStsEnabled: domain.mtaStsEnabled,
      mtaStsMode: domain.mtaStsMode,
      daneEnabled: domain.daneEnabled,
      dmarcPolicy: domain.dmarcPolicy || 'none',
      dmarcSubdomainPolicy: domain.dmarcSubdomainPolicy || 'none',
      dmarcPercentage: domain.dmarcPercentage || 100,
      dmarcAlignment: domain.dmarcAlignment || 'r',
      dmarcReportingEmail: domain.dmarcReportingEmail || '',
      spfIncludes: domain.spfIncludes || '',
      spfSoftFail: domain.spfSoftFail ?? true
    });
  }

  loadRecords(id: number): void {
    this.domainService.getRecords(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe(data => {
        this.records = data.sort((a, b) => a.type.localeCompare(b.type));
      });
  }

  loadProviders(): void {
    this.providerService.getProviders(0, 100)
      .pipe(takeUntil(this.destroy$))
      .subscribe(data => {
        this.providers = data.content;
      });
  }

  sync(): void {
    if (this.domain?.id && !this.isSyncing) {
      this.isSyncing = true;
      this.domainService.syncDomain(this.domain.id)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => this.isSyncing = false)
        )
        .subscribe({
          next: () => {
            this.notificationService.success('Sync completed successfully');
            if (this.isDomainValid(this.domain)) {
              this.loadDomain(this.domain.id);
            }
          },
          error: (err) => {
            this.notificationService.error('Sync failed: ' + (err.error?.message || err.message || 'Unknown error'));
          }
        });
    }
  }

  onSaveSettings(): void {
    if (this.domain?.id) {
      const update: Partial<Domain> = this.settingsForm.value;
      const payload = {
        ...update,
        dnsProvider: update.dnsProviderId ? { id: update.dnsProviderId } : null,
        registrarProvider: update.registrarProviderId ? { id: update.registrarProviderId } : null,
        emailProvider: update.emailProviderId ? { id: update.emailProviderId } : null
      };

      this.domainService.updateDomain(this.domain.id, payload as Domain)
        .pipe(takeUntil(this.destroy$))
        .subscribe(() => {
          this.notificationService.success('Settings saved');
          if (this.isDomainValid(this.domain)) {
            this.loadDomain(this.domain.id);
          }
        });
    }
  }

  onDelete(): void {
    if (this.domain?.id) {
      const dialogRef = this.dialog.open(ConfirmationDialogComponent, {
        data: {
          title: 'Delete Domain',
          message: 'Are you sure you want to delete ' + this.domain.domain + '? This action cannot be undone.',
          confirmText: 'Delete',
          confirmColor: 'warn'
        }
      });

      dialogRef.afterClosed()
        .pipe(takeUntil(this.destroy$))
        .subscribe(confirmed => {
          if (confirmed && this.domain?.id) {
            this.domainService.deleteDomain(this.domain.id)
              .pipe(takeUntil(this.destroy$))
              .subscribe(() => {
                this.notificationService.success('Domain deleted successfully');
                this.router.navigate(['/domains']);
              });
          }
        });
    }
  }

  editRecord(record: DnsRecord): void {
    const dialogRef = this.dialog.open(DnsRecordDialogComponent, {
      width: '500px',
      data: record
    });

    dialogRef.afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe(result => {
        if (result && this.isDomainValid(this.domain) && record.id) {
          this.domainService.updateRecord(record.id, result)
            .pipe(takeUntil(this.destroy$))
            .subscribe(() => {
              if (this.isDomainValid(this.domain)) {
                this.loadRecords(this.domain.id);
              }
              this.notificationService.success('Record updated successfully');
            });
        }
      });
  }

  configureDnssec(): void {
    if (!this.domain) return;
    
    const dialogRef = this.dialog.open(DnssecDialogComponent, {
      width: '600px',
      data: { domain: this.domain }
    });

    dialogRef.afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe(result => {
        if (result && this.domain?.id) {
          this.loadDomain(this.domain.id);
        }
      });
  }

  deleteRecord(record: DnsRecord): void {
    if (!record.id) {
      this.notificationService.error('Record ID not available');
      return;
    }

    const dialogRef = this.dialog.open(ConfirmationDialogComponent, {
      data: {
        title: 'Delete Record',
        message: `Are you sure you want to delete this ${record.type} record?`,
        confirmText: 'Delete',
        confirmColor: 'warn'
      }
    });

    dialogRef.afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe(confirmed => {
        if (confirmed && record.id) {
          this.domainService.deleteRecord(record.id)
            .pipe(takeUntil(this.destroy$))
            .subscribe(() => {
              this.notificationService.success('Record deleted successfully');
              if (this.isDomainValid(this.domain)) {
                this.loadRecords(this.domain.id);
              }
            });
        }
      });
  }
}