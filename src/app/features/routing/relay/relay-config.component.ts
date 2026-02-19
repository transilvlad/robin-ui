import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-relay-config',
    template: `
    <div class="p-6">
      <h1 class="text-3xl font-bold text-gray-900 mb-4">SMTP Relay Configuration</h1>
      <div class="bg-white shadow rounded-lg p-6">
        <p class="text-gray-600">SMTP relay settings will be implemented here.</p>
      </div>
    </div>
  `,
    standalone: true,
    imports: [CommonModule]
})
export class RelayConfigComponent {}
