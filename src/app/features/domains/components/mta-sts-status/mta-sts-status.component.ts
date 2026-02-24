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

  getStatusClass(status: string): string {
    switch (status) {
      case 'DEPLOYED': return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300';
      case 'PENDING':  return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300';
      case 'ERROR':    return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300';
      default:         return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400';
    }
  }
}
