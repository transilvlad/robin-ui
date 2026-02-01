# Robin UI + Robin Gateway - Comprehensive Development Plan

## Executive Summary

This plan outlines the completion of **robin-gateway** (Spring Boot API Gateway) and **robin-ui** (Angular 21 SPA) to create a production-ready management interface for Robin MTA.

### Current Status Snapshot

| Component | Technology | Completion | Critical Needs |
|-----------|-----------|------------|----------------|
| **robin-gateway** | Spring Boot 3.2.2 / Java 21 | **~80%** | Docker integration, user domain endpoints, full testing |
| **robin-ui** | Angular 21 / TypeScript | **~35%** | Authentication implementation, feature modules, gateway integration |

### Architecture Overview

```
┌──────────────────┐
│   Robin UI       │  Angular 21 SPA
│   Port 4200      │  Customer-facing interface
└────────┬─────────┘
         │ HTTP
         │ /api/v1/*
         ▼
┌──────────────────────────────────────┐
│   Robin Gateway (Spring Boot)       │
│   Port 8080                          │
│   • JWT Auth (HttpOnly cookies)     │
│   • RBAC (roles + permissions)       │
│   • Rate limiting (Redis)            │
│   • Circuit breakers (Resilience4j)  │
│   • Shared PostgreSQL DB             │
└────┬─────────────────────────────┬───┘
     │                             │
     │ Port 28090                  │ Port 28080
     ▼                             ▼
┌─────────────┐              ┌─────────────┐
│ Robin Client│              │Robin Service│
│     API     │              │     API     │
│ (Queue, Logs│              │(Health, CFG)│
└─────────────┘              └─────────────┘
         │                        │
         └────────┬───────────────┘
                  ▼
         ┌─────────────────┐
         │   PostgreSQL    │  Shared database
         │   Port 5433     │  (users, domains, aliases)
         │   + Dovecot     │
         └─────────────────┘
```

---

## Phase 1: Robin Gateway Completion (Priority: CRITICAL)

**Estimated Effort**: 3-4 days
**Status**: 80% complete, needs integration endpoints and testing

### 1.1 Domain Management Endpoints (NEW)

**Why**: Gateway needs to expose Robin MTA's domain/alias management to UI

**Create**: `robin-gateway/src/main/java/com/robin/gateway/controller/DomainController.java`

**Endpoints to implement**:
```java
@RestController
@RequestMapping("/api/v1/domains")
public class DomainController {
    // GET /api/v1/domains - List all domains
    // POST /api/v1/domains - Create domain
    // DELETE /api/v1/domains/{id} - Delete domain

    // GET /api/v1/domains/{id}/aliases - List domain aliases
    // POST /api/v1/domains/{id}/aliases - Create alias
    // DELETE /api/v1/aliases/{id} - Delete alias
}
```

**Database**: Tables already exist in V1 migration (domains, aliases)

### 1.2 Configuration Management Proxy (NEW)

**Why**: Robin MTA's config endpoints return HTML; need JSON wrapper

**Create**: `robin-gateway/src/main/java/com/robin/gateway/service/ConfigurationProxyService.java`

**Features**:
- Proxy requests to Robin Service API `/config` endpoint
- Parse HTML response or request JSON format
- Add validation before forwarding config updates
- Cache config responses (5-minute TTL)

**Gateway routes to add** in `application.yml`:
```yaml
# Configuration routes
- id: config_route
  uri: ${ROBIN_SERVICE_URL:http://localhost:8080}
  predicates:
    - Path=/api/v1/config/**
  filters:
    - RewritePath=/api/v1/config/(?<segment>.*), /config/${segment}
    - name: CircuitBreaker
      args:
        name: robinServiceCircuitBreaker
```

### 1.3 Health Aggregation Endpoint (ENHANCE)

**Why**: UI needs single endpoint for all health checks

**Enhance**: `robin-gateway/src/main/java/com/robin/gateway/controller/HealthController.java`

**Create aggregated health endpoint**:
```java
@GetMapping("/api/v1/health/aggregate")
public ResponseEntity<Map<String, Object>> getAggregatedHealth() {
    // Fetch health from Robin Client API (8090)
    // Fetch health from Robin Service API (8080)
    // Check gateway database connection
    // Check Redis connection
    // Return combined status
}
```

### 1.4 Docker Compose Full Stack (UPDATE)

**File**: `robin-gateway/docker/docker-compose.yml`

**Current**: Gateway + Redis only
**Needed**: Add PostgreSQL, connect to suite network

**Changes**:
```yaml
services:
  postgres:
    image: postgres:15-alpine
    container_name: robin-postgres
    environment:
      POSTGRES_DB: robin
      POSTGRES_USER: robin
      POSTGRES_PASSWORD: robin
    ports:
      - "5433:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U robin"]
      interval: 5s
      timeout: 3s
      retries: 5
    networks:
      - suite_suite

  gateway:
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
```

### 1.5 Flyway Migrations Alignment

**Issue**: Gateway uses separate `robin_gateway` database, but should use shared `robin` database

