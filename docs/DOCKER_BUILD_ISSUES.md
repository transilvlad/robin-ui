# Docker Build Issues & Solutions

This document addresses common build issues with the Robin MTA suite.

## Robin Gateway Build - ✅ FIXED

### Issue: Maven Build Failure - Missing Version for Flyway PostgreSQL

**Symptom**:
```
[ERROR] 'dependencies.dependency.version' for org.flywaydb:flyway-database-postgresql:jar is missing. @ line 128, column 21
```

**Root Cause**: The `flyway-database-postgresql` artifact is not managed by Spring Boot 3.2.2's dependency management, so it required an explicit version. However, this dependency is unnecessary since `flyway-core` already supports PostgreSQL when the PostgreSQL JDBC driver is present on the classpath.

**Solution**: Removed the unnecessary `flyway-database-postgresql` dependency. The `flyway-core` dependency (managed by Spring Boot) automatically supports PostgreSQL.

**Status**: ✅ Fixed - Build now succeeds!

---

## Robin MTA Build - ⚠️ UPSTREAM ISSUE

### Issue: Compilation Errors in Robin MTA

**Symptom**:
```
[ERROR] /usr/src/robin/src/main/java/com/mimecast/robin/endpoints/RobinServiceEndpoint.java:[563,48]
incompatible types: java.lang.Long cannot be converted to int
```

**Root Cause**: The Robin MTA repository at `../transilvlad-robin/` has compilation errors. These errors are in the upstream Robin MTA project, not in robin-ui/robin-gateway.

**Workarounds**:

### Option 1: Use Gateway-Only Setup (Recommended)

Run Robin UI and Gateway without Robin MTA:

```bash
# Start Gateway + UI only
docker-compose -f docker-compose.gateway-only.yaml up -d

# Run Robin MTA separately (if you have a working version)
cd ../transilvlad-robin/.suite
docker-compose up -d
```

This setup:
- ✅ Runs Robin Gateway successfully
- ✅ Runs Robin UI successfully
- ✅ Connects to externally running Robin MTA via `host.docker.internal`
- ✅ No build issues

### Option 2: Use Pre-built Robin MTA Image

If a Docker image exists for Robin MTA:

```yaml
# In docker-compose.full.yaml, replace the build section:
robin-mta:
  image: robinmta/robin:latest  # Use pre-built image
  # Remove the build section
```

### Option 3: Fix Robin MTA Compilation Errors

The errors are in the upstream Robin MTA Java source code:

**File**: `../transilvlad-robin/src/main/java/com/mimecast/robin/endpoints/RobinServiceEndpoint.java`

**Lines with errors**:
- Line 563: `Long` to `int` conversion
- Line 593: `Long` to `int` conversion
- Line 619: `RspamdConfig` to `BasicConfig` conversion
- Line 621: `Long` to `int` conversion
- Line 649: `RspamdConfig` to `BasicConfig` conversion
- Line 651: `Long` to `int` conversion

**To fix**: Contact the Robin MTA maintainer or submit a pull request to fix these type conversion issues.

---

## Robin UI Build - ✅ NO ISSUES

The Robin UI builds successfully with no issues.

---

## Current Build Status

| Component | Build Status | Docker Build | Runtime |
|-----------|-------------|--------------|---------|
| **Robin UI** | ✅ Success | ✅ Success | ✅ Works |
| **Robin Gateway** | ✅ Success | ✅ Success | ✅ Works |
| **Robin MTA** | ❌ Compilation Errors | ❌ Fails | N/A |

---

## Recommended Approach

**For development**:
```bash
# 1. Use gateway-only setup
docker-compose -f docker-compose.gateway-only.yaml up -d

# 2. Run Robin MTA separately
cd ../transilvlad-robin/.suite
docker-compose up -d
```

**For production**:
- Wait for Robin MTA compilation errors to be fixed upstream
- Or use a stable Docker image of Robin MTA
- Gateway and UI are ready for production

---

## Testing Robin Gateway Build

```bash
# Build robin-gateway
docker build -f robin-gateway/Dockerfile -t robin-gateway:test robin-gateway

# Should complete successfully with:
# [INFO] BUILD SUCCESS
# [INFO] Total time:  01:25 min

# Run robin-gateway (will fail to connect to DB, which is expected without PostgreSQL running)
docker run --rm robin-gateway:test

# Should output:
# :: Spring Boot ::                (v3.2.2)
# ...
# Caused by: org.postgresql.util.PSQLException: Connection to localhost:5433 refused
# (This error is EXPECTED and confirms the application is built correctly)
```

### Build Time

- **First build**: ~1-2 minutes (downloads dependencies)
- **Subsequent builds**: ~10-20 seconds (cached layers)

---

## Testing Full Stack with Gateway-Only

```bash
# 1. Start gateway-only stack
docker-compose -f docker-compose.gateway-only.yaml up -d

# 2. Wait for health checks
sleep 30

# 3. Test gateway
curl http://localhost:8888/actuator/health

# 4. Test UI (dev mode)
curl http://localhost:4200

# 5. View logs
docker-compose -f docker-compose.gateway-only.yaml logs -f
```

---

## Support

If you encounter other build issues:

1. **Robin Gateway issues**: Report to robin-ui repository
2. **Robin MTA issues**: Report to transilvlad-robin repository
3. **Robin UI issues**: Report to robin-ui repository

---

## Version Information

- **Robin Gateway**: v1.0.0-SNAPSHOT ✅ Builds successfully
- **Robin UI**: Latest ✅ Builds successfully
- **Robin MTA**: Upstream has compilation errors ❌

Last Updated: 2026-01-30
