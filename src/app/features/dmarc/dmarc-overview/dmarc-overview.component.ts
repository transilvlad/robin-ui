import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DmarcLicenseService } from '../services/dmarc-license.service';

@Component({
  selector: 'app-dmarc-overview',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="p-6">
      <h1 class="text-2xl font-semibold mb-4">DMARC</h1>
      <p class="text-gray-500">DMARC reporting and analytics will appear here.</p>
    </div>
  `
})
export class DmarcOverviewComponent implements OnInit {
  protected licenseService = inject(DmarcLicenseService);

  ngOnInit(): void {
    this.licenseService.check();
  }
}
