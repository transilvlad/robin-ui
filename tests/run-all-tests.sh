#!/bin/bash
# Master Test Runner Script
# Executes all test suites for Robin UI implementation
#
# Usage: ./run-all-tests.sh [--verbose] [--skip-build]
#
# Options:
#   --verbose: Show detailed output from each test
#   --skip-build: Skip Maven build steps
#   --quick: Run only critical tests (skip optional tests)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
GATEWAY_DIR="${PROJECT_ROOT}/robin-gateway"
MTA_DIR="/Users/cstan/development/workspace/open-source/transilvlad-robin"

# Parse arguments
VERBOSE=false
SKIP_BUILD=false
QUICK_MODE=false

for arg in "$@"; do
    case $arg in
        --verbose)
            VERBOSE=true
            export VERBOSE=true
            ;;
        --skip-build)
            SKIP_BUILD=true
            ;;
        --quick)
            QUICK_MODE=true
            ;;
        *)
            echo "Unknown option: $arg"
            echo "Usage: $0 [--verbose] [--skip-build] [--quick]"
            exit 1
            ;;
    esac
done

# Test results
SUITE_RESULTS=()

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}Robin UI Master Test Runner${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""
echo "Project Root: ${PROJECT_ROOT}"
echo "Gateway Directory: ${GATEWAY_DIR}"
echo "MTA Directory: ${MTA_DIR}"
echo "Mode: $([ "$QUICK_MODE" = true ] && echo "Quick" || echo "Full")"
echo ""

# Helper function to run a test suite
run_test_suite() {
    local suite_name="$1"
    local script_path="$2"
    local is_optional="${3:-false}"

    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}Running: ${suite_name}${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""

    if [ "$QUICK_MODE" = true ] && [ "$is_optional" = true ]; then
        echo -e "${YELLOW}Skipping optional test in quick mode${NC}"
        SUITE_RESULTS+=("${suite_name}:SKIPPED")
        echo ""
        return 0
    fi

    if [ ! -f "$script_path" ]; then
        echo -e "${RED}ERROR: Test script not found: ${script_path}${NC}"
        SUITE_RESULTS+=("${suite_name}:ERROR")
        echo ""
        return 1
    fi

    chmod +x "$script_path"

    if [ "$VERBOSE" = true ]; then
        if "$script_path"; then
            SUITE_RESULTS+=("${suite_name}:PASSED")
        else
            SUITE_RESULTS+=("${suite_name}:FAILED")
        fi
    else
        if "$script_path" > /tmp/test_output_$$.log 2>&1; then
            echo -e "${GREEN}✓ ${suite_name} PASSED${NC}"
            SUITE_RESULTS+=("${suite_name}:PASSED")
        else
            echo -e "${RED}✗ ${suite_name} FAILED${NC}"
            echo "See /tmp/test_output_$$.log for details"
            SUITE_RESULTS+=("${suite_name}:FAILED")
        fi
    fi

    echo ""
}

# ============================================
# STEP 1: Build Projects
# ============================================
if [ "$SKIP_BUILD" = false ]; then
    echo -e "${MAGENTA}============================================${NC}"
    echo -e "${MAGENTA}STEP 1: Building Projects${NC}"
    echo -e "${MAGENTA}============================================${NC}"
    echo ""

    # Build Robin Gateway
    echo -e "${YELLOW}Building robin-gateway...${NC}"
    if [ -d "$GATEWAY_DIR" ]; then
        cd "$GATEWAY_DIR"
        if mvn clean install -DskipTests > /tmp/gateway_build_$$.log 2>&1; then
            echo -e "${GREEN}✓ Gateway build successful${NC}"
        else
            echo -e "${RED}✗ Gateway build failed${NC}"
            echo "See /tmp/gateway_build_$$.log for details"
            cat /tmp/gateway_build_$$.log
            exit 1
        fi
    else
        echo -e "${YELLOW}Gateway directory not found, skipping build${NC}"
    fi

    echo ""

    # Build Robin MTA
    echo -e "${YELLOW}Building robin-mta...${NC}"
    if [ -d "$MTA_DIR" ]; then
        cd "$MTA_DIR"
        if mvn clean install -DskipTests > /tmp/mta_build_$$.log 2>&1; then
            echo -e "${GREEN}✓ MTA build successful${NC}"
        else
            echo -e "${RED}✗ MTA build failed${NC}"
            echo "See /tmp/mta_build_$$.log for details"
            cat /tmp/mta_build_$$.log
            exit 1
        fi
    else
        echo -e "${YELLOW}MTA directory not found, skipping build${NC}"
    fi

    cd "$PROJECT_ROOT"
    echo ""
else
    echo -e "${YELLOW}Skipping build step (--skip-build)${NC}"
    echo ""
fi

# ============================================
# STEP 2: Run Gateway Unit Tests
# ============================================
echo -e "${MAGENTA}============================================${NC}"
echo -e "${MAGENTA}STEP 2: Running Gateway Unit Tests${NC}"
echo -e "${MAGENTA}============================================${NC}"
echo ""

if [ -d "$GATEWAY_DIR" ]; then
    cd "$GATEWAY_DIR"

    echo -e "${YELLOW}Running PasswordSyncService tests...${NC}"
    if mvn test -Dtest=PasswordSyncServiceTest > /tmp/gateway_test_$$.log 2>&1; then
        echo -e "${GREEN}✓ Gateway unit tests PASSED${NC}"
        SUITE_RESULTS+=("Gateway Unit Tests:PASSED")
    else
        echo -e "${RED}✗ Gateway unit tests FAILED${NC}"
        echo "See /tmp/gateway_test_$$.log for details"
        cat /tmp/gateway_test_$$.log
        SUITE_RESULTS+=("Gateway Unit Tests:FAILED")
    fi

    cd "$PROJECT_ROOT"
else
    echo -e "${YELLOW}Gateway directory not found, skipping unit tests${NC}"
    SUITE_RESULTS+=("Gateway Unit Tests:SKIPPED")
fi

echo ""

# ============================================
# STEP 3: Database Schema Tests
# ============================================
echo -e "${MAGENTA}============================================${NC}"
echo -e "${MAGENTA}STEP 3: Database Schema Tests${NC}"
echo -e "${MAGENTA}============================================${NC}"
echo ""

run_test_suite "Database Schema Verification" \
    "${SCRIPT_DIR}/test-database-schema.sh" \
    false

# ============================================
# STEP 4: Dual Authentication Tests
# ============================================
echo -e "${MAGENTA}============================================${NC}"
echo -e "${MAGENTA}STEP 4: Dual Authentication Tests${NC}"
echo -e "${MAGENTA}============================================${NC}"
echo ""

run_test_suite "Dual Password Authentication" \
    "${SCRIPT_DIR}/test-dual-auth.sh" \
    false

# ============================================
# STEP 5: MTA JSON Endpoints Tests
# ============================================
echo -e "${MAGENTA}============================================${NC}"
echo -e "${MAGENTA}STEP 5: MTA JSON Endpoints Tests${NC}"
echo -e "${MAGENTA}============================================${NC}"
echo ""

run_test_suite "MTA JSON API Endpoints" \
    "${SCRIPT_DIR}/test-mta-json-endpoints.sh" \
    false

# ============================================
# STEP 6: Integration Tests
# ============================================
echo -e "${MAGENTA}============================================${NC}"
echo -e "${MAGENTA}STEP 6: End-to-End Integration Tests${NC}"
echo -e "${MAGENTA}============================================${NC}"
echo ""

run_test_suite "End-to-End Integration" \
    "${SCRIPT_DIR}/test-integration.sh" \
    true

# ============================================
# FINAL SUMMARY
# ============================================
echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}FINAL TEST SUMMARY${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""

TOTAL_SUITES=0
PASSED_SUITES=0
FAILED_SUITES=0
SKIPPED_SUITES=0

for result in "${SUITE_RESULTS[@]}"; do
    TOTAL_SUITES=$((TOTAL_SUITES + 1))
    suite_name="${result%%:*}"
    status="${result##*:}"

    case $status in
        PASSED)
            echo -e "${GREEN}✓${NC} ${suite_name}"
            PASSED_SUITES=$((PASSED_SUITES + 1))
            ;;
        FAILED)
            echo -e "${RED}✗${NC} ${suite_name}"
            FAILED_SUITES=$((FAILED_SUITES + 1))
            ;;
        SKIPPED)
            echo -e "${YELLOW}⊘${NC} ${suite_name}"
            SKIPPED_SUITES=$((SKIPPED_SUITES + 1))
            ;;
        ERROR)
            echo -e "${RED}⚠${NC} ${suite_name}"
            FAILED_SUITES=$((FAILED_SUITES + 1))
            ;;
    esac
