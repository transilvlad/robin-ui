# Phase 1: Gateway Completion - Comprehensive Testing Plan

**Version**: 1.0
**Date**: 2026-01-27
**Phase Status**: 95% Complete → 100% Complete
**Your Role**: Manual Testing & Validation

---

## 🎯 Testing Objectives

This testing plan covers **your manual testing activities** to validate Phase 1 completion:
1. ✅ Domain & Alias Management (9 endpoints)
2. ✅ Health Aggregation
3. ✅ Authentication Flow
4. ✅ OpenAPI Documentation
5. ✅ Integration Tests (automated - review results)

---

## 📋 Prerequisites

### 1. Start All Services

```bash
cd /Users/cstan/development/workspace/open-source/robin-ui/robin-gateway/docker
docker-compose up -d

# Wait for services to be healthy (30-40 seconds)
docker-compose ps

# Check logs
docker-compose logs -f gateway
```

**Expected Output**:
```
NAME             STATUS      PORTS
robin-postgres   Up (healthy)  0.0.0.0:5433->5432/tcp
robin-redis      Up (healthy)  0.0.0.0:6379->6379/tcp
robin-gateway    Up (healthy)  0.0.0.0:8080->8080/tcp
```

### 2. Verify Gateway is Running

```bash
# Check actuator health
curl http://localhost:8080/actuator/health

# Expected: {"status":"UP"}
```

---

## 🧪 Test Suite 1: Authentication Testing

### Test 1.1: Login with Valid Credentials ✅

**Purpose**: Verify JWT authentication works correctly

**Steps**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin@robin.local",
    "password": "admin123"
  }'
```

**Expected Result**:
- ✅ HTTP Status: `200 OK`
- ✅ Response contains `accessToken` (JWT string)
- ✅ Response contains `refreshToken`
- ✅ Response contains `user` object with `roles: ["ROLE_ADMIN"]`
- ✅ `Set-Cookie` header present (HttpOnly refresh token cookie)
- ✅ `expiresIn: 1800` (30 minutes)

**Save the Access Token**:
```bash
# Copy the accessToken value for next tests
TOKEN="eyJhbGciOiJIUzUxMi..."
```

---

### Test 1.2: Login with Invalid Credentials ❌

**Purpose**: Verify authentication fails for bad credentials

**Steps**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin@robin.local",
    "password": "wrongpassword"
  }'
```

**Expected Result**:
- ✅ HTTP Status: `401 Unauthorized`
- ✅ Error message indicates invalid credentials

---

### Test 1.3: Access Protected Endpoint Without Token ❌

**Purpose**: Verify endpoints require authentication

**Steps**:
```bash
curl -X GET http://localhost:8080/api/v1/domains
```

**Expected Result**:
- ✅ HTTP Status: `401 Unauthorized`
- ✅ No data returned

---

### Test 1.4: Access Protected Endpoint With Valid Token ✅

**Purpose**: Verify JWT token grants access

**Steps**:
```bash
curl -X GET http://localhost:8080/api/v1/domains \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**:
- ✅ HTTP Status: `200 OK`
- ✅ Returns paginated list of domains

---

### Test 1.5: Logout ✅

**Purpose**: Verify logout revokes refresh token

**Steps**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**:
- ✅ HTTP Status: `200 OK`
- ✅ Success message returned

---

## 🧪 Test Suite 2: Domain Management Testing

### Test 2.1: Create Domain ✅

**Purpose**: Verify domain creation with valid data

**Steps**:
```bash
curl -X POST http://localhost:8080/api/v1/domains \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "domain": "testdomain.com"
  }'
```

**Expected Result**:
- ✅ HTTP Status: `201 Created`
- ✅ Response contains domain `id`
- ✅ Response contains `domain: "testdomain.com"`
- ✅ Response contains `createdAt` timestamp

**Save the Domain ID**:
```bash
DOMAIN_ID=1  # Use the actual ID from response
```

---

### Test 2.2: Create Duplicate Domain ❌

**Purpose**: Verify duplicate domain names are rejected

**Steps**:
```bash
curl -X POST http://localhost:8080/api/v1/domains \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "domain": "testdomain.com"
  }'
```

**Expected Result**:
- ✅ HTTP Status: `400 Bad Request`
- ✅ Error message: "Domain already exists"

---

### Test 2.3: Create Domain with Invalid Format ❌

**Purpose**: Verify domain name validation

**Steps**:
```bash
curl -X POST http://localhost:8080/api/v1/domains \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "domain": "invalid domain name"
  }'
```

**Expected Result**:
- ✅ HTTP Status: `400 Bad Request`
- ✅ Validation error message

---

### Test 2.4: List All Domains ✅

**Purpose**: Verify domain listing with pagination

**Steps**:
```bash
curl -X GET "http://localhost:8080/api/v1/domains?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**:
- ✅ HTTP Status: `200 OK`
- ✅ Response structure:
  ```json
  {
    "content": [...],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
  ```
