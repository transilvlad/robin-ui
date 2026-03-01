import { NgModule, Optional, SkipSelf, APP_INITIALIZER } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { ErrorInterceptor } from './interceptors/error.interceptor';
import { AuthStore } from './state/auth.store';
import { ThemeService } from './services/theme.service';

/**
 * Auth Initializer Factory
 *
 * Ensures authentication state is restored from storage before the app starts routing.
 * This prevents race conditions where guards run before auth state is initialized.
 */
export function initializeAuth(authStore: InstanceType<typeof AuthStore>): () => Promise<void> {
  return () => authStore.autoLogin();
}

/** Theme Initializer Factory — loads and applies saved/default theme before first render. */
export function initializeTheme(themeService: ThemeService): () => Promise<void> {
  return () => themeService.loadSavedTheme();
}

@NgModule({
  declarations: [],
  imports: [CommonModule, HttpClientModule],
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: initializeAuth,
      deps: [AuthStore],
      multi: true,
    },
    {
      provide: APP_INITIALIZER,
      useFactory: initializeTheme,
      deps: [ThemeService],
      multi: true,
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true,
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: ErrorInterceptor,
      multi: true,
    },
  ],
})
export class CoreModule {
  constructor(@Optional() @SkipSelf() parentModule: CoreModule) {
    if (parentModule) {
      throw new Error(
        'CoreModule is already loaded. Import it in the AppModule only.'
      );
    }
  }
}
