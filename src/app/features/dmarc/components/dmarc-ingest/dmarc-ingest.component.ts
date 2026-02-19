import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '@core/services/api.service';

@Component({
  selector: 'app-dmarc-ingest',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dmarc-ingest.component.html',
  styleUrls: ['./dmarc-ingest.component.scss']
})
export class DmarcIngestComponent {
  activeTab: 'xml' | 'email' = 'xml';
  xmlPayload = '';
  selectedFile: File | null = null;
  
  loading = false;
  error: string | null = null;

  constructor(
    private apiService: ApiService,
    private router: Router
  ) {}

  setTab(tab: 'xml' | 'email'): void {
    this.activeTab = tab;
    this.error = null;
  }

  onFileSelected(event: any): void {
    const file = event.target.files[0];
    if (file) {
      this.selectedFile = file;
    }
  }

  ingestXml(): void {
    if (!this.xmlPayload.trim()) return;

    this.loading = true;
    this.error = null;

    this.apiService.ingestDmarcXml(this.xmlPayload).subscribe({
      next: (report) => {
        this.router.navigate(['/dmarc/reports', report.id]);
      },
      error: (err) => {
        this.error = 'Failed to ingest XML. Please ensure the XML is valid.';
        this.loading = false;
        console.error(err);
      }
    });
  }

  ingestEmail(): void {
    if (!this.selectedFile) return;

    this.loading = true;
    this.error = null;

    this.apiService.ingestDmarcEmail(this.selectedFile).subscribe({
      next: (report) => {
        this.router.navigate(['/dmarc/reports', report.id]);
      },
      error: (err) => {
        this.error = 'Failed to process email file. Ensure it is a valid .eml with a DMARC attachment.';
        this.loading = false;
        console.error(err);
      }
    });
  }
}
