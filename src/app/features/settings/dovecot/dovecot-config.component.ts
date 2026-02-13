import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, FormArray, FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCardModule } from '@angular/material/card';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatChipsModule } from '@angular/material/chips';
import { Subject, takeUntil } from 'rxjs';
import { ConfigService } from '@core/services/config.service';
import { DovecotConfig } from '@core/models/config.model';
import { LoggingService } from '@core/services/logging.service';

@Component({
  selector: 'app-dovecot-config',
  templateUrl: './dovecot-config.component.html',
  styleUrls: [],
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
    MatChipsModule,
  ]
})
export class DovecotConfigComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly configService = inject(ConfigService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly loggingService = inject(LoggingService);
  private readonly destroy$ = new Subject<void>();

  configForm!: FormGroup;
  loading = false;
  saving = false;

  ngOnInit(): void {
    this.initializeForm();
    this.loadConfig();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeForm(): void {
    this.configForm = this.fb.group({
      protocols: this.fb.array([]),
      listen: ['*', Validators.required],
      mailLocation: ['maildir:~/Maildir', Validators.required],
      authentication: this.fb.group({
        mechanisms: this.fb.array([]),
        defaultRealm: ['']
      }),
      ssl: this.fb.group({
        enabled: [true],
        certFile: ['', Validators.required],
        keyFile: ['', Validators.required]
      }),
      limits: this.fb.group({
        maxConnections: [100, [Validators.required, Validators.min(1)]],
        maxUserConnections: [10, [Validators.required, Validators.min(1)]]
      })
    });
  }

  get protocols(): FormArray {
    return this.configForm.get('protocols') as FormArray;
  }

  get authMechanisms(): FormArray {
    return this.configForm.get('authentication.mechanisms') as FormArray;
  }
  
  get sslEnabled(): FormControl {
      return this.configForm.get('ssl.enabled') as FormControl;
  }

  private loadConfig(): void {
    this.loading = true;
    this.configService.getConfig<DovecotConfig>('dovecot')
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (config) => {
          this.patchForm(config);
          this.loading = false;
        },
        error: (error: HttpErrorResponse) => {
          this.loggingService.error('Failed to load Dovecot config:', error);
          this.snackBar.open('Failed to load Dovecot configuration', 'Close', {
            duration: 5000,
            panelClass: ['error-snackbar']
          });
          this.loading = false;
        }
      });
  }

  private patchForm(config: DovecotConfig): void {
    this.configForm.patchValue({
      listen: config.listen,
      mailLocation: config.mailLocation,
      authentication: {
        defaultRealm: config.authentication.defaultRealm
      },
      ssl: config.ssl,
      limits: config.limits
    });

    // Handle FormArrays
    this.protocols.clear();
    if (config.protocols) {
        config.protocols.forEach(p => this.protocols.push(this.fb.control(p)));
    }

    this.authMechanisms.clear();
    if (config.authentication && config.authentication.mechanisms) {
        config.authentication.mechanisms.forEach(m => this.authMechanisms.push(this.fb.control(m)));
    }
  }

  toggleProtocol(protocol: string): void {
    const index = this.protocols.value.indexOf(protocol);
    if (index === -1) {
      this.protocols.push(this.fb.control(protocol));
    } else {
      this.protocols.removeAt(index);
    }
    this.configForm.markAsDirty();
  }

  toggleAuthMech(mech: string): void {
    const index = this.authMechanisms.value.indexOf(mech);
    if (index === -1) {
      this.authMechanisms.push(this.fb.control(mech));
    } else {
      this.authMechanisms.removeAt(index);
    }
    this.configForm.markAsDirty();
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
    const config = this.configForm.value as DovecotConfig;

    this.configService.updateConfig('dovecot', config)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (updatedConfig) => {
          this.saving = false;
          this.patchForm(updatedConfig);
          this.snackBar.open('✓ Dovecot configuration saved successfully', 'Close', {
            duration: 3000,
            panelClass: ['success-snackbar']
          });
        },
        error: (error: HttpErrorResponse) => {
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
}