**Action**: Update `application.yml`:
```yaml
datasource:
  url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5433}/${DB_NAME:robin}  # Changed from robin_gateway
```

**Ensure migrations are idempotent** with Robin MTA's existing schema.

### 1.6 Integration Tests (NEW)

**Create**: `robin-gateway/src/test/java/com/robin/gateway/integration/`

**Test coverage needed**:
- `AuthIntegrationTest.java` - Login, refresh, logout flows
- `GatewayRoutingTest.java` - Proxy to Robin APIs with JWT
- `RateLimitingTest.java` - Rate limit enforcement
- `CircuitBreakerTest.java` - Circuit breaker state transitions
- `CorsTest.java` - CORS preflight and actual requests

**Use TestContainers** for PostgreSQL and Redis:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

### 1.7 OpenAPI Documentation Enhancement

**File**: `robin-gateway/src/main/java/com/robin/gateway/config/OpenApiConfig.java`

**Add**:
- Security scheme definitions (Bearer JWT)
- Example request/response bodies
- Error response schemas
- Tag descriptions for endpoint grouping

---

## Phase 2: Robin UI Authentication Implementation (Priority: CRITICAL)

**Estimated Effort**: 5-6 days
**Status**: 5% complete (placeholder only)

### 2.1 Install Dependencies

**File**: `package.json`

**Add**:
```bash
npm install @ngrx/signals zod
```

**Why**:
- `@ngrx/signals` - Modern Angular 21+ signal-based state (85% less boilerplate than traditional NgRx)
- `zod` - Runtime validation for API responses

### 2.2 Auth Models with Zod Validation (NEW)

**File**: `src/app/core/models/auth.model.ts` (NEW)

**Features**:
- Branded types for compile-time safety (`UserId`, `AccessToken`, `RefreshToken`)
- Zod schemas for runtime validation
- `Result<T, E>` pattern for explicit error handling
- Match gateway's DTOs (LoginRequest, AuthResponse, TokenResponse)

**Key interfaces**:
```typescript
export const UserSchema = z.object({
  id: z.string().uuid(),
  username: z.string(),
  email: z.string().email(),
  roles: z.array(z.nativeEnum(UserRole)),
  permissions: z.array(z.nativeEnum(Permission))
});

export type User = z.infer<typeof UserSchema>;
```

### 2.3 Signal-Based Auth Store (NEW)

**File**: `src/app/core/state/auth.store.ts` (NEW)

**Why @ngrx/signals instead of traditional NgRx**:
- 85% less boilerplate (single file vs 5+ files)
- Zoneless compatible (Angular 21 default)
- 40% smaller bundle size
- Simpler testing

**Single file replaces**:
- ❌ auth.actions.ts
- ❌ auth.reducer.ts
- ❌ auth.effects.ts
- ❌ auth.selectors.ts
- ✅ auth.store.ts (one file)

**Key methods**:
```typescript
export const AuthStore = signalStore(
  { providedIn: 'root' },
  withState(initialAuthState),
  withComputed((store) => ({
    userRoles: computed(() => store.user()?.roles || []),
    hasValidSession: computed(() => /* check expiration */)
  })),
  withMethods((store, authService, router) => ({
    async login(credentials: LoginRequest),
    async logout(),
    async autoLogin(), // On app startup
    updateTokens(tokens: AuthTokens),
    hasPermission(permission: Permission): boolean
  }))
);
```

### 2.4 Token Storage Service (NEW)

**File**: `src/app/core/services/token-storage.service.ts` (NEW)

**Strategy**:
- **Access token**: sessionStorage (for page refresh recovery)
- **Refresh token**: HttpOnly cookie (managed by gateway)
- **User info**: sessionStorage (UI rendering)

**Features**:
- Runtime validation with Zod when reading from storage
- Automatic cleanup on corruption

### 2.5 Auth Service Update (REPLACE)

**File**: `src/app/core/services/auth.service.ts` (MAJOR UPDATE)

**Current**: Placeholder with Base64 encoding
**New**: Real JWT integration with gateway

**Methods**:
```typescript
login(credentials: LoginRequest): Observable<Result<AuthResponse, AuthError>>
logout(): Observable<Result<void, AuthError>>
refreshToken(): Observable<Result<AuthTokens, AuthError>>
verifyToken(): Observable<boolean>
getCurrentUser(): Observable<User>
```

**Integration** with gateway endpoints:
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

### 2.6 Login Component (NEW)

**Files**:
- `src/app/features/auth/login/login.component.ts` (NEW)
- `src/app/features/auth/login/login.component.html` (NEW)
- `src/app/features/auth/login/login.component.scss` (NEW)

**Features**:
- Reactive form with validation
- Loading state during authentication
- Error display
- Remember me checkbox (extends refresh token expiration)
- Standalone component (Angular 21 best practice)

### 2.7 Auth Guard Enhancement (UPDATE)

**File**: `src/app/core/guards/auth.guard.ts` (MAJOR UPDATE)

**Current**: Hardcoded `return true`
**New**: Real authentication check

