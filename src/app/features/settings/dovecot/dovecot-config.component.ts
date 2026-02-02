import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators, FormArray, FormControl } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ConfigService } from '@core/services/config.service';
import { DovecotConfig } from '@core/models/config.model';

@Component({
  selector: 'app-dovecot-config',
  templateUrl: './dovecot-config.component.html',
  styleUrls: [],
  standalone: false
})
export class DovecotConfigComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly configService = inject(ConfigService);
  private readonly snackBar = inject(MatSnackBar);

  configForm!: FormGroup;
  loading = false;
  saving = false;

  ngOnInit(): void {
    this.initializeForm();
    this.loadConfig();
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
    this.configService.getConfig<DovecotConfig>('dovecot').subscribe({
      next: (config) => {
        this.patchForm(config);
        this.loading = false;
      },
      error: (error: any) => {
        console.error('Failed to load Dovecot config:', error);
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

    this.configService.updateConfig('dovecot', config).subscribe({
      next: (updatedConfig) => {
        this.saving = false;
        this.patchForm(updatedConfig);
        this.snackBar.open('✓ Dovecot configuration saved successfully', 'Close', {
          duration: 3000,
          panelClass: ['success-snackbar']
        });
      },
      error: (error: any) => {
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