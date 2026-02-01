#!/bin/bash
# Test Script: Robin MTA JSON Endpoints
# Tests all 15 new JSON endpoints for proper JSON responses
#
# Usage: ./test-mta-json-endpoints.sh [mta-host] [mta-client-port] [mta-service-port]
#
# Defaults:
#   mta-host: localhost
#   mta-client-port: 8090 (ApiEndpoint)
#   mta-service-port: 8080 (RobinServiceEndpoint)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
MTA_HOST="${1:-localhost}"
CLIENT_PORT="${2:-8090}"
SERVICE_PORT="${3:-8080}"
AUTH_USER="${ROBIN_AUTH_USER:-admin}"
AUTH_PASS="${ROBIN_AUTH_PASS:-password}"

# Test counters
TESTS_TOTAL=0
TESTS_PASSED=0
TESTS_FAILED=0

# URLs
CLIENT_API="http://${MTA_HOST}:${CLIENT_PORT}"
SERVICE_API="http://${MTA_HOST}:${SERVICE_PORT}"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Robin MTA JSON Endpoints Test Suite${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo "MTA Host: ${MTA_HOST}"
echo "Client API Port: ${CLIENT_PORT}"
echo "Service API Port: ${SERVICE_PORT}"
echo "Auth User: ${AUTH_USER}"
echo ""

# Helper function to run a test
run_test() {
    local test_name="$1"
    local url="$2"
    local method="${3:-GET}"
    local expected_status="${4:-200}"
    local additional_args="${5:-}"

    TESTS_TOTAL=$((TESTS_TOTAL + 1))

    echo -n "Testing: ${test_name}... "

    # Make request
    response=$(curl -s -w "\n%{http_code}" -u "${AUTH_USER}:${AUTH_PASS}" \
        -X "${method}" \
        ${additional_args} \
        "${url}" 2>&1)

    # Extract status code (last line)
    status_code=$(echo "$response" | tail -n 1)

    # Extract body (all but last line)
    body=$(echo "$response" | sed '$d')

    # Check status code
    if [ "$status_code" != "$expected_status" ]; then
        echo -e "${RED}FAILED${NC} (Expected HTTP ${expected_status}, got ${status_code})"
        echo "Response: ${body}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    # Check if response is valid JSON
    if ! echo "$body" | jq . > /dev/null 2>&1; then
        echo -e "${RED}FAILED${NC} (Invalid JSON response)"
        echo "Response: ${body}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    echo -e "${GREEN}PASSED${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))

    # Print formatted JSON in verbose mode
    if [ "${VERBOSE}" = "true" ]; then
        echo "$body" | jq .
    fi

    return 0
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
echo -e "${GREEN}Dependencies OK${NC}"
echo ""

# Wait for services to be ready
echo -e "${YELLOW}Checking service availability...${NC}"
for i in {1..30}; do
    if curl -s -u "${AUTH_USER}:${AUTH_PASS}" "${CLIENT_API}/health" > /dev/null 2>&1; then
        echo -e "${GREEN}Client API (port ${CLIENT_PORT}) is ready${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}ERROR: Client API not available after 30 seconds${NC}"
        exit 1
    fi
    echo -n "."
    sleep 1
done

for i in {1..30}; do
    if curl -s -u "${AUTH_USER}:${AUTH_PASS}" "${SERVICE_API}/health" > /dev/null 2>&1; then
        echo -e "${GREEN}Service API (port ${SERVICE_PORT}) is ready${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}ERROR: Service API not available after 30 seconds${NC}"
        exit 1
    fi
    echo -n "."
    sleep 1
done
echo ""

# ============================================
# TEST SUITE 1: Client API Endpoints (Port 8090)
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 1: Client API Endpoints${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test 1: Queue JSON endpoint
run_test "GET /client/queue/json (default pagination)" \
    "${CLIENT_API}/client/queue/json" \
    "GET" \
    "200"

# Test 2: Queue JSON with pagination
run_test "GET /client/queue/json (page=1&limit=10)" \
    "${CLIENT_API}/client/queue/json?page=1&limit=10" \
    "GET" \
    "200"

# Test 3: Queue JSON page 2
run_test "GET /client/queue/json (page=2&limit=5)" \
    "${CLIENT_API}/client/queue/json?page=2&limit=5" \
    "GET" \
    "200"

echo ""

# ============================================
# TEST SUITE 2: Configuration Endpoints (Port 8080)
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 2: Configuration Endpoints${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test configuration sections
for section in storage queue relay dovecot clamav rspamd webhooks blocklist; do
    run_test "GET /config/json?section=${section}" \
        "${SERVICE_API}/config/json?section=${section}" \
        "GET" \
        "200"
done

# Test invalid section
run_test "GET /config/json?section=invalid (should fail)" \
    "${SERVICE_API}/config/json?section=invalid" \
    "GET" \
    "400"

echo ""

# ============================================
# TEST SUITE 3: Scanner Endpoints (Port 8080)
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 3: Scanner Endpoints${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test ClamAV endpoints
run_test "POST /scanners/clamav/test" \
    "${SERVICE_API}/scanners/clamav/test" \
    "POST" \
    "200"

run_test "GET /scanners/clamav/status" \
    "${SERVICE_API}/scanners/clamav/status" \
    "GET" \
    "200"

# Test Rspamd endpoints
run_test "POST /scanners/rspamd/test" \
    "${SERVICE_API}/scanners/rspamd/test" \
    "POST" \
    "200"

run_test "GET /scanners/rspamd/status" \
    "${SERVICE_API}/scanners/rspamd/status" \
    "GET" \
    "200"

echo ""

# ============================================
# TEST SUITE 4: Metrics Endpoints (Port 8080)
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 4: Metrics Endpoints${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

run_test "GET /metrics/system" \
    "${SERVICE_API}/metrics/system" \
    "GET" \
    "200"

run_test "GET /metrics/queue" \
    "${SERVICE_API}/metrics/queue" \
    "GET" \
    "200"

echo ""

# ============================================
# TEST SUITE 5: Logs Endpoints (Port 8080)
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 5: Logs Endpoints${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

run_test "GET /logs/json (default)" \
    "${SERVICE_API}/logs/json" \
    "GET" \
    "200"

run_test "GET /logs/json?search=error" \
    "${SERVICE_API}/logs/json?search=error" \
    "GET" \
    "200"

run_test "GET /logs/json?limit=10&offset=0" \
    "${SERVICE_API}/logs/json?limit=10&offset=0" \
    "GET" \
    "200"

echo ""

# ============================================
# TEST SUITE 6: Blocklist Endpoints (Port 8080)
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 6: Blocklist Endpoints${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

run_test "GET /blocklist" \
    "${SERVICE_API}/blocklist" \
    "GET" \
    "200"

# Note: POST/DELETE/PATCH return 501 (Not Implemented) as per placeholder
run_test "POST /blocklist (placeholder - expect 501)" \
    "${SERVICE_API}/blocklist" \
    "POST" \
    "501" \
    '-H "Content-Type: application/json" -d "{\"type\":\"IP\",\"value\":\"192.168.1.1\"}"'

run_test "DELETE /blocklist?id=1 (placeholder - expect 501)" \
    "${SERVICE_API}/blocklist?id=1" \
    "DELETE" \
    "501"

run_test "PATCH /blocklist?id=1 (placeholder - expect 501)" \
    "${SERVICE_API}/blocklist?id=1" \
    "PATCH" \
    "501" \
    '-H "Content-Type: application/json" -d "{\"active\":false}"'

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

if [ ${TESTS_FAILED} -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi
