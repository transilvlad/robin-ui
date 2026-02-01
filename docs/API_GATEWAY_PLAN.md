# Robin MTA API Gateway - Implementation Plan (FastAPI)

## Executive Summary

**Recommendation:** FastAPI (Python) API Gateway

**Rationale:**
- Team has Python expertise
- Enables rapid development and iteration
- Best ML/AI ecosystem for future spam detection/anomaly detection
- Modern async architecture with excellent performance
- Auto-generated OpenAPI documentation
- Works seamlessly with Docker Compose

## Architecture Overview

```
┌─────────────────┐
│   Robin UI      │
│  (Angular 18)   │
└────────┬────────┘
         │ HTTPS
         │ Port 8000
         ▼
┌─────────────────────────────────┐
│   FastAPI Gateway               │
│   - JWT Auth Middleware         │
│   - Rate Limiter (Redis)        │
│   - Circuit Breaker             │
│   - Request Validator           │
│   - Response Cache              │
│   - Prometheus Metrics          │
└────┬───────────────────────┬────┘
     │                       │
     │ Port 8090             │ Port 8080
     ▼                       ▼
┌─────────────┐         ┌─────────────┐
│ Robin Client│         │Robin Service│
│     API     │         │     API     │
└─────────────┘         └─────────────┘
```

## Technology Stack

- **Core:** FastAPI 0.110+ (Python 3.11+)
- **ASGI Server:** Uvicorn with Gunicorn workers
- **Auth:** python-jose (JWT), passlib (password hashing)
- **Rate Limiting:** slowapi (Redis-backed)
- **Circuit Breaker:** pybreaker
- **Caching:** redis-py, aiocache
- **HTTP Client:** httpx (async)
- **Validation:** Pydantic v2
- **Monitoring:** prometheus-client, structlog
- **Database:** SQLAlchemy 2.0 + PostgreSQL (user sessions)

## Project Structure

```
robin-gateway/
├── src/
│   ├── main.py                    # FastAPI app entry point
│   ├── config.py                  # Environment configuration
│   ├── auth/
│   │   ├── __init__.py
│   │   ├── jwt.py                 # JWT token handling
│   │   ├── models.py              # User, Session models
│   │   ├── service.py             # Authentication service
│   │   └── router.py              # /auth endpoints
│   ├── middleware/
│   │   ├── __init__.py
│   │   ├── rate_limit.py          # Rate limiting
│   │   ├── circuit_breaker.py     # Circuit breaker
│   │   ├── cache.py               # Response caching
│   │   └── monitoring.py          # Prometheus metrics
│   ├── robin/
│   │   ├── __init__.py
│   │   ├── client.py              # Robin Client API client
│   │   ├── service.py             # Robin Service API client
│   │   ├── models.py              # Robin API response models
│   │   └── router.py              # Proxy endpoints
│   ├── models/
│   │   ├── __init__.py
│   │   ├── user.py                # User database models
│   │   ├── session.py             # Session models
│   │   └── api.py                 # API request/response models
│   └── database.py                # SQLAlchemy setup
├── tests/
│   ├── test_auth.py
│   ├── test_robin_proxy.py
│   └── test_rate_limiting.py
├── alembic/                       # Database migrations
├── docker/
│   ├── Dockerfile
│   └── docker-compose.yml
├── requirements.txt
├── requirements-dev.txt
├── .env.example
└── README.md
```

## Implementation Phases

### Phase 1: Project Setup (Day 1)

**Goal:** Initialize FastAPI project with basic structure

**Tasks:**
1. Create new directory at `~/development/workspace/personal/robin-gateway/`
2. Set up Python virtual environment (Python 3.11+)
3. Install core dependencies:
   ```
   fastapi==0.110.0
   uvicorn[standard]==0.27.0
   gunicorn==21.2.0
   pydantic==2.6.0
   pydantic-settings==2.1.0
   python-jose[cryptography]==3.3.0
   passlib[bcrypt]==1.7.4
   httpx==0.26.0
   ```
4. Create project structure (directories above)
5. Set up `.env.example` with configuration template
6. Initialize git repository

**Critical Files to Create:**
- `src/main.py` - FastAPI application entry point
- `src/config.py` - Environment configuration using pydantic-settings
- `requirements.txt` - Production dependencies
- `requirements-dev.txt` - Development dependencies (pytest, black, ruff)
- `.env.example` - Configuration template

### Phase 2: Authentication System (Days 2-3)

**Goal:** Implement JWT-based authentication with user management

**Tasks:**
1. Set up PostgreSQL database connection with SQLAlchemy
2. Create User model with fields: id, username, email, password_hash, roles, permissions, created_at
3. Create Session model for refresh token storage
4. Implement authentication service:
   - `login()` - Validate credentials, generate JWT tokens
   - `refresh_token()` - Generate new access token from refresh token
   - `logout()` - Revoke refresh token
