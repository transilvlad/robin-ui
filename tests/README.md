# Robin UI Test Suite

Comprehensive automated test scripts for validating the Robin UI implementation, including JSON APIs, dual-hash authentication, and end-to-end integration.

## Quick Start

```bash
# Run all tests (recommended)
cd /Users/cstan/development/workspace/open-source/robin-ui/tests
./run-all-tests.sh

# Run all tests in quick mode (skip optional tests)
./run-all-tests.sh --quick

# Run all tests with verbose output
./run-all-tests.sh --verbose

# Skip Maven build steps
./run-all-tests.sh --skip-build
```

---

## Test Scripts

### 1. Master Test Runner
**File:** `run-all-tests.sh`

Executes all test suites in order, with build steps and comprehensive reporting.

**Usage:**
```bash
./run-all-tests.sh [OPTIONS]

Options:
  --verbose     Show detailed output from each test
  --skip-build  Skip Maven build steps
  --quick       Run only critical tests (skip optional tests)
```

**Test Execution Order:**
1. Build robin-gateway (Maven)
2. Build robin-mta (Maven)
3. Run Gateway unit tests (PasswordSyncServiceTest)
4. Run database schema verification
5. Run dual authentication tests
6. Run MTA JSON endpoints tests
7. Run end-to-end integration tests

---

### 2. MTA JSON Endpoints Test
**File:** `test-mta-json-endpoints.sh`

Tests all 15 new JSON endpoints in Robin MTA.

**Usage:**
```bash
./test-mta-json-endpoints.sh [mta-host] [mta-client-port] [mta-service-port]

# Examples:
./test-mta-json-endpoints.sh                          # Use defaults (localhost:8090, localhost:8080)
./test-mta-json-endpoints.sh localhost 8090 8080      # Explicit ports
./test-mta-json-endpoints.sh suite-robin 8090 8080    # Docker container name
```

**Environment Variables:**
- `ROBIN_AUTH_USER` - Basic auth username (default: admin)
- `ROBIN_AUTH_PASS` - Basic auth password (default: password)
- `VERBOSE` - Set to `true` for detailed JSON responses

**Tests:**
- Client API (Port 8090):
  - GET /client/queue/json (pagination)
- Service API (Port 8080):
  - GET/PUT /config/json (8 sections)
  - POST /scanners/clamav/test
  - GET /scanners/clamav/status
  - POST /scanners/rspamd/test
  - GET /scanners/rspamd/status
  - GET /metrics/system
  - GET /metrics/queue
  - GET /logs/json
  - GET/POST/DELETE/PATCH /blocklist

**Output:**
- Colored test results (GREEN = passed, RED = failed)
- JSON validation for all responses
- Test summary with pass/fail counts

---

### 3. Dual Authentication Test
**File:** `test-dual-auth.sh`

Tests both BCrypt (Gateway) and SHA512-CRYPT (MTA/Dovecot) authentication.

**Usage:**
```bash
./test-dual-auth.sh [gateway-host] [gateway-port] [db-host] [db-port] [db-name]

# Examples:
./test-dual-auth.sh                                   # Use defaults
./test-dual-auth.sh localhost 8080 localhost 5432 robin
```

**Environment Variables:**
- `PGUSER` - PostgreSQL username (default: postgres)
- `PGPASSWORD` - PostgreSQL password (default: postgres)

**Tests:**
1. **Database Schema Verification** (6 tests)
   - password_bcrypt column exists
   - password column exists
   - Admin user exists
   - Admin has BCrypt hash
   - Admin has SHA512-CRYPT hash
   - V3 migration applied

2. **Gateway Authentication (BCrypt)** (4 tests)
   - Login with valid credentials
   - Login with invalid password (should fail)
   - Login with non-existent user (should fail)
   - Access protected endpoint with JWT token

3. **Database Password Verification** (3 tests)
   - BCrypt hash format is valid
   - SHA512-CRYPT hash format is valid
   - Both hashes exist for admin

4. **Password Sync Verification** (2 tests)
   - Column documentation comments present
   - Index on password_bcrypt exists

**Output:**
- Test results with pass/fail status
- Displays actual password hashes for verification
- Troubleshooting tips if tests fail

---

### 4. Database Schema Test
**File:** `test-database-schema.sh`

Verifies database schema structure and dual-hash setup.

**Usage:**
```bash
./test-database-schema.sh [db-host] [db-port] [db-name]

# Examples:
./test-database-schema.sh                    # Use defaults
./test-database-schema.sh localhost 5432 robin
```