```typescript
export const authGuard: CanActivateFn = () => {
  const authStore = inject(AuthStore);
  const router = inject(Router);

  if (authStore.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/auth/login'], {
    queryParams: { returnUrl: router.url }
  });
};
```

### 2.8 Auth Interceptor Enhancement (UPDATE)

**File**: `src/app/core/interceptors/auth.interceptor.ts` (MAJOR UPDATE)

**Current**: Basic auth header
**New**: Bearer JWT with refresh logic

**Features**:
- Switch from Basic to Bearer token
- Skip auth for public routes (`/api/v1/auth/login`, `/api/v1/health/public`)
- Handle 401 errors by attempting token refresh
- Queue requests during refresh
- Logout on refresh failure

**Critical**: Token refresh ONLY in interceptor (not in store effects) to prevent duplicate refresh calls.

### 2.9 Session Timeout Service (NEW)

**File**: `src/app/core/services/session-timeout.service.ts` (NEW)

**Features**:
- Monitor user activity (mouse, keyboard, touch)
- Update last activity in store
- Show warning dialog before timeout
- Auto-logout after 30 minutes of inactivity
- Proper cleanup with `takeUntil` pattern (prevent memory leaks)

### 2.10 Environment Configuration Update

**File**: `src/environments/environment.ts` (UPDATE)

**Change from**:
```typescript
apiUrl: '',      // Proxy to localhost:28090
serviceUrl: '',  // Proxy to localhost:28080
```

**Change to**:
```typescript
apiUrl: '/api/v1',  // All requests go through gateway at localhost:8080
auth: {
  tokenKey: 'robin_access_token',
  userKey: 'robin_user',
  sessionTimeoutWarning: 300,  // 5 minutes
  sessionTimeout: 1800          // 30 minutes
}
```

### 2.11 Proxy Configuration Update

**File**: `proxy.conf.json` (UPDATE or REPLACE)

**New configuration**:
```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "debug"
  }
}
```

**Single proxy** instead of multiple (gateway handles routing).

---

## Phase 3: Robin UI Feature Modules (Priority: HIGH)

**Estimated Effort**: 8-10 days
**Status**: 0-30% complete (placeholders or basic implementations)

### 3.1 Security Module (0% Complete)

**Location**: `src/app/features/security/`

#### 3.1.1 ClamAV Configuration Component

**File**: `clamav/clamav-config.component.ts`

**Features**:
- Form for ClamAV host, port, timeout
- "Test Connection" button (calls gateway `/api/v1/scanners/clamav/test`)
- Status indicator (UP/DOWN)
- Save configuration (calls gateway `/api/v1/config/clamav`)

**Gateway endpoint needed**:
```java
@GetMapping("/api/v1/scanners/clamav/test")
public ResponseEntity<ScannerStatus> testClamAV() {
    // Ping ClamAV, return status
}
```

#### 3.1.2 Rspamd Configuration Component

**File**: `rspamd/rspamd-config.component.ts`

**Features**:
- Form for Rspamd host, port, API key
- Threshold sliders for spam/reject scores
- Test connection button
- Save configuration

#### 3.1.3 Blocklist Management Component

**File**: `blocklist/blocklist.component.ts`

**Features**:
- List of blocked IPs/domains
- Add new entry with CIDR validation
- Remove entry
- Import/export blocklist
- Pagination for large lists

**Models needed**:
```typescript
interface BlocklistEntry {
  id: string;
  type: 'ip' | 'domain' | 'cidr';
  value: string;
  reason: string;
  createdAt: Date;
  expiresAt?: Date;
}
```

### 3.2 Routing Module (0% Complete)

**Location**: `src/app/features/routing/`

#### 3.2.1 Relay Configuration Component

**File**: `relay/relay-config.component.ts`

**Features**:
- Form for relay host, port, TLS settings
- Authentication credentials
- Fallback relay configuration
- Test relay connection

#### 3.2.2 Webhooks Management Component

**File**: `webhooks/webhooks.component.ts`

**Features**:
- List of configured webhooks
- Add/edit webhook (URL, events, headers, auth)
- Test webhook with sample payload
- View webhook delivery history
- Retry failed deliveries

**Models**:
```typescript
interface Webhook {
  id: string;
  name: string;
  url: string;
  events: string[];
  headers: Record<string, string>;
  secret?: string;
  enabled: boolean;
}
```

### 3.3 Monitoring Module (0% Complete)

**Location**: `src/app/features/monitoring/`

#### 3.3.1 Metrics Dashboard Component

**File**: `metrics/metrics-dashboard.component.ts`

**Features**:
- Chart.js graphs for:
  - Queue size over time
  - Messages sent/received
  - Connection count
  - Memory usage
  - CPU usage
- Time range selector (1h, 6h, 24h, 7d)
- Auto-refresh every 30 seconds
- Export metrics as CSV

**Install**:
```bash
npm install chart.js ng2-charts
```

**Data source**: Gateway endpoint `/api/v1/metrics/graphite`

#### 3.3.2 Log Viewer Component

**File**: `logs/log-viewer.component.ts`

