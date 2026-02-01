import { Injectable, inject, OnDestroy } from '@angular/core';
import { fromEvent, merge, Subject, timer } from 'rxjs';
import { throttleTime, takeUntil, switchMap, tap } from 'rxjs/operators';
import { AuthStore } from '../state/auth.store';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class SessionTimeoutService implements OnDestroy {
  private authStore = inject(AuthStore);
  private destroy$ = new Subject<void>();
  private warningShown = false;

  private readonly SESSION_TIMEOUT = environment.auth.sessionTimeout * 1000;
  private readonly WARNING_TIME = environment.auth.sessionTimeoutWarning * 1000;

  init(): void {
    if (!this.authStore.isAuthenticated()) {
      return;
    }

    const events$ = merge(
      fromEvent(document, 'mousemove'),
      fromEvent(document, 'mousedown'),
      fromEvent(document, 'keypress'),
      fromEvent(document, 'touchstart'),
      fromEvent(document, 'scroll')
    );

    events$.pipe(
      throttleTime(10000),
      tap(() => {
        this.authStore.updateLastActivity();
        this.warningShown = false;
      }),
      takeUntil(this.destroy$)
    ).subscribe();

    this.checkInactivity();
  }

  private checkInactivity(): void {
    timer(0, 60000).pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => {
      if (!this.authStore.isAuthenticated()) {
        return;
      }

      const lastActivity = this.authStore.lastActivity();
      if (!lastActivity) {
        return;
      }

      const now = new Date().getTime();
      const lastActivityTime = new Date(lastActivity).getTime();
      const inactiveTime = now - lastActivityTime;

      if (inactiveTime >= this.SESSION_TIMEOUT) {
        this.authStore.logout();
      } else if (inactiveTime >= (this.SESSION_TIMEOUT - this.WARNING_TIME) && !this.warningShown) {
        this.showWarning();
      }
    });
  }

  private showWarning(): void {
    this.warningShown = true;
    console.warn('Session will expire soon due to inactivity');
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
