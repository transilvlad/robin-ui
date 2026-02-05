import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DomainService, DnsRecord, Domain } from '@core/services/domain.service';
import { NotificationService } from '@core/services/notification.service';
import { finalize } from 'rxjs/operators';

export interface DnssecDialogData {
  domain: Domain;
}

@Component({
  selector: 'app-dnssec-dialog',
  standalone: false,
  templateUrl: './dnssec-dialog.component.html'
})
export class DnssecDialogComponent implements OnInit {
  dsRecords: DnsRecord[] = [];
  isLoading = false;
  isActionLoading = false;

  constructor(
    public dialogRef: MatDialogRef<DnssecDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: DnssecDialogData,
    private domainService: DomainService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    if (this.data.domain.dnssecEnabled) {
      this.loadStatus();
    }
  }

  loadStatus(): void {
    if (!this.data.domain.id) return;
    this.isLoading = true;
    this.domainService.getDnssecStatus(this.data.domain.id)
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: (records) => {
          this.dsRecords = records;
        },
        error: (err) => {
          this.notificationService.error('Failed to load DNSSEC status');
        }
      });
  }

  enable(): void {
    if (!this.data.domain.id) return;
    this.isActionLoading = true;
    this.domainService.enableDnssec(this.data.domain.id)
      .pipe(finalize(() => this.isActionLoading = false))
      .subscribe({
        next: () => {
          this.notificationService.success('DNSSEC enabled successfully');
          this.data.domain.dnssecEnabled = true;
          this.loadStatus();
        },
        error: (err) => {
          this.notificationService.error('Failed to enable DNSSEC: ' + (err.error?.message || err.message));
        }
      });
  }

  disable(): void {
    if (!this.data.domain.id) return;
    if (!confirm('Are you sure you want to disable DNSSEC? This may make your domain unreachable until caches expire.')) return;
    
    this.isActionLoading = true;
    this.domainService.disableDnssec(this.data.domain.id)
      .pipe(finalize(() => this.isActionLoading = false))
      .subscribe({
        next: () => {
          this.notificationService.success('DNSSEC disabled successfully');
          this.data.domain.dnssecEnabled = false;
          this.dsRecords = [];
          this.dialogRef.close(true);
        },
        error: (err) => {
          this.notificationService.error('Failed to disable DNSSEC: ' + (err.error?.message || err.message));
        }
      });
  }
}
