#!/bin/bash
# =============================================================================
# Robin MTA Complete Suite - Setup Script
# =============================================================================
# This script helps set up the Robin MTA suite for the first time.
#
# Usage:
#   chmod +x scripts/setup.sh
#   ./scripts/setup.sh
#
# =============================================================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print functions
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Main setup
main() {
    echo ""
    echo "=========================================="
    echo "  Robin MTA Complete Suite - Setup"
    echo "=========================================="
    echo ""

    # Check prerequisites
    print_info "Checking prerequisites..."

    if ! command_exists docker; then
        print_error "Docker is not installed. Please install Docker first."
        exit 1
    fi
    print_success "Docker found: $(docker --version)"

    if ! command_exists docker-compose || ! docker compose version >/dev/null 2>&1; then
        print_error "Docker Compose is not installed. Please install Docker Compose first."
        exit 1
    fi
    print_success "Docker Compose found: $(docker compose version)"

    # Check if Robin MTA exists
    print_info "Checking for Robin MTA repository..."
    if [ ! -d "../transilvlad-robin" ]; then
        print_warning "Robin MTA not found at ../transilvlad-robin/"
        echo ""
        echo "Would you like to clone it now? (y/n)"
        read -r response
        if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
            print_info "Cloning Robin MTA..."
            cd ..
            git clone https://github.com/transilvlad/robin.git transilvlad-robin
            cd robin-ui
            print_success "Robin MTA cloned successfully"
        else
            print_error "Robin MTA is required. Please clone it manually to ../transilvlad-robin/"
            exit 1
        fi
    else
        print_success "Robin MTA found"
    fi

    # Create .env file
    print_info "Setting up environment variables..."
    if [ -f ".env" ]; then
        print_warning ".env file already exists. Backing up to .env.backup"
        cp .env .env.backup
    fi

    # Generate secure JWT secret
    print_info "Generating secure JWT secret..."
    if command_exists openssl; then
        JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
    else
        print_warning "openssl not found, using default JWT secret"
        JWT_SECRET="change-this-to-a-very-long-and-secure-random-secret-key-in-production"
    fi

    # Generate secure database password
    print_info "Generating secure database password..."
    if command_exists openssl; then
        DB_PASSWORD=$(openssl rand -base64 32 | tr -d '\n')
    else
        DB_PASSWORD="robin123"
    fi

    cat > .env <<EOF
# Robin MTA Complete Suite - Environment Configuration
# Generated on $(date)

# Database Configuration
DB_PASSWORD=${DB_PASSWORD}

# JWT Secret for Robin Gateway
JWT_SECRET=${JWT_SECRET}

# Optional: Override default ports if needed
# POSTGRES_PORT=5432
# REDIS_PORT=6379
# GATEWAY_PORT=8888
# UI_PORT=80
# SMTP_PORT=25
# SUBMISSION_PORT=587
# SMTPS_PORT=465
EOF

    print_success ".env file created"

    # Ask which environment to set up
    echo ""
    echo "Which environment would you like to set up?"
    echo "  1) Development (with hot reload and debugging)"
    echo "  2) Production (optimized build)"
    echo ""
    read -p "Enter choice [1-2]: " env_choice

    case $env_choice in
        1)
            COMPOSE_FILE="docker-compose.dev.full.yaml"
            print_info "Setting up development environment..."
            ;;
        2)
            COMPOSE_FILE="docker-compose.full.yaml"
            print_info "Setting up production environment..."
            ;;
        *)
            print_error "Invalid choice"
            exit 1
            ;;
    esac

    # Create necessary directories for Robin MTA
    print_info "Creating necessary directories..."
    mkdir -p ../transilvlad-robin/cfg
    mkdir -p ../transilvlad-robin/log
    mkdir -p ../transilvlad-robin/store
    print_success "Directories created"

    # Build images
    print_info "Building Docker images (this may take a while)..."
    docker compose -f "$COMPOSE_FILE" build

    print_success "Docker images built successfully"

    # Ask if user wants to start services
    echo ""
    echo "Would you like to start the services now? (y/n)"
    read -r start_services

    if [[ "$start_services" =~ ^([yY][eE][sS]|[yY])$ ]]; then
        print_info "Starting services..."
        docker compose -f "$COMPOSE_FILE" up -d

        echo ""
        print_success "Services started successfully!"
        echo ""
        print_info "Waiting for services to be healthy..."
        sleep 5

        # Check service health
        docker compose -f "$COMPOSE_FILE" ps

        echo ""
        echo "=========================================="
        echo "  Setup Complete!"
        echo "=========================================="
        echo ""
        if [ "$env_choice" == "1" ]; then
            print_success "Development environment is running"
            echo ""
            echo "Access points:"
            echo "  - Robin UI (dev):     http://localhost:4200"
            echo "  - Robin Gateway:      http://localhost:8888"
            echo "  - Robin MTA API:      http://localhost:8080"
            echo "  - Rspamd Web UI:      http://localhost:11334"
            echo ""
            echo "Debug ports:"
            echo "  - Robin MTA:          localhost:5005"
            echo "  - Robin Gateway:      localhost:5006"
        else
            print_success "Production environment is running"
            echo ""
            echo "Access points:"
            echo "  - Robin UI:           http://localhost"
            echo "  - Robin Gateway:      http://localhost:8888"
            echo "  - Rspamd Web UI:      http://localhost:11334"
        fi
        echo ""
        echo "Default login credentials:"
        echo "  - Username: admin@localhost"
        echo "  - Password: admin"
        echo "  (Change immediately in production!)"
        echo ""
        echo "View logs with:"
        echo "  docker compose -f $COMPOSE_FILE logs -f"
        echo ""
        echo "Stop services with:"
        echo "  docker compose -f $COMPOSE_FILE down"
        echo ""
    else
        echo ""
        print_info "Services not started. Start them manually with:"
        echo "  docker compose -f $COMPOSE_FILE up -d"
        echo ""
    fi

    print_success "Setup complete!"
}

# Run main
main "$@"