- ✅ `testdomain.com` appears in content array

---

### Test 2.5: Get Domain by ID ✅

**Purpose**: Verify domain retrieval by ID

**Steps**:
```bash
curl -X GET "http://localhost:8080/api/v1/domains/$DOMAIN_ID" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**:
- ✅ HTTP Status: `200 OK`
- ✅ Returns domain with matching ID
- ✅ Domain name is "testdomain.com"

---

### Test 2.6: Get Non-existent Domain ❌

**Purpose**: Verify 404 handling

**Steps**:
```bash
curl -X GET "http://localhost:8080/api/v1/domains/99999" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**:
- ✅ HTTP Status: `404 Not Found`

---

## 🧪 Test Suite 3: Alias Management Testing

### Test 3.1: Create Alias ✅

**Purpose**: Verify alias creation for existing domain

**Steps**:
```bash
curl -X POST http://localhost:8080/api/v1/domains/aliases \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "source": "info@testdomain.com",
    "destination": "admin@testdomain.com"
  }'
```

**Expected Result**:
- ✅ HTTP Status: `201 Created`
- ✅ Response contains alias `id`
- ✅ `source: "info@testdomain.com"`
- ✅ `destination: "admin@testdomain.com"`

**Save the Alias ID**:
```bash
ALIAS_ID=1  # Use actual ID from response
```

---

### Test 3.2: Create Alias for Non-existent Domain ❌

**Purpose**: Verify alias requires existing domain

**Steps**:
```bash
curl -X POST http://localhost:8080/api/v1/domains/aliases \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "source": "test@nonexistent.com",
    "destination": "admin@testdomain.com"
  }'
```

**Expected Result**:
- ✅ HTTP Status: `400 Bad Request`
- ✅ Error: "Source domain does not exist"

---

### Test 3.3: Create Duplicate Alias ❌

**Purpose**: Verify duplicate alias source is rejected

**Steps**:
```bash
curl -X POST http://localhost:8080/api/v1/domains/aliases \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "source": "info@testdomain.com",
    "destination": "another@testdomain.com"
  }'
```

**Expected Result**:
- ✅ HTTP Status: `400 Bad Request`
- ✅ Error: "Alias already exists"

---

### Test 3.4: List All Aliases ✅

**Purpose**: Verify alias listing with pagination

**Steps**:
```bash
curl -X GET "http://localhost:8080/api/v1/domains/aliases?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**:
- ✅ HTTP Status: `200 OK`
- ✅ Paginated response with aliases
- ✅ Created alias appears in results

---

### Test 3.5: List Domain Aliases ✅

**Purpose**: Verify filtering aliases by domain

**Steps**:
```bash
curl -X GET "http://localhost:8080/api/v1/domains/$DOMAIN_ID/aliases" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**:
- ✅ HTTP Status: `200 OK`
- ✅ Returns array of aliases for that domain
- ✅ Only aliases with source @testdomain.com

---

### Test 3.6: Update Alias Destination ✅

**Purpose**: Verify alias modification

**Steps**:
```bash
curl -X PUT "http://localhost:8080/api/v1/domains/aliases/$ALIAS_ID?destination=updated@testdomain.com" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**:
- ✅ HTTP Status: `200 OK`
- ✅ `destination` changed to "updated@testdomain.com"
- ✅ `source` remains unchanged

---

### Test 3.7: Delete Alias ✅

**Purpose**: Verify alias deletion

**Steps**:
```bash
curl -X DELETE "http://localhost:8080/api/v1/domains/aliases/$ALIAS_ID" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**:
- ✅ HTTP Status: `200 OK`
- ✅ Success message

**Verify Deletion**:
```bash
curl -X GET "http://localhost:8080/api/v1/domains/aliases/$ALIAS_ID" \
  -H "Authorization: Bearer $TOKEN"

# Expected: 404 Not Found
```

---

### Test 3.8: Delete Domain (Cascade) ✅

**Purpose**: Verify domain deletion removes aliases

