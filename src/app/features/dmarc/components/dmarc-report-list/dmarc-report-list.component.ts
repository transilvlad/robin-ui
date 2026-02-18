import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { ApiService } from '@core/services/api.service';
import { DmarcReport, DmarcReportList } from '@features/dmarc/models';

@Component({
  selector: 'app-dmarc-report-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dmarc-report-list.component.html',
  styleUrls: ['./dmarc-report-list.component.scss']
})
export class DmarcReportListComponent implements OnInit {
  reports: DmarcReport[] = [];
  total = 0;
  page = 0;
  size = 20;
  loading = false;
  error: string | null = null;
  Math = Math;

  constructor(private apiService: ApiService, private router: Router) {}

  ngOnInit(): void {
    this.loadReports();
  }

  loadReports(): void {
    this.loading = true;
    this.error = null;
    this.apiService.getDmarcReports(this.page, this.size).subscribe({
      next: (data: DmarcReportList) => {
        this.reports = data.reports;
        this.total = data.total;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load reports';
        this.loading = false;
        console.error(err);
      }
    });
  }

  nextPage(): void {
    if ((this.page + 1) * this.size < this.total) {
      this.page++;
      this.loadReports();
    }
  }

  prevPage(): void {
    if (this.page > 0) {
      this.page--;
      this.loadReports();
    }
  }

  viewReport(id: string): void {
    this.router.navigate(['/dmarc/reports', id]);
  }
}
