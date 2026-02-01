#!/bin/bash
# =============================================================================
# Robin MTA Complete Suite - Validation Script
# =============================================================================
# This script validates that all services are running correctly.
#
# Usage:
#   chmod +x scripts/validate.sh
#   ./scripts/validate.sh [docker-compose-file]
#
# Examples:
#   ./scripts/validate.sh docker-compose.full.yaml
#   ./scripts/validate.sh docker-compose.dev.full.yaml
#
# =============================================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Compose file (default to production)
COMPOSE_FILE="${1:-docker-compose.full.yaml}"

if [ ! -f "$COMPOSE_FILE" ]; then
    echo -e "${RED}[ERROR]${NC} Docker Compose file not found: $COMPOSE_FILE"
    exit 1
fi

echo ""
echo "=========================================="
echo "  Robin MTA Suite - Health Check"
echo "=========================================="
echo ""
echo "Using: $COMPOSE_FILE"
echo ""

# Track overall health
ALL_HEALTHY=true

# Function to check service health
check_service() {
    local service=$1
    local container=$2
    local port=$3
    local endpoint=${4:-""}

    printf "%-20s " "$service:"

    # Check if container is running
    if ! docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        echo -e "${RED}✗ Container not running${NC}"
        ALL_HEALTHY=false
        return 1
    fi

    # Check container health status
    health_status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "none")

    if [ "$health_status" == "healthy" ]; then
        echo -e "${GREEN}✓ Healthy${NC}"
        return 0
    elif [ "$health_status" == "starting" ]; then
        echo -e "${YELLOW}⟳ Starting...${NC}"
        ALL_HEALTHY=false
        return 1
    elif [ "$health_status" == "unhealthy" ]; then
        echo -e "${RED}✗ Unhealthy${NC}"
        ALL_HEALTHY=false
        return 1
    else
        # No health check defined, check if port is accessible
        if [ -n "$port" ]; then
            if nc -z localhost "$port" 2>/dev/null; then
                echo -e "${GREEN}✓ Running (port $port open)${NC}"
                return 0
            else
                echo -e "${YELLOW}⟳ Port $port not accessible yet${NC}"
                ALL_HEALTHY=false
                return 1
            fi
        else
            echo -e "${YELLOW}? No health check${NC}"
            return 0
        fi
    fi
}

# Function to check HTTP endpoint
check_http() {
    local name=$1
    local url=$2

    printf "%-20s " "$name:"

    if curl -sf "$url" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Accessible${NC}"
        return 0
    else
        echo -e "${RED}✗ Not accessible${NC}"
        ALL_HEALTHY=false
        return 1
    fi
}

echo "Checking container health..."
echo ""

# Check each service
check_service "PostgreSQL" "robin-postgres" "5432"
check_service "Redis" "robin-redis" "6379"
check_service "ClamAV" "robin-clamav" "3310"
check_service "Rspamd" "robin-rspamd" "11333"
check_service "Robin MTA" "robin-mta" "8080"
check_service "Robin Gateway" "robin-gateway" "8888"

# Check Robin UI based on compose file
if [[ "$COMPOSE_FILE" == *"dev"* ]]; then
    check_service "Robin UI (dev)" "robin-ui-dev" "4200"
else
    check_service "Robin UI" "robin-ui" "80"
fi

echo ""
echo "Checking HTTP endpoints..."
echo ""

# Check HTTP endpoints
check_http "Gateway health" "http://localhost:8888/actuator/health"
check_http "Robin MTA API" "http://localhost:8080/health"
check_http "Rspamd UI" "http://localhost:11334"

if [[ "$COMPOSE_FILE" == *"dev"* ]]; then
    check_http "Robin UI (dev)" "http://localhost:4200"
else
    check_http "Robin UI" "http://localhost"
fi

echo ""
echo "Checking SMTP ports..."
echo ""

# Check SMTP ports
check_service "SMTP (25)" "robin-mta" "25"
check_service "SUBMISSION (587)" "robin-mta" "587"
check_service "SMTPS (465)" "robin-mta" "465"

echo ""
echo "=========================================="

if [ "$ALL_HEALTHY" = true ]; then
    echo -e "${GREEN}✓ All services are healthy!${NC}"
    echo ""
    echo "Access points:"
    if [[ "$COMPOSE_FILE" == *"dev"* ]]; then
        echo "  - Robin UI (dev):     http://localhost:4200"
    else
        echo "  - Robin UI:           http://localhost"
    fi
    echo "  - Robin Gateway:      http://localhost:8888"
    echo "  - Rspamd Web UI:      http://localhost:11334"
    echo ""
    echo "Default login:"
    echo "  - Username: admin@localhost"
    echo "  - Password: admin"
    echo ""
    exit 0
else
    echo -e "${YELLOW}⚠ Some services are not healthy yet${NC}"
    echo ""
    echo "This is normal if services just started."
    echo "Wait a few minutes and run this script again."
    echo ""
    echo "To view logs:"
    echo "  docker compose -f $COMPOSE_FILE logs -f"
    echo ""
    echo "To check specific service:"
    echo "  docker compose -f $COMPOSE_FILE logs <service-name>"
    echo ""
    exit 1
fi
