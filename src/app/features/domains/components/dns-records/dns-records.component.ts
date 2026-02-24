import { Component, Input, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { DomainService } from '../../services/domain.service';
import { DomainDnsRecord } from '../../models/domain.models';

@Component({
  selector: 'app-dns-records',
  templateUrl: './dns-records.component.html',
  styleUrls: ['./dns-records.component.scss'],
  standalone: false,
})
export class DnsRecordsComponent implements OnInit, OnChanges {
  @Input() domainId!: number;

  records: DomainDnsRecord[] = [];
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

  constructor(private domainService: DomainService) {}

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

  deleteRecord(record: DomainDnsRecord): void {
    if (!confirm(`Delete ${record.recordType} record "${record.name}"?`)) return;
    this.domainService.deleteDnsRecord(this.domainId, record.id).subscribe(result => {
      if (result.ok) {
        this.records = this.records.filter(r => r.id !== record.id);
      } else {
        this.error = 'Failed to delete DNS record';
      }
    });
  }
}
