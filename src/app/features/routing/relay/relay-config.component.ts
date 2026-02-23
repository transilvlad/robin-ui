import { Component } from '@angular/core';

@Component({
    selector: 'app-relay-config',
    template: `
    <div class="page-root">
      <div class="page-header">
        <h1>SMTP Relay Configuration</h1>
        <p class="page-subtitle">Configure outbound SMTP relay settings</p>
      </div>
      <div class="glass-panel p-6">
        <p style="color:var(--text-dim)">SMTP relay settings will be implemented here.</p>
      </div>
    </div>
  `,
    standalone: false
})
export class RelayConfigComponent {}
