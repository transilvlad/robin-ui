import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { SecurityService } from '../../../core/services/security.service';
import { RspamdConfig, RspamdStatus } from '../../../core/models/security.model';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'app-rspamd-config',
  templateUrl: './rspamd-config.component.html',
  styleUrls: ['./rspamd-config.component.scss'],
  standalone: false
})
export class RspamdConfigComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly securityService = inject(SecurityService);
  private readonly snackBar = inject(MatSnackBar);

  configForm!: FormGroup;
  loading = false;
  saving = false;
  testing = false;
  status: RspamdStatus | null = null;

  ngOnInit(): void {
    this.initializeForm();
    this.loadConfig();
    this.loadStatus();
  }

  private initializeForm(): void {
    this.configForm = this.fb.group({
      enabled: [false],
      host: ['localhost', [Validators.required, Validators.minLength(1)]],
      port: [11333, [Validators.required, Validators.min(1), Validators.max(65535)]],
      apiKey: [''],
      rejectScore: [15, [Validators.required, Validators.min(1), Validators.max(100)]],
      addHeaderScore: [6, [Validators.required, Validators.min(1), Validators.max(100)]],
      greylistScore: [4, [Validators.min(1), Validators.max(100)]],
      timeout: [30000, [Validators.min(1000), Validators.max(60000)]],
    });
  }

  private loadConfig(): void {
    this.loading = true;
    this.securityService.getRspamdConfig().subscribe({
      next: (config) => {
        this.configForm.patchValue(config);
        this.loading = false;
      },
      error: (error) => {
        console.error('Failed to load Rspamd config:', error);
        this.snackBar.open('Failed to load Rspamd configuration', 'Close', {
          duration: 5000,
          panelClass: ['error-snackbar']
        });
        this.loading = false;
      }
    });
  }

  private loadStatus(): void {
    this.securityService.getRspamdStatus().subscribe({
      next: (status) => {
        this.status = status;
      },
      error: (error) => {
        console.error('Failed to load Rspamd status:', error);
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
    const config = this.configForm.value as RspamdConfig;

    this.securityService.testRspamd(config).subscribe({
      next: (result) => {
        this.testing = false;
        if (result.success) {
          this.snackBar.open(
            `✓ Rspamd connection successful${result.responseTime ? ` (${result.responseTime}ms)` : ''}`,
            'Close',
            {
              duration: 5000,
              panelClass: ['success-snackbar']
            }
          );
          this.loadStatus();
        } else {
          this.snackBar.open(
            `✗ Rspamd connection failed: ${result.message}`,
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
          `✗ Rspamd test failed: ${error.message || 'Unknown error'}`,
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
    const config = this.configForm.value as RspamdConfig;

    this.securityService.updateRspamdConfig(config).subscribe({
      next: (updatedConfig) => {
        this.saving = false;
        this.configForm.patchValue(updatedConfig);
        this.snackBar.open('✓ Rspamd configuration saved successfully', 'Close', {
          duration: 3000,
          panelClass: ['success-snackbar']
        });
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

  formatNumber(num: number | undefined): string {
    if (num === undefined || num === null) return '0';
    return num.toLocaleString();
  }

  getSpamPercentage(): number {
    if (!this.status || !this.status.scannedTotal || this.status.scannedTotal === 0) {
      return 0;
    }
    return Math.round((this.status.spamCount || 0) / this.status.scannedTotal * 100);
  }

  formatUptime(seconds: number | undefined): string {
    if (seconds === undefined || seconds === null) return 'N/A';
    if (seconds === 0) return '0s';

    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);

    const parts: string[] = [];
    if (days > 0) parts.push(`${days}d`);
    if (hours > 0) parts.push(`${hours}h`);
    if (minutes > 0) parts.push(`${minutes}m`);
    if (secs > 0 || parts.length === 0) parts.push(`${secs}s`);

    return parts.join(' ');
  }
}
