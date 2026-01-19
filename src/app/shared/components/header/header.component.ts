import { Component, OnInit, OnDestroy } from '@angular/core';
import { ApiService } from '@core/services/api.service';
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
  private healthSubscription?: Subscription;

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
}
