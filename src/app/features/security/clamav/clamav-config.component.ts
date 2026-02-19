import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCardModule } from '@angular/material/card';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subject, takeUntil } from 'rxjs';
import { SecurityService } from '../../../core/services/security.service';
import { ClamAVConfig, ClamAVStatus } from '../../../core/models/security.model';
import { LoggingService } from '@core/services/logging.service';

@Component({
  selector: 'app-clamav-config',
  templateUrl: './clamav-config.component.html',
  styleUrls: ['./clamav-config.component.scss'],
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatCardModule,
    MatSlideToggleModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ]
})
export class ClamavConfigComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly securityService = inject(SecurityService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly loggingService = inject(LoggingService);
  private destroy$ = new Subject<void>();

  configForm!: FormGroup;
  loading = false;
  saving = false;
  testing = false;
  status: ClamAVStatus | null = null;

  ngOnInit(): void {
    this.initializeForm();
    this.loadConfig();
    this.loadStatus();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeForm(): void {
    this.configForm = this.fb.group({
      enabled: [false],
      host: ['localhost', [Validators.required, Validators.minLength(1)]],
      port: [3310, [Validators.required, Validators.min(1), Validators.max(65535)]],
      timeout: [30000, [Validators.required, Validators.min(1000), Validators.max(60000)]],
      maxFileSize: [25 * 1024 * 1024], // 25 MB default
      scanArchives: [true],
    });
  }

  private loadConfig(): void {
    this.loading = true;
    this.securityService.getClamAVConfig()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (config) => {
          this.configForm.patchValue(config);
          this.loading = false;
        },
        error: (error) => {
          this.loggingService.error('Failed to load ClamAV config:', error);
          this.snackBar.open('Failed to load ClamAV configuration', 'Close', {
            duration: 5000,
            panelClass: ['error-snackbar']
          });
          this.loading = false;
        }
      });
  }

  private loadStatus(): void {
    this.securityService.getClamAVStatus()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (status) => {
          this.status = status;
        },
        error: (error) => {
          this.loggingService.error('Failed to load ClamAV status:', error);
        }
      });
  }

  testConnection(): void {
    if (this.configForm.invalid) {
      this.snackBar.open('Please fix form errors before testing', 'Close', {
        duration: 3000,
        panelClass: ['error-snackbar']
      });
      return;
    }

    this.testing = true;
    const config = this.configForm.value as ClamAVConfig;

    this.securityService.testClamAV(config)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (result) => {
          this.testing = false;
          if (result.success) {
            this.snackBar.open(
              `✓ ClamAV connection successful${result.responseTime ? ` (${result.responseTime}ms)` : ''}`,
              'Close',
              {
                duration: 5000,
                panelClass: ['success-snackbar']
              }
            );
            // Refresh status after successful test
            this.loadStatus();
          } else {
            this.snackBar.open(
              `✗ ClamAV connection failed: ${result.message}`,
              'Close',
              {
                duration: 7000,
                panelClass: ['error-snackbar']
              }
            );
          }
        },
        error: (error) => {
          this.testing = false;
          this.snackBar.open(
            `✗ ClamAV test failed: ${error.message || 'Unknown error'}`,
            'Close',
            {
              duration: 7000,
              panelClass: ['error-snackbar']
            }
          );
        }
      });
  }

  saveConfiguration(): void {
    if (this.configForm.invalid) {
      this.snackBar.open('Please fix form errors before saving', 'Close', {
        duration: 3000,
        panelClass: ['error-snackbar']
      });
      return;
    }

    this.saving = true;
    const config = this.configForm.value as ClamAVConfig;

    this.securityService.updateClamAVConfig(config)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (updatedConfig) => {
          this.saving = false;
          this.configForm.patchValue(updatedConfig);
          this.snackBar.open('✓ ClamAV configuration saved successfully', 'Close', {
            duration: 3000,
            panelClass: ['success-snackbar']
          });
          // Refresh status
          this.loadStatus();
        },
        error: (error) => {
          this.saving = false;
          this.snackBar.open(
            `✗ Failed to save configuration: ${error.message || 'Unknown error'}`,
            'Close',
            {
              duration: 7000,
              panelClass: ['error-snackbar']
            }
          );
        }
      });
  }

  resetForm(): void {
    this.loadConfig();
  }

  getStatusColor(): string {
    if (!this.status) return 'text-gray-500';
    switch (this.status.status) {
      case 'UP':
        return 'text-green-600';
      case 'DOWN':
        return 'text-red-600';
      default:
        return 'text-gray-500';
    }
  }

  getStatusIcon(): string {
    if (!this.status) return 'help_outline';
    switch (this.status.status) {
      case 'UP':
        return 'check_circle';
      case 'DOWN':
        return 'error';
      default:
        return 'help_outline';
    }
  }

  formatBytes(bytes: number | undefined): string {
    if (!bytes) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
  }
}