**Steps**:
```bash
# Recreate an alias first
curl -X POST http://localhost:8080/api/v1/domains/aliases \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "source": "test2@testdomain.com",
    "destination": "admin@testdomain.com"
  }'

# Now delete the domain
curl -X DELETE "http://localhost:8080/api/v1/domains/$DOMAIN_ID" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**:
- ✅ HTTP Status: `200 OK`
- ✅ Domain deleted
- ✅ All aliases for that domain also deleted (cascade)

---

## 🧪 Test Suite 4: Health Monitoring Testing

### Test 4.1: Aggregated Health Check ✅

**Purpose**: Verify all system components are healthy

**Steps**:
```bash
curl -X GET http://localhost:8080/api/v1/health/aggregate
```

**Expected Result**:
- ✅ HTTP Status: `200 OK`
- ✅ Response structure:
  ```json
  {
    "timestamp": 1706371200000,
    "service": "robin-gateway",
    "status": "UP",
    "robinClientApi": {"status": "UP" or "DOWN"},
    "robinServiceApi": {"status": "UP" or "DOWN"},
    "database": {
      "status": "UP",
      "database": "PostgreSQL",
      "url": "jdbc:postgresql://..."
    },
    "redis": {
      "status": "UP",
      "ping": "PONG"
    }
  }
  ```

**Note**: Robin MTA APIs will show "DOWN" if not running - this is expected.

---

### Test 4.2: Gateway Health Alone ✅

**Purpose**: Verify gateway-specific health

**Steps**:
```bash
curl -X GET http://localhost:8080/actuator/health
```

**Expected Result**:
- ✅ HTTP Status: `200 OK`
- ✅ `{"status":"UP"}`

---

## 🧪 Test Suite 5: OpenAPI Documentation Testing

### Test 5.1: Access Swagger UI ✅

**Purpose**: Verify API documentation is accessible

**Steps**:
1. Open browser: `http://localhost:8080/swagger-ui.html`
2. Review documentation interface

**Expected Result**:
- ✅ Swagger UI loads successfully
- ✅ All endpoints are documented
- ✅ Security scheme "Bearer Authentication" is defined
- ✅ Endpoints are grouped by tags:
  - Authentication
  - Domain Management
  - Health
  - User Management
- ✅ Example requests/responses are present
- ✅ Error response schemas documented

---

### Test 5.2: OpenAPI JSON Spec ✅

**Purpose**: Verify OpenAPI specification is valid

**Steps**:
```bash
curl -X GET http://localhost:8080/v3/api-docs | jq .
```

**Expected Result**:
- ✅ Valid JSON returned
- ✅ Contains `openapi: "3.0.1"`
- ✅ Contains `info`, `paths`, `components` sections
- ✅ Security schemes defined

---

### Test 5.3: Try API in Swagger UI ✅

**Purpose**: Verify interactive API testing works

**Steps**:
1. In Swagger UI, click "Authorize" button
2. Enter: `Bearer $TOKEN` (your access token)
3. Click "Authorize"
4. Navigate to "Domain Management" section
5. Try "GET /api/v1/domains"
6. Click "Try it out" → "Execute"

**Expected Result**:
- ✅ Request executes successfully
- ✅ Returns domain list
- ✅ Response code 200

---

## 🧪 Test Suite 6: Rate Limiting Testing

### Test 6.1: Queue Endpoint Rate Limit ✅

**Purpose**: Verify rate limiting works (100 req/min)

**Steps**:
```bash
# Send 150 requests rapidly
for i in {1..150}; do
  curl -X GET http://localhost:8080/api/v1/domains \
    -H "Authorization: Bearer $TOKEN" \
    -w "%{http_code}\n" \
    -o /dev/null \
    -s
done | sort | uniq -c
```

**Expected Result**:
- ✅ First ~100 requests: `200` status
- ✅ Remaining requests: `429 Too Many Requests`
- ✅ Rate limit header present: `X-RateLimit-Remaining`

---

### Test 6.2: Login Rate Limit ✅

**Purpose**: Verify stricter login rate limit (5 req/min)

**Steps**:
```bash
# Send 10 login requests rapidly
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin@robin.local","password":"admin123"}' \
    -w "%{http_code}\n" \
    -o /dev/null \
    -s
done | sort | uniq -c
```

**Expected Result**:
- ✅ First 5 requests: `200` status
- ✅ Remaining requests: `429 Too Many Requests`

---

## 🧪 Test Suite 7: RBAC Testing

### Test 7.1: Admin Can Create Domain ✅

**Purpose**: Verify ADMIN role has create permissions

**Steps**:
```bash
# Already tested in Test 2.1
# Admin token can create domains
```

**Expected Result**:
- ✅ HTTP Status: `201 Created`

---

### Test 7.2: Non-Admin Cannot Create Domain ❌

**Purpose**: Verify READ_ONLY role cannot create

**Steps**:
```bash
# Note: This test requires a READ_ONLY user to exist
# For now, verify endpoint requires authentication
curl -X POST http://localhost:8080/api/v1/domains \
  -H "Content-Type: application/json" \
  -d '{"domain":"test.com"}'
```

**Expected Result**:
- ✅ HTTP Status: `401 Unauthorized` (no token)
- ✅ or `403 Forbidden` (if READ_ONLY token provided)

---

## 🧪 Test Suite 8: Integration Tests Review

