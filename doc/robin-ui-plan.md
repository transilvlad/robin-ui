# Robin MTA Customer Management UI - Implementation Plan

## Overview

Angular 18+ SPA with NgModules for business customers to manage Robin MTA services.

- **Target Users**: Business customers (organizations using Robin as a service)
- **Tech Stack**: Angular 18+ with TypeScript, Tailwind CSS, NgRx + Signals
- **Architecture**: NgModule-based with lazy-loaded feature modules
- **Deployment**: Standalone SPA connecting to Robin's REST API (ports 8080/8090)
- **Scope**: All integrated services - full management capabilities

---

## Phase 1: Robin Backend API Enhancements

### 1.1 Add CORS Headers

**File**: `src/main/java/com/mimecast/robin/endpoints/HttpEndpoint.java`

Add CORS headers to enable cross-origin requests from the Angular SPA:
```java
exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
```

### 1.2 New Configuration API Endpoints

**File**: `src/main/java/com/mimecast/robin/endpoints/ApiEndpoint.java`

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/config/storage` | Get storage configuration |
| PUT | `/api/config/storage` | Update storage configuration |
| GET | `/api/config/queue` | Get queue configuration |
| PUT | `/api/config/queue` | Update queue configuration |
| GET | `/api/config/relay` | Get relay configuration |
| PUT | `/api/config/relay` | Update relay configuration |
| GET | `/api/config/dovecot` | Get Dovecot configuration |
| PUT | `/api/config/dovecot` | Update Dovecot configuration |
| GET | `/api/config/clamav` | Get ClamAV configuration |
| PUT | `/api/config/clamav` | Update ClamAV configuration |
| GET | `/api/config/rspamd` | Get Rspamd configuration |
| PUT | `/api/config/rspamd` | Update Rspamd configuration |
| GET | `/api/config/webhooks` | Get webhooks configuration |
| PUT | `/api/config/webhooks` | Update webhooks configuration |
| GET | `/api/config/blocklist` | Get blocklist configuration |
| PUT | `/api/config/blocklist` | Update blocklist configuration |
| GET | `/api/config/proxy` | Get proxy rules |
| PUT | `/api/config/proxy` | Update proxy rules |

### 1.3 User Management Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/users` | List all users |
| POST | `/api/users` | Create user |
| GET | `/api/users/{email}` | Get user details |
| PUT | `/api/users/{email}` | Update user |
| DELETE | `/api/users/{email}` | Delete user |

### 1.4 Queue JSON API (extend existing HTML endpoints)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/queue` | List queue items (JSON, paginated) |
| GET | `/api/queue/{uid}` | Get queue item details |
| GET | `/api/queue/stats` | Queue statistics |

### 1.5 Storage JSON API

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/store` | List storage (JSON) |
| DELETE | `/api/store/{path}` | Delete message |
| POST | `/api/store/search` | Search messages |

### 1.6 Scanner Status Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/scanners/status` | ClamAV/Rspamd connectivity status |
| POST | `/api/scanners/test` | Test scanner configuration |

---

## Phase 2: Angular Project Setup

### 2.1 Initialize Project

```bash
ng new robin-ui --routing --style=scss
cd robin-ui
npm install tailwindcss postcss autoprefixer
npx tailwindcss init
npm install @ngrx/store @ngrx/effects @ngrx/entity
npm install chart.js ng2-charts
npm install @angular/material
```

### 2.2 Project Structure (NgModule-based)

```
robin-ui/
├── src/app/
│   ├── app.module.ts              # Root module
│   ├── app-routing.module.ts      # Root routing with lazy loading
│   ├── app.component.ts
│   │
│   ├── core/                      # CoreModule (singleton services)
│   │   ├── core.module.ts
│   │   ├── interceptors/
│   │   │   ├── auth.interceptor.ts
│   │   │   └── error.interceptor.ts
│   │   ├── guards/
│   │   │   └── auth.guard.ts
│   │   ├── services/
│   │   │   ├── api.service.ts
│   │   │   ├── auth.service.ts
│   │   │   ├── config.service.ts
│   │   │   └── notification.service.ts
│   │   └── models/
│   │       ├── config.model.ts
│   │       ├── queue.model.ts
│   │       └── health.model.ts
│   │
│   ├── shared/                    # SharedModule (reusable components)
│   │   ├── shared.module.ts
│   │   ├── components/
│   │   │   ├── header/
│   │   │   ├── sidebar/
│   │   │   ├── data-table/
│   │   │   ├── modal/
│   │   │   └── status-badge/
│   │   ├── directives/
│   │   └── pipes/
│   │       ├── bytes.pipe.ts
│   │       └── relative-time.pipe.ts
│   │
│   ├── features/                  # Lazy-loaded feature modules
│   │   ├── dashboard/
│   │   │   ├── dashboard.module.ts
│   │   │   ├── dashboard-routing.module.ts
│   │   │   └── components/
│   │   ├── email/
│   │   │   ├── email.module.ts
│   │   │   ├── email-routing.module.ts
│   │   │   ├── queue/
│   │   │   ├── storage/
│   │   │   └── compose/
│   │   ├── security/
│   │   │   ├── security.module.ts
│   │   │   ├── security-routing.module.ts
│   │   │   ├── clamav/
│   │   │   ├── rspamd/
│   │   │   ├── blocklist/
│   │   │   └── rbl/
│   │   ├── routing/
│   │   │   ├── routing.module.ts
│   │   │   ├── routing-routing.module.ts
│   │   │   ├── relay/
│   │   │   ├── proxy/
│   │   │   ├── routes/
│   │   │   └── webhooks/
│   │   ├── monitoring/
│   │   │   ├── monitoring.module.ts
│   │   │   ├── monitoring-routing.module.ts
│   │   │   ├── metrics/
│   │   │   ├── logs/
│   │   │   └── diagnostics/
│   │   └── settings/
│   │       ├── settings.module.ts
│   │       ├── settings-routing.module.ts
│   │       ├── server/
│   │       ├── storage/
│   │       ├── dovecot/
│   │       ├── users/
│   │       └── bots/
│   │
│   └── store/                     # NgRx state management
│       ├── app.state.ts
│       ├── config/
│       ├── queue/
│       └── health/
```

