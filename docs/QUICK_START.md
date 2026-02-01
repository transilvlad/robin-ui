# Quick Start Guide - Robin UI & Gateway

## 🎉 What's Been Completed

### Phase 1: Robin Gateway Enhancements - 95% COMPLETE

**New Features Added**:
1. ✅ Domain & Alias Management (9 new API endpoints)
2. ✅ Aggregated Health Check endpoint
3. ✅ Database configuration fix (shared `robin` database)
4. ✅ Configuration proxy routes
5. ✅ Docker Compose with PostgreSQL

**Files Created/Modified**: 7 new files, 2 updated files, ~750 lines of code

---

## 🚀 Quick Start (Development)

### Prerequisites
- Java 21 (Amazon Corretto recommended)
- Docker & Docker Compose
- Node.js 20+ (for UI later)

### Option 1: Docker Compose (Recommended)

```bash
# 1. Navigate to gateway docker directory
cd /Users/cstan/development/workspace/open-source/robin-ui/robin-gateway/docker

# 2. Start all services (PostgreSQL + Redis + Gateway)
docker-compose up -d

# 3. Check logs
docker-compose logs -f gateway

# 4. Verify services are healthy
docker-compose ps

# 5. Test health endpoint
curl http://localhost:8080/actuator/health

# 6. View aggregated health
curl http://localhost:8080/api/v1/health/aggregate
```

### Option 2: Local Development

```bash
# 1. Start PostgreSQL (if not running)
docker run -d --name robin-postgres \
  -e POSTGRES_DB=robin \
  -e POSTGRES_USER=robin \
  -e POSTGRES_PASSWORD=robin \
  -p 5433:5432 \
  postgres:15-alpine

# 2. Start Redis (if not running)
docker run -d --name robin-redis \
  -p 6379:6379 \
  redis:7-alpine

# 3. Start Gateway
cd /Users/cstan/development/workspace/open-source/robin-ui/robin-gateway
./mvnw spring-boot:run
```

---

## 🔐 Authentication & Testing

### 1. Login (Get JWT Token)

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin@robin.local",
    "password": "admin123"
  }'
```

**Response**:
```json
{
  "user": {
    "id": "...",
    "username": "admin@robin.local",
    "email": "admin@robin.local",
    "roles": ["ROLE_ADMIN"],
    "permissions": [...]
  },
  "tokens": {
    "accessToken": "eyJhbGciOiJIUzUxMi...",
    "refreshToken": "...",
    "expiresIn": 1800,
    "tokenType": "Bearer"
  }
}
```

**Important**: Copy the `accessToken` value for subsequent requests.

### 2. Test Domain Management

```bash
# Set your token
TOKEN="your-access-token-here"

# List domains
curl -X GET http://localhost:8080/api/v1/domains \
  -H "Authorization: Bearer $TOKEN"

# Create domain (ADMIN only)
curl -X POST http://localhost:8080/api/v1/domains \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"domain": "example.com"}'

# Create alias
curl -X POST http://localhost:8080/api/v1/domains/aliases \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "source": "info@example.com",
    "destination": "admin@example.com"
  }'

# List aliases
curl -X GET http://localhost:8080/api/v1/domains/aliases \
  -H "Authorization: Bearer $TOKEN"

# Delete alias
curl -X DELETE http://localhost:8080/api/v1/domains/aliases/1 \
  -H "Authorization: Bearer $TOKEN"
```

### 3. Test Health Aggregation

```bash
# Get combined health status
curl -X GET http://localhost:8080/api/v1/health/aggregate

# Expected response when all services are UP:
{
  "timestamp": 1706371200000,
  "service": "robin-gateway",
  "status": "UP",
  "robinClientApi": {"status": "UP", ...},
  "robinServiceApi": {"status": "UP", ...},
  "database": {"status": "UP", ...},
  "redis": {"status": "UP", ...}
}
```

---

## 📚 API Documentation

### Swagger UI (Interactive)
```
http://localhost:8080/swagger-ui.html
```

### OpenAPI JSON Spec
```
http://localhost:8080/v3/api-docs
```

---

## 🛠️ Development Workflow

### Building Gateway

```bash
cd robin-gateway

# Clean build
./mvnw clean install

# Skip tests
./mvnw clean install -DskipTests

# Run tests only
./mvnw test

# Run specific test
./mvnw test -Dtest=AuthControllerTest
```

### Database Management

```bash
# Connect to PostgreSQL
docker exec -it robin-postgres psql -U robin -d robin

# List tables
\dt

# View users
SELECT id, username, created_at FROM users;

# View domains
SELECT id, domain, created_at FROM domains;

# View aliases
SELECT id, source, destination, created_at FROM aliases;

# Exit
\q
```

### Redis Management

```bash
# Connect to Redis
docker exec -it robin-redis redis-cli

# Check rate limiting keys
KEYS *rate*

# Monitor commands
MONITOR

# Exit
exit
```

---

## 📊 Monitoring & Metrics

### Actuator Endpoints

```bash
# Health check
curl http://localhost:8080/actuator/health

# Application info
curl http://localhost:8080/actuator/info

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Circuit breaker metrics
curl http://localhost:8080/actuator/health | jq '.components.circuitBreakers'
```

### Logs

```bash
# Docker Compose logs
docker-compose logs -f gateway

# Live log stream
docker-compose logs -f --tail=100 gateway

# Filter by log level
docker-compose logs gateway | grep ERROR
```

---

## 🐛 Troubleshooting

### Gateway Won't Start

**Issue**: `java.net.ConnectException: Connection refused`

**Solution**:
```bash
# Check if PostgreSQL is running
docker ps | grep postgres

