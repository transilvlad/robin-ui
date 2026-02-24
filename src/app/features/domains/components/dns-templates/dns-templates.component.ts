import { Component, OnInit } from '@angular/core';
import { DnsTemplateService, CreateDnsTemplateRequest } from '../../services/dns-template.service';
import { DnsTemplate } from '../../models/domain.models';

@Component({
  selector: 'app-dns-templates',
  templateUrl: './dns-templates.component.html',
  styleUrls: ['./dns-templates.component.scss'],
  standalone: false,
})
export class DnsTemplatesComponent implements OnInit {
  templates: DnsTemplate[] = [];
  loading = false;
  error: string | null = null;

  showForm = false;
  editingTemplate: DnsTemplate | null = null;

  formData: CreateDnsTemplateRequest = {
    name: '',
    description: '',
    records: '[]',
  };

  exampleRecords = JSON.stringify([
    { type: 'MX', name: '@', value: 'mail.example.com', priority: 10, ttl: 3600 },
    { type: 'TXT', name: '@', value: 'v=spf1 include:_spf.example.com ~all', ttl: 3600 },
  ], null, 2);

  constructor(private dnsTemplateService: DnsTemplateService) {}

  ngOnInit(): void {
    this.loadTemplates();
  }

  loadTemplates(): void {
    this.loading = true;
    this.error = null;
    this.dnsTemplateService.getTemplates().subscribe(result => {
      this.loading = false;
      if (result.ok) {
        this.templates = result.value;
      } else {
        this.error = 'Failed to load DNS templates';
      }
    });
  }

  openAddForm(): void {
    this.editingTemplate = null;
    this.formData = { name: '', description: '', records: '[]' };
    this.showForm = true;
  }

  editTemplate(template: DnsTemplate): void {
    this.editingTemplate = template;
    this.formData = {
      name: template.name,
      description: template.description ?? '',
      records: template.records,
    };
    this.showForm = true;
  }

  cancelForm(): void {
    this.showForm = false;
    this.editingTemplate = null;
  }

  saveTemplate(): void {
    if (!this.formData.name) return;

    if (!this.isValidJson(this.formData.records)) {
      this.error = 'Records must be valid JSON';
      return;
    }

    if (this.editingTemplate) {
      this.dnsTemplateService.updateTemplate(this.editingTemplate.id, this.formData).subscribe(result => {
        if (result.ok) {
          this.templates = this.templates.map(t => t.id === result.value.id ? result.value : t);
          this.cancelForm();
        } else {
          this.error = 'Failed to update template';
        }
      });
    } else {
      this.dnsTemplateService.createTemplate(this.formData).subscribe(result => {
        if (result.ok) {
          this.templates = [...this.templates, result.value];
          this.cancelForm();
        } else {
          this.error = 'Failed to create template';
        }
      });
    }
  }

  deleteTemplate(template: DnsTemplate): void {
    if (!confirm(`Delete DNS template "${template.name}"?`)) return;
    this.dnsTemplateService.deleteTemplate(template.id).subscribe(result => {
      if (result.ok) {
        this.templates = this.templates.filter(t => t.id !== template.id);
      } else {
        this.error = 'Failed to delete template';
      }
    });
  }

  getRecordCount(records: string): number {
    try {
      const parsed = JSON.parse(records);
      return Array.isArray(parsed) ? parsed.length : 0;
    } catch {
      return 0;
    }
  }

  isValidJson(value: string): boolean {
    try {
      JSON.parse(value);
      return true;
    } catch {
      return false;
    }
  }

  useExample(): void {
    this.formData.records = this.exampleRecords;
  }
}
