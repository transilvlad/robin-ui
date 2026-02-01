import { Component } from '@angular/core';

/**
 * Root Application Component
 *
 * Note: Authentication initialization is handled by APP_INITIALIZER in CoreModule,
 * which ensures auth state is restored before routing begins. This prevents race
 * conditions where guards run before auth state is ready.
 */
@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss'],
    standalone: false
})
export class AppComponent {
  title = 'Robin MTA Management';
}