5. Create auth router with endpoints:
   - `POST /api/v1/auth/login` - Login endpoint
   - `POST /api/v1/auth/refresh` - Refresh token endpoint
   - `POST /api/v1/auth/logout` - Logout endpoint
6. Implement JWT middleware for token verification
7. Implement permission-based authorization decorator
8. Set up database migrations with Alembic

**Security Considerations:**
- Store refresh tokens as HttpOnly cookies
- Use bcrypt for password hashing (passlib)
- Set JWT expiration: 30min (access), 7 days (refresh)
- Add request ID tracking for audit logs

**Critical Files to Create:**
- `src/auth/jwt.py` - JWT token creation/verification
- `src/auth/service.py` - Authentication business logic
- `src/auth/router.py` - Auth endpoints
- `src/auth/models.py` - SQLAlchemy User/Session models
- `src/middleware/auth.py` - JWT verification middleware
- `src/database.py` - Database connection setup
- `alembic/versions/001_create_users.py` - User table migration

**Reference:**
- `AUTH_IMPLEMENTATION_PLAN.md` - Auth requirements
- `src/app/core/services/auth.service.ts` - Current auth patterns

### Phase 3: Robin MTA Integration (Days 4-5)

**Goal:** Implement proxy to Robin Client API (8090) and Service API (8080)

**Tasks:**
1. Create async HTTP clients for both Robin APIs using httpx:
   - `RobinClientService` for port 8090
   - `RobinServiceService` for port 8080
2. Implement connection pooling (max 100 connections, 20 keepalive)
3. Create Pydantic models for Robin API responses:
   - `QueueItem`, `QueueListResponse`
   - `StorageItem`, `StorageListResponse`
   - `HealthResponse`, `MetricsResponse`
   - `ServerConfig`, `LogEntry`
4. Create proxy router with endpoints:
   - `GET /api/v1/queue` → `/client/queue?page=&size=`
   - `POST /api/v1/queue/{uid}/retry` → `/client/queue/{uid}/retry`
   - `DELETE /api/v1/queue/{uid}` → `/client/queue/{uid}`
   - `GET /api/v1/storage` → `/store?path=`
   - `GET /api/v1/logs` → `/logs?lines=&level=`
   - `GET /api/v1/health` → `/health` (both APIs)
   - `GET /api/v1/config` → `/config`
   - `GET /api/v1/metrics` → `/metrics`
5. Add request/response logging with request ID
6. Handle Robin API errors gracefully

**Critical Files to Create:**
- `src/robin/client.py` - Robin Client API (8090) client
- `src/robin/service.py` - Robin Service API (8080) client
- `src/robin/models.py` - Robin API response models
- `src/robin/router.py` - Proxy endpoints

**Reference:**
- `src/app/core/services/api.service.ts` - Current API integration
- `proxy.conf.json` - Current routing logic
- `src/app/core/models/` - TypeScript models to convert

### Phase 4: Rate Limiting & CORS (Day 6)

**Goal:** Add rate limiting and CORS handling

**Tasks:**
1. Install slowapi and configure Redis backend
2. Implement rate limiting:
   - Global: 100 req/min per IP
   - Login endpoint: 5 req/min per IP (strict)
   - Queue endpoints: 100 req/min per user
   - Health endpoint: No limit
3. Configure CORS middleware:
   - Allow origins: `http://localhost:4200`, `https://robin-ui.example.com`
   - Allow credentials: true
   - Allow methods: GET, POST, PUT, DELETE, OPTIONS
   - Allow headers: Authorization, Content-Type
4. Add GZip compression middleware (min 1000 bytes)

**Critical Files to Create:**
- `src/middleware/rate_limit.py` - Rate limiting configuration
- `src/main.py` (update) - Add CORS and compression middleware

**Reference:**
- `.docker/nginx.conf.template` - Current CORS config

### Phase 5: Circuit Breaker & Resilience (Day 7)

**Goal:** Implement circuit breaker pattern for backend services

**Tasks:**
1. Install pybreaker
2. Create circuit breakers for Robin Client API and Service API:
   - Failure threshold: 5 consecutive failures
   - Timeout: 30 seconds open state
   - Automatically transition to half-open for testing
3. Add circuit breaker decorator to Robin service methods
4. Handle circuit breaker errors with 503 Service Unavailable
5. Add health check endpoint showing circuit breaker status

**Critical Files to Create:**
- `src/middleware/circuit_breaker.py` - Circuit breaker service
- `src/health.py` - Health check endpoint

### Phase 6: Caching (Day 8)

**Goal:** Implement response caching for read-heavy endpoints