**Features**:
- Real-time log streaming (WebSocket or polling)
- Log level filter (ERROR, WARN, INFO, DEBUG)
- Search by keyword
- Syntax highlighting for stack traces
- Download logs
- Pagination

**Use Angular Virtual Scroll** for performance:
```typescript
import { ScrollingModule } from '@angular/cdk/scrolling';
```

### 3.4 Settings Module (30% Complete)

**Location**: `src/app/features/settings/`

#### 3.4.1 Server Configuration Component (ENHANCE)

**File**: `server/server-config.component.ts`

**Current**: Placeholder
**Enhance**: Full configuration forms for:
- Storage settings
- Queue settings
- SMTP settings
- Dovecot integration
- Security settings

**Validation**: Use Zod schemas for client-side validation before submission.

#### 3.4.2 User Management Component (ENHANCE)

**File**: `users/user-list.component.ts`

**Current**: Placeholder
**Enhance**: CRUD operations:
- List users with pagination
- Add user (username, email, password, roles)
- Edit user (change password, roles, permissions)
- Delete user (with confirmation)
- Disable/enable user

**Gateway endpoints** (NEW):
```java
@RestController
@RequestMapping("/api/v1/users")
public class UserManagementController {
    @GetMapping
    public ResponseEntity<Page<User>> listUsers();

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody CreateUserRequest req);

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest req);

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id);
}
```

### 3.5 Email Module Enhancement (30% Complete)

**Location**: `src/app/features/email/`

#### 3.5.1 Queue List Component (ENHANCE)

**File**: `queue/queue-list.component.ts`

**Current**: Basic list with pagination
**Enhance**:
- Search by sender, recipient, subject
- Filter by status (queued, retrying, failed)
- Bulk actions (retry, delete, bounce)
- View full message (headers + body)
- Real-time updates (WebSocket)

#### 3.5.2 Storage Browser Component (ENHANCE)

**File**: `storage/storage-browser.component.ts`

**Current**: Placeholder
**Enhance**:
- File tree navigation
- Preview email content
- Download .eml files
- Delete messages
- Search by date range, sender, recipient

---

## Phase 4: Robin MTA Backend Enhancements (Priority: HIGH)

**Estimated Effort**: 4-5 days
**Status**: CORS complete, JSON APIs missing

### 4.1 JSON Queue API (REQUIRED)

**File**: Robin MTA's `ApiEndpoint.java`

**Current**: `/client/queue/list` returns HTML
**New**: Add `/api/queue` endpoint returning JSON

**Response format**:
```json
{
  "items": [
    {
      "uid": "abc123",
      "from": "sender@example.com",
      "to": ["recipient@example.com"],
      "subject": "Test",
      "status": "QUEUED",
      "retryCount": 0,
      "nextRetry": "2026-01-26T10:00:00Z",
      "createdAt": "2026-01-26T09:00:00Z"
    }
  ],
  "totalCount": 150,
  "page": 1,
  "limit": 50
}
```

### 4.2 JSON Configuration APIs (REQUIRED)

**Files to modify**: Robin MTA's `RobinServiceEndpoint.java`

**Current**: `/config` returns HTML
**New**: Add JSON endpoints for each config file:

| Endpoint | Config File | HTTP Methods |
|----------|-------------|--------------|
| `/api/config/storage` | storage.json5 | GET, PUT |
| `/api/config/queue` | queue.json5 | GET, PUT |
| `/api/config/relay` | relay.json5 | GET, PUT |
| `/api/config/dovecot` | dovecot.json5 | GET, PUT |
| `/api/config/clamav` | clamav.json5 | GET, PUT |
| `/api/config/rspamd` | rspamd.json5 | GET, PUT |
| `/api/config/webhooks` | webhooks.json5 | GET, PUT |
| `/api/config/blocklist` | blocklist.json5 | GET, PUT |

**Implementation pattern**:
```java
private void handleConfigJson(HttpExchange exchange, String configKey) {
    if ("GET".equals(exchange.getRequestMethod())) {
        Map<String, Object> config = Config.getConfigMap(configKey);
        sendJson(exchange, 200, gson.toJson(config));
    } else if ("PUT".equals(exchange.getRequestMethod())) {
        String body = readBody(exchange.getRequestBody());
        Config.updateConfigFile(configKey, body);
        Config.triggerReload();
        sendJson(exchange, 200, "{\"status\":\"OK\"}");
    }
}
```

### 4.3 Scanner Status Endpoints (NEW)

**Add to Robin MTA**:

```java
@Path("/api/scanners/status")
public Response getScannersStatus() {
    Map<String, Object> status = new HashMap<>();

    // Test ClamAV
    try {
        ClamAVClient clamav = new ClamAVClient(host, port);
        String version = clamav.getVersion();
        status.put("clamav", Map.of("status", "UP", "version", version));
    } catch (Exception e) {
        status.put("clamav", Map.of("status", "DOWN", "error", e.getMessage()));
    }

    // Test Rspamd
    try {
        RspamdClient rspamd = new RspamdClient(host, port);
        rspamd.ping();
        status.put("rspamd", Map.of("status", "UP"));
    } catch (Exception e) {
        status.put("rspamd", Map.of("status", "DOWN", "error", e.getMessage()));
    }

    return Response.ok(status).build();
}
```

