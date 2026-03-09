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
    {
      "type": "MX",
      "name": "@",
      "value": "mail.example.com.",
      "priority": 10,
      "ttl": 3600,
      "_comment": "Primary mail exchanger. Replace mail.example.com with your mail server hostname."
    },
    {
      "type": "MX",
      "name": "@",
      "value": "mail2.example.com.",
      "priority": 20,
      "ttl": 3600,
      "_comment": "Secondary/backup mail exchanger (optional). Remove if not using a backup MX."
    },
    {
      "type": "TXT",
      "name": "@",
      "value": "v=spf1 mx a:mail.example.com ip4:0.0.0.0/32 ~all",
      "ttl": 3600,
      "_comment": "SPF: Authorise your MX host and server IP to send mail. Replace ip4 with your server IP. Use -all (hard fail) for strict enforcement once validated."
    },
    {
      "type": "TXT",
      "name": "_dmarc",
      "value": "v=DMARC1; p=quarantine; rua=mailto:dmarc@example.com; ruf=mailto:dmarc@example.com; sp=quarantine; adkim=s; aspf=s; pct=100; fo=1",
      "ttl": 3600,
      "_comment": "DMARC: p=quarantine quarantines failing mail. Start with p=none for monitoring. Use p=reject for strict enforcement. rua/ruf are aggregate/forensic report addresses."
    },
    {
      "type": "TXT",
      "name": "mail._domainkey",
      "value": "v=DKIM1; k=rsa; p=YOUR_DKIM_PUBLIC_KEY",
      "ttl": 3600,
      "_comment": "DKIM: Replace selector (mail) and public key. Managed automatically via DKIM Management if using this platform."
    },
    {
      "type": "TXT",
      "name": "_mta-sts",
      "value": "v=STSv1; id=20240101000000",
      "ttl": 3600,
      "_comment": "MTA-STS: Signals that this domain supports strict TLS. Update id (timestamp) whenever the policy changes to force re-fetch."
    },
    {
      "type": "CNAME",
      "name": "mta-sts",
      "value": "mta-sts-policy.example.com.",
      "ttl": 3600,
      "_comment": "MTA-STS: The https://mta-sts.yourdomain.com/.well-known/mta-sts.txt policy file endpoint. Deployed automatically via MTA-STS management if using this platform."
    },
    {
      "type": "TXT",
      "name": "_smtp._tls",
      "value": "v=TLSRPTv1; rua=mailto:tlsrpt@example.com",
      "ttl": 3600,
      "_comment": "SMTP TLS Reporting (RFC 8460): Receive daily TLS failure reports from other mail servers. Replace with your reporting email address."
    },
    {
      "type": "CNAME",
      "name": "autoconfig",
      "value": "mail.example.com.",
      "ttl": 3600,
      "_comment": "Thunderbird/Mozilla mail client auto-configuration endpoint (https://autoconfig.yourdomain.com/mail/config-v1.1.xml)."
    },
    {
      "type": "CNAME",
      "name": "autodiscover",
      "value": "mail.example.com.",
      "ttl": 3600,
      "_comment": "Outlook/Exchange autodiscover endpoint (https://autodiscover.yourdomain.com/autodiscover/autodiscover.xml)."
    },
    {
      "type": "TXT",
      "name": "default._bimi",
      "value": "v=BIMI1; l=https://example.com/brand/logo.svg; a=https://example.com/brand/vmc.pem",
      "ttl": 3600,
      "_comment": "BIMI (Brand Indicators for Message Identification): Displays your logo in supporting mail clients (Gmail, Yahoo). Requires a VMC certificate (a=) for Gmail. l= must be an SVG Tiny PS file served over HTTPS."
    }
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
