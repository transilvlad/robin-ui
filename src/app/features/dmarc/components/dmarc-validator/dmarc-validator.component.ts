import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '@core/services/api.service';
import { DmarcValidationResult } from '@features/dmarc/models';

@Component({
  selector: 'app-dmarc-validator',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dmarc-validator.component.html',
  styleUrls: ['./dmarc-validator.component.scss']
})
export class DmarcValidatorComponent {
  domain = '';
  result: DmarcValidationResult | null = null;
  loading = false;
  error: string | null = null;

  constructor(private apiService: ApiService) {}

  validate(): void {
    if (!this.domain.trim()) return;

    this.loading = true;
    this.error = null;
    this.result = null;

    this.apiService.validateDmarcDomain(this.domain.trim()).subscribe({
      next: (res) => {
        this.result = res;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to validate domain. Please ensure the domain is correct and try again.';
        this.loading = false;
        console.error(err);
      }
    });
  }

  getRecordTags(): { key: string; value: string }[] {
    if (!this.result || !this.result.record) return [];
    return Object.entries(this.result.record).map(([key, value]) => ({ key, value }));
  }
}