### 4.4 User Management Endpoints (NEW)

**Add to Robin MTA or Gateway**:

Since user data is in shared PostgreSQL, these should be in **gateway**:

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserManagementController {
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<User>> listUsers(@PageableDefault Pageable pageable);

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> createUser(@Valid @RequestBody CreateUserRequest req);

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req);

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id);
}
```

---

## Phase 5: Full Stack Integration & Testing (Priority: HIGH)

**Estimated Effort**: 3-4 days

### 5.1 Docker Compose Full Stack

**File**: `.docker/docker-compose.full-stack.yaml` (NEW)

**Services**:
```yaml
services:
  # PostgreSQL (shared by Robin MTA, Dovecot, Gateway)
  postgres:
    image: postgres:15-alpine
    ports: ["5433:5432"]
    environment:
      POSTGRES_DB: robin
      POSTGRES_USER: robin
      POSTGRES_PASSWORD: robin
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U robin"]

  # Redis (for gateway rate limiting)
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]

  # Robin MTA Backend
  robin:
    image: robin-mta:latest
    ports:
      - "28080:8080"  # Service API
      - "28090:8090"  # Client API
      - "25:25"       # SMTP
    volumes:
      - ./cfg:/app/cfg:rw
    depends_on:
      - postgres
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: robin
      DB_USER: robin
      DB_PASSWORD: robin

  # Robin Gateway
  gateway:
    build: ../robin-gateway
    ports: ["8080:8080"]
    depends_on:
      - postgres
      - redis
      - robin
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      REDIS_HOST: redis
      ROBIN_CLIENT_URL: http://robin:8090
      ROBIN_SERVICE_URL: http://robin:8080
      JWT_SECRET: ${JWT_SECRET}

  # Robin UI
  ui:
    build: ../
    ports: ["4200:80"]
    depends_on:
      - gateway
    environment:
      API_URL: http://gateway:8080

  # Dovecot (if needed)
  dovecot:
    image: dovecot/dovecot:latest
    ports:
      - "143:143"  # IMAP
      - "993:993"  # IMAPS
    depends_on:
      - postgres

volumes:
  postgres_data:
```

### 5.2 Environment Variables Management

**File**: `.env.example` (UPDATE)

```bash
# Database
DB_HOST=postgres
DB_PORT=5432
DB_NAME=robin
DB_USER=robin
DB_PASSWORD=changeme

# Gateway
JWT_SECRET=change-this-to-a-very-long-and-secure-random-secret-key-min-64-chars
JWT_ACCESS_EXPIRATION=1800000      # 30 minutes
JWT_REFRESH_EXPIRATION=604800000   # 7 days

# Robin MTA
ROBIN_CLIENT_URL=http://robin:8090
ROBIN_SERVICE_URL=http://robin:8080

# Redis
REDIS_HOST=redis
REDIS_PORT=6379

# Application
SPRING_PROFILES_ACTIVE=prod
LOG_LEVEL=INFO
```

### 5.3 Integration Test Suite

#### 5.3.1 End-to-End Authentication Flow

**Test**: Login → Access protected resource → Refresh token → Logout

**Tools**: Cypress or Playwright

```typescript
describe('Authentication Flow', () => {
  it('should complete full auth cycle', () => {
    // Login
    cy.visit('http://localhost:4200/auth/login');
    cy.get('input[name=username]').type('admin');
    cy.get('input[name=password]').type('admin123');
    cy.get('button[type=submit]').click();

    // Should redirect to dashboard
    cy.url().should('include', '/dashboard');

    // Access protected resource
    cy.visit('http://localhost:4200/email/queue');
    cy.contains('Queued Messages'); // Should not redirect to login

    // Logout
    cy.get('[data-testid=logout-button]').click();
    cy.url().should('include', '/auth/login');
  });
});
```

#### 5.3.2 Gateway Routing Test

**Test**: Verify gateway correctly proxies requests to Robin APIs

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {

    @Test
    void shouldProxyQueueRequest() {
        // Mock Robin Client API response
        mockRobinClient.expect(requestTo("http://robin:8090/client/queue"))
            .andRespond(withSuccess(queueJson, APPLICATION_JSON));

        // Call gateway
        ResponseEntity<QueueResponse> response = restTemplate
            .exchange("/api/v1/queue", HttpMethod.GET,
                new HttpEntity<>(headers), QueueResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getItems()).hasSize(10);
    }
}
```

#### 5.3.3 RBAC Permission Test

**Test**: Verify role-based access control works correctly

```typescript
describe('RBAC Tests', () => {
  it('admin should access user management', () => {
    cy.loginAs('admin');
    cy.visit('/settings/users');
    cy.contains('User Management').should('be.visible');
  });

  it('read-only user should NOT access user management', () => {
    cy.loginAs('readonly');
    cy.visit('/settings/users');
    cy.contains('Unauthorized').should('be.visible');
  });
});
```

