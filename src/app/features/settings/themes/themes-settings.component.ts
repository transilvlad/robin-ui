import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ThemeService } from '../../../core/services/theme.service';
import { ThemeCatalogEntry } from '../../../core/models/theme.model';

@Component({
  selector: 'app-themes-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './themes-settings.component.html',
  styleUrl: './themes-settings.component.scss',
})
export class ThemesSettingsComponent {
  private themeService = inject(ThemeService);

  get bundledThemes(): ThemeCatalogEntry[] { return this.themeService.bundledThemes(); }
  get currentThemeId(): string { return this.themeService.currentThemeId(); }

  remoteUrl = '';
  loading = false;
  error = '';
  success = '';

  async selectTheme(id: string): Promise<void> {
    this.loading = true; this.error = ''; this.success = '';
    try {
      await this.themeService.loadTheme(`assets/themes/${id}.json5`);
      this.success = 'Theme applied and saved.';
    } catch {
      this.error = 'Failed to load theme.';
    } finally { this.loading = false; }
  }

  async loadFromUrl(): Promise<void> {
    if (!this.remoteUrl.trim()) return;
    this.loading = true; this.error = ''; this.success = '';
    try {
      await this.themeService.loadFromUrl(this.remoteUrl.trim());
      this.success = 'Remote theme loaded and applied.';
    } catch {
      this.error = 'Could not load theme from URL. Ensure it is a valid JSON5 file with CORS allowed.';
    } finally { this.loading = false; }
  }
}
