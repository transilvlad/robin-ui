# Robin MTA Customer Management UI - Implementation Status

**Last Updated**: 2026-01-17
**Status**: In Progress

---

## Overview

Building an Angular 18+ customer-facing management UI for Robin MTA Server. The UI is a standalone SPA that connects to Robin's REST API endpoints.

---

## Completed Work

### 1. Angular UI Project (`robin-ui/`)

**Location**: `/Users/cstan/development/workspace/open-source/transilvlad-robin/robin-ui/`

**Status**: Core structure complete, builds successfully

#### Project Structure
```
robin-ui/
├── src/app/
│   ├── app.module.ts                    # Root module
│   ├── app-routing.module.ts            # Lazy-loaded routes
│   ├── app.component.ts                 # Main layout
│   │
│   ├── core/                            # CoreModule (singleton)
│   │   ├── core.module.ts
│   │   ├── interceptors/
│   │   │   ├── auth.interceptor.ts      # Adds Authorization header
│   │   │   └── error.interceptor.ts     # Global error handling
│   │   ├── guards/
│   │   │   └── auth.guard.ts            # Route protection
│   │   ├── services/
│   │   │   ├── api.service.ts           # Robin API integration
│   │   │   ├── auth.service.ts          # Basic auth handling
│   │   │   └── notification.service.ts  # Toast notifications
│   │   └── models/
│   │       ├── config.model.ts          # Configuration interfaces
│   │       ├── queue.model.ts           # Queue item interfaces
│   │       └── health.model.ts          # Health response interfaces
│   │
│   ├── shared/                          # SharedModule (reusable)
│   │   ├── shared.module.ts
│   │   ├── components/
│   │   │   ├── header/                  # Top nav with health indicator
│   │   │   ├── sidebar/                 # Main navigation
│   │   │   └── status-badge/            # Status indicators
│   │   └── pipes/
│   │       ├── bytes.pipe.ts            # Format bytes
│   │       └── relative-time.pipe.ts    # Format timestamps
│   │
│   └── features/                        # Lazy-loaded feature modules
│       ├── dashboard/
│       │   ├── dashboard.module.ts
│       │   ├── dashboard-routing.module.ts
│       │   ├── dashboard.component.ts
│       │   ├── components/
│       │   │   ├── health-widget/
│       │   │   └── queue-widget/
│       │   └── services/
│       │       └── dashboard.service.ts
│       ├── email/
│       │   ├── email.module.ts
│       │   ├── email-routing.module.ts
│       │   ├── queue/
│       │   │   └── queue-list.component.ts
│       │   ├── storage/
│       │   │   └── storage-browser.component.ts
│       │   └── services/
│       │       ├── queue.service.ts
│       │       └── storage.service.ts
│       ├── security/
│       │   ├── security.module.ts
│       │   ├── clamav/
│       │   ├── rspamd/
│       │   └── blocklist/
│       ├── routing/
│       │   ├── routing.module.ts
│       │   ├── relay/
│       │   └── webhooks/
│       ├── monitoring/
│       │   ├── monitoring.module.ts
│       │   ├── metrics/
│       │   └── logs/
│       └── settings/
│           ├── settings.module.ts
│           ├── server/
│           └── users/
```

#### Commands
```bash
# Install dependencies
cd robin-ui && npm install

# Development server
npm start
# Open http://localhost:4200

# Production build
npm run build
# Output in dist/robin-ui/
```

#### Environment Configuration
```typescript
// src/environments/environment.ts
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8090',      // Robin API endpoint
  serviceUrl: 'http://localhost:8080'   // Robin Service endpoint
};
```

---

### 2. Robin Backend CORS Support

**Status**: Complete, compiles successfully

#### Files Modified

**`src/main/java/com/mimecast/robin/endpoints/HttpEndpoint.java`**
- Added `addCorsHeaders(HttpExchange exchange)` method
- Added `handleCorsPreflightRequest(HttpExchange exchange)` method
- Updated all send methods to include CORS headers:
  - `sendJson()`
  - `sendHtml()`
  - `sendText()`
  - `sendResponse()`
  - `sendError()`

**`src/main/java/com/mimecast/robin/endpoints/ApiEndpoint.java`**
- Added preflight handling to:
  - `handleClientSend()`
  - `handleClientQueue()`
  - `handleQueueList()`
  - `handleLogs()`
  - `handleStore()`

**`src/main/java/com/mimecast/robin/endpoints/RobinServiceEndpoint.java`**
- Added preflight handling to:
  - `handleConfigViewer()`
  - `handleConfigReload()`
  - `handleHealth()`

#### CORS Headers Added
```java
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With
Access-Control-Max-Age: 3600
```

---

## Remaining Work

### 1. New Robin API Endpoints (HIGH PRIORITY)

The Angular UI needs JSON API endpoints that don't exist yet. Currently Robin returns HTML for queue list and config viewer.

#### Configuration API (`/api/config/*`)

**File to modify**: `src/main/java/com/mimecast/robin/endpoints/ApiEndpoint.java`

