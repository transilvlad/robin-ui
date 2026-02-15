# Robin Gateway - Makefile
#
# Common commands for development workflow
# Usage: make <target>

.PHONY: help install test coverage compliance clean build run docker-up docker-down verify quick

# Default target: show help
help:
	@echo "╔═══════════════════════════════════════════════════════════════╗"
	@echo "║              Robin Gateway - Available Commands               ║"
	@echo "╚═══════════════════════════════════════════════════════════════╝"
	@echo ""
	@echo "Setup Commands:"
	@echo "  make install        Install Maven dependencies"
	@echo "  make setup          Complete first-time setup"
	@echo ""
	@echo "Development Commands:"
	@echo "  make test           Run all tests"
	@echo "  make coverage       Generate coverage report"
	@echo "  make verify         Run full compliance checks"
	@echo "  make quick          Run quick compliance checks"
	@echo "  make clean          Clean build artifacts"
	@echo "  make build          Build the project"
	@echo ""
	@echo "Quality Commands:"
	@echo "  make checkstyle     Run Checkstyle checks"
	@echo "  make pmd            Run PMD checks"
	@echo "  make spotbugs       Run SpotBugs checks"
	@echo "  make owasp          Run OWASP security scan"
	@echo "  make arch           Run architecture tests"
	@echo ""
	@echo "Docker Commands:"
	@echo "  make docker-up      Start PostgreSQL and Redis"
	@echo "  make docker-down    Stop all Docker containers"
	@echo ""
	@echo "Documentation:"
	@echo "  make docs           Open documentation index"
	@echo "  make coverage-html  Open coverage report in browser"
	@echo ""

# Setup Commands
install:
	@echo "📦 Installing Maven dependencies..."
	mvn clean install -DskipTests

setup: install
	@echo "🔧 Setting up pre-commit hooks..."
	@command -v pre-commit >/dev/null 2>&1 && pre-commit install || echo "⚠️  pre-commit not installed. Run: pip install pre-commit"
	@echo "✅ Setup complete!"
	@echo ""
	@echo "Next steps:"
	@echo "  1. Run: make verify"
	@echo "  2. Read: COMPLIANCE_README.md"

# Development Commands
test:
	@echo "🧪 Running tests..."
	mvn test

coverage:
	@echo "📊 Generating coverage report..."
	mvn clean test jacoco:report

coverage-html: coverage
	@echo "🌐 Opening coverage report..."
	@open target/site/jacoco/index.html || xdg-open target/site/jacoco/index.html

clean:
	@echo "🧹 Cleaning build artifacts..."
	mvn clean

build:
	@echo "🏗️  Building project..."
	mvn clean package

run:
	@echo "🚀 Starting Robin Gateway..."
	mvn spring-boot:run

# Quality Commands
checkstyle:
	@echo "✏️  Running Checkstyle..."
	mvn checkstyle:check

pmd:
	@echo "🔍 Running PMD..."
	mvn pmd:check

spotbugs:
	@echo "🐛 Running SpotBugs..."
	mvn spotbugs:check

owasp:
	@echo "🔒 Running OWASP Dependency Check (this may take 5-10 minutes)..."
	mvn org.owasp:dependency-check-maven:check

arch:
	@echo "🏛️  Running architecture tests..."
	mvn test -Dtest=ArchitectureTest

# Compliance Commands
verify:
	@echo "✅ Running full compliance verification..."
	./verify-compliance.sh

quick:
	@echo "⚡ Running quick compliance checks..."
	./verify-compliance.sh --quick

compliance: verify

# Docker Commands
docker-up:
	@echo "🐳 Starting PostgreSQL and Redis..."
	@docker ps -q -f name=robin-postgres >/dev/null 2>&1 || \
		docker run -d --name robin-postgres \
			-p 5433:5432 \
			-e POSTGRES_DB=robin \
			-e POSTGRES_USER=robin \
			-e POSTGRES_PASSWORD=changeme \
			postgres:15
	@docker ps -q -f name=robin-redis >/dev/null 2>&1 || \
		docker run -d --name robin-redis \
			-p 6379:6379 \
			redis:7-alpine
	@echo "✅ Docker containers started"
	@echo "  - PostgreSQL: localhost:5433"
	@echo "  - Redis: localhost:6379"

docker-down:
	@echo "🛑 Stopping Docker containers..."
	@docker stop robin-postgres robin-redis 2>/dev/null || true
	@docker rm robin-postgres robin-redis 2>/dev/null || true
	@echo "✅ Docker containers stopped"

docker-logs:
	@echo "📋 Docker container logs:"
	@docker logs robin-postgres --tail 20 2>/dev/null || echo "PostgreSQL not running"
	@docker logs robin-redis --tail 20 2>/dev/null || echo "Redis not running"

# Documentation
docs:
	@echo "📚 Opening documentation..."
	@open docs/INDEX.md || xdg-open docs/INDEX.md || cat docs/INDEX.md

readme:
	@echo "📖 Opening README..."
	@open COMPLIANCE_README.md || xdg-open COMPLIANCE_README.md || cat COMPLIANCE_README.md

# Git Commands
commit-check:
	@echo "✅ Pre-commit checks..."
	@./verify-compliance.sh --quick

pr-check:
	@echo "✅ Pre-PR checks..."
	@./verify-compliance.sh

# Utility Commands
version:
	@echo "📌 Version Information:"
	@echo "  Java:    $$(java -version 2>&1 | head -1)"
	@echo "  Maven:   $$(mvn --version | head -1)"
	@echo "  Docker:  $$(docker --version 2>/dev/null || echo 'Not installed')"
	@echo "  Git:     $$(git --version)"

status:
	@echo "📊 Project Status:"
	@echo ""
	@./verify-compliance.sh --quick 2>/dev/null || echo "Run 'make install' first"

# Combined workflows
pre-commit: clean test checkstyle
	@echo "✅ Pre-commit checks passed!"

pre-pr: clean test coverage checkstyle pmd spotbugs arch
	@echo "✅ Pre-PR checks passed!"

all: clean build test coverage verify
	@echo "✅ All checks passed!"

# Development workflow shortcuts
dev: docker-up test
	@echo "✅ Development environment ready"
	@echo "Run 'make run' to start the application"

# First time setup
first-time:
	@echo "🎉 First Time Setup"
	@echo ""
	@echo "📦 Step 1/4: Installing dependencies..."
	@make install
	@echo ""
	@echo "🐳 Step 2/4: Starting Docker containers..."
	@make docker-up
	@echo ""
	@echo "🧪 Step 3/4: Running tests..."
	@make test
	@echo ""
	@echo "✅ Step 4/4: Running compliance checks..."
	@make quick
	@echo ""
	@echo "🎉 Setup complete!"
	@echo ""
	@echo "📚 Next steps:"
	@echo "  1. Read: COMPLIANCE_README.md"
	@echo "  2. Run: make docs"
	@echo "  3. Run: make run (to start application)"

.DEFAULT_GOAL := help
