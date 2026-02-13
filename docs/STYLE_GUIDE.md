# Robin UI - Style Guide

**Version:** 1.0
**Last Updated:** 2026-02-06
**Purpose:** Ensure consistent UI/UX across the Robin MTA management interface

---

## Table of Contents

1. [Color Palette](#color-palette)
2. [Typography](#typography)
3. [Spacing System](#spacing-system)
4. [Border Radius](#border-radius)
5. [Shadows](#shadows)
6. [Components](#components)
7. [Icons](#icons)
8. [Forms](#forms)
9. [Loading States](#loading-states)
10. [Responsive Design](#responsive-design)

---

## Color Palette

### Brand Colors

```css
--robin-green: #44A83A;  /* Primary brand color */
--robin-orange: #FE8502; /* Secondary brand color */
```

### Semantic Colors

#### Background & Foreground
```css
--background: 0 0% 100%;           /* White */
--foreground: 222.2 84% 4.9%;      /* Near black */
```

#### Sidebar
```css
--sidebar: 0 0% 98%;               /* Light gray */
--sidebar-foreground: 240 5.3% 26.1%;
--sidebar-border: 220 13% 91%;
--sidebar-accent: 220 14.3% 95.9%;
--sidebar-accent-foreground: 240 5.9% 10%;
```

#### UI Elements
```css
--muted: 210 40% 96.1%;            /* Light backgrounds */
--muted-foreground: 215.4 16.3% 46.9%; /* Subtle text */

--accent: 210 40% 96.1%;           /* Hover states */
--accent-foreground: 222.2 47.4% 11.2%;

--border: 214.3 31.8% 91.4%;       /* Borders */
--input: 214.3 31.8% 91.4%;        /* Form borders */
--ring: 222.2 84% 4.9%;            /* Focus rings */
```

#### Status Colors
```css
--robin-green: #44A83A;    /* Success, UP, Active */
--destructive: #EF4444;    /* Error, DOWN, Destructive */
--warning: #F59E0B;        /* Warning, UNKNOWN */
--info: #3B82F6;           /* Info, informational */
```

### Usage Guidelines

- **Primary actions:** Use `--robin-green` for CTAs
- **Secondary actions:** Use `--robin-orange` for secondary emphasis
- **Destructive actions:** Use `--destructive` (red) for delete/remove
- **Status indicators:** Match colors to semantic meaning

---

## Typography

### Font Family

```css
font-family: Inter, system-ui, -apple-system, sans-serif;
```

### Scale

| Size | Class | Usage |
|------|-------|-------|
| 3xl | `text-3xl` (30px) | Page titles |
| 2xl | `text-2xl` (24px) | Section headers |
| xl | `text-xl` (20px) | Card titles |
| lg | `text-lg` (18px) | Large text |
| base | `text-base` (16px) | Body text |
| sm | `text-sm` (14px) | Secondary text |
| xs | `text-xs` (12px) | Captions, labels |

### Font Weights

- **font-bold** (700) - Page titles, emphasis
- **font-semibold** (600) - Card titles, headings
- **font-medium** (500) - Button text, labels
- **font-normal** (400) - Body text

### Examples

```html
<!-- Page Title -->
<h1 class="text-3xl font-bold tracking-tight">Dashboard</h1>

<!-- Card Title -->
<h3 class="card-title text-lg">Server Health</h3>

<!-- Body Text -->
<p class="text-base text-foreground">Configuration settings</p>

<!-- Secondary Text -->
<span class="text-sm text-muted-foreground">Last updated: 2 hours ago</span>
```

---

## Spacing System

### Padding & Margins

Based on Tailwind's spacing scale (1 unit = 0.25rem = 4px):

| Class | Value | Usage |
|-------|-------|-------|
| `p-2` | 8px | Compact spacing |
| `p-3` | 12px | Form fields, small elements |
| `p-4` | 16px | Cards, containers |
| `p-6` | 24px | Card content |
| `p-8` | 32px | Large sections |
| `p-10` | 40px | Page padding |

### Gap (Flex/Grid)

- `gap-2` (8px) - Icons and text
- `gap-3` (12px) - Form groups
- `gap-4` (16px) - Cards, sections
- `gap-6` (24px) - Major sections

### Examples

```html
<!-- Card -->
<div class="card p-6">
  <div class="card-header mb-4">...</div>
  <div class="card-content space-y-4">...</div>
</div>

<!-- Form -->
<form class="space-y-6">
  <div class="space-y-2">...</div>
</form>
```

---

## Border Radius

### Standards

| Class | Value | Usage |
|-------|-------|-------|
| `rounded` | 4px | Small elements |
| `rounded-md` | 6px | **Buttons, inputs, badges** |
| `rounded-lg` | 8px | **Cards, modals** |
| `rounded-xl` | 12px | Large cards |
| `rounded-full` | 9999px | Circular elements, dots |

### Guidelines

- **Buttons & Inputs:** Use `rounded-md` (6px)
- **Cards & Containers:** Use `rounded-lg` (8px)
- **Status Dots:** Use `rounded-full`
- **Consistency:** Don't mix radius sizes within same component

```html
<!-- Button -->
<button class="btn-primary rounded-md">Save</button>

<!-- Card -->
<div class="card rounded-lg">...</div>

<!-- Status Dot -->
<span class="w-2 h-2 rounded-full bg-robin-green"></span>
```

---

## Shadows

### Shadow Utilities

- `shadow-sm` - Subtle elevation
- `shadow` - Default cards
- `shadow-md` - Raised elements
- `shadow-lg` - **Modals, dialogs, toasts**
- `shadow-xl` - Maximum elevation

### Usage

```html
<!-- Card -->
<div class="card shadow">...</div>

<!-- Modal -->
<div class="modal shadow-lg">...</div>

<!-- Toast -->
<div class="toast shadow-lg">...</div>
```

---

## Components

### Buttons

#### Variants

```html
<!-- Primary -->
<button class="btn-primary btn-md">
  Save Changes
</button>

<!-- Secondary -->
<button class="btn-secondary btn-md">
  Cancel
</button>

<!-- Ghost -->
<button class="btn-ghost btn-md">
  Learn More
</button>

<!-- Destructive -->
<button class="btn-destructive btn-md">
  Delete
</button>
```

#### Sizes

- `btn-sm` - Small (height: 32px, padding: 8px 12px)
- `btn-md` - **Default** (height: 40px, padding: 10px 16px)
- `btn-lg` - Large (height: 48px, padding: 12px 24px)

#### Styles

```css
.btn-primary {
  background: var(--robin-green);
  color: white;
  border-radius: 0.375rem; /* rounded-md */
  font-weight: 500;
  transition: all 150ms;
}

.btn-primary:hover {
  background: #3a8f31; /* Darker green */
}
```

### Cards

```html
<div class="card">
  <div class="card-header">
    <h3 class="card-title">Title</h3>
    <p class="card-description">Subtitle</p>
  </div>
  <div class="card-content">
    <!-- Content -->
  </div>
</div>
```

**Styles:**
- Border: `border border-sidebar-border`
- Background: `bg-sidebar`
- Padding: `p-6`
- Radius: `rounded-lg`

### Badges

```html
<!-- Status Badge -->
<app-status-badge
  [status]="'UP'"
  [size]="'md'"
  [showDot]="true">
</app-status-badge>

<!-- Custom Badge -->
<span class="badge badge-secondary">
  3
</span>
```

**Sizes:**
- `sm` - Small (text-xs, px-2, py-0.5)
- `md` - **Default** (text-xs, px-2.5, py-0.5)
- `lg` - Large (text-sm, px-3, py-1)

### Modals/Dialogs

```html
<div class="modal">
  <div class="modal-overlay"></div>
  <div class="modal-content">
    <div class="modal-header">
      <h3>Title</h3>
      <button aria-label="Close">×</button>
    </div>
    <div class="modal-body">
      <!-- Content -->
    </div>
    <div class="modal-footer">
      <button class="btn-ghost">Cancel</button>
      <button class="btn-primary">Confirm</button>
    </div>
  </div>
</div>
```

**Styles:**
- Width: `max-w-md` (448px) for small, `max-w-2xl` (672px) for large
- Shadow: `shadow-lg`
- Radius: `rounded-lg`

---

## Icons

### Guidelines

- **Size:** Use `h-4 w-4` (16px) for buttons, `h-5 w-5` (20px) for larger elements
- **Stroke:** Use `stroke-width="2"` for consistency
- **Color:** Inherit from parent or use semantic colors
- **Accessibility:** Add `aria-hidden="true"` to decorative icons

### Examples

```html
<!-- Icon Button -->
<button aria-label="Delete">
  <svg class="h-4 w-4" aria-hidden="true">...</svg>
</button>

<!-- Icon with Text -->
<div class="flex items-center gap-2">
  <svg class="h-5 w-5 text-robin-green">...</svg>
  <span>Success</span>
</div>
```

---

## Forms

### Input Fields

```html
<div class="space-y-2">
  <label for="email" class="text-sm font-medium">
    Email Address
  </label>
  <input
    type="email"
    id="email"
    class="input"
    placeholder="user@example.com"
    aria-describedby="email-error">
  <span
    id="email-error"
    class="text-destructive text-sm">
    Please enter a valid email
  </span>
</div>
```

**Input Styles:**
```css
.input {
  width: 100%;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--input);
  border-radius: 0.375rem; /* rounded-md */
  background: var(--background);
  font-size: 0.875rem;
}

.input:focus {
  outline: none;
  border-color: var(--ring);
  box-shadow: 0 0 0 3px rgba(68, 168, 58, 0.1);
}

.input:invalid {
  border-color: var(--destructive);
}
```

### Select Fields

```html
<select class="input">
  <option value="">Select option</option>
  <option value="1">Option 1</option>
</select>
```

### Checkboxes & Radios

```html
<!-- Checkbox -->
<label class="flex items-center gap-2">
  <input type="checkbox" class="rounded">
  <span class="text-sm">Remember me</span>
</label>

<!-- Radio -->
<label class="flex items-center gap-2">
  <input type="radio" name="choice" class="rounded-full">
  <span class="text-sm">Option 1</span>
</label>
```

### Error States

- Add `border-destructive` class to invalid inputs
- Display error message with `text-destructive text-sm`
- Use `aria-describedby` to link error messages
- Show errors after field is touched

---

## Loading States

### Skeleton Loaders

```html
<div class="skeleton h-4 w-32"></div>
<div class="skeleton h-10 w-full"></div>
```

**Skeleton Styles:**
```css
.skeleton {
  background: linear-gradient(
    90deg,
    var(--muted) 0%,
    var(--muted-foreground)/10% 50%,
    var(--muted) 100%
  );
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s ease-in-out infinite;
  border-radius: 0.375rem;
}

@keyframes skeleton-loading {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
```

### Spinners

```html
<div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
```

### Loading Buttons

```html
<button class="btn-primary" disabled>
  <svg class="animate-spin h-4 w-4 mr-2">...</svg>
  Loading...
</button>
```

---

## Responsive Design

### Breakpoints

| Breakpoint | Size | Prefix |
|------------|------|--------|
| Mobile | < 640px | (none) |
| Tablet | ≥ 640px | `sm:` |
| Desktop | ≥ 1024px | `lg:` |
| Large | ≥ 1280px | `xl:` |

### Mobile-First Examples

```html
<!-- Stack on mobile, row on desktop -->
<div class="flex flex-col lg:flex-row gap-4">...</div>

<!-- 1 column mobile, 2 on tablet, 3 on desktop -->
<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">...</div>

<!-- Hide on mobile, show on desktop -->
<div class="hidden lg:block">Desktop only</div>
```

### Layout Guidelines

- **Mobile:** Single column, collapsible navigation
- **Tablet:** 2-column grids, expanded navigation
- **Desktop:** 3-column grids, side-by-side layouts
- **Padding:** Increase from `p-4` (mobile) to `p-10` (desktop)

---

## Best Practices

### Do's ✅

- Use semantic color variables (--robin-green, --destructive)
- Maintain consistent spacing (use Tailwind scale)
- Apply rounded-md to buttons/inputs, rounded-lg to cards
- Add aria-labels to icon-only buttons
- Use skeleton loaders for better perceived performance
- Test on multiple screen sizes

### Don'ts ❌

- Don't use arbitrary color values (use CSS variables)
- Don't mix border radius sizes within a component
- Don't forget focus states on interactive elements
- Don't use icon buttons without aria-labels
- Don't use console.error (use LoggingService)
- Don't mutate objects (use spread operator for immutability)

---

## Component Checklist

When creating new components:

- [ ] Uses semantic color variables
- [ ] Follows spacing system (gap-4, p-6, etc.)
- [ ] Uses correct border radius (buttons: rounded-md, cards: rounded-lg)
- [ ] Has proper hover/focus states
- [ ] Icon-only buttons have aria-labels
- [ ] Decorative icons have aria-hidden="true"
- [ ] Forms have proper validation states
- [ ] Loading states are implemented
- [ ] Responsive design applied
- [ ] Follows accessibility guidelines

---

## Examples

### Complete Card Component

```html
<div class="card rounded-lg shadow p-6 bg-sidebar border border-sidebar-border">
  <div class="card-header mb-4">
    <div class="flex items-center justify-between">
      <h3 class="card-title text-lg font-semibold text-foreground">
        Server Health
      </h3>
      <app-status-badge [status]="'UP'" [size]="'md'"></app-status-badge>
    </div>
    <p class="card-description text-sm text-muted-foreground mt-1">
      Real-time server status and metrics
    </p>
  </div>
  <div class="card-content space-y-4">
    <div class="flex items-center justify-between p-3 rounded-md bg-muted/50">
      <span class="text-sm font-medium text-muted-foreground">Uptime</span>
      <span class="text-sm font-semibold text-foreground">24h 15m</span>
    </div>
  </div>
</div>
```

### Complete Form

```html
<form class="space-y-6">
  <div class="space-y-2">
    <label for="domain" class="text-sm font-medium text-foreground">
      Domain Name
    </label>
    <input
      type="text"
      id="domain"
      class="input rounded-md"
      placeholder="example.com"
      [class.border-destructive]="field.invalid && field.touched"
      aria-describedby="domain-error">
    <span
      *ngIf="field.invalid && field.touched"
      id="domain-error"
      class="text-destructive text-sm">
      Please enter a valid domain name
    </span>
  </div>
  <div class="flex gap-3 justify-end">
    <button type="button" class="btn-ghost btn-md rounded-md">
      Cancel
    </button>
    <button type="submit" class="btn-primary btn-md rounded-md">
      Save Domain
    </button>
  </div>
</form>
```

---

## References

- **Tailwind CSS:** https://tailwindcss.com/docs
- **Lucide Icons:** https://lucide.dev
- **WCAG Guidelines:** https://www.w3.org/WAI/WCAG21/quickref/

---

**Last Updated:** 2026-02-06
**Maintained by:** Robin UI Development Team
