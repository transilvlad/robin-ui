import { Component, OnInit, inject } from '@angular/core';
import { AuthStore } from './core/state/auth.store';

/**
 * Root Application Component
 *
 * Initializes authentication on app startup by attempting auto-login
 * from stored session or refresh token.
 */
@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss'],
    standalone: false
})
export class AppComponent implements OnInit {
  title = 'Robin MTA Management';
  private authStore = inject(AuthStore);

  ngOnInit(): void {
    // Attempt auto-login on app initialization
    // This will restore session from sessionStorage or refresh token cookie
    this.authStore.autoLogin();
  }
}
