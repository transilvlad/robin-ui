import { Component, inject, signal, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ThemeService } from '../../../core/services/theme.service';
import { ThemeCatalogEntry } from '../../../core/models/theme.model';

@Component({
  selector: 'app-theme-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './theme-picker.component.html',
  styleUrl: './theme-picker.component.scss',
})
export class ThemePickerComponent {
  private themeService = inject(ThemeService);

  open = signal(false);
  remoteUrl = signal('');
  loading = signal(false);
  error = signal('');

  get currentTheme() { return this.themeService.currentTheme(); }
  get bundledThemes(): ThemeCatalogEntry[] { return this.themeService.bundledThemes(); }

  @HostListener('document:keydown.escape')
  close() { this.open.set(false); }

  toggle() { this.open.update(v => !v); this.error.set(''); }

  async selectTheme(id: string) {
    this.loading.set(true);
    this.error.set('');
    try {
      await this.themeService.loadTheme(id);
      this.open.set(false);
    } catch {
      this.error.set('Failed to load theme.');
    } finally {
      this.loading.set(false);
    }
  }

  async loadFromUrl() {
    const url = this.remoteUrl().trim();
    if (!url) return;
    this.loading.set(true);
    this.error.set('');
    try {
      await this.themeService.loadFromUrl(url);
      this.open.set(false);
    } catch {
      this.error.set('Could not load theme from URL. Make sure it is a valid JSON5 file.');
    } finally {
      this.loading.set(false);
    }
  }
}
