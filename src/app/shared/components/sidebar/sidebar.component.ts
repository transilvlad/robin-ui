import { Component } from '@angular/core';

interface MenuItem {
  label: string;
  icon: string;
  route: string;
  children?: MenuItem[];
}

@Component({
    selector: 'app-sidebar',
    templateUrl: './sidebar.component.html',
    styleUrls: ['./sidebar.component.scss'],
    standalone: false
})
export class SidebarComponent {
  menuItems: MenuItem[] = [
    {
      label: 'Dashboard',
      icon: 'dashboard',
      route: '/dashboard',
    },
    {
      label: 'Email',
      icon: 'mail',
      route: '/email',
      children: [
        { label: 'Queue', icon: 'queue', route: '/email/queue' },
        { label: 'Storage', icon: 'folder', route: '/email/storage' },
      ],
    },
    {
      label: 'Security',
      icon: 'shield',
      route: '/security',
      children: [
        { label: 'ClamAV', icon: 'security', route: '/security/clamav' },
        { label: 'Rspamd', icon: 'filter', route: '/security/rspamd' },
        { label: 'Blocklist', icon: 'block', route: '/security/blocklist' },
      ],
    },
    {
      label: 'Routing',
      icon: 'route',
      route: '/routing',
      children: [
        { label: 'Relay Config', icon: 'send', route: '/routing/relay' },
        { label: 'Webhooks', icon: 'webhook', route: '/routing/webhooks' },
      ],
    },
    {
      label: 'Monitoring',
      icon: 'monitoring',
      route: '/monitoring',
      children: [
        { label: 'Metrics', icon: 'analytics', route: '/monitoring/metrics' },
        { label: 'Logs', icon: 'list', route: '/monitoring/logs' },
      ],
    },
    {
      label: 'Settings',
      icon: 'settings',
      route: '/settings',
      children: [
        { label: 'Server', icon: 'server', route: '/settings/server' },
        { label: 'Users', icon: 'people', route: '/settings/users' },
        { label: 'Dovecot', icon: 'dovecot', route: '/settings/dovecot' },
      ],
    },
  ];

  expandedItems: Set<string> = new Set();
  isCollapsed = false;

  toggleExpand(item: MenuItem): void {
    if (this.expandedItems.has(item.label)) {
      this.expandedItems.delete(item.label);
    } else {
      this.expandedItems.add(item.label);
    }
  }

  isExpanded(item: MenuItem): boolean {
    return this.expandedItems.has(item.label);
  }

  toggleCollapse(): void {
    this.isCollapsed = !this.isCollapsed;
  }

  // Map icon names to SVG paths (Lucide icons)
  getIconPath(icon: string): string {
    const icons: { [key: string]: string } = {
      dashboard:
        'M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M9 22V12h6v10',
      mail: 'M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z M22 6l-10 7L2 6',
      queue:
        'M3 12h18 M3 6h18 M3 18h18',
      folder:
        'M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z',
      shield:
        'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z',
      security:
        'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z M9 12l2 2 4-4',
      filter: 'M22 3H2l8 9.46V19l4 2v-8.54L22 3z',
      block: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 0L2 12 M4.93 4.93l14.14 14.14',
      route: 'M3 17h18 M3 12l4-4 4 4 4-4 4 4',
      send: 'M22 2L11 13 M22 2l-7 20-4-9-9-4 20-7z',
      webhook:
        'M18 16.98h-5.99c-1.66 0-3.01-1.34-3.01-3s1.35-3 3.01-3h5.99 M12 5.02h5.99c1.66 0 3.01 1.34 3.01 3s-1.35 3-3.01 3H12 M8.97 21.01L6 18l2.97-3.01 M15.03 2.99L18 6l-2.97 3.01',
      monitoring:
        'M18 20V10 M12 20V4 M6 20v-6',
      analytics:
        'M12 20V10 M18 20V4 M6 20v-4',
      list: 'M3 12h18 M3 6h18 M3 18h18',
      settings:
        'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z',
      server:
        'M2 4h20v5H2z M2 14h20v5H2z M6 7h.01 M6 17h.01',
      people:
        'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 7a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M23 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75',
      dovecot:
        'M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4L12 14.01l-3-3',
    };
    return icons[icon] || icons['dashboard'];
  }
}
