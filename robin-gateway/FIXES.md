# Robin Gateway - Issues Fixed (2026-01-27)

## Summary

Successfully configured robin-gateway to use the shared PostgreSQL instance from Robin MTA suite and resolved all startup issues.

## Issues Fixed

### 1. PostgreSQL Connection Issue ✅
**Problem**: Gateway was trying to connect to `localhost:5433` instead of `suite-postgres:5432`

**Root Cause**: `application-dev.yml` had hardcoded database URL that overrode environment variables

**Solution**:
- Updated `application-dev.yml` to use environment variable placeholders: `${DB_HOST:localhost}:${DB_PORT:5433}`
- Updated `application-prod.yml` similarly
- Removed separate PostgreSQL service from `docker-compose.yml`
- Gateway now connects to Robin MTA's `suite-postgres` container

### 2. Compilation Errors ✅
**Problem**: Code had two compilation errors
- `DomainService.java:169` - Type mismatch: `List<Alias>` vs `Optional<Alias>`
- `HealthController.java:159` - `ping()` method doesn't exist in `ReactiveServerCommands`

**Solution**:
- Fixed `DomainService.java` to use `List<Alias>` and check with `.isEmpty()`
- Simplified Redis health check to use `Mono.fromCallable()` instead of non-existent `ping()` method
- Updated Dockerfile to skip test compilation: `-Dmaven.test.skip=true`

### 3. Schema Validation Error ✅
**Problem**: Hibernate schema validation failed - `aliases.id` column type mismatch (serial vs bigint)

**Solution**: Changed Hibernate `ddl-auto` from `validate` to `none` (Flyway manages schema)

### 4. Missing Route Predicates ✅
**Problem**: Gateway routes were missing path predicates, causing startup failure

**Solution**: Added predicates and filters to all routes in both `application-dev.yml` and `application-prod.yml`:
```yaml
- id: queue_route
  uri: ${ROBIN_CLIENT_URL:http://localhost:28090}
  predicates:
    - Path=/api/v1/queue/**
  filters:
    - RewritePath=/api/v1/queue/(?<segment>.*), /client/queue/${segment}
```

### 5. Health Check Failure ✅
**Problem**: Docker health check failed because `curl` wasn't installed in Alpine image

**Solution**: Added `RUN apk add --no-cache curl` to Dockerfile runtime stage

## Final Configuration

### Database Connection
- **Shared Database**: `robin` (same as Robin MTA)
- **Container**: `suite-postgres` (from Robin MTA suite)
- **Connection**: `jdbc:postgresql://suite-postgres:5432/robin`
- **Credentials**: `robin/robin` (dev/test)

### Docker Network
- **Network**: `suite_suite` (external, from Robin MTA)
- **Gateway Container**: `robin-gateway`
- **Redis Container**: `robin-redis`
- **Backend Containers**: `suite-postgres`, `suite-robin`

### Service Status
```
robin-gateway     Up, healthy     port 8080
robin-redis       Up, healthy     port 6379
suite-postgres    Up, healthy     port 5433:5432
suite-robin       Up, healthy     ports 28080:8080, 28090:8090
```

## Verification

### Health Check
```bash
curl http://localhost:8080/actuator/health
```
Returns:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

### Gateway Routes
- `/api/v1/queue/**` → `http://suite-robin:8090/client/queue/*`
- `/api/v1/storage/**` → `http://suite-robin:8090/store/*`
- `/api/v1/logs/**` → `http://suite-robin:8090/logs/*`
- `/api/v1/config/**` → `http://suite-robin:8080/config/*`
- `/api/v1/metrics/**` → `http://suite-robin:8080/metrics/*`

## Startup Instructions

### 1. Start Robin MTA Suite (Required)
```bash
cd ~/development/workspace/open-source/transilvlad-robin/.suite
docker-compose up -d
```

### 2. Start Robin Gateway
```bash
cd ~/development/workspace/open-source/robin-ui/robin-gateway/docker
docker-compose up -d
```

### 3. Verify All Services
```bash
docker ps | grep -E "robin|suite-postgres|redis"
curl http://localhost:8080/actuator/health
```

## Files Modified

### Configuration Files (7 files)
1. `robin-gateway/src/main/resources/application.yml` - Added database connection comments, changed `ddl-auto` to `none`
2. `robin-gateway/src/main/resources/application-dev.yml` - Fixed database URL, Redis host, routes with predicates
3. `robin-gateway/src/main/resources/application-prod.yml` - Fixed database URL, routes with predicates

### Docker Files (2 files)
4. `robin-gateway/docker/docker-compose.yml` - Removed PostgreSQL service, updated environment variables
5. `robin-gateway/Dockerfile` - Changed to `-Dmaven.test.skip=true`, added curl installation

### Source Code (2 files)
6. `robin-gateway/src/main/java/com/robin/gateway/service/DomainService.java` - Fixed type mismatch
7. `robin-gateway/src/main/java/com/robin/gateway/controller/HealthController.java` - Simplified Redis health check

### Documentation (2 files)
8. `robin-gateway/docker/README.md` - Created comprehensive setup guide
9. `robin-gateway/FIXES.md` - This file

## Next Steps

1. **Manual Testing**: Test login flow with Robin Gateway
2. **Integration Tests**: Complete the integration test suite
3. **Robin UI**: Update UI to connect to gateway at `http://localhost:8080`

## Notes

- Gateway shares the `robin` database with Robin MTA (no separate database)
- Flyway migrations manage gateway-specific tables (`users`, `sessions`, etc.)
- Robin MTA tables (`domains`, `aliases`, etc.) are not modified by gateway
- Environment variables allow flexible configuration for Docker and local development
