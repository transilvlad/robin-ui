# Robin UI - Outstanding Tasks

**Last Updated**: 2026-01-17

---

## Priority 1: Backend API Endpoints (Required for UI to function)

### Task 1.1: Add Queue JSON API
**File**: `src/main/java/com/mimecast/robin/endpoints/ApiEndpoint.java`
**Effort**: Medium

Add these endpoints:
- [ ] `GET /api/queue` - Return queue items as JSON with pagination
- [ ] `GET /api/queue/{uid}` - Get specific queue item
- [ ] `GET /api/queue/stats` - Return queue statistics

```java
// Register in start() method:
server.createContext("/api/queue", this::handleQueueJson);

// Handler implementation:
private void handleQueueJson(HttpExchange exchange) throws IOException {
    if (handleCorsPreflightRequest(exchange)) return;
    if (!checkMethodAndAuth(exchange, "GET")) return;

    String path = exchange.getRequestURI().getPath();

    if (path.equals("/api/queue/stats")) {
        // Return stats only
        Map<String, Object> stats = new HashMap<>();
        stats.put("size", RelayQueueCron.getQueueSize());
        stats.put("retryHistogram", RelayQueueCron.getRetryHistogram());
        sendJson(exchange, 200, gson.toJson(stats));
        return;
    }

    // Parse pagination
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

---

### Task 1.2: Add Configuration GET APIs
**File**: `src/main/java/com/mimecast/robin/endpoints/ApiEndpoint.java`
**Effort**: Medium

Add these endpoints:
- [ ] `GET /api/config/storage`
- [ ] `GET /api/config/queue`
- [ ] `GET /api/config/relay`
- [ ] `GET /api/config/dovecot`
- [ ] `GET /api/config/clamav`
- [ ] `GET /api/config/rspamd`
- [ ] `GET /api/config/webhooks`
- [ ] `GET /api/config/blocklist`
- [ ] `GET /api/config/proxy`
- [ ] `GET /api/config/users`
- [ ] `GET /api/config/bots`

```java
// Register in start() method:
server.createContext("/api/config", this::handleConfigApi);

// Handler implementation:
private void handleConfigApi(HttpExchange exchange) throws IOException {
    if (handleCorsPreflightRequest(exchange)) return;
    if (!auth.isAuthenticated(exchange)) {
        auth.sendAuthRequired(exchange);
        return;
    }

    String path = exchange.getRequestURI().getPath();
    String configType = path.replace("/api/config/", "");

    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
        sendText(exchange, 405, "Method Not Allowed");
        return;
    }

    Map<String, Object> configMap;
    switch (configType) {
        case "storage" -> configMap = Config.getServer().getStorage().getMap();
        case "queue" -> configMap = Config.getServer().getQueue().getMap();
        case "relay" -> configMap = Config.getServer().getRelay().getMap();
        case "dovecot" -> configMap = Config.getServer().getDovecot().getMap();
        case "clamav" -> configMap = Config.getServer().getClamAV().getMap();
        case "rspamd" -> configMap = Config.getServer().getRspamd().getMap();
        case "webhooks" -> {
            configMap = new HashMap<>();
            Config.getServer().getWebhooks().forEach((k, v) -> configMap.put(k, v.getMap()));
        }
        case "blocklist" -> configMap = Config.getServer().getBlocklist().getMap();
        case "proxy" -> configMap = Config.getServer().getProxy().getMap();
        case "users" -> configMap = Config.getServer().getUsers().getMap();
        case "bots" -> configMap = Config.getServer().getBots().getMap();
        default -> {
            sendText(exchange, 404, "Unknown config type: " + configType);
            return;
        }
    }

    sendJson(exchange, 200, gson.toJson(configMap));
}
```

---

### Task 1.3: Add Scanner Status API
**File**: `src/main/java/com/mimecast/robin/endpoints/ApiEndpoint.java`
**Effort**: Small

Add endpoint:
- [ ] `GET /api/scanners/status` - Return ClamAV and Rspamd connectivity status

```java
// Register in start() method:
server.createContext("/api/scanners/status", this::handleScannersStatus);

