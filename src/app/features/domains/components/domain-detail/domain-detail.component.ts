import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DomainService } from '../../services/domain.service';
import { Domain } from '../../models/domain.models';

type TabId = 'overview' | 'dns' | 'dkim' | 'mta-sts' | 'health';

interface Tab {
  id: TabId;
  label: string;
}

@Component({
  selector: 'app-domain-detail',
  templateUrl: './domain-detail.component.html',
  styleUrls: ['./domain-detail.component.scss'],
  standalone: false,
})
export class DomainDetailComponent implements OnInit {
  domain: Domain | null = null;
  loading = false;
  error: string | null = null;
  activeTab: TabId = 'overview';

  tabs: Tab[] = [
    { id: 'overview', label: 'Overview' },
    { id: 'dns',      label: 'DNS Records' },
    { id: 'dkim',     label: 'DKIM' },
    { id: 'mta-sts',  label: 'MTA-STS' },
    { id: 'health',   label: 'Health' },
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private domainService: DomainService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) {
      this.loadDomain(id);
    }
  }

  loadDomain(id: number): void {
    this.loading = true;
    this.error = null;
    this.domainService.getDomain(id).subscribe(result => {
      this.loading = false;
      if (result.ok) {
        this.domain = result.value;
      } else {
        this.error = 'Failed to load domain details';
      }
    });
  }

  setActiveTab(tab: TabId): void {
    this.activeTab = tab;
  }

  isActiveTab(tab: TabId): boolean {
    return this.activeTab === tab;
  }

  getStatusClass(status?: string): string {
    switch (status) {
      case 'ACTIVE':  return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300';
      case 'ERROR':   return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300';
      default:        return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300';
    }
  }

  goBack(): void {
    this.router.navigate(['/domains']);
  }
}
