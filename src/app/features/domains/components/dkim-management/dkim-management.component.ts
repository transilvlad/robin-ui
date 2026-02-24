import { Component, Input, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { DkimService, GenerateDkimKeyRequest } from '../../services/dkim.service';
import { DkimKey } from '../../models/domain.models';

@Component({
  selector: 'app-dkim-management',
  templateUrl: './dkim-management.component.html',
  styleUrls: ['./dkim-management.component.scss'],
  standalone: false,
})
export class DkimManagementComponent implements OnInit, OnChanges {
  @Input() domainId!: number;

  keys: DkimKey[] = [];
  loading = false;
  error: string | null = null;

  showGenerateModal = false;
  generating = false;

  generateRequest: GenerateDkimKeyRequest = {
    selector: 'default',
    algorithm: 'RSA_2048',
  };

  algorithms: Array<{ value: 'RSA_2048' | 'ED25519'; label: string }> = [
    { value: 'RSA_2048', label: 'RSA 2048-bit' },
    { value: 'ED25519',  label: 'Ed25519' },
  ];

  constructor(private dkimService: DkimService) {}

  ngOnInit(): void {
    this.loadKeys();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['domainId'] && !changes['domainId'].firstChange) {
      this.loadKeys();
    }
  }

  loadKeys(): void {
    if (!this.domainId) return;
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
    this.generateRequest = { selector: 'default', algorithm: 'RSA_2048' };
  }

  closeGenerateModal(): void {
    this.showGenerateModal = false;
  }

  generateKey(): void {
    if (!this.generateRequest.selector) return;
    this.generating = true;
    this.dkimService.generateKey(this.domainId, this.generateRequest).subscribe(result => {
      this.generating = false;
      if (result.ok) {
        this.keys = [...this.keys, result.value];
        this.closeGenerateModal();
      } else {
        this.error = 'Failed to generate DKIM key';
      }
    });
  }

  rotateKey(): void {
    if (!confirm('Rotate the active DKIM key? A new key will be generated and the current one set to ROTATING.')) return;
    this.dkimService.rotateKey(this.domainId).subscribe(result => {
      if (result.ok) {
        this.loadKeys();
      } else {
        this.error = 'Failed to rotate DKIM key';
      }
    });
  }

  retireKey(key: DkimKey): void {
    if (!confirm(`Retire DKIM key "${key.selector}"? This cannot be undone.`)) return;
    this.dkimService.retireKey(this.domainId, key.id).subscribe(result => {
      if (result.ok) {
        this.loadKeys();
      } else {
        this.error = 'Failed to retire DKIM key';
      }
    });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'ACTIVE':   return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300';
      case 'ROTATING': return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300';
      case 'RETIRED':  return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400';
      default:         return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400';
    }
  }
}
