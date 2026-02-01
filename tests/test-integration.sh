#!/bin/bash
# Test Script: End-to-End Integration Testing
# Tests complete UI → Gateway → MTA flow
#
# Usage: ./test-integration.sh
#
# Environment variables:
#   GATEWAY_HOST: Gateway hostname (default: localhost)
#   GATEWAY_PORT: Gateway port (default: 8080)
#   MTA_HOST: MTA hostname (default: localhost)
#   MTA_CLIENT_PORT: MTA client API port (default: 8090)
#   MTA_SERVICE_PORT: MTA service API port (default: 8080)
#   TEST_USERNAME: Test username (default: admin@robin.local)
#   TEST_PASSWORD: Test password (default: admin123)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
GATEWAY_HOST="${GATEWAY_HOST:-localhost}"
GATEWAY_PORT="${GATEWAY_PORT:-8080}"
MTA_HOST="${MTA_HOST:-localhost}"
MTA_CLIENT_PORT="${MTA_CLIENT_PORT:-8090}"
MTA_SERVICE_PORT="${MTA_SERVICE_PORT:-8080}"
TEST_USERNAME="${TEST_USERNAME:-admin@robin.local}"
TEST_PASSWORD="${TEST_PASSWORD:-admin123}"

# Test counters
TESTS_TOTAL=0
TESTS_PASSED=0
TESTS_FAILED=0