**Tasks:**
1. Install aiocache with Redis serializer
2. Add caching decorator for:
   - `/api/v1/health` - TTL 30 seconds
   - `/api/v1/config` - TTL 5 minutes
   - `/api/v1/metrics` - TTL 1 minute
3. Implement cache invalidation on config updates
4. Add cache hit/miss metrics to Prometheus

**Critical Files to Create:**
- `src/middleware/cache.py` - Caching decorator and service

### Phase 7: Monitoring & Logging (Day 9)

**Goal:** Add comprehensive monitoring and structured logging

**Tasks:**
1. Install prometheus-client and structlog
2. Implement Prometheus metrics:
   - `gateway_requests_total` - Counter by method, endpoint, status
   - `gateway_request_duration_seconds` - Histogram by method, endpoint
   - `gateway_circuit_breaker_state` - Gauge by service
   - `gateway_rate_limit_exceeded_total` - Counter
3. Set up structured logging with request ID:
   - Log all requests: method, path, status, duration
   - Log errors with stack traces
   - Log circuit breaker state changes
4. Add `/metrics` endpoint for Prometheus scraping
5. Add `/health` endpoint for liveness/readiness checks

**Critical Files to Create:**
- `src/middleware/monitoring.py` - Prometheus metrics
- `src/middleware/logging.py` - Structured logging setup

### Phase 8: WebSocket Support (Day 10, Optional)

**Goal:** Add WebSocket endpoint for real-time queue monitoring

**Tasks:**
1. Install `websockets` library
2. Create WebSocket endpoint `/ws/queue`
3. Verify JWT token from query parameter
4. Poll Robin queue every 5 seconds and broadcast to connected clients
5. Handle client connect/disconnect events

**Critical Files to Create:**
- `src/websocket/queue_monitor.py` - WebSocket handler

### Phase 9: Docker Deployment (Day 11)

**Goal:** Containerize gateway and set up Docker Compose

**Tasks:**
1. Create multi-stage Dockerfile:
   - Build stage: Install dependencies
   - Production stage: Copy built app, run with Gunicorn + Uvicorn workers
2. Create docker-compose.yml with services:
   - `gateway` - FastAPI app (port 8000)
   - `redis` - Redis 7 (port 6379)
   - `postgres` - PostgreSQL 15 (port 5432)
3. Add health checks to all services
4. Configure environment variables
5. Add volume mounts for persistence
6. Test full stack deployment

**Critical Files to Create:**
- `docker/Dockerfile` - Multi-stage Docker build
- `docker/docker-compose.yml` - Service orchestration
- `.dockerignore` - Exclude unnecessary files

**Reference:**
- `.docker/` - Current Docker setup

### Phase 10: Testing (Day 12)

**Goal:** Write comprehensive tests

**Tasks:**
1. Install pytest, pytest-asyncio, httpx test client
2. Write unit tests:
   - `test_auth.py` - Login, logout, token refresh, JWT validation
   - `test_robin_proxy.py` - All proxy endpoints
   - `test_rate_limiting.py` - Rate limit enforcement
   - `test_circuit_breaker.py` - Circuit breaker state transitions
3. Write integration tests:
   - End-to-end authentication flow
   - Proxy with authentication
   - Error handling scenarios
4. Set up pytest fixtures for database, Redis, mock Robin APIs
5. Achieve 80%+ code coverage

**Critical Files to Create:**
- `tests/conftest.py` - Pytest fixtures
- `tests/test_auth.py`
- `tests/test_robin_proxy.py`
- `tests/test_rate_limiting.py`

### Phase 11: Documentation (Day 13)

**Goal:** Create comprehensive documentation

**Tasks:**
1. Write README.md with:
   - Project overview
   - Installation instructions
   - Configuration guide
   - API documentation (link to /docs)
   - Deployment guide
2. Add OpenAPI tags and descriptions to all endpoints
3. Configure Swagger UI at `/docs`
4. Configure ReDoc at `/redoc`
5. Create API migration guide for Robin UI integration

**Critical Files to Create:**
- `README.md` - Project documentation
- `docs/API_MIGRATION.md` - Guide for updating Robin UI

### Phase 12: Robin UI Integration (Day 14)

**Goal:** Update Robin UI to use new gateway

**Tasks:**
1. Update environment files:
   - Development: `apiUrl: 'http://localhost:8000/api/v1'`
   - Production: `apiUrl: '/api/v1'` (Nginx proxy)
2. Update proxy.conf.json to point to gateway (port 8000)
3. Update ApiService methods to match new endpoints
4. Update auth service to handle JWT properly
5. Test all features end-to-end
6. Update Docker Compose to include gateway

**Critical Files to Modify:**
- `src/environments/environment.ts`
- `src/environments/environment.prod.ts`
- `proxy.conf.json`
- `src/app/core/services/api.service.ts`
- `.docker/docker-compose.yaml`