**Environment Variables:**
- `PGUSER` - PostgreSQL username
- `PGPASSWORD` - PostgreSQL password

**Tests:**
1. **Required Tables** (2 tests)
   - users table exists
   - flyway_schema_history table exists

2. **Users Table Schema** (8 tests)
   - All required columns exist

3. **Password Columns Configuration** (4 tests)
   - Column types are correct
   - Column lengths are sufficient

4. **Indexes and Constraints** (3 tests)
   - Primary key, unique constraints, indexes

5. **Flyway Migrations** (3 tests)
   - V1, V2, V3 migrations applied

6. **Admin User Configuration** (4 tests)
   - Admin exists, has passwords, is enabled

7. **Password Hash Format Validation** (2 tests)
   - BCrypt format valid
   - SHA512-CRYPT format valid

8. **Data Integrity** (3 tests)
   - No duplicate usernames
   - All users have both hashes

**Output:**
- Schema information display
- Applied migrations list
- User statistics
- Detailed test results

---

### 5. End-to-End Integration Test
**File:** `test-integration.sh`

Tests complete UI → Gateway → MTA flow.

**Usage:**
```bash
./test-integration.sh

# With environment variables:
GATEWAY_HOST=gateway.local \
GATEWAY_PORT=8080 \
MTA_HOST=mta.local \
./test-integration.sh
```

**Environment Variables:**
- `GATEWAY_HOST` - Gateway hostname (default: localhost)
- `GATEWAY_PORT` - Gateway port (default: 8080)
- `MTA_HOST` - MTA hostname (default: localhost)
- `MTA_CLIENT_PORT` - MTA client API port (default: 8090)
- `MTA_SERVICE_PORT` - MTA service API port (default: 8080)
- `TEST_USERNAME` - Test username (default: admin@robin.local)
- `TEST_PASSWORD` - Test password (default: admin123)

**Tests:**
1. **Service Availability** (3 tests)
   - Gateway health endpoint
   - MTA Client API health
   - MTA Service API health

2. **Authentication Flow** (2 tests)
   - Login and retrieve JWT token
   - Access protected endpoint with JWT

3. **Gateway → MTA Proxy Tests** (7 tests)
   - Queue listing via Gateway proxy
   - Config access via Gateway proxy
   - Scanner test via Gateway proxy
   - Metrics access via Gateway proxy
   - Logs access via Gateway proxy
   - Direct MTA access (fallback verification)

4. **Data Consistency** (1 test)
   - Queue data consistency (direct vs proxy)

**Output:**
- Service availability status
- JWT token acquisition success
- Proxy routing verification
- System status summary

---

## Prerequisites

### Required Software
- **curl** - HTTP client for API testing
- **jq** - JSON processor for response validation
- **psql** - PostgreSQL client for database tests
- **mvn** - Maven for building Java projects (optional with --skip-build)

### Install Prerequisites

**macOS:**
```bash
brew install curl jq postgresql
```

**Ubuntu/Debian:**
```bash
sudo apt-get install curl jq postgresql-client
```

**RHEL/CentOS:**
```bash
sudo yum install curl jq postgresql
```

### Required Services
All test scripts expect the following services to be running:

1. **PostgreSQL** - Database server on port 5432
2. **robin-gateway** - Spring Boot Gateway on port 8080
3. **robin-mta** - Mail Transfer Agent
   - Client API on port 8090
   - Service API on port 8080

---

## Running Services

### Option 1: Docker Compose (Recommended)

```bash
# Start all services
cd /Users/cstan/development/workspace/open-source/robin-ui
docker-compose up -d

# Check service status
docker-compose ps

# View logs
docker-compose logs -f robin-gateway
docker-compose logs -f robin-mta
```

### Option 2: Manual Startup

**PostgreSQL:**
```bash
# macOS
brew services start postgresql

# Linux
sudo systemctl start postgresql
```

**Robin Gateway:**
```bash
cd /Users/cstan/development/workspace/open-source/robin-ui/robin-gateway
mvn spring-boot:run
```

**Robin MTA:**
```bash
cd /Users/cstan/development/workspace/open-source/transilvlad-robin
mvn exec:java -Dexec.mainClass="com.mimecast.robin.main.Main"
```

---

## Test Credentials

### Default Test User
- **Username:** `admin@robin.local`
- **Password:** `admin123`
- **BCrypt Hash:** `$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN96E7VQnI.DK7ow3zPvu`
- **SHA512-CRYPT Hash:** `{SHA512-CRYPT}$6$...` (generated by database)

### Database Connection
- **Host:** `localhost`
- **Port:** `5432`
- **Database:** `robin`
- **User:** `postgres`
- **Password:** `postgres`

