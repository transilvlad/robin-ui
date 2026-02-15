#!/bin/bash

###############################################################################
# Robin Gateway Compliance Verification Script
#
# This script runs all compliance checks and generates a summary report.
#
# Usage: ./verify-compliance.sh [--quick]
#   --quick: Skip OWASP dependency check (saves ~5 minutes)
#
# Author: Robin Gateway Team
# Date: 2026-02-06
###############################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
QUICK_MODE=false
if [ "$1" = "--quick" ]; then
    QUICK_MODE=true
fi

# Banner
echo -e "${BLUE}"
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║       Robin Gateway - Compliance Verification                 ║"
echo "║       Date: $(date +%Y-%m-%d)                                       ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}✗ Maven is not installed${NC}"
    echo "  Install with: brew install maven"
    exit 1
fi

echo -e "${GREEN}✓ Maven found: $(mvn --version | head -1)${NC}\n"

# Variables to track results
TESTS_PASSED=0
COVERAGE_PASSED=0
CHECKSTYLE_PASSED=0
PMD_PASSED=0
SPOTBUGS_PASSED=0
OWASP_PASSED=0
ARCHUNIT_PASSED=0

###############################################################################
# Step 1: Build and Test
###############################################################################
echo -e "${BLUE}[1/7] Running tests...${NC}"
if mvn clean test > /tmp/robin-gateway-test.log 2>&1; then
    TESTS_PASSED=1
    echo -e "${GREEN}  ✓ All tests passed${NC}"
    TEST_COUNT=$(grep -o "Tests run: [0-9]*" /tmp/robin-gateway-test.log | tail -1 | grep -o "[0-9]*")
    echo -e "  ${GREEN}Tests executed: ${TEST_COUNT}${NC}"
else
    echo -e "${RED}  ✗ Tests failed${NC}"
    echo -e "  ${YELLOW}Check /tmp/robin-gateway-test.log for details${NC}"
fi
echo ""

###############################################################################
# Step 2: Code Coverage
###############################################################################
echo -e "${BLUE}[2/7] Generating coverage report...${NC}"
if mvn jacoco:report jacoco:check > /tmp/robin-gateway-coverage.log 2>&1; then
    COVERAGE_PASSED=1
    echo -e "${GREEN}  ✓ Coverage meets threshold (≥60%)${NC}"
else
    echo -e "${YELLOW}  ⚠ Coverage below threshold${NC}"
    echo -e "  ${YELLOW}Check target/site/jacoco/index.html${NC}"
fi

# Extract coverage percentage if available
if [ -f target/site/jacoco/index.html ]; then
    COVERAGE=$(grep -o "Total.*[0-9]*%" target/site/jacoco/index.html | grep -o "[0-9]*%" | head -1)
    echo -e "  ${BLUE}Current coverage: ${COVERAGE}${NC}"
fi
echo ""

###############################################################################
# Step 3: Code Style (Checkstyle)
###############################################################################
echo -e "${BLUE}[3/7] Running Checkstyle...${NC}"
if mvn checkstyle:check > /tmp/robin-gateway-checkstyle.log 2>&1; then
    CHECKSTYLE_PASSED=1
    echo -e "${GREEN}  ✓ No style violations${NC}"
else
    echo -e "${YELLOW}  ⚠ Style violations found${NC}"
    VIOLATIONS=$(grep -c "^\[" /tmp/robin-gateway-checkstyle.log || echo "0")
    echo -e "  ${YELLOW}Violations: ${VIOLATIONS}${NC}"
    echo -e "  ${YELLOW}Check target/checkstyle-result.xml${NC}"
fi
echo ""

###############################################################################
# Step 4: Code Quality (PMD)
###############################################################################
echo -e "${BLUE}[4/7] Running PMD...${NC}"
if mvn pmd:check > /tmp/robin-gateway-pmd.log 2>&1; then
    PMD_PASSED=1
    echo -e "${GREEN}  ✓ No code quality issues${NC}"
else
    echo -e "${YELLOW}  ⚠ Code quality issues found${NC}"
    echo -e "  ${YELLOW}Check target/pmd.xml${NC}"
fi
echo ""

###############################################################################
# Step 5: Bug Detection (SpotBugs)
###############################################################################
echo -e "${BLUE}[5/7] Running SpotBugs...${NC}"
if mvn spotbugs:check > /tmp/robin-gateway-spotbugs.log 2>&1; then
    SPOTBUGS_PASSED=1
    echo -e "${GREEN}  ✓ No bugs detected${NC}"
else
    echo -e "${YELLOW}  ⚠ Potential bugs found${NC}"
    echo -e "  ${YELLOW}Check target/spotbugsXml.xml${NC}"
fi
echo ""

###############################################################################
# Step 6: Security Scan (OWASP Dependency Check)
###############################################################################
if [ "$QUICK_MODE" = false ]; then
    echo -e "${BLUE}[6/7] Running OWASP dependency check (this may take 5-10 minutes)...${NC}"
    if mvn org.owasp:dependency-check-maven:check > /tmp/robin-gateway-owasp.log 2>&1; then
        OWASP_PASSED=1
        echo -e "${GREEN}  ✓ No critical vulnerabilities (CVSS <7)${NC}"
    else
        echo -e "${RED}  ✗ Critical vulnerabilities found (CVSS ≥7)${NC}"
        echo -e "  ${RED}Check target/dependency-check-report.html${NC}"
    fi
    echo ""
else
    echo -e "${YELLOW}[6/7] Skipping OWASP check (--quick mode)${NC}\n"
fi