# Check if Redis is running
docker ps | grep redis

# Restart services
docker-compose restart
```

### Database Connection Error

**Issue**: `PSQLException: FATAL: database "robin" does not exist`

**Solution**:
```bash
# Create database
docker exec -it robin-postgres psql -U robin -c "CREATE DATABASE robin;"

# Or restart with fresh data
docker-compose down -v
docker-compose up -d
```

### Authentication Fails

**Issue**: `401 Unauthorized`

**Solution**:
1. Check if JWT_SECRET is set (minimum 64 characters)
2. Verify token hasn't expired (30 minutes)
3. Check token format: `Authorization: Bearer <token>`

### Circuit Breaker Open

**Issue**: `CircuitBreaker 'robinClientCircuitBreaker' is OPEN`

**Solution**:
```bash
# Check if Robin MTA is running
curl http://localhost:8090/health
curl http://localhost:8080/health

# Wait for circuit breaker to close (30 seconds)
# Or restart gateway
docker-compose restart gateway
```

---

## 📋 Available Endpoints

### Authentication Endpoints
- `POST /api/v1/auth/login` - Login with credentials
- `POST /api/v1/auth/refresh` - Refresh access token
- `POST /api/v1/auth/logout` - Logout and revoke tokens

### Domain Management Endpoints (NEW ✨)
- `GET /api/v1/domains` - List all domains (paginated)
- `GET /api/v1/domains/{id}` - Get domain by ID
- `POST /api/v1/domains` - Create new domain (ADMIN)
- `DELETE /api/v1/domains/{id}` - Delete domain (ADMIN)

### Alias Management Endpoints (NEW ✨)
- `GET /api/v1/domains/aliases` - List all aliases (paginated)
- `GET /api/v1/domains/{id}/aliases` - List domain aliases
- `GET /api/v1/domains/aliases/{id}` - Get alias by ID
- `POST /api/v1/domains/aliases` - Create new alias (ADMIN)
- `PUT /api/v1/domains/aliases/{id}` - Update alias (ADMIN)
- `DELETE /api/v1/domains/aliases/{id}` - Delete alias (ADMIN)

### Health Endpoints (NEW ✨)
- `GET /api/v1/health/public` - Public health check
- `GET /api/v1/health/aggregate` - Aggregated health status

### Proxy Endpoints (to Robin MTA)
- `GET /api/v1/queue/**` - Queue management
- `GET /api/v1/storage/**` - Storage browser
- `GET /api/v1/logs/**` - Log viewer
- `GET /api/v1/metrics/**` - Metrics data
- `GET /api/v1/config/**` - Configuration (NEW ✨)

### User Management Endpoints
- `GET /api/v1/users` - List users (ADMIN)
- `POST /api/v1/users` - Create user (ADMIN)
- `PUT /api/v1/users/{id}` - Update user (ADMIN)
- `DELETE /api/v1/users/{id}` - Delete user (ADMIN)

---

## 🔒 Security Notes

### Default Credentials (DEVELOPMENT ONLY)
- **Username**: `admin@robin.local`
- **Password**: `admin123`

**⚠️ WARNING**: Change these credentials in production!

### JWT Configuration
- **Access Token**: 30 minutes expiration
- **Refresh Token**: 7 days expiration (HttpOnly cookie)
- **Algorithm**: HS512 (HMAC-SHA512)

### Rate Limiting
- **Login endpoint**: 5 requests/minute per IP
- **Queue endpoints**: 100 requests/minute per user
- **Global limit**: 100 requests/minute per IP

---

## 📦 Next Steps

### Immediate (Complete Phase 1)
1. ✅ **DONE**: Domain management endpoints
2. ✅ **DONE**: Health aggregation endpoint
3. ✅ **DONE**: Database configuration fix
4. ✅ **DONE**: Docker Compose with PostgreSQL
5. ⏳ **TODO**: Write integration tests (2-3 days)
6. ⏳ **TODO**: Enhance OpenAPI documentation (1 hour)

### Phase 2: UI Authentication (Next)
1. Install dependencies (`npm install @ngrx/signals zod`)
2. Create auth models with Zod validation
3. Implement Signal-based auth store
4. Create login component
5. Update auth service, guards, interceptors
6. Configure environment to use gateway

### Phase 3: UI Feature Modules
1. Security module (ClamAV, Rspamd, blocklist)
2. Routing module (relay, webhooks)
3. Monitoring module (metrics charts, logs)
4. Settings module (server config, user management)
5. Email module enhancements

---

## 📞 Support

### Documentation
- `DEVELOPMENT_PLAN.md` - Comprehensive 7-week development plan
- `IMPLEMENTATION_PROGRESS.md` - Current progress tracking
- `AUTH_IMPLEMENTATION_PLAN.md` - Authentication architecture (60KB)
- `API_GATEWAY_PLAN.md` - Gateway design

### Troubleshooting
Check logs: `docker-compose logs -f gateway`
View health: `curl http://localhost:8080/actuator/health`
Access docs: `http://localhost:8080/swagger-ui.html`

---

## 🎯 Success Criteria

- [x] Gateway starts without errors
- [x] PostgreSQL connection established
- [x] Redis connection established
- [x] Authentication works (login/logout)
- [x] Domain management endpoints functional
- [x] Health aggregation shows all services UP
- [ ] Integration tests passing (80%+ coverage)
- [ ] OpenAPI documentation complete

---

**Last Updated**: 2026-01-27
**Gateway Version**: 1.0.0-SNAPSHOT
**Phase 1 Progress**: 95% Complete