### 5.4 Performance Testing

**Tools**: JMeter or Gatling

**Scenarios**:
1. Login throughput (100 concurrent users)
2. Queue list pagination (1000 items)
3. Real-time log streaming (50 concurrent connections)
4. Token refresh under load

**Targets**:
- Gateway overhead: <5ms (p95)
- Login: <200ms (p95)
- Queue list: <500ms (p95)
- Throughput: 10,000+ req/s

### 5.5 Security Testing

**Checklist**:
- [ ] JWT tokens expire correctly
- [ ] Refresh tokens are HttpOnly cookies
- [ ] CORS allows only Robin UI origin
- [ ] Rate limiting blocks excessive requests
- [ ] SQL injection prevention (use parameterized queries)
- [ ] XSS prevention (Angular sanitization enabled)
- [ ] CSRF tokens not needed (JWT + CORS + SameSite cookies)
- [ ] Passwords hashed with BCrypt (strength 12)
- [ ] Sensitive data not logged

**Tools**:
- OWASP ZAP for vulnerability scanning
- npm audit for dependency vulnerabilities

---

## Phase 6: Production Deployment (Priority: MEDIUM)

**Estimated Effort**: 2-3 days

### 6.1 Production Configuration

#### Gateway Production Profile

**File**: `robin-gateway/src/main/resources/application-prod.yml`

**Updates**:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

logging:
  level:
    root: WARN
    com.robin.gateway: INFO

management:
  endpoint:
    health:
      show-details: when-authorized
