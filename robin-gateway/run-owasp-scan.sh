#!/usr/bin/env bash

# OWASP Dependency Check Scanner for Robin Gateway
# Requires NVD API Key - see docs/SECURITY_SCANNING.md

set -e

# Change to the script's directory (robin-gateway)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

echo "════════════════════════════════════════════════════════════════"
echo "  OWASP Dependency Check - Robin Gateway"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "Working directory: $(pwd)"
echo ""

# Try to load NVD_API_KEY from common shell configuration files
if [ -z "$NVD_API_KEY" ]; then
    # Try .env file first
    if [ -f .env ]; then
        export $(grep -v '^#' .env | grep NVD_API_KEY | xargs)
    fi
fi

# Check if NVD_API_KEY is set
if [ -z "$NVD_API_KEY" ]; then
    echo "❌ ERROR: NVD_API_KEY environment variable is not set"
    echo ""
    echo "To set the API key for this session:"
    echo "  export NVD_API_KEY='your-api-key-here'"
    echo "  ./run-owasp-scan.sh"
    echo ""
    echo "Or add it to .env file:"
    echo "  echo 'NVD_API_KEY=your-api-key-here' >> .env"
    echo "  ./run-owasp-scan.sh"
    echo ""
    echo "To get an API key:"
    echo "  1. Visit: https://nvd.nist.gov/developers/request-an-api-key"
    echo "  2. Fill out the form with your email"
    echo "  3. You'll receive the key via email (usually within minutes)"
    echo ""
    echo "For permanent configuration, add to your shell profile:"
    echo "  echo 'export NVD_API_KEY=\"your-api-key-here\"' >> ~/.zshrc"
    echo "  source ~/.zshrc"
    echo ""
    exit 1
fi

echo "✓ NVD API Key detected"
echo ""

# Determine scan type
SCAN_TYPE=${1:-check}

case $SCAN_TYPE in
    "check")
        echo "Running OWASP Dependency Check (fails on CVSS >= 7)..."
        echo ""
        mvn org.owasp:dependency-check-maven:check -DnvdApiKey="$NVD_API_KEY"
        ;;
    "aggregate")
        echo "Running OWASP Dependency Check (aggregate report, no fail)..."
        echo ""
        mvn org.owasp:dependency-check-maven:aggregate -DnvdApiKey="$NVD_API_KEY"
        ;;
    "update-only")
        echo "Updating NVD database only (no scan)..."
        echo ""
        mvn org.owasp:dependency-check-maven:update-only -DnvdApiKey="$NVD_API_KEY"
        ;;
    *)
        echo "❌ Invalid scan type: $SCAN_TYPE"
        echo ""
        echo "Usage: $0 [check|aggregate|update-only]"
        echo "  check        - Run check and fail on CVSS >= 7 (default)"
        echo "  aggregate    - Generate report without failing build"
        echo "  update-only  - Update NVD database without scanning"
        exit 1
        ;;
esac

echo ""
echo "════════════════════════════════════════════════════════════════"
echo "  Scan Complete!"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "Reports generated in: target/dependency-check-report.html"
echo "JSON report: target/dependency-check-report.json"
echo ""
echo "To view the report:"
echo "  open target/dependency-check-report.html"
echo ""
