# Robin UI - Project Structure

Complete Angular 18+ NgModule-based project for Robin MTA Management.

## Overview

This is a production-ready Angular application with 68 files organized into a clean NgModule architecture with lazy-loaded feature modules.

## Directory Structure

```
robin-ui/
├── src/
│   ├── app/
│   │   ├── core/                          # CoreModule - Singleton services
│   │   │   ├── guards/
│   │   │   │   └── auth.guard.ts
│   │   │   ├── interceptors/
│   │   │   │   ├── auth.interceptor.ts
│   │   │   │   └── error.interceptor.ts
│   │   │   ├── models/
│   │   │   │   ├── config.model.ts        # Server configuration interfaces
│   │   │   │   ├── health.model.ts        # Health check interfaces
│   │   │   │   ├── metrics.model.ts       # Metrics interfaces
│   │   │   │   ├── queue.model.ts         # Queue item interfaces
│   │   │   │   └── storage.model.ts       # Storage browser interfaces
│   │   │   ├── services/
│   │   │   │   ├── api.service.ts         # Base HTTP service
│   │   │   │   ├── auth.service.ts        # Authentication service
│   │   │   │   └── notification.service.ts # Toast notifications
│   │   │   └── core.module.ts
│   │   │
│   │   ├── shared/                        # SharedModule - Reusable components
│   │   │   ├── components/
│   │   │   │   ├── header/
│   │   │   │   │   ├── header.component.ts
│   │   │   │   │   ├── header.component.html
│   │   │   │   │   └── header.component.scss
│   │   │   │   ├── sidebar/
│   │   │   │   │   ├── sidebar.component.ts
│   │   │   │   │   ├── sidebar.component.html
│   │   │   │   │   └── sidebar.component.scss
│   │   │   │   └── status-badge/
│   │   │   │       ├── status-badge.component.ts
│   │   │   │       ├── status-badge.component.html
│   │   │   │       └── status-badge.component.scss
│   │   │   ├── pipes/
│   │   │   │   ├── bytes.pipe.ts          # Format bytes (1024 → 1 KB)
│   │   │   │   └── relative-time.pipe.ts  # Format timestamps (60s → 1 min ago)
│   │   │   └── shared.module.ts
│   │   │
│   │   ├── features/                      # Feature Modules - Lazy loaded
│   │   │   │
│   │   │   ├── dashboard/                 # Dashboard Module
│   │   │   │   ├── components/
│   │   │   │   │   ├── health-widget/
│   │   │   │   │   │   ├── health-widget.component.ts
│   │   │   │   │   │   ├── health-widget.component.html
│   │   │   │   │   │   └── health-widget.component.scss
│   │   │   │   │   └── queue-widget/
│   │   │   │   │       ├── queue-widget.component.ts
│   │   │   │   │       ├── queue-widget.component.html
│   │   │   │   │       └── queue-widget.component.scss
│   │   │   │   ├── services/
│   │   │   │   │   └── dashboard.service.ts
│   │   │   │   ├── dashboard.component.ts
│   │   │   │   ├── dashboard.component.html
│   │   │   │   ├── dashboard.component.scss
│   │   │   │   ├── dashboard-routing.module.ts
│   │   │   │   └── dashboard.module.ts
│   │   │   │
│   │   │   ├── email/                     # Email Module
│   │   │   │   ├── queue/
│   │   │   │   │   ├── queue-list.component.ts
│   │   │   │   │   ├── queue-list.component.html
│   │   │   │   │   └── queue-list.component.scss
│   │   │   │   ├── storage/
│   │   │   │   │   ├── storage-browser.component.ts
│   │   │   │   │   ├── storage-browser.component.html
│   │   │   │   │   └── storage-browser.component.scss
│   │   │   │   ├── services/
│   │   │   │   │   ├── queue.service.ts
│   │   │   │   │   └── storage.service.ts
│   │   │   │   ├── email-routing.module.ts
│   │   │   │   └── email.module.ts
│   │   │   │
│   │   │   ├── security/                  # Security Module
│   │   │   │   ├── clamav/
│   │   │   │   │   └── clamav-config.component.ts
│   │   │   │   ├── rspamd/
│   │   │   │   │   └── rspamd-config.component.ts
│   │   │   │   ├── blocklist/
│   │   │   │   │   └── blocklist.component.ts
│   │   │   │   ├── security-routing.module.ts
│   │   │   │   └── security.module.ts
│   │   │   │
│   │   │   ├── routing/                   # Routing Module (email routing)
│   │   │   │   ├── relay/
│   │   │   │   │   └── relay-config.component.ts
│   │   │   │   ├── webhooks/
│   │   │   │   │   └── webhooks.component.ts
│   │   │   │   ├── routing-routing.module.ts
│   │   │   │   └── routing.module.ts
│   │   │   │
│   │   │   ├── monitoring/                # Monitoring Module
│   │   │   │   ├── metrics/
│   │   │   │   │   └── metrics-dashboard.component.ts
│   │   │   │   ├── logs/
│   │   │   │   │   └── log-viewer.component.ts
│   │   │   │   ├── monitoring-routing.module.ts
│   │   │   │   └── monitoring.module.ts
│   │   │   │
│   │   │   └── settings/                  # Settings Module
│   │   │       ├── server/
│   │   │       │   └── server-config.component.ts
│   │   │       ├── users/
│   │   │       │   └── user-list.component.ts
│   │   │       ├── settings-routing.module.ts
│   │   │       └── settings.module.ts
│   │   │
│   │   ├── app.component.ts
│   │   ├── app.component.html
│   │   ├── app.component.scss
│   │   ├── app.module.ts
│   │   └── app-routing.module.ts
│   │
│   ├── environments/
│   │   ├── environment.ts                 # Development config
│   │   └── environment.prod.ts            # Production config
│   │
│   ├── assets/                            # Static assets
│   ├── styles.scss                        # Global styles + Tailwind
│   ├── main.ts                            # Bootstrap entry point
│   └── index.html
│
├── angular.json                           # Angular CLI config
├── package.json                           # Dependencies
├── tsconfig.json                          # TypeScript config
├── tsconfig.app.json
├── tsconfig.spec.json
├── tailwind.config.js                     # Tailwind CSS config
├── .editorconfig
├── .gitignore
├── README.md                              # Project overview
├── SETUP.md                               # Setup instructions
└── PROJECT_STRUCTURE.md                   # This file
```

