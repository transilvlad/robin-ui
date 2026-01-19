import { Component } from '@angular/core';

@Component({
    selector: 'app-log-viewer',
    template: `
    <div class="p-6">
      <h1 class="text-3xl font-bold text-gray-900 mb-4">Log Viewer</h1>
      <div class="bg-white shadow rounded-lg p-6">
        <p class="text-gray-600">Real-time log viewer will be implemented here.</p>
      </div>
    </div>
  `,
    standalone: false
})
export class LogViewerComponent {}
