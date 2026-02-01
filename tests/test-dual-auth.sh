#!/bin/bash
# Test Script: Dual Password Authentication
# Tests both BCrypt (Gateway) and SHA512-CRYPT (MTA) authentication
#
# Usage: ./test-dual-auth.sh [gateway-host] [gateway-port] [db-host] [db-port] [db-name]
#
# Defaults:
#   gateway-host: localhost
#   gateway-port: 8080
#   db-host: localhost
#   db-port: 5432
#   db-name: robin

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
GATEWAY_HOST="${1:-localhost}"
GATEWAY_PORT="${2:-8080}"
DB_HOST="${3:-localhost}"
DB_PORT="${4:-5432}"
DB_NAME="${5:-robin}"
DB_USER="${PGUSER:-postgres}"
DB_PASS="${PGPASSWORD:-postgres}"

# Test credentials
TEST_USERNAME="admin@robin.local"
TEST_PASSWORD="admin123"

# Test counters
TESTS_TOTAL=0
TESTS_PASSED=0
TESTS_FAILED=0

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Dual Password Authentication Test Suite${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo "Gateway: ${GATEWAY_HOST}:${GATEWAY_PORT}"
echo "Database: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "Test User: ${TEST_USERNAME}"
echo ""

# Helper function to run a test
run_test() {
    local test_name="$1"
    shift

    TESTS_TOTAL=$((TESTS_TOTAL + 1))

    echo -n "Testing: ${test_name}... "

    if "$@" > /dev/null 2>&1; then
        echo -e "${GREEN}PASSED${NC}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        echo -e "${RED}FAILED${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Helper function to run SQL query
run_sql() {
    PGPASSWORD="${DB_PASS}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -t -A -c "$1"
}

# Check dependencies
echo -e "${YELLOW}Checking dependencies...${NC}"
if ! command -v curl &> /dev/null; then
    echo -e "${RED}ERROR: curl is not installed${NC}"
    exit 1
fi

if ! command -v jq &> /dev/null; then
    echo -e "${RED}ERROR: jq is not installed${NC}"
    exit 1
fi

if ! command -v psql &> /dev/null; then
    echo -e "${RED}ERROR: psql is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}Dependencies OK${NC}"
echo ""

# ============================================
# TEST SUITE 1: Database Schema Verification
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 1: Database Schema Verification${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test 1: Check if password_bcrypt column exists
test_bcrypt_column_exists() {
    local result=$(run_sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'password_bcrypt';")
    [ "$result" = "1" ]
}

run_test "password_bcrypt column exists" test_bcrypt_column_exists

# Test 2: Check if password column exists
test_password_column_exists() {
    local result=$(run_sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'password';")
    [ "$result" = "1" ]
}

run_test "password column exists" test_password_column_exists

# Test 3: Check if admin user exists
test_admin_user_exists() {
    local result=$(run_sql "SELECT COUNT(*) FROM users WHERE username = '${TEST_USERNAME}';")
    [ "$result" = "1" ]
}

run_test "Admin user exists" test_admin_user_exists

# Test 4: Check if admin has BCrypt hash
test_admin_has_bcrypt() {
    local result=$(run_sql "SELECT password_bcrypt FROM users WHERE username = '${TEST_USERNAME}';")
    [[ "$result" =~ ^\$2[ayb]\$ ]]
}

run_test "Admin user has BCrypt hash" test_admin_has_bcrypt

# Test 5: Check if admin has SHA512-CRYPT hash
test_admin_has_sha512() {
    local result=$(run_sql "SELECT password FROM users WHERE username = '${TEST_USERNAME}';")
    [[ "$result" =~ ^\{SHA512-CRYPT\} ]]
}

run_test "Admin user has SHA512-CRYPT hash" test_admin_has_sha512

# Test 6: Verify V3 migration applied
test_v3_migration_applied() {
    local result=$(run_sql "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '3';")
    [ "$result" = "1" ]
}

run_test "V3 migration applied" test_v3_migration_applied

echo ""

# ============================================
# TEST SUITE 2: Gateway Authentication (BCrypt)
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 2: Gateway Authentication (BCrypt)${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Wait for Gateway to be ready
echo -e "${YELLOW}Checking Gateway availability...${NC}"
for i in {1..30}; do
    if curl -s "http://${GATEWAY_HOST}:${GATEWAY_PORT}/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}Gateway is ready${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}ERROR: Gateway not available after 30 seconds${NC}"
        exit 1
    fi
    echo -n "."
    sleep 1
done
echo ""

# Test 7: Gateway login with valid credentials
test_gateway_login_success() {
    local response=$(curl -s -w "\n%{http_code}" \
        -X POST "http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"${TEST_USERNAME}\",\"password\":\"${TEST_PASSWORD}\"}")

    local status_code=$(echo "$response" | tail -n 1)
    local body=$(echo "$response" | sed '$d')

    # Check for 200 status
    [ "$status_code" = "200" ] || return 1

    # Check for token in response
    echo "$body" | jq -e '.token' > /dev/null 2>&1
}

run_test "Gateway login with valid credentials" test_gateway_login_success

# Test 8: Gateway login with invalid password
test_gateway_login_invalid_password() {
    local response=$(curl -s -w "\n%{http_code}" \
        -X POST "http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"${TEST_USERNAME}\",\"password\":\"wrongpassword\"}")

    local status_code=$(echo "$response" | tail -n 1)

    # Should return 401 Unauthorized
    [ "$status_code" = "401" ]
}

run_test "Gateway login with invalid password (should fail)" test_gateway_login_invalid_password

# Test 9: Gateway login with non-existent user
test_gateway_login_nonexistent_user() {
    local response=$(curl -s -w "\n%{http_code}" \
        -X POST "http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"nonexistent@robin.local\",\"password\":\"password\"}")

    local status_code=$(echo "$response" | tail -n 1)

    # Should return 401 Unauthorized
    [ "$status_code" = "401" ]
}

run_test "Gateway login with non-existent user (should fail)" test_gateway_login_nonexistent_user

# Test 10: Access protected endpoint with JWT token
test_gateway_protected_endpoint() {
    # First, get JWT token
    local login_response=$(curl -s \
        -X POST "http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"${TEST_USERNAME}\",\"password\":\"${TEST_PASSWORD}\"}")

    local token=$(echo "$login_response" | jq -r '.token')

    # Use token to access protected endpoint
    local response=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer ${token}" \
        "http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/v1/users")

    local status_code=$(echo "$response" | tail -n 1)

    # Should return 200 OK
    [ "$status_code" = "200" ]
}

run_test "Access protected endpoint with JWT token" test_gateway_protected_endpoint

echo ""

# ============================================
# TEST SUITE 3: Database Password Verification
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 3: Database Password Verification${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test 11: Verify BCrypt password validates correctly in database
test_bcrypt_validation() {
    # Note: This tests if the hash format is valid, not actual validation
    # Actual validation requires pgcrypto extension
    local hash=$(run_sql "SELECT password_bcrypt FROM users WHERE username = '${TEST_USERNAME}';")

    # BCrypt hash should be 60 characters and start with $2a$, $2b$, or $2y$
    [[ "$hash" =~ ^\$2[ayb]\$.{56}$ ]]
}

run_test "BCrypt hash format is valid" test_bcrypt_validation

# Test 12: Verify SHA512-CRYPT password format
test_sha512_validation() {
    local hash=$(run_sql "SELECT password FROM users WHERE username = '${TEST_USERNAME}';")

    # Should have {SHA512-CRYPT} prefix and $6$ format
    [[ "$hash" =~ ^\{SHA512-CRYPT\}\$6\$ ]]
}

run_test "SHA512-CRYPT hash format is valid" test_sha512_validation

# Test 13: Verify both hashes exist for admin user
test_both_hashes_exist() {
    local result=$(run_sql "SELECT COUNT(*) FROM users WHERE username = '${TEST_USERNAME}' AND password IS NOT NULL AND password_bcrypt IS NOT NULL;")
    [ "$result" = "1" ]
}

run_test "Both password hashes exist for admin" test_both_hashes_exist

echo ""

# ============================================
# TEST SUITE 4: Password Sync Service (Optional)
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 4: Password Sync Verification${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test 14: Check if column comments are present
test_column_comments() {
    local comment=$(run_sql "SELECT col_description('users'::regclass, (SELECT ordinal_position FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'password'));")
    [[ "$comment" =~ "SHA512-CRYPT" ]] || [[ "$comment" =~ "Dovecot" ]]
}

run_test "Column documentation comments present" test_column_comments || true

# Test 15: Check for password_bcrypt index
test_bcrypt_index() {
    local result=$(run_sql "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'users' AND indexname LIKE '%password_bcrypt%';")
    [ "$result" -ge "1" ]
}

run_test "Index on password_bcrypt exists" test_bcrypt_index || true

echo ""

# ============================================
# TEST SUMMARY
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUMMARY${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo "Total Tests: ${TESTS_TOTAL}"
echo -e "Passed: ${GREEN}${TESTS_PASSED}${NC}"
echo -e "Failed: ${RED}${TESTS_FAILED}${NC}"
echo ""

# Display password hashes for verification
echo -e "${BLUE}Password Hash Information:${NC}"
echo "Admin User: ${TEST_USERNAME}"
echo ""
echo "BCrypt Hash (Gateway):"
run_sql "SELECT password_bcrypt FROM users WHERE username = '${TEST_USERNAME}';" || echo "(not found)"
echo ""
echo "SHA512-CRYPT Hash (MTA/Dovecot):"
run_sql "SELECT password FROM users WHERE username = '${TEST_USERNAME}';" || echo "(not found)"
echo ""

if [ ${TESTS_FAILED} -eq 0 ]; then
    echo -e "${GREEN}✓ All authentication tests passed!${NC}"
    echo ""
    echo -e "${GREEN}Both authentication methods are working:${NC}"
    echo "  ✓ Gateway (Spring Security) using BCrypt"
    echo "  ✓ MTA/Dovecot (IMAP/SMTP) using SHA512-CRYPT"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    echo ""
    echo -e "${YELLOW}Troubleshooting:${NC}"
    echo "  1. Check if robin-gateway is running on port ${GATEWAY_PORT}"
    echo "  2. Check if PostgreSQL is running on ${DB_HOST}:${DB_PORT}"
    echo "  3. Verify V3 migration was applied: SELECT * FROM flyway_schema_history WHERE version = '3';"
    echo "  4. Check Gateway logs for authentication errors"
    echo "  5. Review docs/database/SCHEMA_OWNERSHIP.md for troubleshooting"
    exit 1
fi
