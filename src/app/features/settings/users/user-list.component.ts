import { Component } from '@angular/core';

@Component({
    selector: 'app-user-list',
    template: `
    <div class="p-6">
      <h1 class="text-3xl font-bold text-gray-900 mb-4">User Management</h1>
      <div class="bg-white shadow rounded-lg p-6">
        <p class="text-gray-600">User management interface will be implemented here.</p>
      </div>
    </div>
  `,
    standalone: false
})
export class UserListComponent {}
