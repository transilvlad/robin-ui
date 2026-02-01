import { Component } from '@angular/core';

@Component({
    selector: 'app-user-list',
    templateUrl: './user-list.component.html',
    standalone: false
})
export class UserListComponent {
  users = [
    {
      id: 1,
      name: 'Admin User',
      email: 'admin@robin-mta.local',
      role: 'Administrator',
      status: 'Active',
      lastLogin: '2 minutes ago',
      initials: 'AU'
    },
    {
      id: 2,
      name: 'John Doe',
      email: 'john.doe@example.com',
      role: 'User',
      status: 'Active',
      lastLogin: '1 day ago',
      initials: 'JD'
    },
    {
      id: 3,
      name: 'Sarah Smith',
      email: 'sarah.smith@example.com',
      role: 'Viewer',
      status: 'Inactive',
      lastLogin: '2 weeks ago',
      initials: 'SS'
    },
    {
      id: 4,
      name: 'System Service',
      email: 'system@robin-mta.local',
      role: 'Service Account',
      status: 'Active',
      lastLogin: 'Just now',
      initials: 'SS'
    },
    {
      id: 5,
      name: 'Test Account',
      email: 'test@example.com',
      role: 'User',
      status: 'Suspended',
      lastLogin: '3 months ago',
      initials: 'TA'
    }
  ];
}
