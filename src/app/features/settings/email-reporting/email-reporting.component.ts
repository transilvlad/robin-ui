import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { ConfigService } from '@core/services/config.service';
import { CommonModule } from '@angular/common';

interface EmailReportingConfig {
  reportingEmail: string;
  dmarc: {
    policy: 'none' | 'quarantine' | 'reject';
    subdomainPolicy: 'none' | 'quarantine' | 'reject';
    percentage: number;
    alignment: 'r' | 's'; // relaxed or strict
  };
  spf: {
    includes: string; // comma separated
    softFail: boolean; // ~all vs -all
  };
}

@Component({
  selector: 'app-email-reporting',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './email-reporting.component.html'
})
export class EmailReportingComponent implements OnInit {
  private fb = inject(FormBuilder);
  private configService = inject(ConfigService);

  form: FormGroup;
  loading = false;
  saving = false;
  successMessage: string | null = null;
  errorMessage: string | null = null;

  constructor() {
    this.form = this.fb.group({
      reportingEmail: ['', [Validators.required, Validators.email]],
      dmarc: this.fb.group({
        policy: ['none', Validators.required],
        subdomainPolicy: ['none'],
        percentage: [100, [Validators.required, Validators.min(0), Validators.max(100)]],
        alignment: ['r']
      }),
      spf: this.fb.group({
        includes: [''],
        softFail: [true]
      })
    });
  }

  ngOnInit(): void {
    this.loadConfig();
  }

  loadConfig() {
    this.loading = true;
    this.configService.getConfig<EmailReportingConfig>('email_reporting').subscribe({
      next: (config: EmailReportingConfig) => {
        // If config is empty or partial, use defaults
        const fullConfig = {
          reportingEmail: config?.reportingEmail || '',
          dmarc: {
            policy: config?.dmarc?.policy || 'none',
            subdomainPolicy: config?.dmarc?.subdomainPolicy || 'none',
            percentage: config?.dmarc?.percentage ?? 100,
            alignment: config?.dmarc?.alignment || 'r'
          },
          spf: {
            includes: config?.spf?.includes || '',
            softFail: config?.spf?.softFail ?? true
          }
        };
        this.form.patchValue(fullConfig);
        this.loading = false;
      },
      error: (err: any) => {
        // Maybe file doesn't exist yet, just keep defaults
        this.loading = false;
      }
    });
  }

  save() {
    if (this.form.invalid) return;

    this.saving = true;
    this.successMessage = null;
    this.errorMessage = null;

    const formValue = this.form.value;
    // Ensure numbers are numbers
    formValue.dmarc.percentage = Number(formValue.dmarc.percentage);

    this.configService.updateConfig('email_reporting', formValue).subscribe({
      next: () => {
        this.saving = false;
        this.successMessage = 'Settings saved successfully.';
        setTimeout(() => this.successMessage = null, 3000);
      },
      error: (err: any) => {
        this.saving = false;
        this.errorMessage = 'Failed to save settings.';
        console.error('Error saving config', err);
      }
    });
  }
}
