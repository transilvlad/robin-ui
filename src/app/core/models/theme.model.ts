export interface ThemeColors {
  backgrounds: {
    base: string;
    blur: string;
    layer_1: string;
    panel: string;
  };
  borders: {
    dim: string;
    mid: string;
    bright: string;
  };
  text: {
    main: string;
    dim: string;
  };
  accents: {
    primary: string;
    dim: string;
  };
  status: {
    success: string;
    warning: string;
    danger: string;
  };
}

export interface ThemeTypography {
  fonts: {
    primary: string;
    mono: string;
  };
  weights: {
    regular: number;
    medium: number;
    semibold: number;
    bold: number;
  };
  sizes: {
    base: string;
    small: string;
    large: string;
    hero: string;
  };
}

export interface ThemeLayout {
  structure: {
    type: string;
    columns: string;
    rows: string;
  };
  spacing: {
    header_padding: string;
    sidebar_padding: string;
    main_padding: string;
    panel_padding: string;
    grid_gap: string;
  };
  radii: {
    panel: string;
    button: string;
    badge: string;
    avatar: string;
  };
}

export interface ThemeEffects {
  glassmorphism: {
    global_blur: string;
    panel_blur: string;
    header_blur: string;
    sidebar_blur: string;
  };
  shadows: {
    panel: string;
    button: string;
  };
}

export interface ThemeAssets {
  background_image: string;
  icons?: string;
}

export interface ThemeMeta {
  id: string;
  name: string;
  version: string;
  description: string;
  author?: string;
}

export interface Theme {
  meta: ThemeMeta;
  colors: ThemeColors;
  typography: ThemeTypography;
  layout: ThemeLayout;
  effects: ThemeEffects;
  assets: ThemeAssets;
}

export interface ThemeCatalogEntry {
  id: string;
  name: string;
  description: string;
  path: string;
  preview_color: string;
}