| Method | Endpoint | Implementation Notes |
|--------|----------|---------------------|
| GET | `/api/config/storage` | Return `Config.getServer().getStorage().getMap()` as JSON |
| PUT | `/api/config/storage` | Parse JSON body, update storage.json5, trigger reload |
| GET | `/api/config/queue` | Return `Config.getServer().getQueue().getMap()` as JSON |
| PUT | `/api/config/queue` | Parse JSON body, update queue.json5, trigger reload |
| GET | `/api/config/relay` | Return `Config.getServer().getRelay().getMap()` as JSON |
| PUT | `/api/config/relay` | Parse JSON body, update relay.json5, trigger reload |
| GET | `/api/config/dovecot` | Return `Config.getServer().getDovecot().getMap()` as JSON |
| PUT | `/api/config/dovecot` | Parse JSON body, update dovecot.json5, trigger reload |
| GET | `/api/config/clamav` | Return `Config.getServer().getClamAV().getMap()` as JSON |
| PUT | `/api/config/clamav` | Parse JSON body, update clamav.json5, trigger reload |
| GET | `/api/config/rspamd` | Return `Config.getServer().getRspamd().getMap()` as JSON |
| PUT | `/api/config/rspamd` | Parse JSON body, update rspamd.json5, trigger reload |
| GET | `/api/config/webhooks` | Return webhooks config as JSON |
| PUT | `/api/config/webhooks` | Update webhooks.json5, trigger reload |
| GET | `/api/config/blocklist` | Return blocklist config as JSON |
| PUT | `/api/config/blocklist` | Update blocklist.json5, trigger reload |
| GET | `/api/config/proxy` | Return proxy rules as JSON |
| PUT | `/api/config/proxy` | Update proxy.json5, trigger reload |

**Implementation pattern**:
```java
private void handleConfigStorage(HttpExchange exchange) throws IOException {
    if (handleCorsPreflightRequest(exchange)) return;

    if (!auth.isAuthenticated(exchange)) {
        auth.sendAuthRequired(exchange);
        return;
    }

    if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
        String json = gson.toJson(Config.getServer().getStorage().getMap());
        sendJson(exchange, 200, json);
    } else if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
        String body = readBody(exchange.getRequestBody());
        // TODO: Write to storage.json5, trigger Config.triggerReload()
        sendJson(exchange, 200, "{\"status\":\"OK\"}");
    } else {
        sendText(exchange, 405, "Method Not Allowed");
    }
}
```

#### User Management API (`/api/users/*`)

| Method | Endpoint | Implementation Notes |
|--------|----------|---------------------|
| GET | `/api/users` | Return `Config.getServer().getUsers().getMap()` as JSON array |
| POST | `/api/users` | Add user to users.json5, trigger reload |
| GET | `/api/users/{email}` | Get specific user |
| PUT | `/api/users/{email}` | Update user password |
| DELETE | `/api/users/{email}` | Remove user from users.json5 |

#### Queue JSON API (`/api/queue/*`)

| Method | Endpoint | Implementation Notes |
|--------|----------|---------------------|
| GET | `/api/queue` | Return queue items as JSON (not HTML), support pagination via `?page=1&limit=50` |
| GET | `/api/queue/{uid}` | Get specific queue item details |
| GET | `/api/queue/stats` | Return `{size, retryHistogram}` |

**Implementation for GET /api/queue**:
```java
private void handleQueueJson(HttpExchange exchange) throws IOException {
    if (handleCorsPreflightRequest(exchange)) return;
    if (!checkMethodAndAuth(exchange, "GET")) return;

    Map<String, String> query = parseQuery(exchange.getRequestURI());
    int page = Integer.parseInt(query.getOrDefault("page", "1"));
    int limit = Integer.parseInt(query.getOrDefault("limit", "50"));

    PersistentQueue<RelaySession> queue = PersistentQueue.getInstance();
    List<RelaySession> all = queue.snapshot();

    int start = (page - 1) * limit;
    int end = Math.min(start + limit, all.size());
    List<RelaySession> items = start < all.size() ? all.subList(start, end) : new ArrayList<>();

    Map<String, Object> response = new HashMap<>();
    response.put("items", items);
    response.put("totalCount", all.size());
    response.put("page", page);
    response.put("limit", limit);

    sendJson(exchange, 200, gson.toJson(response));
}
```

#### Storage JSON API (`/api/store/*`)

| Method | Endpoint | Implementation Notes |
|--------|----------|---------------------|
| GET | `/api/store` | Return directory listing as JSON (not HTML) |
| DELETE | `/api/store/{path}` | Delete message file |
| POST | `/api/store/search` | Search messages by query |

#### Scanner Status API (`/api/scanners/*`)

| Method | Endpoint | Implementation Notes |
|--------|----------|---------------------|
| GET | `/api/scanners/status` | Ping ClamAV and Rspamd, return connectivity status |
| POST | `/api/scanners/test` | Test scanner with sample data |