## Future Enhancements (Post-MVP)

### ML/AI Integration (Phase 13+)
1. **Anomaly Detection:**
   - Monitor queue patterns for unusual spikes
   - Detect suspicious login attempts
   - Alert on abnormal API usage

2. **Spam Detection:**
   - Integrate with scikit-learn or TensorFlow
   - Train models on historical spam data
   - Real-time scoring of incoming emails

3. **Predictive Analytics:**
   - Queue size forecasting
   - Resource usage prediction
   - Capacity planning insights

### Advanced Features
1. **API Versioning** - Support v1, v2 endpoints
2. **GraphQL Support** - Add GraphQL endpoint alongside REST
3. **Request Replay** - Debug failed requests
4. **Audit Logging** - Comprehensive audit trail
5. **Admin Dashboard** - Gateway management UI

## Configuration Management

### Environment Variables

```bash
# Server
PORT=8000
HOST=0.0.0.0
WORKERS=4

# Security
JWT_SECRET=<generate-random-secret>
JWT_ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=30
REFRESH_TOKEN_EXPIRE_DAYS=7

# Backend APIs
ROBIN_CLIENT_API_URL=http://robin-backend:8090
ROBIN_SERVICE_API_URL=http://robin-backend:8080

# Database
DATABASE_URL=postgresql://robin:password@postgres:5432/robin_gateway

# Redis
REDIS_URL=redis://redis:6379/0

# Rate Limiting
RATE_LIMIT_ENABLED=true
RATE_LIMIT_PER_MINUTE=100

# Circuit Breaker
CIRCUIT_BREAKER_FAILURE_THRESHOLD=5
CIRCUIT_BREAKER_TIMEOUT_SECONDS=30

# Logging
LOG_LEVEL=INFO
LOG_FORMAT=json

# CORS
CORS_ORIGINS=http://localhost:4200,https://robin-ui.example.com
CORS_ALLOW_CREDENTIALS=true
```

## Performance Targets

- **Throughput:** 10,000-20,000 req/s (single instance)
- **Latency:** <5ms overhead (p95)
- **Memory:** 100-200MB per worker
- **Availability:** 99.9% uptime

## Security Checklist

- ✅ JWT tokens with short expiration (30min access, 7 days refresh)
- ✅ HttpOnly cookies for refresh tokens
- ✅ Bcrypt password hashing (cost factor 12)
- ✅ CORS properly configured
- ✅ Rate limiting on all endpoints
- ✅ Request/response validation with Pydantic
- ✅ SQL injection prevention (SQLAlchemy ORM)
- ✅ No secrets in code (environment variables)
- ✅ HTTPS in production (nginx/load balancer)
- ✅ Security headers (X-Frame-Options, CSP, etc.)

## Deployment Strategy

### Development
```bash
cd robin-gateway
python -m venv venv
source venv/bin/activate
pip install -r requirements-dev.txt
uvicorn src.main:app --reload --port 8000
```

### Production (Docker Compose)
```bash
cd robin-gateway/docker
docker-compose up -d
```

### Scaling (Future - Kubernetes)
- Deploy multiple gateway replicas
- Use Redis for shared state (rate limiting, sessions)
- PostgreSQL with connection pooling
- Horizontal pod autoscaler based on CPU/memory

## Success Metrics

1. **Development Velocity:**
   - Gateway MVP in 14 days
   - Ready for production deployment

2. **Performance:**
   - <5ms gateway overhead
   - 10,000+ req/s throughput
   - 99.9% uptime

3. **Developer Experience:**
   - Auto-generated API docs at /docs
   - Type-safe requests/responses
   - Comprehensive test coverage (80%+)

4. **Operational Excellence:**
   - Prometheus metrics for monitoring
   - Structured logging for debugging
   - Health checks for reliability
   - Circuit breakers for resilience

## Alternative Options Considered

While FastAPI is recommended based on your requirements, the following alternatives were evaluated:

1. **Spring Cloud Gateway (Java):** Best for Java teams, enterprise features, stack alignment with Robin MTA
2. **NestJS (TypeScript):** Best for full-stack TypeScript teams, shared types with Angular UI
3. **Kong Gateway (Platform):** Best for production scale, config-driven, minimal code

**Why FastAPI Won:**
- ✅ Team has Python expertise
- ✅ Fastest time-to-market
- ✅ Best ML/AI ecosystem for future features
- ✅ Modern async architecture
- ✅ Excellent documentation (auto-generated OpenAPI)
- ✅ Lower operational complexity than Spring/Kong

## Gateway Project Location

**Project Path:** `~/development/workspace/personal/robin-gateway/`

This follows the workspace organization rules:
- **documents/** - Research, planning, documentation ONLY
- **workspace/** - Software project implementations ← Gateway goes here
- **infrastructure/** - Infrastructure as Code