// Handler implementation:
private void handleScannersStatus(HttpExchange exchange) throws IOException {
    if (handleCorsPreflightRequest(exchange)) return;
    if (!checkMethodAndAuth(exchange, "GET")) return;

    Map<String, Object> status = new HashMap<>();

    // ClamAV
    var clamavConfig = Config.getServer().getClamAV();
    if (clamavConfig.isEnabled()) {
        try {
            ClamAVClient client = new ClamAVClient(
                clamavConfig.getHost(),
                clamavConfig.getPort()
            );
            client.ping();
            status.put("clamav", Map.of(
                "enabled", true,
                "status", "UP",
                "host", clamavConfig.getHost(),
                "port", clamavConfig.getPort()
            ));
        } catch (Exception e) {
            status.put("clamav", Map.of(
                "enabled", true,
                "status", "DOWN",
                "error", e.getMessage()
            ));
        }
    } else {
        status.put("clamav", Map.of("enabled", false));
    }

    // Rspamd
    var rspamdConfig = Config.getServer().getRspamd();
    if (rspamdConfig.isEnabled()) {
        try {
            RspamdClient client = new RspamdClient(
                rspamdConfig.getHost(),
                rspamdConfig.getPort()
            );
            client.ping();
            status.put("rspamd", Map.of(
                "enabled", true,
                "status", "UP",
                "host", rspamdConfig.getHost(),
                "port", rspamdConfig.getPort()
            ));
        } catch (Exception e) {
            status.put("rspamd", Map.of(
                "enabled", true,
                "status", "DOWN",
                "error", e.getMessage()
            ));
        }
    } else {
        status.put("rspamd", Map.of("enabled", false));
    }

    sendJson(exchange, 200, gson.toJson(status));
}
```

---

### Task 1.4: Add Storage JSON API
**File**: `src/main/java/com/mimecast/robin/endpoints/ApiEndpoint.java`
**Effort**: Medium

Add endpoint:
- [ ] `GET /api/store` - Return directory listing as JSON (not HTML)
- [ ] `DELETE /api/store/{path}` - Delete message file

```java
// Add Accept header check to existing handleStore:
String accept = exchange.getRequestHeaders().getFirst("Accept");
if (accept != null && accept.contains("application/json")) {
    // Return JSON listing
    StorageDirectoryListing listing = new StorageDirectoryListing("/store");
    List<Map<String, Object>> items = listing.generateItemsJson(target, decoded);
    sendJson(exchange, 200, gson.toJson(Map.of("items", items, "path", decoded)));
    return;
}
```

---

## Priority 2: Configuration Update APIs (For settings management)

### Task 2.1: Add Configuration PUT APIs
**File**: `src/main/java/com/mimecast/robin/endpoints/ApiEndpoint.java`
**Effort**: Large

Add these endpoints:
- [ ] `PUT /api/config/storage`
- [ ] `PUT /api/config/queue`
- [ ] `PUT /api/config/relay`
- [ ] `PUT /api/config/dovecot`
- [ ] `PUT /api/config/clamav`
- [ ] `PUT /api/config/rspamd`
- [ ] `PUT /api/config/webhooks`
- [ ] `PUT /api/config/blocklist`

**Note**: This requires:
1. Parsing JSON body
2. Writing to the appropriate `.json5` file in `cfg/`
3. Calling `Config.triggerReload()`
4. Handling validation errors

---

### Task 2.2: Add User Management APIs
**File**: `src/main/java/com/mimecast/robin/endpoints/ApiEndpoint.java`
**Effort**: Medium

Add endpoints:
- [ ] `GET /api/users` - List all users
- [ ] `POST /api/users` - Create user
- [ ] `PUT /api/users/{email}` - Update user
- [ ] `DELETE /api/users/{email}` - Delete user

---

## Priority 3: Angular UI Enhancements

### Task 3.1: Complete Security Module
**Files**: `robin-ui/src/app/features/security/`
**Effort**: Medium

- [ ] ClamAV configuration form with host/port/action fields
- [ ] Rspamd configuration form with threshold sliders
- [ ] "Test Connection" buttons using `/api/scanners/status`
- [ ] Blocklist management with IP/CIDR input validation

---

### Task 3.2: Complete Monitoring Module
**Files**: `robin-ui/src/app/features/monitoring/`
**Effort**: Medium

- [ ] Metrics dashboard with Chart.js graphs
- [ ] Log viewer with search and auto-refresh
- [ ] Diagnostics page with thread/heap dump buttons

---

### Task 3.3: Complete Settings Module
**Files**: `robin-ui/src/app/features/settings/`
**Effort**: Large

- [ ] Server configuration form
- [ ] User management CRUD interface
- [ ] Storage configuration form
- [ ] Dovecot configuration form
- [ ] Queue backend selection

---

### Task 3.4: Dashboard Enhancements
**Files**: `robin-ui/src/app/features/dashboard/`
**Effort**: Small

- [ ] Add queue size history chart
- [ ] Add connection count widget
- [ ] Add recent activity feed

---

## Priority 4: Testing

### Task 4.1: Java Unit Tests
**Location**: `src/test/java/com/mimecast/robin/endpoints/`
**Effort**: Medium

- [ ] Test new API endpoints
- [ ] Test CORS headers
- [ ] Test authentication

---

### Task 4.2: Angular Tests
**Location**: `robin-ui/`
**Effort**: Medium

- [ ] Unit tests for services
- [ ] Component tests for widgets
- [ ] E2E tests for critical flows

---

## Quick Start for Next Session

```bash
# 1. Start Robin server (if not running)
cd /Users/cstan/development/workspace/open-source/transilvlad-robin
java -jar target/robin-jar-with-dependencies.jar --server cfg/

# 2. Start Angular dev server
cd robin-ui
npm start

# 3. Open browser
open http://localhost:4200

# 4. Check API docs
open http://localhost:8090/
open http://localhost:8080/
```

---

## File Quick Reference

| Purpose | Location |
|---------|----------|
| Implementation Plan | `doc/robin-ui-plan.md` |
| Status & Details | `doc/robin-ui-implementation-status.md` |
| This TODO | `doc/robin-ui-todo.md` |
| Angular Project | `robin-ui/` |
| API Endpoint | `src/main/java/com/mimecast/robin/endpoints/ApiEndpoint.java` |
| Service Endpoint | `src/main/java/com/mimecast/robin/endpoints/RobinServiceEndpoint.java` |
| Base HTTP | `src/main/java/com/mimecast/robin/endpoints/HttpEndpoint.java` |