---

## Phase 3: UI Modules Implementation

### 3.1 Dashboard Module
- Health status widget (uptime, status indicator)
- Queue size widget with retry distribution chart
- Listener statistics (per-port thread/connection stats)
- LMTP pool utilization widget
- Quick actions (send test email, reload config)

### 3.2 Email Management Module
- **Queue**: Paginated list, detail view, bulk operations (delete/retry/bounce)
- **Storage**: Directory tree, file list, message viewer (.eml parser)
- **Compose**: Email form with envelope editor, attachments, send/queue options

### 3.3 Security Center Module
- ClamAV config (host, port, actions on virus, test connection)
- Rspamd config (thresholds, SPF/DKIM/DMARC toggles, test connection)
- Blocklist management (IP/CIDR editor, import/export)
- RBL configuration (provider list, timeouts)

### 3.4 Routing Module
- Relay config (inbound/outbound, MX lookup, protocols)
- Proxy rules editor (drag-reorder, regex builder, rule tester)
- Routes management (CRUD for named routes)
- Webhooks config (per-verb settings, URL/auth, tester)

### 3.5 Monitoring Module
- Metrics dashboard (Chart.js, real-time from /metrics/graphite)
- Log viewer (live tail, search, level filtering)
- Diagnostics (thread dump, heap dump trigger, env/props)

### 3.6 Settings Module
- Server config (hostname, ports, TLS, listener settings)
- Storage config (paths, auto-delete, mailbox settings)
- Dovecot config (auth backend, LMTP/LDA settings)
- Queue config (backend selection, timing)
- User management (CRUD)
- Bot config

---

## Phase 4: State Management

### NgRx with Signals Pattern

```typescript
// Signal-based health service with polling
@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly healthState = signal<HealthState>({ status: 'UNKNOWN', ... });
  readonly health = this.healthState.asReadonly();
  readonly isHealthy = computed(() => this.healthState().status === 'UP');

  constructor(private api: ApiService) {
    // Poll every 30 seconds
    interval(30000).pipe(switchMap(() => this.api.getHealth()))
      .subscribe(h => this.healthState.set(h));
  }
}
```

### State Slices
- `ConfigState`: All configuration objects, loading status, errors
- `QueueState`: Queue items, pagination, selected items
- `HealthState`: Health response, last checked timestamp

---

## Phase 5: Testing Strategy

| Type | Tool | Coverage Target |
|------|------|-----------------|
| Unit | Jest | Services, pipes, utils (>80%) |
| Component | Cypress Component | Interactive components |
| E2E | Cypress | Critical user flows |

---

## Implementation Order

| Step | Scope | Deliverables |
|------|-------|--------------|
| 1 | Backend: CORS + /api/queue JSON | Enable frontend development |
| 2 | Angular: Project + auth + shared components | Working shell |
| 3 | Dashboard + health polling | Real-time monitoring |
| 4 | Queue management | Core email operations |
| 5 | Storage browser | Email viewing |
| 6 | Security center | ClamAV/Rspamd config |
| 7 | Backend: Config CRUD endpoints | Enable settings UI |
| 8 | Routing module | Relay/proxy/webhooks |
| 9 | Monitoring module | Metrics/logs |
| 10 | Settings + user management | Full configuration |
| 11 | Polish + testing | Production ready |

---

## Critical Files to Modify (Backend)

| File | Changes |
|------|---------|
| `src/main/java/com/mimecast/robin/endpoints/HttpEndpoint.java` | Add CORS headers |
| `src/main/java/com/mimecast/robin/endpoints/ApiEndpoint.java` | Add JSON queue/storage/config endpoints |
| `src/main/java/com/mimecast/robin/endpoints/RobinServiceEndpoint.java` | Add config update endpoints |

---

## Technology Stack

| Category | Technology |
|----------|------------|
| Framework | Angular 18+ |
| Language | TypeScript 5.4+ |
| State | NgRx + Signals |
| Styling | Tailwind CSS 3.4+ |
| Charts | Chart.js + ng2-charts |
| Testing | Jest + Cypress |

---

## Notes

- Use `typescript-master` agent for Angular frontend development
- Angular NgModule architecture with CoreModule, SharedModule, and feature modules
- Lazy loading for all feature modules
- Real-time updates via polling (no WebSocket in Robin currently)
