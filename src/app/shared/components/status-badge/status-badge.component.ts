import { Component, Input } from '@angular/core';

@Component({
    selector: 'app-status-badge',
    templateUrl: './status-badge.component.html',
    styleUrls: ['./status-badge.component.scss'],
    standalone: false
})
export class StatusBadgeComponent {
  @Input() status: 'UP' | 'DOWN' | 'UNKNOWN' | 'active' | 'inactive' | 'warning' | 'error' =
    'inactive';
  @Input() label?: string;
  @Input() size: 'sm' | 'md' | 'lg' = 'md';
  @Input() showDot = true;

  get badgeClass(): string {
    const sizeClasses = {
      sm: 'text-xs px-2 py-0.5',
      md: 'text-xs px-2.5 py-0.5',
      lg: 'text-sm px-3 py-1',
    };

    const baseClasses = `badge ${sizeClasses[this.size]}`;

    switch (this.status) {
      case 'UP':
      case 'active':
        return `${baseClasses} border-transparent bg-robin-green/15 text-robin-green`;
      case 'DOWN':
      case 'error':
        return `${baseClasses} border-transparent bg-destructive/15 text-destructive`;
      case 'warning':
      case 'UNKNOWN':
        return `${baseClasses} border-transparent bg-warning/15 text-warning`;
      case 'inactive':
      default:
        return `${baseClasses} border-transparent bg-muted text-muted-foreground`;
    }
  }

  get dotClass(): string {
    switch (this.status) {
      case 'UP':
      case 'active':
        return 'bg-robin-green';
      case 'DOWN':
      case 'error':
        return 'bg-destructive';
      case 'warning':
      case 'UNKNOWN':
        return 'bg-warning';
      case 'inactive':
      default:
        return 'bg-muted-foreground';
    }
  }

  get displayLabel(): string {
    return this.label || this.status;
  }
}
