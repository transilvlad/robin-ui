import { Injectable, signal, effect, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import JSON5 from 'json5';
import { Theme, ThemeCatalogEntry } from '../models/theme.model';
import { firstValueFrom } from 'rxjs';

const STORAGE_KEY = 'robin-theme';
const STORAGE_URL_KEY = 'robin-theme-url';
const DEFAULT_THEME_PATH = 'assets/themes/hybrid-enterprise-palette.json5';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private http = inject(HttpClient);

  readonly currentTheme = signal<Theme | null>(null);
  readonly currentThemeId = signal<string>('hybrid-enterprise-palette');
  readonly bundledThemes = signal<ThemeCatalogEntry[]>([]);

  /** Load and apply the last-saved theme from localStorage, falling back to default. */
  async loadSavedTheme(): Promise<void> {
    this.listBundledThemes().then(list => this.bundledThemes.set(list)).catch(() => {});
    const savedUrl = localStorage.getItem(STORAGE_URL_KEY);
    const savedId = localStorage.getItem(STORAGE_KEY);

    if (savedUrl) {
      try {
        await this.loadFromUrl(savedUrl);
        return;
      } catch {
        // Fall through to bundled theme
      }
    }

    const path = savedId
      ? `assets/themes/${savedId}.json5`
      : DEFAULT_THEME_PATH;

    try {
      await this.loadTheme(path);
    } catch {
      await this.loadTheme(DEFAULT_THEME_PATH);
    }
  }

  /** Load a theme from a relative assets path or absolute URL. */
  async loadTheme(path: string): Promise<void> {
    const text = await firstValueFrom(
      this.http.get(path, { responseType: 'text' })
    );
    const theme: Theme = JSON5.parse(text);
    this.applyTheme(theme);
    localStorage.setItem(STORAGE_KEY, theme.meta.id);
    localStorage.removeItem(STORAGE_URL_KEY);
  }

  /** Load a theme from an arbitrary remote URL (CORS must be allowed). */
  async loadFromUrl(url: string): Promise<void> {
    const text = await firstValueFrom(
      this.http.get(url, { responseType: 'text' })
    );
    const theme: Theme = JSON5.parse(text);
    this.applyTheme(theme);
    localStorage.setItem(STORAGE_URL_KEY, url);
    localStorage.setItem(STORAGE_KEY, theme.meta.id);
  }

  /** List all bundled themes from the catalogue. */
  async listBundledThemes(): Promise<ThemeCatalogEntry[]> {
    const text = await firstValueFrom(
      this.http.get('assets/themes/index.json5', { responseType: 'text' })
    );
    return JSON5.parse(text);
  }

  /** Apply a parsed Theme object to the document root CSS variables. */
  applyTheme(theme: Theme): void {
    const root = document.documentElement;
    const s = root.style;
    const { colors, typography, layout, effects, assets } = theme;

    // ── Backgrounds ──────────────────────────────────────────────────────
    s.setProperty('--bg-blur',    colors.backgrounds.blur);
    s.setProperty('--bg-layer-1', colors.backgrounds.layer_1);
    s.setProperty('--bg-panel',   colors.backgrounds.panel);
    s.setProperty('--bg-image',   assets.background_image
      ? `url('${assets.background_image}')` : 'none');

    // ── Borders ───────────────────────────────────────────────────────────
    s.setProperty('--border-dim',    colors.borders.dim);
    s.setProperty('--border-mid',    colors.borders.mid);
    s.setProperty('--border-bright', colors.borders.bright);

    // ── Text ──────────────────────────────────────────────────────────────
    s.setProperty('--text-main', colors.text.main);
    s.setProperty('--text-dim',  colors.text.dim);

    // ── Accents ───────────────────────────────────────────────────────────
    s.setProperty('--accent',     colors.accents.primary);
    s.setProperty('--accent-dim', colors.accents.dim);

    // ── Status ────────────────────────────────────────────────────────────
    s.setProperty('--status-success', colors.status.success);
    s.setProperty('--status-warning', colors.status.warning);
    s.setProperty('--status-danger',  colors.status.danger);

    // ── Typography ────────────────────────────────────────────────────────
    s.setProperty('--font-primary', typography.fonts.primary);
    s.setProperty('--font-mono',    typography.fonts.mono);

    // ── Layout radii ──────────────────────────────────────────────────────
    s.setProperty('--radius-panel',  layout.radii.panel);
    s.setProperty('--radius-button', layout.radii.button);
    s.setProperty('--radius-badge',  layout.radii.badge);

    // ── Bridge to existing shadcn/ui + Tailwind CSS vars ──────────────────
    s.setProperty('--background',           this.toHslVar(colors.backgrounds.base) ?? colors.backgrounds.base);
    s.setProperty('--foreground',           colors.text.main);
    s.setProperty('--card',                 colors.backgrounds.panel);
    s.setProperty('--card-foreground',      colors.text.main);
    s.setProperty('--popover',              colors.backgrounds.panel);
    s.setProperty('--popover-foreground',   colors.text.main);
    s.setProperty('--primary',              colors.accents.primary);
    s.setProperty('--primary-foreground',   '#ffffff');
    s.setProperty('--secondary',            colors.backgrounds.layer_1);
    s.setProperty('--secondary-foreground', colors.text.main);
    s.setProperty('--muted',                colors.backgrounds.layer_1);
    s.setProperty('--muted-foreground',     colors.text.dim);
    s.setProperty('--accent',              colors.accents.primary);
    s.setProperty('--accent-foreground',   '#ffffff');
    s.setProperty('--destructive',          colors.status.danger);
    s.setProperty('--destructive-foreground', '#ffffff');
    s.setProperty('--border',              colors.borders.mid);
    s.setProperty('--input',               colors.borders.mid);
    s.setProperty('--ring',                colors.accents.primary);
    s.setProperty('--sidebar-background',  colors.backgrounds.layer_1);
    s.setProperty('--sidebar-foreground',  colors.text.main);
    s.setProperty('--sidebar-border',      colors.borders.mid);
    s.setProperty('--sidebar-primary',     colors.accents.primary);
    s.setProperty('--sidebar-primary-foreground', '#ffffff');
    s.setProperty('--sidebar-accent',      colors.backgrounds.panel);
    s.setProperty('--sidebar-accent-foreground', colors.text.main);

    // ── Body glassmorphic background ──────────────────────────────────────
    document.body.style.setProperty('background-image',
      assets.background_image ? `url('${assets.background_image}')` : 'none');

    this.currentTheme.set(theme);
    this.currentThemeId.set(theme.meta.id);
  }

  /** Passthrough: HSL strings stay as-is; non-HSL values returned as null. */
  private toHslVar(value: string): string | null {
    return value.startsWith('hsl') ? value : null;
  }
}
