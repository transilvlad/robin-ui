import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { DomainService } from '../../services/domain.service';
import { DkimService, GenerateDkimKeyRequest } from '../../services/dkim.service';
import { DkimDnsRecord, DkimKey, DkimKeyStatus, DkimAlgorithm } from '../../models/domain.models';

type DkimAction = 'retiring' | 'pushing';

@Component({
  selector: 'app-dkim-management',
  templateUrl: './dkim-management.component.html',
  styleUrls: ['./dkim-management.component.scss'],
  standalone: false,
})
export class DkimManagementComponent implements OnInit, OnChanges {
  @Input() domainId!: number;
  @Input() domain!: string;

  keys: DkimKey[] = [];
  loading = false;
  error: string | null = null;

  showGenerateModal = false;
  showRotationWizard = false;
  generating = false;
  actionKeyId: number | null = null;
  actionType: DkimAction | null = null;
  showDnsDrawer = false;
  drawerKey: DkimKey | null = null;
  drawerRecord: DkimDnsRecord | null = null;
  dnsPushSuccess: string | null = null;
  dnsPushError: string | null = null;

  generateRequest: GenerateDkimKeyRequest = {
    algorithm: 'RSA_2048',
    selector: '',
  };

  algorithms: Array<{ value: 'RSA_2048' | 'ED25519'; label: string }> = [
    { value: 'RSA_2048', label: 'RSA 2048-bit' },
    { value: 'ED25519', label: 'Ed25519' },
  ];

  constructor(
    private dkimService: DkimService,
    private domainService: DomainService
  ) {}

  ngOnInit(): void {
    this.loadKeys();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (
      (changes['domain'] && !changes['domain'].firstChange) ||
      (changes['domainId'] && !changes['domainId'].firstChange)
    ) {
      this.loadKeys();
    }
  }

  loadKeys(): void {
    if (!this.domainId) {
      return;
    }
    this.loading = true;
    this.error = null;
    this.dkimService.getKeys(this.domainId).subscribe(result => {
      this.loading = false;
      if (result.ok) {
        this.keys = result.value;
      } else {
        this.error = 'Failed to load DKIM keys';
      }
    });
  }

  openGenerateModal(): void {
    this.showGenerateModal = true;
    this.generateRequest = { algorithm: 'RSA_2048', selector: '' };
  }

  closeGenerateModal(): void {
    this.showGenerateModal = false;
  }

  generateKey(): void {
    if (!this.domainId) {
      return;
    }
    const selector = this.generateRequest.selector?.trim();
    const payload: GenerateDkimKeyRequest = {
      algorithm: this.generateRequest.algorithm,
      selector: selector || undefined,
    };
    this.generating = true;
    this.dkimService.generateKey(this.domainId, payload).subscribe(result => {
      this.generating = false;
      if (result.ok) {
        this.loadKeys();
        this.closeGenerateModal();
      } else {
        this.error = 'Failed to generate DKIM key';
      }
    });
  }

  openRotationWizard(): void {
    this.showRotationWizard = true;
  }

  closeRotationWizard(): void {
    this.showRotationWizard = false;
  }

  onRotationCompleted(): void {
    this.loadKeys();
  }

  retireKey(key: DkimKey): void {
    if (!this.domainId) {
      return;
    }
    if (!confirm(`Retire DKIM key "${key.selector}"? This cannot be undone.`)) {
      return;
    }
    this.setAction(key.id, 'retiring');
    this.dkimService.retireKey(this.domainId, key.id).subscribe(result => {
      this.clearAction();
      if (result.ok) {
        this.loadKeys();
      } else {
        this.error = 'Failed to retire DKIM key';
      }
    });
  }

  openDnsRecordDrawer(key: DkimKey): void {
    this.drawerKey = key;
    this.drawerRecord = this.buildDnsRecord(key);
    this.showDnsDrawer = true;
    this.dnsPushError = null;
    this.dnsPushSuccess = null;
  }

  closeDnsRecordDrawer(): void {
    this.showDnsDrawer = false;
    this.drawerKey = null;
    this.drawerRecord = null;
    this.dnsPushError = null;
    this.dnsPushSuccess = null;
  }

  pushDnsRecord(record: DkimDnsRecord): void {
    if (!this.domainId) {
      this.dnsPushError = 'Domain ID is required to push DNS records.';
      return;
    }
    const value = record.value?.trim() ?? '';
    if (!value) {
      this.dnsPushError = 'Cannot push an empty DKIM DNS value.';
      return;
    }
    const name = this.toRelativeRecordName(record.name);
    this.dnsPushError = null;
    this.dnsPushSuccess = null;
    this.setAction(record.keyId ?? -1, 'pushing');
    this.domainService.createDnsRecord(this.domainId, {
      recordType: 'TXT',
      name,
      value,
      ttl: 300,
    }).subscribe(result => {
      this.clearAction();
      if (result.ok) {
        this.dnsPushSuccess = `DNS TXT record pushed for ${record.name}`;
      } else {
        this.dnsPushError = 'Failed to push DNS record to provider API';
      }
    });
  }

  getStatusClass(status: DkimKeyStatus | string): string {
    switch (status) {
      case 'ACTIVE':
        return 'badge-success';
      case 'ROTATING':
        return 'badge-warning';
      case 'RETIRED':
        return 'badge-secondary opacity-60';
      default:
        return 'badge-secondary';
    }
  }

  getStatusLabel(status: string): string {
    return status.replace(/_/g, ' ');
  }

  canRetire(status: DkimKeyStatus): boolean {
    return status !== DkimKeyStatus.RETIRED;
  }

  isActionInProgress(keyId: number, action?: DkimAction): boolean {
    if (this.actionKeyId !== keyId) {
      return false;
    }
    if (!action) {
      return true;
    }
    return this.actionType === action;
  }

  private buildDnsRecord(key: DkimKey): DkimDnsRecord {
    const algorithmTag = key.algorithm === DkimAlgorithm.ED25519 ? 'ed25519' : 'rsa';
    return {
      keyId: key.id,
      name: `${key.selector}._domainkey.${this.domain}`,
      type: 'TXT',
      value: key.publicKey ? `v=DKIM1; k=${algorithmTag}; p=${key.publicKey}` : '',
    };
  }

  private setAction(keyId: number, action: DkimAction): void {
    this.actionKeyId = keyId;
    this.actionType = action;
  }

  private clearAction(): void {
    this.actionKeyId = null;
    this.actionType = null;
  }

  private toRelativeRecordName(name: string): string {
    const normalizedName = name.replace(/\.$/, '').toLowerCase();
    const normalizedDomain = this.domain.replace(/\.$/, '').toLowerCase();
    const domainSuffix = `.${normalizedDomain}`;
    if (normalizedName === normalizedDomain) {
      return '@';
    }
    if (normalizedName.endsWith(domainSuffix)) {
      return normalizedName.slice(0, -domainSuffix.length);
    }
    return normalizedName;
  }
}
