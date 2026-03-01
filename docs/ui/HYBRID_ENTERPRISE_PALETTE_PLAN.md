# Robin UI — Hybrid Enterprise Palette Redesign

## Problem Statement
Robin UI (Angular 21, Tailwind, Angular Material) uses a light/hardcoded CSS-var theme based on Robin orange/green brand colors. The goal is to adopt the **Hybrid Enterprise Palette** visual design from the Sanctum mockup as the new default look-and-feel, while adding a **JSON5-driven runtime theme system** so any session can switch themes by loading a new `.json5` file — either from bundled assets or a remote URL.

## Decisions Made (confirmed with user)
| Question | Answer |
|---|---|
| Navigation/content scope | Restructure sidebar + dashboard widgets to match mockup layout, adapted to MTA context |
| Theme file location | Both: bundled in `src/assets/themes/` **and** loadable from remote URL |
| Theme switcher location | Both: quick-picker in header **and** full management in Settings → Themes |
| Glassmorphic background | Yes — full blur + configurable background image via `assets.background_image` in theme |
| Theme format | **JSON5** (already used project-wide; use `json5` npm package) |
| HTML mockup | Standalone preview in session `files/` folder |

## Counter-Proposal (accepted): JSON5 over JSONC
The project already uses JSON5 for all its configs (`cfg/*.json5`, test fixtures). Using JSONC would require a separate `strip-json-comments` parser. JSON5 is a strict superset that natively supports comments and is the idiomatic choice here. The `json5` npm package is well-maintained and tiny.

## Architecture Overview

### Theme System
```
src/assets/themes/
  index.json5                         ← catalogue of bundled themes
  hybrid-enterprise-palette.json5     ← new default (amber + glassmorphism)
  robin-default.json5                 ← existing light theme
  robin-dark.json5                    ← existing dark theme

src/app/core/models/theme.model.ts    ← TypeScript interfaces
src/app/core/services/theme.service.ts ← loads JSON5, writes CSS vars, persists
src/app/shared/components/theme-picker/ ← header dropdown + URL loader
src/app/features/settings/themes/    ← full management page
```

### CSS Variable Strategy
ThemeService maps JSON5 theme fields → CSS custom properties on `:root`. This bridges the new theme system with existing Tailwind + shadcn/ui CSS var consumers:

| Theme JSON5 field | CSS var set |
|---|---|
| `colors.accents.primary` | `--accent`, `--primary` (hsl-converted), `--ring` |
| `colors.backgrounds.panel` | `--card`, `--popover` |
| `colors.text.main` | `--foreground`, `--card-foreground` |
| `colors.backgrounds.blur` | `--bg-blur`, `--background` |
| `assets.background_image` | `--bg-image` (used in body background) |
| `colors.borders.mid` | `--border`, `--input`, `--sidebar-border` |
| `typography.fonts.primary` | `--font-primary` |
| `typography.fonts.mono` | `--font-mono` |

### Glassmorphic Layer Values (from source mockup — exact)
| Layer | Background | Blur |
|---|---|---|
| `body::before` (full overlay) | `rgba(10, 10, 10, 0.8)` | `backdrop-filter: blur(20px)` |
| Header & Sidebar | `rgba(20, 20, 22, 0.6)` | `backdrop-filter: blur(10px)` |
| Panels / Cards | `rgba(28, 28, 30, 0.75)` | `backdrop-filter: blur(10px)` |
| Background image | `photo-1550684848-fac1c5b4e853` (Unsplash) | fixed + cover |

### Layout Change (app.component)
Current: `flex` (sidebar + div) → New: CSS Grid `260px 1fr` / `64px 1fr`  
Body gets `background-image: var(--bg-image)` + glassmorphic `::before` overlay (`rgba(10,10,10,0.8)` + `blur(20px)`).  
Header and sidebar both use `rgba(20, 20, 22, 0.6)` + `blur(10px)` — matching the source mockup exactly.

### Sidebar Navigation Groups (MTA-adapted)
```
Overview          Dashboard · Monitoring · Log Viewer
Email Services    Queue · Storage
Security          ClamAV · Rspamd · Blocklist
Routing           Relay · Webhooks
Compliance        DMARC (Dashboard · Reports · Validate · Ingest)
Administration    Domains · Users · Settings · Themes
```

## Task Tracking (SQL)
All 27 tasks are tracked in the `todos` table in the session SQLite database.  
Dependencies are in `todo_deps`. Query ready (unblocked) tasks with:

```sql
SELECT t.id, t.title FROM todos t
WHERE t.status = 'pending'
AND NOT EXISTS (
  SELECT 1 FROM todo_deps td
  JOIN todos dep ON td.depends_on = dep.id
  WHERE td.todo_id = t.id AND dep.status != 'done'
);
```

## Phases & Task List

### Phase 1 — Theme Engine (Foundation)
Can be worked independently by one session. All Phase 2+ tasks depend on this.

