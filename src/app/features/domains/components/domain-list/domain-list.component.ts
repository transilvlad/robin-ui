import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DomainService } from '../../services/domain.service';
import { Domain } from '../../models/domain.models';

@Component({
  selector: 'app-domain-list',
  templateUrl: './domain-list.component.html',
  styleUrls: ['./domain-list.component.scss'],
  standalone: false,
})
export class DomainListComponent implements OnInit {
  domains: Domain[] = [];
  loading = false;
  showAddModal = false;
  newDomainName = '';
  error: string | null = null;

  constructor(
    private domainService: DomainService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadDomains();
  }

  loadDomains(): void {
    this.loading = true;
    this.error = null;
    this.domainService.getDomains().subscribe(result => {
      this.loading = false;
      if (result.ok) {
        this.domains = result.value.content;
      } else {
        this.error = 'Failed to load domains';
      }
    });
  }

  viewDomain(domain: Domain): void {
    this.router.navigate(['/domains', domain.id]);
  }

  openAddModal(): void {
    this.showAddModal = true;
  }

  closeAddModal(): void {
    this.showAddModal = false;
    this.newDomainName = '';
  }

  addDomain(): void {
    if (!this.newDomainName.trim()) return;
    this.domainService.createDomain(this.newDomainName.trim()).subscribe(result => {
      if (result.ok) {
        this.domains = [...this.domains, result.value];
        this.closeAddModal();
      } else {
        this.error = 'Failed to create domain';
      }
    });
  }

  deleteDomain(domain: Domain, event: Event): void {
    event.stopPropagation();
    if (!confirm(`Delete domain ${domain.domain}?`)) return;
    this.domainService.deleteDomain(domain.id).subscribe(result => {
      if (result.ok) {
        this.domains = this.domains.filter(d => d.id !== domain.id);
      }
    });
  }

  getStatusClass(status?: string): string {
    switch (status) {
      case 'ACTIVE':  return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300';
      case 'ERROR':   return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300';
      default:        return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300';
    }
  }
}
