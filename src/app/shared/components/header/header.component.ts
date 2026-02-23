import { Component, OnInit, OnDestroy, inject, ElementRef, HostListener, ChangeDetectorRef } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { AuthStore } from '@core/state/auth.store';
import { interval, of, Subscription } from 'rxjs';
import { switchMap, startWith, catchError } from 'rxjs/operators';

@Component({
    selector: 'app-header',
    templateUrl: './header.component.html',
    styleUrls: ['./header.component.scss'],
    standalone: false
})
export class HeaderComponent implements OnInit, OnDestroy {
  serverStatus: 'UP' | 'DOWN' | 'UNKNOWN' = 'UNKNOWN';
  uptime = '';
  showUserMenu = false;
  pageTitle = 'Dashboard';
  private healthSubscription?: Subscription;
  protected authStore = inject(AuthStore);
  private elementRef = inject(ElementRef);
  private cdr = inject(ChangeDetectorRef);

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    // Poll health status every 30 seconds
    this.healthSubscription = interval(30000)
      .pipe(
        startWith(0),
        switchMap(() =>
          this.apiService.getHealth().pipe(
            catchError(() => of({ status: 'DOWN', uptime: '' }))
          )
        )
      )
      .subscribe({
        next: (health) => {
          this.serverStatus = health.status;
          this.uptime = health.uptime || '';
        },
      });
  }

  ngOnDestroy(): void {
    this.healthSubscription?.unsubscribe();
  }

  toggleUserMenu(event?: MouseEvent): void {
    if (event) {
      event.stopPropagation();
    }
    this.showUserMenu = !this.showUserMenu;
    this.cdr.markForCheck();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      if (this.showUserMenu) {
        this.showUserMenu = false;
        this.cdr.markForCheck();
      }
    }
  }

  async logout(): Promise<void> {
    this.showUserMenu = false;
    this.cdr.markForCheck();
    await this.authStore.logout();
  }
}