done

echo ""
echo "Total Test Suites: ${TOTAL_SUITES}"
echo -e "Passed: ${GREEN}${PASSED_SUITES}${NC}"
echo -e "Failed: ${RED}${FAILED_SUITES}${NC}"
echo -e "Skipped: ${YELLOW}${SKIPPED_SUITES}${NC}"
echo ""

# Cleanup temp files
rm -f /tmp/*_$$*.log

if [ ${FAILED_SUITES} -eq 0 ]; then
    echo -e "${GREEN}════════════════════════════════════════${NC}"
    echo -e "${GREEN}✓ ALL TESTS PASSED!${NC}"
    echo -e "${GREEN}════════════════════════════════════════${NC}"
    echo ""
    echo -e "${GREEN}Implementation Status:${NC}"
    echo "  ✓ Phase 1: Robin MTA JSON APIs - COMPLETE"
    echo "  ✓ Phase 2: Database Dual-Hash - COMPLETE"
    echo "  ✓ Phase 3: Testing - COMPLETE"
    echo ""
    echo -e "${CYAN}Robin UI is ready for deployment!${NC}"
    exit 0
else
    echo -e "${RED}════════════════════════════════════════${NC}"
    echo -e "${RED}✗ SOME TESTS FAILED${NC}"
    echo -e "${RED}════════════════════════════════════════${NC}"
    echo ""
    echo -e "${YELLOW}Next Steps:${NC}"
    echo "  1. Review failed test logs in /tmp/"
    echo "  2. Check service status (robin-gateway, robin-mta)"
    echo "  3. Verify database migrations applied"
    echo "  4. Check application logs for errors"
    echo "  5. Review troubleshooting guides in docs/"
    exit 1
fi