---

## Interpreting Test Results

### Success Output
```
============================================
TEST SUMMARY
============================================

Total Tests: 25
Passed: 25
Failed: 0

✓ All tests passed!
```

### Failure Output
```
Testing: Admin user has BCrypt hash... FAILED

✗ Some tests failed

Troubleshooting:
  1. Check if robin-gateway is running on port 8080
  2. Verify V3 migration was applied
  ...
```

### Test Status Icons
- ✓ `PASSED` - Test completed successfully
- ✗ `FAILED` - Test failed (check logs for details)
- ⊘ `SKIPPED` - Test skipped (optional or quick mode)
- ⚠ `ERROR` - Test script error (missing dependencies, etc.)

---

## Troubleshooting

### Common Issues

#### 1. "Cannot connect to database"
**Solution:**
```bash
# Check if PostgreSQL is running
psql -h localhost -U postgres -d robin -c "SELECT 1;"

# Start PostgreSQL if not running
brew services start postgresql  # macOS
sudo systemctl start postgresql # Linux
```

#### 2. "Gateway not available after 30 seconds"
**Solution:**
```bash
# Check if Gateway is running
curl http://localhost:8080/actuator/health

# Start Gateway
cd robin-gateway
mvn spring-boot:run
```

#### 3. "MTA Client API not available"
**Solution:**
```bash
# Check if MTA is running
curl -u admin:password http://localhost:8090/health

# Start MTA
cd transilvlad-robin
mvn exec:java -Dexec.mainClass="com.mimecast.robin.main.Main"
```

#### 4. "V3 migration not applied"
**Solution:**
```bash
# Check Flyway migration status
psql -h localhost -U postgres -d robin -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"

# If V3 missing, restart Gateway to trigger migration
cd robin-gateway
mvn spring-boot:run
```

#### 5. "BCrypt hash format is valid... FAILED"
**Solution:**
```bash
# Check admin password hash
psql -h localhost -U postgres -d robin -c \
  "SELECT username, password_bcrypt FROM users WHERE username = 'admin@robin.local';"

# Reset admin password via Gateway API
curl -X POST http://localhost:8080/api/v1/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@robin.local","password":"admin123"}'
```

#### 6. "jq: command not found"
**Solution:**
```bash
# Install jq
brew install jq  # macOS
sudo apt-get install jq  # Ubuntu/Debian
```

---

## Test Coverage

### Phase 1: Robin MTA JSON APIs
- ✅ Queue JSON endpoint with pagination
- ✅ Configuration JSON endpoints (8 sections)
- ✅ Scanner test/status endpoints (ClamAV, Rspamd)
- ✅ Metrics endpoints (system, queue)
- ✅ Logs JSON endpoint
- ✅ Blocklist endpoints (placeholder)

### Phase 2: Database Dual-Hash
- ✅ V3 migration creates password_bcrypt column
- ✅ Admin user has both BCrypt and SHA512-CRYPT hashes
- ✅ PasswordSyncService unit tests (13 tests)
- ✅ Gateway authentication with BCrypt
- ✅ MTA/Dovecot authentication with SHA512-CRYPT

### Phase 3: Integration
- ✅ Service availability checks
- ✅ Gateway JWT authentication flow
- ✅ Gateway → MTA proxy routing
- ✅ Data consistency across systems

---

## Continuous Integration

### GitHub Actions Example

```yaml
name: Robin UI Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_DB: robin
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres
        ports:
          - 5432:5432

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'

      - name: Install dependencies
        run: |
          sudo apt-get update
          sudo apt-get install -y curl jq postgresql-client

      - name: Run all tests
        run: |
          cd tests
          ./run-all-tests.sh --verbose

      - name: Upload test logs
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: test-logs
          path: /tmp/*_test_*.log
```

---

## Contributing

When adding new tests:

1. Follow the existing test script structure
2. Use consistent color coding (GREEN=pass, RED=fail, YELLOW=warning)
3. Add comprehensive test descriptions
4. Include troubleshooting tips for common failures
5. Update this README with new test documentation

---

## Support

For issues or questions:

1. Check troubleshooting section above
2. Review [SCHEMA_OWNERSHIP.md](../docs/database/SCHEMA_OWNERSHIP.md)
3. Review [DUAL_HASH_PASSWORD_STRATEGY.md](../robin-gateway/DUAL_HASH_PASSWORD_STRATEGY.md)
4. Open an issue on GitHub

---

**Last Updated:** 2026-01-29
**Version:** 1.0
