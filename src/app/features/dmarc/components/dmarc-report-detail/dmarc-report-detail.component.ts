import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService } from '@core/services/api.service';
import { DmarcReport } from '@features/dmarc/models';

@Component({
  selector: 'app-dmarc-report-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dmarc-report-detail.component.html',
  styleUrls: ['./dmarc-report-detail.component.scss']
})
export class DmarcReportDetailComponent implements OnInit {
  report: DmarcReport | null = null;
  loading = false;
  error: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private apiService: ApiService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadReport(id);
    } else {
      this.error = 'Report ID not found';
    }
  }

  loadReport(id: string): void {
    this.loading = true;
    this.apiService.getDmarcReport(id).subscribe({
      next: (data) => {
        this.report = data;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load report details';
        this.loading = false;
        console.error(err);
      }
    });
  }

  getBadgeClass(ipClass: string): string {
    switch (ipClass) {
      case 'OWN': return 'bg-green-100 text-green-800';
      case 'AUTHORIZED': return 'bg-blue-100 text-blue-800';
      case 'DKIM_FORWARDER': return 'bg-yellow-100 text-yellow-800';
      case 'FORWARDER': return 'bg-orange-100 text-orange-800';
      case 'UNKNOWN': return 'bg-red-100 text-red-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  }
}