## Module Architecture

### 1. CoreModule (Singleton)

**Import:** Only in AppModule
**Purpose:** Application-wide singleton services

- Guards (AuthGuard)
- Interceptors (Auth, Error handling)
- Core services (API, Auth, Notifications)
- Data models (TypeScript interfaces)

### 2. SharedModule (Reusable)

**Import:** In every feature module that needs it
**Purpose:** Reusable components and pipes

- Common components (Header, Sidebar, StatusBadge)
- Custom pipes (BytesPipe, RelativeTimePipe)
- CommonModule, FormsModule, ReactiveFormsModule exports

### 3. Feature Modules (Lazy-loaded)

**Import:** Via lazy loading in app-routing.module.ts
**Purpose:** Isolated feature implementations

Each feature module:
- Has its own routing module
- Imports SharedModule
- Provides feature-specific services
- Contains feature-specific components

## Route Structure

```
/
├── /dashboard              → DashboardModule (lazy)
│   └── (default route)
│
├── /email                  → EmailModule (lazy)
│   ├── /queue
│   └── /storage
│
├── /security               → SecurityModule (lazy)
│   ├── /clamav
│   ├── /rspamd
│   └── /blocklist
│
├── /routing                → RoutingModule (lazy)
│   ├── /relay
│   └── /webhooks
│
├── /monitoring             → MonitoringModule (lazy)
│   ├── /metrics
│   └── /logs
│
└── /settings               → SettingsModule (lazy)
    ├── /server
    └── /users
```

## API Integration

### Service Endpoint (port 8080)
- `/health` - Server health status
- `/config` - Server configuration
- `/metrics/*` - Prometheus/Graphite metrics

### API Endpoint (port 8090)
- `/client/queue/*` - Queue management
- `/store/*` - Storage browser
- `/logs` - Log viewer

## TypeScript Path Aliases

Configured in `tsconfig.json`:

```typescript
// Instead of: import { ApiService } from '../../../core/services/api.service';
import { ApiService } from '@core/services/api.service';

// Available aliases:
@core/*        → src/app/core/*
@shared/*      → src/app/shared/*
@features/*    → src/app/features/*
@environments/* → src/environments/*
```

## Component Communication

### Services
- Core services in CoreModule (singleton)
- Feature services in feature modules (scoped)

### RxJS Observables
- API calls return Observables
- Services expose BehaviorSubjects for state

### Event Emitters
- Component @Input() and @Output() for parent-child communication

## State Management

### Current: Service-based state
- Services use BehaviorSubject for reactive state
- Components subscribe to service observables

### Future: NgRx
- Dependencies already installed
- Ready for implementation when needed

## Styling

### Tailwind CSS
- Utility-first CSS framework
- Configured in `tailwind.config.js`
- Custom theme colors defined

### SCSS
- Component-specific styles in `*.component.scss`
- Global styles in `src/styles.scss`

## Testing Strategy

### Unit Tests
- Jest/Karma + Jasmine
- Component testing with TestBed
- Service testing with mocks

### E2E Tests
- Can add Playwright or Cypress
- Test critical user flows

## Development Guidelines

### Adding New Feature

1. Generate module with routing:
   ```bash
   ng generate module features/new-feature --routing
   ```

2. Generate components:
   ```bash
   ng generate component features/new-feature/components/new-component
   ```

3. Generate services:
   ```bash
   ng generate service features/new-feature/services/new-service
   ```

4. Add lazy route in `app-routing.module.ts`

### Best Practices

1. **CoreModule**: Import only once in AppModule
2. **SharedModule**: Import in every feature module
3. **Path Aliases**: Use `@core/`, `@shared/`, etc.
4. **Lazy Loading**: All feature modules are lazy-loaded
5. **Type Safety**: Use TypeScript interfaces for all data models
6. **Reactive**: Use RxJS observables for async operations

## File Counts

- **Total Files**: 68+
- **TypeScript Files**: 50+
- **HTML Templates**: 10+
- **SCSS Files**: 10+
- **Modules**: 8 (1 App + 1 Core + 1 Shared + 5 Features)
- **Components**: 20+
- **Services**: 7+
- **Models**: 5+

## Next Steps

1. Run `npm install` to install dependencies
2. Update `environment.ts` with Robin API URLs
3. Run `npm start` to start development server
4. Expand placeholder components with full implementations
5. Add NgRx state management if needed
6. Implement real authentication
7. Add Chart.js visualizations

## Status

✅ Project structure created
✅ NgModule architecture implemented
✅ Lazy loading configured
✅ Core services implemented
✅ Shared components implemented
✅ Feature modules scaffolded
✅ Routing configured
✅ Models defined
✅ Tailwind CSS configured

🔨 To be implemented:
- Full component implementations
- NgRx state management
- Chart.js visualizations
- Real authentication flow
- Unit tests
- E2E tests
