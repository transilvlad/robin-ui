import { Component, Input, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { MtaStsService } from '../../services/mta-sts.service';
import { MtaStsWorker } from '../../models/domain.models';

@Component({
  selector: 'app-mta-sts-status',
  templateUrl: './mta-sts-status.component.html',
  styleUrls: ['./mta-sts-status.component.scss'],
  standalone: false,
})
export class MtaStsStatusComponent implements OnInit, OnChanges {
  @Input() domainId!: number;

  worker: MtaStsWorker | null = null;
  loading = false;
  deploying = false;
  error: string | null = null;

  selectedPolicyMode: 'testing' | 'enforce' | 'none' = 'testing';

  // Policy content editing
  editingContent = false;
  editedContent = '';
  savingContent = false;
  contentError: string | null = null;

  policyModes: Array<{ value: 'testing' | 'enforce' | 'none'; label: string; description: string }> = [
    { value: 'testing', label: 'Testing',  description: 'Report-only mode. No enforcement.' },
    { value: 'enforce', label: 'Enforce',  description: 'Reject non-conforming connections.' },
    { value: 'none',    label: 'None',     description: 'Disable MTA-STS policy.' },
  ];

  constructor(private mtaStsService: MtaStsService) {}

  ngOnInit(): void {
    this.loadWorker();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['domainId'] && !changes['domainId'].firstChange) {
      this.loadWorker();
    }
  }

  loadWorker(): void {
    if (!this.domainId) return;
    this.loading = true;
    this.error = null;
    this.mtaStsService.getWorker(this.domainId).subscribe(result => {
      this.loading = false;
      if (result.ok) {
        this.worker = result.value;
        this.selectedPolicyMode = result.value.policyMode;
        this.editedContent = result.value.policyContent ?? '';
      } else {
        // Worker may not exist yet; treat 404-like cases gracefully
        this.worker = null;
      }
    });
  }

  deploy(): void {
    this.deploying = true;
    this.mtaStsService.deploy(this.domainId, { policyMode: this.selectedPolicyMode }).subscribe(result => {
      this.deploying = false;
      if (result.ok) {
        this.worker = result.value;
      } else {
        this.error = 'Failed to deploy MTA-STS worker';
      }
    });
  }

  updatePolicy(): void {
    this.mtaStsService.updatePolicy(this.domainId, this.selectedPolicyMode).subscribe(result => {
      if (result.ok) {
        this.worker = result.value;
      } else {
        this.error = 'Failed to update policy mode';
      }
    });
  }

  startEditContent(): void {
    this.editedContent = this.worker?.policyContent ?? '';
    this.editingContent = true;
    this.contentError = null;
  }

  cancelEditContent(): void {
    this.editingContent = false;
    this.contentError = null;
  }

  saveContent(): void {
    if (!this.editedContent.trim()) return;
    this.savingContent = true;
    this.contentError = null;
    this.mtaStsService.updatePolicyContent(this.domainId, this.editedContent).subscribe(result => {
      this.savingContent = false;
      if (result.ok) {
        this.worker = result.value;
        this.editingContent = false;
      } else {
        this.contentError = 'Failed to save policy content';
      }
    });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'DEPLOYED': return 'badge-success';
      case 'PENDING':  return 'badge-warning';
      case 'ERROR':    return 'badge-destructive';
      default:         return 'badge-secondary';
    }
  }
}