**Implementation**:
```java
private void handleScannersStatus(HttpExchange exchange) throws IOException {
    if (handleCorsPreflightRequest(exchange)) return;
    if (!checkMethodAndAuth(exchange, "GET")) return;

    Map<String, Object> status = new HashMap<>();

    // ClamAV status
    try {
        ClamAVClient clamav = new ClamAVClient(
            Config.getServer().getClamAV().getHost(),
            Config.getServer().getClamAV().getPort()
        );
        clamav.ping();
        status.put("clamav", Map.of("status", "UP", "version", clamav.getVersion()));
    } catch (Exception e) {
        status.put("clamav", Map.of("status", "DOWN", "error", e.getMessage()));
    }

    // Rspamd status
    try {
        RspamdClient rspamd = new RspamdClient(
            Config.getServer().getRspamd().getHost(),
            Config.getServer().getRspamd().getPort()
        );
        rspamd.ping();
        status.put("rspamd", Map.of("status", "UP"));
    } catch (Exception e) {
        status.put("rspamd", Map.of("status", "DOWN", "error", e.getMessage()));
    }

    sendJson(exchange, 200, gson.toJson(status));
}
```

---

### 2. Angular UI Enhancements (MEDIUM PRIORITY)

#### Dashboard Improvements
- Add real-time Chart.js graphs for queue size over time
- Add connection count visualization
- Add recent activity feed

#### Security Module Implementation
- Complete ClamAV configuration form with validation
- Complete Rspamd configuration form with threshold sliders
- Add "Test Connection" buttons that call `/api/scanners/test`
- Implement blocklist management with CIDR validation

#### Monitoring Module Implementation
- Implement metrics dashboard with Chart.js (fetch from `/metrics/graphite`)
- Implement log viewer with auto-refresh and search
- Add thread dump and heap dump buttons

#### Settings Module Implementation
- Complete server configuration form
- Complete user management CRUD
- Add confirmation dialogs for destructive operations

---

### 3. Testing (LOW PRIORITY)

#### Angular Unit Tests
```bash
cd robin-ui
npm test
```

#### Angular E2E Tests
```bash
npm run e2e
```

#### Java Unit Tests for New Endpoints
- Add tests in `src/test/java/com/mimecast/robin/endpoints/`

---

## Technical References

### Robin's Existing API Endpoints

**API Endpoint (port 8090)**:
- `POST /client/send` - Send email
- `POST /client/queue` - Queue email
- `GET /client/queue/list` - List queue (HTML)
- `POST /client/queue/delete` - Delete queue items
- `POST /client/queue/retry` - Retry queue items
- `POST /client/queue/bounce` - Bounce queue items
- `GET /logs?query=term` - Search logs
- `GET /store/[path]` - Browse storage (HTML)
- `GET /health` - Health check

**Service Endpoint (port 8080)**:
- `GET /config` - View config (HTML)
- `POST /config/reload` - Reload config
- `GET /system/env` - Environment variables
- `GET /system/props` - System properties
- `GET /system/threads` - Thread dump
- `GET /system/heapdump` - Heap dump
- `GET /metrics` - Metrics UI (HTML)
- `GET /metrics/graphite` - Graphite format
- `GET /metrics/prometheus` - Prometheus format
- `GET /health` - Health with stats (JSON)

### Key Java Classes

| Class | Purpose |
|-------|---------|
| `HttpEndpoint` | Base class with CORS, send methods |
| `ApiEndpoint` | API handlers (queue, storage, logs) |
| `RobinServiceEndpoint` | Service handlers (health, config, metrics) |
| `Config` | Configuration access |
| `PersistentQueue` | Queue operations |
| `ClamAVClient` | ClamAV integration |
| `RspamdClient` | Rspamd integration |

### Key Angular Services

| Service | Purpose |
|---------|---------|
| `ApiService` | HTTP calls to Robin API |
| `AuthService` | Basic auth credentials |
| `NotificationService` | Toast messages |
| `DashboardService` | Health polling |
| `QueueService` | Queue CRUD |
| `StorageService` | Storage browsing |

---

## Development Commands

```bash
# Angular development
cd robin-ui
npm install           # Install dependencies
npm start             # Dev server at http://localhost:4200
npm run build         # Production build

# Java compilation
cd /path/to/robin
mvn compile           # Compile Java
mvn test              # Run tests
mvn package           # Build JAR

# Run Robin server
java -jar target/robin-jar-with-dependencies.jar --server cfg/

# Docker suite
cd .suite
docker-compose up -d  # Start Dovecot, PostgreSQL, ClamAV, Rspamd
```

---

## Notes for Future Sessions

1. **Start with API endpoints**: The Angular UI is ready but waiting for JSON APIs
2. **Use the typescript-master agent** for Angular work
3. **Test CORS** by running both Angular (`npm start`) and Robin server
4. **Config updates** require writing to JSON5 files and calling `Config.triggerReload()`
5. **Gson serialization** uses `GsonExclusionStrategy` to filter sensitive fields
