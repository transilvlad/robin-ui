import { Component } from '@angular/core';

@Component({
    selector: 'app-webhooks',
    template: `
    <div class="page-root">
      <div class="page-header">
        <h1>Webhooks</h1>
        <p class="page-subtitle">Configure webhook endpoints for email events</p>
      </div>
      <div class="glass-panel p-6">
        <p style="color:var(--text-dim)">Webhook configuration for email events will be implemented here.</p>
      </div>
    </div>
  `,
    standalone: false
})
export class WebhooksComponent {}