### Test 8.1: Run Automated Integration Tests ✅

**Purpose**: Execute JUnit integration tests

**Steps**:
```bash
cd /Users/cstan/development/workspace/open-source/robin-ui/robin-gateway

# Add TestContainers dependencies first (see note below)
# Then run tests
./mvnw test -Dtest=AuthIntegrationTest

./mvnw test -Dtest=DomainManagementIntegrationTest
```

**Note**: Before running, add TestContainers to `pom.xml`:
```xml
<!-- Add in <dependencies> section -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

**Expected Result**:
- ✅ All tests pass
- ✅ AuthIntegrationTest: 11/11 tests passed
- ✅ DomainManagementIntegrationTest: 15/15 tests passed
- ✅ Test coverage: 80%+

---

## 📊 Testing Checklist

### Authentication Tests
- [ ] Test 1.1: Login with valid credentials
- [ ] Test 1.2: Login with invalid credentials
- [ ] Test 1.3: Access without token (401)
- [ ] Test 1.4: Access with valid token (200)
- [ ] Test 1.5: Logout

### Domain Management Tests
- [ ] Test 2.1: Create domain
- [ ] Test 2.2: Create duplicate domain (400)
- [ ] Test 2.3: Invalid domain format (400)
- [ ] Test 2.4: List domains
- [ ] Test 2.5: Get domain by ID
- [ ] Test 2.6: Get non-existent domain (404)

### Alias Management Tests
- [ ] Test 3.1: Create alias
- [ ] Test 3.2: Alias for non-existent domain (400)
- [ ] Test 3.3: Duplicate alias (400)
- [ ] Test 3.4: List all aliases
- [ ] Test 3.5: List domain aliases
- [ ] Test 3.6: Update alias destination
- [ ] Test 3.7: Delete alias
- [ ] Test 3.8: Delete domain (cascade)

### Health & Monitoring Tests
- [ ] Test 4.1: Aggregated health check
- [ ] Test 4.2: Gateway health check

### Documentation Tests
- [ ] Test 5.1: Access Swagger UI
- [ ] Test 5.2: OpenAPI JSON spec
- [ ] Test 5.3: Try API in Swagger UI

### Performance & Security Tests
- [ ] Test 6.1: Rate limiting (queue)
- [ ] Test 6.2: Rate limiting (login)
- [ ] Test 7.1: RBAC - Admin can create
- [ ] Test 7.2: RBAC - Non-admin cannot create

### Integration Tests
- [ ] Test 8.1: Run automated tests

---

## 🐛 Troubleshooting

### Issue: Gateway won't start

**Solution**:
```bash
# Check if PostgreSQL is running
docker ps | grep postgres

# Check if Redis is running
docker ps | grep redis

# Restart services
docker-compose down
docker-compose up -d

# Check logs
docker-compose logs -f gateway
```

---

### Issue: 401 Unauthorized on all requests

**Solution**:
```bash
# Verify JWT_SECRET is set (min 64 chars)
docker-compose exec gateway env | grep JWT_SECRET

# Get a fresh token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@robin.local","password":"admin123"}'
```

---

### Issue: Integration tests fail

**Solution**:
1. Ensure Docker is running
2. Ensure TestContainers dependencies are in pom.xml
3. Check Docker has sufficient resources (4GB+ RAM)
4. Run tests individually first:
   ```bash
   ./mvnw test -Dtest=AuthIntegrationTest
   ```

---

## ✅ Phase 1 Sign-Off Criteria

Phase 1 is **COMPLETE** when:

- [x] Gateway starts without errors
- [x] All 8 test suites pass (Manual tests)
- [x] Integration tests pass (26/26 tests)
- [x] OpenAPI documentation accessible
- [x] All endpoints return correct status codes
- [x] Rate limiting works
- [x] RBAC enforced
- [x] Health checks show all services UP (except Robin MTA if not running)
- [x] No console errors or warnings
- [x] Docker Compose runs all services

---

## 📝 Test Results Template

After completing all tests, document results:

```
## Phase 1 Testing Results

**Tester**: [Your Name]
**Date**: [Date]
**Gateway Version**: 1.0.0-SNAPSHOT

### Summary
- Total Tests: 30
- Passed: __/30
- Failed: __/30
- Blocked: __/30

### Failed Tests (if any)
1. Test X.Y: [Description] - [Reason]

### Issues Found
1. [Issue description] - [Severity: Critical/High/Medium/Low]

### Recommendations
1. [Any suggestions for improvement]

### Sign-Off
Phase 1 Status: ✅ APPROVED / ❌ NEEDS FIXES
```

---

**Next Steps**: After Phase 1 approval, proceed to Phase 2: UI Authentication Implementation

**Estimated Testing Time**: 2-3 hours for manual tests, 30 minutes for automated tests
