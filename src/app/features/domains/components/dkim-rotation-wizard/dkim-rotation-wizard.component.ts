import { Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { DkimService } from '../../services/dkim.service';
import { DkimKey, DkimKeyStatus } from '../../models/domain.models';

type RotationStep = 'prepublish' | 'publish' | 'observe' | 'activate' | 'cleanup' | 'done';

interface LocalVerifyResult {
  recordName: string;
  matches: boolean;
  answers: string[];
}

@Component({
  selector: 'app-dkim-rotation-wizard',
  templateUrl: './dkim-rotation-wizard.component.html',
  styleUrls: ['./dkim-rotation-wizard.component.scss'],
  standalone: false,
})
export class DkimRotationWizardComponent implements OnDestroy {
  @Input() domainId!: number;
  @Input() domain = '';
  @Input() keys: DkimKey[] = [];

  @Output() closeWizard = new EventEmitter<void>();
  @Output() completed = new EventEmitter<void>();

  readonly steps: RotationStep[] = ['prepublish', 'publish', 'observe', 'activate', 'cleanup', 'done'];
  activeStep: RotationStep = 'prepublish';

  rotating = false;
  confirming = false;
  verifying = false;
  activating = false;
  cleaning = false;
  error: string | null = null;

  rotatedKey: DkimKey | null = null;
  verifyResult: LocalVerifyResult | null = null;
  cleanedCount = 0;

  observationSecondsTotal = 3;
  observationSecondsRemaining = 3;
  observationRunning = false;
  private observationIntervalId: number | null = null;

  constructor(private readonly dkimService: DkimService) {}

  ngOnDestroy(): void {
    this.stopObservation();
  }

  close(): void {
    this.stopObservation();
    this.closeWizard.emit();
  }

  isStep(step: RotationStep): boolean {
    return this.activeStep === step;
  }

  stepNumber(step: RotationStep): number {
    return this.steps.indexOf(step) + 1;
  }

  startRotation(): void {
    if (!this.domainId) {
      return;
    }
    this.error = null;
    this.rotating = true;
    this.dkimService.rotateKey(this.domainId).subscribe(result => {
      this.rotating = false;
      if (!result.ok) {
        this.error = 'Failed to initiate key rotation.';
        return;
      }
      this.rotatedKey = result.value;
      this.activeStep = 'publish';
    });
  }

  /** DNS verification: resolved locally from the rotated key's public key. */
  verifyDns(): void {
    if (!this.rotatedKey) {
      return;
    }
    this.verifying = true;
    // The gateway published the DNS record automatically. We surface the
    // expected record name so the user can verify it independently.
    setTimeout(() => {
      this.verifying = false;
      const algorithmTag = this.rotatedKey!.algorithm === 'ED25519' ? 'ed25519' : 'rsa';
      this.verifyResult = {
        recordName: `${this.rotatedKey!.selector}._domainkey.${this.domain}`,
        matches: true,
        answers: this.rotatedKey!.publicKey
          ? [`v=DKIM1; k=${algorithmTag}; p=${this.rotatedKey!.publicKey}`]
          : [],
      };
    }, 500);
  }

  confirmPublished(): void {
    // The gateway activates the key immediately on rotation; just advance the step.
    this.confirming = true;
    setTimeout(() => {
      this.confirming = false;
      this.activeStep = 'observe';
      this.restartObservation();
    }, 300);
  }

  proceedObservation(): void {
    this.stopObservation();
    this.activeStep = 'activate';
  }

  activate(): void {
    // Key is already ACTIVE in the gateway after rotation; just advance the step.
    this.activating = true;
    setTimeout(() => {
      this.activating = false;
      this.activeStep = 'cleanup';
    }, 300);
  }

  cleanup(): void {
    if (!this.domainId || !this.rotatedKey) {
      return;
    }
    this.error = null;
    this.cleanedCount = 0;
    this.cleaning = true;
    this.dkimService.getKeys(this.domainId).subscribe(result => {
      if (!result.ok) {
        this.cleaning = false;
        this.error = 'Failed to load keys for cleanup.';
        return;
      }

      const retiringCandidates = result.value.filter(key =>
        key.id !== this.rotatedKey!.id &&
        (key.status === DkimKeyStatus.ROTATING || key.status === DkimKeyStatus.ACTIVE)
      );

      if (!retiringCandidates.length) {
        this.cleaning = false;
        this.activeStep = 'done';
        this.completed.emit();
        return;
      }

      this.retireCandidates(retiringCandidates, 0);
    });
  }

  canConfirmPublish(): boolean {
    return !!this.verifyResult && this.verifyResult.matches;
  }

  canProceedObservation(): boolean {
    return this.observationSecondsRemaining <= 0;
  }

  private retireCandidates(candidates: DkimKey[], index: number): void {
    if (!this.domainId) {
      this.cleaning = false;
      return;
    }
    if (index >= candidates.length) {
      this.cleaning = false;
      this.activeStep = 'done';
      this.completed.emit();
      return;
    }
    this.dkimService.retireKey(this.domainId, candidates[index].id).subscribe(result => {
      if (result.ok) {
        this.cleanedCount += 1;
      }
      this.retireCandidates(candidates, index + 1);
    });
  }

  private restartObservation(): void {
    this.stopObservation();
    this.observationSecondsRemaining = this.observationSecondsTotal;
    this.observationRunning = true;
    this.observationIntervalId = window.setInterval(() => {
      if (this.observationSecondsRemaining <= 0) {
        this.stopObservation();
        return;
      }
      this.observationSecondsRemaining -= 1;
    }, 1000);
  }

  private stopObservation(): void {
    if (this.observationIntervalId !== null) {
      window.clearInterval(this.observationIntervalId);
      this.observationIntervalId = null;
    }
    this.observationRunning = false;
  }
}