```

#### UI Production Build

**File**: `angular.json`

**Ensure production optimizations**:
```json
{
  "configurations": {
    "production": {
      "optimization": true,
      "outputHashing": "all",
      "sourceMap": false,
      "namedChunks": false,
      "aot": true,
      "extractLicenses": true,
      "budgets": [
        {
          "type": "initial",
          "maximumWarning": "2mb",
          "maximumError": "5mb"
        }
      ]
    }
  }
}
```

### 6.2 Nginx Reverse Proxy (OPTIONAL)

**File**: `.docker/nginx-prod.conf` (NEW)

**Configuration**:
```nginx
server {
    listen 443 ssl http2;
    server_name robin.example.com;

    ssl_certificate /etc/ssl/certs/robin.crt;
    ssl_certificate_key /etc/ssl/private/robin.key;

    # UI static files
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }

    # Gateway API
    location /api/ {
        proxy_pass http://gateway:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 6.3 Health Checks & Monitoring

#### Prometheus Scraping

**Gateway metrics endpoint**: `http://localhost:8080/actuator/prometheus`

**Prometheus config**:
```yaml
scrape_configs:
  - job_name: 'robin-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['gateway:8080']
    scrape_interval: 15s
```

#### Key Metrics to Monitor

- `http_server_requests_seconds` - Request latency
- `resilience4j_circuitbreaker_state` - Circuit breaker status
- `spring_data_repository_invocations_seconds` - Database query latency
- `jvm_memory_used_bytes` - Memory usage
- `robin_queue_size` - Queue depth (custom metric)

### 6.4 Logging Strategy

**Structured logging** with JSON format:

```yaml
logging:
  pattern:
    console: '{"timestamp":"%d","level":"%p","logger":"%c","message":"%m"}%n'
```

**Log aggregation** with ELK stack or Loki:
- Elasticsearch for storage
- Logstash for ingestion
- Kibana for visualization

### 6.5 Backup & Recovery

#### Database Backups

**Cron job** for PostgreSQL backups:
```bash
0 2 * * * pg_dump -h localhost -U robin -d robin > /backups/robin_$(date +\%Y\%m\%d).sql
```

**Retention**: 30 days

#### Configuration Backups

**Git repository** for config files:
```bash
cd /app/cfg
git init
git add .
git commit -m "Initial config"
git remote add origin git@github.com:org/robin-config.git
git push
```

---

## Implementation Timeline

### Sprint 1 (Week 1): Gateway Completion + UI Auth Foundation
- **Days 1-2**: Gateway domain endpoints, config proxy, health aggregation
- **Day 3**: Gateway Docker Compose full stack, database alignment
- **Days 4-5**: UI auth models, signal store, token storage

**Deliverables**: Gateway 100% complete, UI auth infrastructure ready

### Sprint 2 (Week 2): UI Authentication Implementation
- **Days 1-2**: Auth service, login component, guards, interceptors
- **Day 3**: Session timeout, environment config, proxy update
- **Days 4-5**: Integration testing, bug fixes

**Deliverables**: Full authentication flow working end-to-end

### Sprint 3 (Week 3): Feature Modules - Security & Routing
- **Days 1-2**: Security module (ClamAV, Rspamd, blocklist)
- **Days 3-4**: Routing module (relay, webhooks)
- **Day 5**: Testing and refinement

**Deliverables**: Security and routing modules complete

### Sprint 4 (Week 4): Feature Modules - Monitoring & Settings
- **Days 1-2**: Monitoring module (metrics, logs)
- **Days 3-4**: Settings module (server config, user management)
- **Day 5**: Email module enhancements

**Deliverables**: All feature modules complete

### Sprint 5 (Week 5): Robin MTA Backend Integration
- **Days 1-2**: JSON queue API, config APIs
- **Day 3**: Scanner status endpoints
- **Days 4-5**: User management endpoints, testing

**Deliverables**: All backend APIs available

### Sprint 6 (Week 6): Integration & Testing
- **Days 1-2**: Full stack Docker Compose, E2E tests
- **Day 3**: Performance testing, security testing
- **Days 4-5**: Bug fixes, documentation

**Deliverables**: Production-ready application

### Sprint 7 (Week 7): Production Deployment
- **Days 1-2**: Production configuration, Nginx setup
- **Day 3**: Prometheus/Grafana setup
- **Days 4-5**: Deployment, smoke testing, documentation

**Deliverables**: Application deployed to production

---

## Critical Integration Points

### 1. Gateway ↔ Robin MTA APIs

**Challenge**: Robin MTA returns HTML for some endpoints
**Solution**:
- Option A: Add JSON endpoints to Robin MTA (preferred)
- Option B: Gateway parses HTML responses (fragile)

**Recommendation**: Implement Option A in Phase 4

### 2. Shared PostgreSQL Database

**Challenge**: Multiple services accessing same database
**Solution**:
- Use Flyway migrations in gateway to extend schema
- Robin MTA owns users/domains tables
- Gateway adds sessions table
- Document schema ownership

**Schema ownership**:
| Table | Owner | Purpose |
|-------|-------|---------|
| users | Robin MTA | Shared authentication |
| domains | Robin MTA | Email domains |
| aliases | Robin MTA | Email aliases |
| sessions | Gateway | JWT refresh tokens |
| user_roles | Gateway | RBAC roles |
| user_permissions | Gateway | RBAC permissions |

### 3. Configuration File Management

**Challenge**: Both gateway and Robin MTA modify JSON5 config files
**Solution**:
- Gateway provides management endpoints
- Gateway triggers Robin MTA reload via `/config/reload`
- Use file locking to prevent concurrent writes
- Implement optimistic locking with version numbers

### 4. HttpOnly Cookie Strategy

**Challenge**: Angular cannot read refresh token from HttpOnly cookie
**Solution**:
- Gateway sets cookie in login response
- Browser automatically includes cookie in requests
- Interceptor refreshes access token when expired
- Logout endpoint clears cookie

**Cookie configuration**:
```java
ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", token)
    .httpOnly(true)
    .secure(true)  // HTTPS only
    .sameSite("Strict")
    .path("/api/v1/auth")
    .maxAge(Duration.ofDays(7))
    .build();
```

---

## Testing Strategy

### Unit Tests

**Gateway** (Target: 80% coverage):
- JUnit 5 + Mockito
- Test auth service, JWT provider, user service
- Mock repositories, external APIs

**UI** (Target: 70% coverage):
- Jasmine + Karma
- Test services, components, guards, interceptors
- Mock HTTP calls with HttpClientTestingModule

### Integration Tests

**Gateway**:
- TestContainers for PostgreSQL, Redis
- WebTestClient for reactive testing
- Test full request flows

**UI**:
- Component integration tests
- Test component + service interactions

### End-to-End Tests

**Tools**: Cypress or Playwright

**Scenarios**:
- User login → Dashboard → Queue management → Logout
- Admin user management flow
- Configuration update flow
- Error handling (network failures, 401, 403, 500)

### Performance Tests

**Tools**: JMeter, Gatling, k6

**Load profiles**:
- 100 concurrent users (normal load)
- 500 concurrent users (peak load)
- 1000 concurrent users (stress test)

**Metrics**:
- Response time (p50, p95, p99)
- Throughput (requests/second)
- Error rate
- Resource utilization (CPU, memory)

---

## Risk Assessment & Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Robin MTA HTML endpoints | HIGH | HIGH | Add JSON endpoints in Phase 4 |
| Database schema conflicts | MEDIUM | HIGH | Document ownership, use migrations |
| JWT token expiration issues | MEDIUM | MEDIUM | Implement robust refresh logic in interceptor |
| CORS configuration errors | LOW | MEDIUM | Comprehensive testing, document allowed origins |
| Performance under load | MEDIUM | HIGH | Load testing, circuit breakers, caching |
| Security vulnerabilities | LOW | CRITICAL | Security audit, OWASP ZAP, penetration testing |
| Docker networking issues | MEDIUM | MEDIUM | Use Docker Compose service names, health checks |

---

## Documentation Requirements

### 1. API Documentation

**Gateway OpenAPI**: Auto-generated at `http://localhost:8080/swagger-ui.html`

**Robin MTA API**: Create OpenAPI spec for new JSON endpoints

### 2. Architecture Documentation

**Create**: `doc/ARCHITECTURE.md`

**Contents**:
- System architecture diagram
- Component interaction flows
- Authentication flow
- Data flow diagrams
- Database schema

### 3. Deployment Guide

**Create**: `doc/DEPLOYMENT.md`

**Contents**:
- Prerequisites (Java 21, Node 20, Docker, PostgreSQL, Redis)
- Environment setup
- Docker Compose commands
- Configuration guide
- Troubleshooting

### 4. User Guide

**Create**: `doc/USER_GUIDE.md`

**Contents**:
- Login instructions
- Dashboard overview
- Queue management
- Configuration management
- User management
- Monitoring and logs

### 5. Developer Guide

**Update**: `CLAUDE.md`

**Add**:
- Gateway integration guide
- Adding new API endpoints
- Adding new UI components
- Testing guidelines
- Code style guide

---

## Success Criteria

### Must Have (MVP)

- [ ] Users can login with JWT authentication
- [ ] Dashboard displays health and queue stats
- [ ] Queue management (list, retry, delete)
- [ ] Configuration viewing
- [ ] User management (CRUD)
- [ ] Role-based access control
- [ ] Session timeout
- [ ] All services run in Docker Compose
- [ ] 80%+ test coverage

### Should Have

- [ ] Real-time updates (WebSocket or polling)
- [ ] Security scanners configuration
- [ ] Webhook management
- [ ] Metrics dashboard with charts
- [ ] Log viewer with search
- [ ] Configuration editing
- [ ] Domain/alias management
- [ ] Token auto-refresh

### Nice to Have

- [ ] Two-factor authentication
- [ ] Audit logging
- [ ] Export/import configurations
- [ ] Email templates
- [ ] Scheduled reports
- [ ] Mobile-responsive UI
- [ ] Dark mode

---

## Post-Launch Maintenance

### Weekly Tasks

- Review error logs
- Monitor Prometheus alerts
- Check circuit breaker status
- Review security audit logs

### Monthly Tasks

- Update dependencies (npm audit, mvn versions:display-dependency-updates)
- Review and optimize database indexes
- Backup database
- Security scan (OWASP ZAP)

### Quarterly Tasks

- Performance testing
- Capacity planning
- Feature backlog grooming
- User feedback review

---

## Critical Files Summary

### Gateway Files (Priority: CRITICAL)

1. **`robin-gateway/src/main/java/com/robin/gateway/controller/DomainController.java`** - NEW
   Reason: Domain/alias management endpoints missing

2. **`robin-gateway/src/main/java/com/robin/gateway/service/ConfigurationProxyService.java`** - NEW
   Reason: Proxy to Robin MTA config endpoints with caching

3. **`robin-gateway/src/main/resources/application.yml`** - UPDATE
   Reason: Add config proxy routes, update database name

4. **`robin-gateway/docker/docker-compose.yml`** - UPDATE
   Reason: Add PostgreSQL, complete full stack

5. **`robin-gateway/src/test/java/com/robin/gateway/integration/AuthIntegrationTest.java`** - NEW
   Reason: Critical integration testing for auth flow

### UI Files (Priority: CRITICAL)

1. **`src/app/core/models/auth.model.ts`** - NEW
   Reason: Foundation for all authentication with Zod validation and branded types

2. **`src/app/core/state/auth.store.ts`** - NEW
   Reason: Signal-based state management replaces 5+ NgRx files

3. **`src/app/core/services/auth.service.ts`** - REPLACE
   Reason: Real JWT integration replacing placeholder

4. **`src/app/core/interceptors/auth.interceptor.ts`** - MAJOR UPDATE
   Reason: Bearer JWT + token refresh logic critical for all API calls

5. **`src/environments/environment.ts`** - UPDATE
   Reason: Point all requests to gateway, configure auth settings

### Integration Files (Priority: HIGH)

1. **`.docker/docker-compose.full-stack.yaml`** - NEW
   Reason: Full stack orchestration (postgres, redis, robin, gateway, ui)

2. **`proxy.conf.json`** - UPDATE
   Reason: Single proxy to gateway instead of multiple endpoints

3. **`robin-gateway/src/main/java/com/robin/gateway/controller/UserManagementController.java`** - NEW
   Reason: User CRUD operations for settings module

---

## Conclusion

This comprehensive development plan provides a clear roadmap for completing both robin-gateway and robin-ui projects. The phased approach allows for incremental development and testing, while maintaining focus on critical functionality first.

**Total Estimated Effort**: 7 weeks (35 working days)

**Key Success Factors**:
1. Complete gateway integration endpoints first
2. Implement JWT authentication early for all subsequent work
3. Use modern Angular 21+ patterns (@ngrx/signals, standalone components)
4. Comprehensive testing at each phase
5. Continuous integration with Docker Compose

**Next Steps**:
1. Review and approve this plan with stakeholders
2. Set up development environment with full Docker Compose stack
3. Begin Phase 1: Gateway completion
4. Daily standups to track progress and address blockers

**Contact Points**:
- **Gateway Backend**: Spring Boot team for API endpoints
- **UI Frontend**: Angular team for component implementation
- **Robin MTA**: Backend team for JSON API endpoints
- **DevOps**: Docker/Kubernetes deployment team