###############################################################################
# Step 7: Architecture Tests
###############################################################################
echo -e "${BLUE}[7/7] Running architecture tests...${NC}"
if mvn test -Dtest=ArchitectureTest > /tmp/robin-gateway-archunit.log 2>&1; then
    ARCHUNIT_PASSED=1
    echo -e "${GREEN}  ✓ All architecture rules passed${NC}"
else
    echo -e "${RED}  ✗ Architecture violations found${NC}"
    echo -e "  ${RED}Check /tmp/robin-gateway-archunit.log${NC}"
fi
echo ""

###############################################################################
# Summary Report
###############################################################################
echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                    COMPLIANCE SUMMARY                         ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}\n"

# Calculate score
TOTAL_CHECKS=7
if [ "$QUICK_MODE" = true ]; then
    TOTAL_CHECKS=6
fi

PASSED=$((TESTS_PASSED + COVERAGE_PASSED + CHECKSTYLE_PASSED + PMD_PASSED + SPOTBUGS_PASSED + OWASP_PASSED + ARCHUNIT_PASSED))
SCORE=$((PASSED * 100 / TOTAL_CHECKS))

# Display results
echo -e "  Tests:              $([ $TESTS_PASSED -eq 1 ] && echo -e "${GREEN}✓ PASS${NC}" || echo -e "${RED}✗ FAIL${NC}")"
echo -e "  Coverage:           $([ $COVERAGE_PASSED -eq 1 ] && echo -e "${GREEN}✓ PASS${NC}" || echo -e "${YELLOW}⚠ WARN${NC}")"
echo -e "  Checkstyle:         $([ $CHECKSTYLE_PASSED -eq 1 ] && echo -e "${GREEN}✓ PASS${NC}" || echo -e "${YELLOW}⚠ WARN${NC}")"
echo -e "  PMD:                $([ $PMD_PASSED -eq 1 ] && echo -e "${GREEN}✓ PASS${NC}" || echo -e "${YELLOW}⚠ WARN${NC}")"
echo -e "  SpotBugs:           $([ $SPOTBUGS_PASSED -eq 1 ] && echo -e "${GREEN}✓ PASS${NC}" || echo -e "${YELLOW}⚠ WARN${NC}")"
if [ "$QUICK_MODE" = false ]; then
    echo -e "  OWASP:              $([ $OWASP_PASSED -eq 1 ] && echo -e "${GREEN}✓ PASS${NC}" || echo -e "${RED}✗ FAIL${NC}")"
else
    echo -e "  OWASP:              ${YELLOW}⊘ SKIPPED${NC}"
fi
echo -e "  Architecture:       $([ $ARCHUNIT_PASSED -eq 1 ] && echo -e "${GREEN}✓ PASS${NC}" || echo -e "${RED}✗ FAIL${NC}")"

echo ""
echo -e "  ${BLUE}Compliance Score: ${SCORE}% (${PASSED}/${TOTAL_CHECKS})${NC}"
echo ""

# Overall status
if [ $SCORE -ge 95 ]; then
    echo -e "  ${GREEN}✓ PRODUCTION READY${NC}"
    EXIT_CODE=0
elif [ $SCORE -ge 80 ]; then
    echo -e "  ${YELLOW}⚠ NEEDS IMPROVEMENT${NC}"
    EXIT_CODE=1
else
    echo -e "  ${RED}✗ CRITICAL ISSUES - NOT READY${NC}"
    EXIT_CODE=2
fi

echo ""
echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                    NEXT STEPS                                 ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}\n"

if [ $TESTS_PASSED -eq 0 ]; then
    echo -e "  ${RED}1. Fix failing tests${NC}"
    echo -e "     mvn test"
    echo -e "     Check /tmp/robin-gateway-test.log"
    echo ""
fi

if [ $COVERAGE_PASSED -eq 0 ]; then
    echo -e "  ${YELLOW}2. Improve test coverage${NC}"
    echo -e "     mvn jacoco:report"
    echo -e "     open target/site/jacoco/index.html"
    echo -e "     Target: 60% minimum"
    echo ""
fi

if [ $CHECKSTYLE_PASSED -eq 0 ]; then
    echo -e "  ${YELLOW}3. Fix style violations${NC}"
    echo -e "     mvn checkstyle:check"
    echo -e "     See docs/COMPLIANCE_QUICK_START.md"
    echo ""
fi

if [ $OWASP_PASSED -eq 0 ] && [ "$QUICK_MODE" = false ]; then
    echo -e "  ${RED}4. Fix security vulnerabilities${NC}"
    echo -e "     open target/dependency-check-report.html"
    echo -e "     Update dependencies or add suppressions"
    echo ""
fi

if [ $ARCHUNIT_PASSED -eq 0 ]; then
    echo -e "  ${RED}5. Fix architecture violations${NC}"
    echo -e "     mvn test -Dtest=ArchitectureTest"
    echo -e "     Check /tmp/robin-gateway-archunit.log"
    echo ""
fi

echo -e "${BLUE}Reports:${NC}"
echo -e "  Coverage:  target/site/jacoco/index.html"
echo -e "  Checkstyle: target/checkstyle-result.xml"
echo -e "  PMD:       target/pmd.xml"
echo -e "  SpotBugs:  target/spotbugsXml.xml"
if [ "$QUICK_MODE" = false ]; then
    echo -e "  OWASP:     target/dependency-check-report.html"
fi
echo ""

echo -e "${BLUE}Documentation:${NC}"
echo -e "  Quick Start: docs/COMPLIANCE_QUICK_START.md"
echo -e "  Security:    docs/SECURITY.md"
echo -e "  Summary:     docs/COMPLIANCE_IMPLEMENTATION_SUMMARY.md"
echo ""

exit $EXIT_CODE
