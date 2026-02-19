#!/bin/bash

set -e

# Change to the script's directory (robin-gateway)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

echo "🚀 Robin Gateway - Docker Deployment"
echo "======================================"
echo ""
echo "Working directory: $(pwd)"
echo ""

# Check if .env exists
if [ ! -f .env ]; then
    echo "❌ Error: .env file not found!"
    echo "Please create .env file with required configuration."
    exit 1
fi

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running!"
    echo "Please start Docker and try again."
    exit 1
fi

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Error: docker-compose is not installed!"
    echo "Please install docker-compose and try again."
    exit 1
fi

echo "✅ Prerequisites check passed"
echo ""

# Build and start services
echo "🔨 Building and starting services..."
docker-compose up -d --build

echo ""
echo "⏳ Waiting for services to be healthy..."
sleep 5

# Check service status
echo ""
echo "📊 Service Status:"
docker-compose ps

echo ""
echo "🏥 Health Check:"
sleep 10
for i in {1..30}; do
    if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "✅ Gateway is healthy!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "⚠️  Warning: Gateway health check timed out"
        echo "Check logs with: docker-compose logs gateway"
    fi
    echo "Waiting... ($i/30)"
    sleep 2
done

echo ""
echo "✅ Deployment complete!"
echo ""
echo "📋 Useful commands:"
echo "  View logs:        docker-compose logs -f gateway"
echo "  Check health:     curl http://localhost:8080/actuator/health"
echo "  API docs:         http://localhost:8080/swagger-ui.html"
echo "  Stop services:    docker-compose down"
echo "  Restart:          docker-compose restart gateway"
echo ""
