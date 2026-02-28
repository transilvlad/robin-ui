import { Component, EventEmitter, Input, Output } from '@angular/core';
import { DkimDnsRecord } from '../../models/domain.models';

export interface DkimVerifyDnsResult {
  recordName: string;
  matches: boolean;
  answers: string[];
}

@Component({
  selector: 'app-dkim-dns-record',
  templateUrl: './dkim-dns-record.component.html',
  styleUrls: ['./dkim-dns-record.component.scss'],
  standalone: false,
})
export class DkimDnsRecordComponent {
  @Input() domain = '';
  @Input() record: DkimDnsRecord | null = null;
  @Input() verifyResult: DkimVerifyDnsResult | null = null;
  @Input() verifying = false;
  @Input() pushing = false;
  @Input() pushSuccess: string | null = null;
  @Input() pushError: string | null = null;

  @Output() closeDrawer = new EventEmitter<void>();
  @Output() verifyRecord = new EventEmitter<void>();
  @Output() pushRecord = new EventEmitter<DkimDnsRecord>();

  copied: 'name' | 'value' | null = null;

  close(): void {
    this.closeDrawer.emit();
  }

  verify(): void {
    this.verifyRecord.emit();
  }

  push(): void {
    if (!this.record) {
      return;
    }
    this.pushRecord.emit(this.record);
  }

  copyName(): void {
    if (!this.record?.name) {
      return;
    }
    this.copyToClipboard(this.record.name, 'name');
  }

  copyValue(): void {
    const value = this.recordValue;
    if (!value) {
      return;
    }
    this.copyToClipboard(value, 'value');
  }

  get recordValue(): string {
    if (!this.record) {
      return '';
    }
    if (this.record.value?.trim()) {
      return this.record.value.trim();
    }
    if (this.record.chunks?.length) {
      return this.record.chunks.map(chunk => `"${chunk}"`).join(' ');
    }
    return '';
  }

  get verifyResultClass(): string {
    if (!this.verifyResult) {
      return 'badge-secondary';
    }
    return this.verifyResult.matches ? 'badge-success' : 'badge-error';
  }

  private async copyToClipboard(text: string, field: 'name' | 'value'): Promise<void> {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
      } else {
        this.fallbackCopy(text);
      }
      this.copied = field;
      setTimeout(() => {
        this.copied = null;
      }, 1400);
    } catch {
      this.copied = null;
    }
  }

  private fallbackCopy(text: string): void {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
  }
}
