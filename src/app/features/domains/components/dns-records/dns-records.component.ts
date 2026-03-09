import { Component, Input, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { DomainService, SyncResult } from '../../services/domain.service';
import { DnsProviderService } from '../../services/dns-provider.service';
import { DomainDnsRecord, DnsProvider, Domain, DnsProviderType } from '../../models/domain.models';

@Component({
  selector: 'app-dns-records',
  templateUrl: './dns-records.component.html',
  styleUrls: ['./dns-records.component.scss'],
  standalone: false,
})
export class DnsRecordsComponent implements OnInit, OnChanges {
  @Input() domainId!: number;

  records: DomainDnsRecord[] = [];
  domain: Domain | null = null;
  providers: DnsProvider[] = [];
  loading = false;
  error: string | null = null;

  showAddForm = false;
  editingRecord: DomainDnsRecord | null = null;

  newRecord: Partial<DomainDnsRecord> = {
    recordType: 'A',
    name: '',
    value: '',
    ttl: 3600,
  };

  recordTypes = ['A', 'AAAA', 'CNAME', 'MX', 'TXT', 'NS', 'SPF', 'DKIM', 'DMARC', 'SRV'];

  // Delete record state
  recordToDelete: DomainDnsRecord | null = null;
  deleteRecordLoading = false;
  deleteRecordError: string | null = null;

  // Sync state
  syncingManaged = false;
  syncingNs = false;
  syncResult: SyncResult | null = null;
  syncError: string | null = null;
  showNsProviderPicker = false;
  selectedNsProviderId: number | null = null;

  get mainRecords(): DomainDnsRecord[] {
    return this.records.filter(r => r.recordType !== 'NS');
  }

  get nsRecords(): DomainDnsRecord[] {
    return this.records.filter(r => r.recordType === 'NS');
  }

  get cloudflareProviders(): DnsProvider[] {
    return this.providers.filter(p => p.type === DnsProviderType.CLOUDFLARE);
  }

  get canSyncManaged(): boolean {
    return this.domain?.dnsProviderId != null;
  }

  /** True when the domain has no nsProviderId or the assigned provider is not Cloudflare. */
  get needsNsProviderSelection(): boolean {
    if (!this.domain?.nsProviderId) return true;
    const current = this.providers.find(p => p.id === this.domain!.nsProviderId);
    return !current || current.type !== DnsProviderType.CLOUDFLARE;
  }

  constructor(
    private domainService: DomainService,
    private dnsProviderService: DnsProviderService,
  ) {}

  ngOnInit(): void {
    this.loadRecords();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['domainId'] && !changes['domainId'].firstChange) {
      this.loadRecords();
    }
  }

  loadRecords(): void {
    if (!this.domainId) return;
    this.loading = true;
    this.error = null;
    this.domainService.getDnsRecords(this.domainId).subscribe(result => {
      this.loading = false;
      if (result.ok) {
        this.records = result.value;
      } else {
        this.error = 'Failed to load DNS records';
      }
    });
    this.domainService.getDomain(this.domainId).subscribe(result => {
      if (result.ok) this.domain = result.value;
    });
    this.dnsProviderService.getProviders().subscribe(result => {
      if (result.ok) {
        this.providers = result.value;
        if (!this.selectedNsProviderId) {
          this.selectedNsProviderId = this.cloudflareProviders[0]?.id ?? null;
        }
      }
    });
  }

  openAddForm(): void {
    this.showAddForm = true;
    this.editingRecord = null;
    this.newRecord = { recordType: 'A', name: '', value: '', ttl: 3600 };
  }

  cancelForm(): void {
    this.showAddForm = false;
    this.editingRecord = null;
  }

  saveRecord(): void {
    if (!this.newRecord.recordType || !this.newRecord.name || !this.newRecord.value) return;

    if (this.editingRecord) {
      this.domainService.updateDnsRecord(this.domainId, this.editingRecord.id, this.newRecord).subscribe(result => {
        if (result.ok) {
          this.records = this.records.map(r => r.id === result.value.id ? result.value : r);
          this.cancelForm();
        } else {
          this.error = 'Failed to update DNS record';
        }
      });
    } else {
      this.domainService.createDnsRecord(this.domainId, this.newRecord).subscribe(result => {
        if (result.ok) {
          this.records = [...this.records, result.value];
          this.cancelForm();
        } else {
          this.error = 'Failed to create DNS record';
        }
      });
    }
  }

  editRecord(record: DomainDnsRecord): void {
    this.editingRecord = record;
    this.newRecord = { ...record };
    this.showAddForm = true;
  }

  confirmDeleteRecord(record: DomainDnsRecord): void {
    this.recordToDelete = record;
    this.deleteRecordError = null;
  }

  cancelDeleteRecord(): void {
    this.recordToDelete = null;
    this.deleteRecordError = null;
    this.deleteRecordLoading = false;
  }

  executeDeleteRecord(): void {
    if (!this.recordToDelete) return;
    this.deleteRecordLoading = true;
    this.deleteRecordError = null;
    this.domainService.deleteDnsRecord(this.domainId, this.recordToDelete.id).subscribe(result => {
      this.deleteRecordLoading = false;
      if (result.ok) {
        this.records = this.records.filter(r => r.id !== this.recordToDelete!.id);
        this.cancelDeleteRecord();
      } else {
        this.deleteRecordError = 'Failed to delete DNS record. Please try again.';
      }
    });
  }

  // ─── Sync ────────────────────────────────────────────────────────────────────

  syncManagedRecords(): void {
    this.syncingManaged = true;
    this.syncResult = null;
    this.syncError = null;
    this.domainService.syncManagedRecords(this.domainId).subscribe(result => {
      this.syncingManaged = false;
      if (result.ok) {
        this.syncResult = result.value;
      } else {
        this.syncError = result.error?.message ?? 'Sync failed';
      }
    });
  }

  openNsSync(): void {
    this.syncResult = null;
    this.syncError = null;
    if (this.needsNsProviderSelection) {
      this.showNsProviderPicker = true;
    } else {
      this.doSyncNs(this.domain!.nsProviderId!);
    }
  }

  confirmNsSync(): void {
    if (!this.selectedNsProviderId) return;
    this.doSyncNs(this.selectedNsProviderId);
  }

  cancelNsProviderPicker(): void {
    this.showNsProviderPicker = false;
  }

  private doSyncNs(nsProviderId: number): void {
    this.syncingNs = true;
    this.showNsProviderPicker = false;
    this.domainService.syncNsRecords(this.domainId, nsProviderId).subscribe(result => {
      this.syncingNs = false;
      if (result.ok) {
        this.syncResult = result.value;
        if (this.domain) {
          this.domain = { ...this.domain, nsProviderId };
        }
      } else {
        this.syncError = result.error?.message ?? 'NS sync failed';
      }
    });
  }
}