| ID | Task |
|---|---|
| p1-t01 | Install `json5` npm package |
| p1-t02 | Create `ThemeModel` TypeScript interfaces |
| p1-t03 | Create `ThemeService` (load/apply/persist/remote URL) |
| p1-t04 | Create `src/assets/themes/` + `hybrid-enterprise-palette.json5` |
| p1-t05 | Create `robin-default.json5` (current light theme) |
| p1-t06 | Create `robin-dark.json5` (current dark theme) |
| p1-t07 | Wire `ThemeService` in `AppComponent` / `APP_INITIALIZER` |

### Phase 2 — Shell Redesign (Layout + Global Styles)
Depends on Phase 1. Tasks p2-t08 → p2-t10 can run in parallel with p2-t11/p2-t12.

| ID | Task |
|---|---|
| p2-t08 | Redesign `app.component` shell (CSS Grid, glassmorphic body) |
| p2-t09 | Redesign `SidebarComponent` (grouped nav, amber active, footer) |
| p2-t10 | Redesign `HeaderComponent` (logo, breadcrumb, icons) |
| p2-t11 | Create `ThemePickerComponent` (header dropdown + URL input) |
| p2-t12 | Update `styles.scss` (global utility classes, remove hardcoded vars) |

### Phase 3 — Dashboard Redesign
Depends on Phase 2 shell. Tasks p3-t13, p3-t14, p3-t15 can run in parallel.

| ID | Task |
|---|---|
| p3-t13 | Redesign `DashboardComponent` (hero banner, KPI grid, panels) |
| p3-t14 | Redesign `health-widget` (status-list, [OK]/[WARN] badges) |
| p3-t15 | Redesign `queue-widget` (data-table, status badges) |

### Phase 4 — Feature Page Restyling
All depend on p2-t12 (global styles). All 7 tasks are fully parallel.

| ID | Task |
|---|---|
| p4-t16 | Restyle Login page |
| p4-t17 | Restyle Email pages (Queue, Storage) |
| p4-t18 | Restyle Domains pages |
| p4-t19 | Restyle Security pages |
| p4-t20 | Restyle Monitoring pages |
| p4-t21 | Restyle DMARC pages |
| p4-t22 | Restyle Settings pages |

### Phase 5 — Theme Management UI
Depends on ThemeService and ThemePickerComponent.

| ID | Task |
|---|---|
| p5-t23 | Create Settings → Themes page (bundled themes grid, load from URL/file) |
| p5-t24 | Implement remote theme URL loading in ThemeService |

### Phase 6 — QA & Build
| ID | Task |
|---|---|
| p6-t25 | Write unit tests for `ThemeService` |
| p6-t26 | Run lint + build, fix errors |
| p6-t27 | Update `angular.json` assets glob for `themes/*.json5` |

## Parallelism Guide for Multiple Sessions
```
Session A: p1-t01 → p1-t02 → p1-t03 → p1-t07 → p2-t08 → p3-t13
Session B:                    p1-t04 → p1-t05 → p1-t06 (after t02)
Session C:                              p2-t09 → p2-t10 → p2-t11 (after t07)
Session D:                              p2-t12 → p4-t16..p4-t22 (after t07, parallel)
Session E:                                        p5-t23 → p5-t24 (after t11+t03)
QA:                                               p6-t25 → p6-t26 → p6-t27
```

## Files to Change (High-Level)
```
NEW:
  src/app/core/models/theme.model.ts
  src/app/core/services/theme.service.ts
  src/app/core/services/theme.service.spec.ts
  src/app/shared/components/theme-picker/ (3 files)
  src/app/features/settings/themes/ (3 files)
  src/assets/themes/index.json5
  src/assets/themes/hybrid-enterprise-palette.json5
  src/assets/themes/robin-default.json5
  src/assets/themes/robin-dark.json5

MODIFIED:
  package.json (add json5)
  angular.json (assets glob)
  src/index.html (Google Fonts)
  src/styles.scss (utility classes, remove hardcoded vars)
  src/app/app.module.ts (APP_INITIALIZER)
  src/app/app.component.html/.scss (CSS Grid shell)
  src/app/shared/components/sidebar/sidebar.component.html/.scss/.ts
  src/app/shared/components/header/header.component.html/.scss/.ts
  src/app/shared/shared.module.ts (register ThemePicker)
  src/app/features/dashboard/dashboard.component.html/.scss/.ts
  src/app/features/dashboard/components/health-widget/* 
  src/app/features/dashboard/components/queue-widget/*
  src/app/features/auth/login/login.component.html/.scss
  src/app/features/email/queue/queue-list.component.html/.scss
  src/app/features/email/storage/storage-browser.component.html/.scss
  src/app/features/domains/components/* (5 components)
  src/app/features/security/* (3 components)
  src/app/features/monitoring/* (2 components)
  src/app/features/dmarc/components/* (3 components)
  src/app/features/settings/* (4 components + add themes route)
```

## Reference Files
- Mockup: `/Users/cstan/development/workspace/personal/sanctum/mocks/gemini/ui_mock_hybrid_enterprise_palette.html`
- Theme JSONC: `/Users/cstan/development/workspace/personal/sanctum/mocks/gemini/ui_mock_hybrid_enterprise_palette_characteristics.jsonc`
- Robin UI: `/Users/cstan/development/workspace/open-source/robin-ui/`
- HTML Preview: `files/robin-ui-redesign-preview.html` (in this session folder)
