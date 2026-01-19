import { Component } from '@angular/core';

@Component({
    selector: 'app-metrics-dashboard',
    template: `
    <div class="p-6">
      <h1 class="text-3xl font-bold text-gray-900 mb-4">Metrics Dashboard</h1>
      <div class="bg-white shadow rounded-lg p-6">
        <p class="text-gray-600">Prometheus/Graphite metrics visualization will be implemented here.</p>
      </div>
    </div>
  `,
    standalone: false
})
export class MetricsDashboardComponent {}