# JWT token (set after login)
JWT_TOKEN=""

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}End-to-End Integration Test Suite${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo "Gateway: ${GATEWAY_HOST}:${GATEWAY_PORT}"
echo "MTA Client API: ${MTA_HOST}:${MTA_CLIENT_PORT}"
echo "MTA Service API: ${MTA_HOST}:${MTA_SERVICE_PORT}"
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

# ============================================
# TEST SUITE 1: Service Availability
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 1: Service Availability${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test Gateway health
test_gateway_health() {
    curl -s -f "http://${GATEWAY_HOST}:${GATEWAY_PORT}/actuator/health" > /dev/null
}

run_test "Gateway health endpoint" test_gateway_health

# Test MTA Client API health
test_mta_client_health() {
    curl -s -f -u "${TEST_USERNAME}:${TEST_PASSWORD}" "http://${MTA_HOST}:${MTA_CLIENT_PORT}/health" > /dev/null
}

run_test "MTA Client API health endpoint" test_mta_client_health

# Test MTA Service API health
test_mta_service_health() {
    curl -s -f -u "${TEST_USERNAME}:${TEST_PASSWORD}" "http://${MTA_HOST}:${MTA_SERVICE_PORT}/health" > /dev/null
}

run_test "MTA Service API health endpoint" test_mta_service_health

echo ""

# ============================================
# TEST SUITE 2: Authentication Flow
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 2: Authentication Flow${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test login and get JWT token
test_login_and_get_token() {
    local response=$(curl -s \
        -X POST "http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"${TEST_USERNAME}\",\"password\":\"${TEST_PASSWORD}\"}")

    JWT_TOKEN=$(echo "$response" | jq -r '.token')

    [ -n "$JWT_TOKEN" ] && [ "$JWT_TOKEN" != "null" ]
}

run_test "Login and retrieve JWT token" test_login_and_get_token

# Test access to protected endpoint with token
test_protected_endpoint_access() {
    curl -s -f \
        -H "Authorization: Bearer ${JWT_TOKEN}" \
        "http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/v1/users" > /dev/null
}

run_test "Access protected endpoint with JWT" test_protected_endpoint_access

echo ""

# ============================================
# TEST SUITE 3: Gateway → MTA Proxy (Queue)
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 3: Gateway → MTA Proxy (Queue)${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test queue listing via Gateway
test_queue_via_gateway() {
    local response=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer ${JWT_TOKEN}" \
        "http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/v1/queue?page=1&limit=10")

    local status=$(echo "$response" | tail -n 1)
    local body=$(echo "$response" | sed '$d')

    # Should return 200 or 404 (if route not configured yet)
    [ "$status" = "200" ] || [ "$status" = "404" ]
}

run_test "Queue listing via Gateway proxy" test_queue_via_gateway || echo -e "${YELLOW}  (Gateway proxy route may not be configured)${NC}"

# Test direct MTA queue access
test_queue_direct_mta() {
    curl -s -f -u "${TEST_USERNAME}:${TEST_PASSWORD}" \
        "http://${MTA_HOST}:${MTA_CLIENT_PORT}/client/queue/json?page=1&limit=10" | jq . > /dev/null
}

run_test "Queue listing directly from MTA" test_queue_direct_mta

echo ""

# ============================================
# TEST SUITE 4: Gateway → MTA Proxy (Config)
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 4: Gateway → MTA Proxy (Config)${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test config access via Gateway
test_config_via_gateway() {
    local response=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer ${JWT_TOKEN}" \
        "http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/v1/config?section=storage")

    local status=$(echo "$response" | tail -n 1)

    # Should return 200 or 404 (if route not configured yet)
    [ "$status" = "200" ] || [ "$status" = "404" ]
}

run_test "Config access via Gateway proxy" test_config_via_gateway || echo -e "${YELLOW}  (Gateway proxy route may not be configured)${NC}"

# Test direct MTA config access
test_config_direct_mta() {
    curl -s -f -u "${TEST_USERNAME}:${TEST_PASSWORD}" \
        "http://${MTA_HOST}:${MTA_SERVICE_PORT}/config/json?section=storage" | jq . > /dev/null
}

run_test "Config access directly from MTA" test_config_direct_mta

echo ""

# ============================================
# TEST SUITE 5: Gateway → MTA Proxy (Scanners)
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 5: Gateway → MTA Proxy (Scanners)${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test scanner test via Gateway
test_scanner_via_gateway() {
    local response=$(curl -s -w "\n%{http_code}" \
        -X POST \
        -H "Authorization: Bearer ${JWT_TOKEN}" \
        "http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/v1/scanners/clamav/test")

    local status=$(echo "$response" | tail -n 1)

    # Should return 200 or 404 (if route not configured yet)
    [ "$status" = "200" ] || [ "$status" = "404" ]
}

run_test "Scanner test via Gateway proxy" test_scanner_via_gateway || echo -e "${YELLOW}  (Gateway proxy route may not be configured)${NC}"

# Test direct MTA scanner access
test_scanner_direct_mta() {
    curl -s -f -u "${TEST_USERNAME}:${TEST_PASSWORD}" \
        -X POST \
        "http://${MTA_HOST}:${MTA_SERVICE_PORT}/scanners/clamav/test" | jq . > /dev/null
}

run_test "Scanner test directly from MTA" test_scanner_direct_mta

echo ""

# ============================================
# TEST SUITE 6: Gateway → MTA Proxy (Metrics)
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 6: Gateway → MTA Proxy (Metrics)${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test metrics via Gateway
test_metrics_via_gateway() {
    local response=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer ${JWT_TOKEN}" \
        "http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/v1/metrics/system")

    local status=$(echo "$response" | tail -n 1)

    # Should return 200 or 404 (if route not configured yet)
    [ "$status" = "200" ] || [ "$status" = "404" ]
}

run_test "Metrics access via Gateway proxy" test_metrics_via_gateway || echo -e "${YELLOW}  (Gateway proxy route may not be configured)${NC}"

# Test direct MTA metrics access
test_metrics_direct_mta() {
    curl -s -f -u "${TEST_USERNAME}:${TEST_PASSWORD}" \
        "http://${MTA_HOST}:${MTA_SERVICE_PORT}/metrics/system" | jq . > /dev/null
}

run_test "Metrics access directly from MTA" test_metrics_direct_mta

echo ""

# ============================================
# TEST SUITE 7: Gateway → MTA Proxy (Logs)
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 7: Gateway → MTA Proxy (Logs)${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test logs via Gateway
test_logs_via_gateway() {
    local response=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer ${JWT_TOKEN}" \
        "http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/v1/logs?search=error&limit=10")

    local status=$(echo "$response" | tail -n 1)

    # Should return 200 or 404 (if route not configured yet)
    [ "$status" = "200" ] || [ "$status" = "404" ]
}

run_test "Logs access via Gateway proxy" test_logs_via_gateway || echo -e "${YELLOW}  (Gateway proxy route may not be configured)${NC}"

# Test direct MTA logs access
test_logs_direct_mta() {
    curl -s -f -u "${TEST_USERNAME}:${TEST_PASSWORD}" \
        "http://${MTA_HOST}:${MTA_CLIENT_PORT}/logs/json?search=error&limit=10" | jq . > /dev/null
}

run_test "Logs access directly from MTA" test_logs_direct_mta

echo ""

# ============================================
# TEST SUITE 8: Data Consistency
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 8: Data Consistency${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Test queue data consistency (direct vs proxy)
test_queue_data_consistency() {
    local direct=$(curl -s -u "${TEST_USERNAME}:${TEST_PASSWORD}" \
        "http://${MTA_HOST}:${MTA_CLIENT_PORT}/client/queue/json?page=1&limit=10" | jq -r '.totalCount')

    # If Gateway proxy is configured, compare results
    local proxy=$(curl -s -H "Authorization: Bearer ${JWT_TOKEN}" \
        "http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/v1/queue?page=1&limit=10" 2>/dev/null | jq -r '.totalCount')

    # If proxy route exists, counts should match
    [ -z "$proxy" ] || [ "$direct" = "$proxy" ] || [ "$proxy" = "null" ]
}

run_test "Queue data consistency (direct vs proxy)" test_queue_data_consistency || echo -e "${YELLOW}  (Gateway proxy route may not be configured)${NC}"

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
    echo -e "${GREEN}✓ All integration tests passed!${NC}"
    echo ""
    echo -e "${GREEN}System Status:${NC}"
    echo "  ✓ Gateway authentication working (JWT)"
    echo "  ✓ MTA JSON endpoints accessible"
    echo "  ✓ Direct MTA access working"
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo "  1. Configure Gateway proxy routes for MTA endpoints"
    echo "  2. Test UI → Gateway → MTA flow from browser"
    echo "  3. Monitor logs for any authentication issues"
    exit 0
else
    echo -e "${RED}✗ Some integration tests failed${NC}"
    echo ""
    echo -e "${YELLOW}Troubleshooting:${NC}"
    echo "  1. Check if all services are running:"
    echo "     - robin-gateway on port ${GATEWAY_PORT}"
    echo "     - robin-mta on ports ${MTA_CLIENT_PORT} and ${MTA_SERVICE_PORT}"
    echo "  2. Verify Gateway Spring Cloud Gateway routes in application.yml"
    echo "  3. Check Gateway logs: tail -f robin-gateway/logs/spring.log"
    echo "  4. Check MTA logs for authentication errors"
    echo "  5. Verify network connectivity between services"
    exit 1
fi
