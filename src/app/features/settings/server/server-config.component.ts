import { Component } from '@angular/core';

@Component({
    selector: 'app-server-config',
    template: `
    <div class="p-6">
      <h1 class="text-3xl font-bold text-gray-900 mb-4">Server Configuration</h1>
      <div class="bg-white shadow rounded-lg p-6">
        <p class="text-gray-600">Server settings (listeners, ports, TLS) will be implemented here.</p>
      </div>
    </div>
  `,
    standalone: false
})
export class ServerConfigComponent {}
