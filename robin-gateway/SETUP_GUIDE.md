# Robin Gateway - Complete Setup Guide

Step-by-step guide to get Robin Gateway compliance framework up and running.

---

## 📋 Prerequisites

### Required Software

| Software | Min Version | Check Command | Install Command |
|----------|-------------|---------------|-----------------|
| **Java** | 21 | `java -version` | `brew install openjdk@21` |
| **Maven** | 3.9+ | `mvn --version` | `brew install maven` |
| **Git** | 2.x | `git --version` | `brew install git` |
| **Docker** | 20.x | `docker --version` | Install Docker Desktop |

### Optional but Recommended

| Software | Purpose | Install Command |
|----------|---------|-----------------|
| **Python** | Pre-commit hooks | `brew install python3` |
| **Pre-commit** | Git hooks | `pip install pre-commit` |
| **IntelliJ IDEA** | IDE | Download from JetBrains |
| **VS Code** | IDE | `brew install --cask visual-studio-code` |

---

## 🚀 Quick Start (5 Minutes)

### 1. Verify Prerequisites

```bash
# Check Java
java -version
# Expected: openjdk 21.x.x

# Check Maven
mvn --version
# Expected: Apache Maven 3.9.x or higher

# Check Docker
docker --version
# Expected: Docker version 20.x.x or higher

# Start Docker (if not running)
# macOS: Open Docker Desktop
# Linux: sudo systemctl start docker
```

### 2. Navigate to Project

```bash
cd /Users/cstan/development/workspace/open-source/robin-ui/robin-gateway
```

### 3. Run Verification

```bash
./verify-compliance.sh --quick
```

**Expected output**:
```
╔═══════════════════════════════════════════════╗
║   Robin Gateway - Compliance Verification     ║
╚═══════════════════════════════════════════════╝

✓ Maven found: Apache Maven 3.9.x
[1/7] Running tests...
  ✓ All tests passed
  Tests executed: X
...
```

### 4. View Coverage Report

```bash
mvn jacoco:report
open target/site/jacoco/index.html
```

---

## 📦 Detailed Setup

### Step 1: Install Java 21

#### macOS
```bash
# Install OpenJDK 21 via Homebrew
brew install openjdk@21

# Set JAVA_HOME (add to ~/.zshrc or ~/.bash_profile)
echo 'export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc

# Reload shell
source ~/.zshrc

# Verify
java -version
```

#### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install openjdk-21-jdk

# Verify
java -version
```

#### Windows
1. Download OpenJDK 21 from [Adoptium](https://adoptium.net/)
2. Run installer
3. Set JAVA_HOME environment variable
4. Add to PATH: `%JAVA_HOME%\bin`

---

### Step 2: Install Maven

#### macOS
```bash
brew install maven

# Verify
mvn --version
```

#### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install maven

# Verify
mvn --version
```

#### Windows
1. Download from [Maven Website](https://maven.apache.org/download.cgi)
2. Extract to C:\Program Files\Apache\maven
3. Add to PATH: `C:\Program Files\Apache\maven\bin`
4. Set M2_HOME: `C:\Program Files\Apache\maven`

---

### Step 3: Install Docker

#### macOS
```bash
# Install Docker Desktop
brew install --cask docker

# Or download from: https://www.docker.com/products/docker-desktop

# Start Docker Desktop (GUI)

# Verify
docker --version
docker ps
```

#### Linux (Ubuntu)
```bash
# Install Docker Engine
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add user to docker group
sudo usermod -aG docker $USER

# Log out and back in, then verify
docker --version
docker ps
```

#### Windows
1. Install Docker Desktop from [Docker Website](https://www.docker.com/products/docker-desktop)
2. Enable WSL 2 backend
3. Start Docker Desktop
4. Verify in PowerShell: `docker --version`

---

### Step 4: Install Pre-commit (Optional)

```bash
# Install Python 3 (if not installed)
brew install python3  # macOS
sudo apt install python3 python3-pip  # Linux

# Install pre-commit
pip install pre-commit

# Verify
pre-commit --version
```

---

### Step 5: Configure Git

```bash
# Set your name and email
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"

# Verify
git config --list | grep user
```

---

## 🔧 Project Setup

### Step 1: Clone Repository

```bash
# If you haven't already
git clone https://github.com/YOUR_USERNAME/robin-ui.git
cd robin-ui/robin-gateway
```

### Step 2: Install Dependencies

```bash
# Download all Maven dependencies
mvn clean install -DskipTests

# This will download:
# - Spring Boot dependencies
# - Testing frameworks
# - Compliance tools (Checkstyle, PMD, etc.)
```

**First build may take 5-10 minutes** to download dependencies.

### Step 3: Configure Environment

```bash
# Copy environment template (if exists)
cp .env.example .env  # If template exists

# Set required environment variables
export JWT_SECRET="your-secret-key-at-least-64-characters-long-for-hs512-security"
export ENCRYPTION_KEY="$(openssl rand -base64 32)"
export DB_HOST="localhost"
export DB_PORT="5433"
export DB_NAME="robin"
export DB_USER="robin"
export DB_PASSWORD="changeme"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export CORS_ALLOWED_ORIGINS="http://localhost:4200,http://localhost:8080"

# Make permanent (add to ~/.zshrc or ~/.bash_profile)
echo 'export JWT_SECRET="..."' >> ~/.zshrc
# ... etc
```

### Step 4: Setup Database (Local Development)

```bash
# Start PostgreSQL via Docker
docker run -d \
  --name robin-postgres \
  -p 5433:5432 \
  -e POSTGRES_DB=robin \
  -e POSTGRES_USER=robin \
  -e POSTGRES_PASSWORD=changeme \
  postgres:15

# Verify
docker ps | grep robin-postgres

# Test connection
docker exec -it robin-postgres psql -U robin -d robin -c "SELECT version();"
```

### Step 5: Setup Redis (Local Development)

```bash
# Start Redis via Docker
docker run -d \
  --name robin-redis \
  -p 6379:6379 \
  redis:7-alpine

# Verify
docker ps | grep robin-redis

# Test connection
docker exec -it robin-redis redis-cli ping
# Expected: PONG
```

---

## ✅ Verification

### Step 1: Run Tests

```bash
mvn test
```

**Expected**: All tests pass ✅

### Step 2: Check Coverage

```bash
mvn jacoco:report jacoco:check
```

**Expected**: Coverage ≥60% (or threshold set in pom.xml)

### Step 3: Run Compliance Checks

```bash
./verify-compliance.sh
```

**Expected**: All checks pass or warnings documented

### Step 4: Generate Reports

```bash
# Coverage report
mvn jacoco:report
open target/site/jacoco/index.html

# Checkstyle report
mvn checkstyle:checkstyle
open target/site/checkstyle.html

# PMD report
mvn pmd:pmd
open target/site/pmd.html
```

---

## 🎯 IDE Setup

### IntelliJ IDEA

#### 1. Install Lombok Plugin
1. Settings → Plugins
2. Search "Lombok"
3. Install and restart

#### 2. Enable Annotation Processing
1. Settings → Build, Execution, Deployment → Compiler → Annotation Processors
2. Check "Enable annotation processing"
3. Apply

#### 3. Import Code Style
1. Settings → Editor → Code Style → Java
2. Import Scheme → IntelliJ IDEA code style XML
3. Select `checkstyle.xml`
4. Apply

#### 4. Configure Maven
1. View → Tool Windows → Maven
2. Reload All Maven Projects
3. Verify no errors

#### 5. Run Configuration
```
Name: Robin Gateway
Main class: com.robin.gateway.GatewayApplication
VM options: -Dspring.profiles.active=dev
Environment variables: JWT_SECRET=...; DB_HOST=localhost; ...
Use classpath of module: gateway
```

### VS Code

#### 1. Install Extensions
```bash
# Java Extension Pack
code --install-extension vscjava.vscode-java-pack

# Spring Boot Extension Pack
code --install-extension pivotal.vscode-spring-boot

# Lombok
code --install-extension gabrielbb.vscode-lombok

# Checkstyle
code --install-extension shengchen.vscode-checkstyle
```

#### 2. Configure Settings
Create `.vscode/settings.json`:
```json
{
  "java.home": "/opt/homebrew/opt/openjdk@21",
  "java.configuration.runtimes": [
    {
      "name": "JavaSE-21",
      "path": "/opt/homebrew/opt/openjdk@21",
      "default": true
    }
  ],
  "java.checkstyle.configuration": "${workspaceFolder}/checkstyle.xml",
  "editor.tabSize": 4,
  "editor.insertSpaces": true,
  "files.trimTrailingWhitespace": true
}
```

#### 3. Run Configuration
Create `.vscode/launch.json`:
```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Robin Gateway",
      "request": "launch",
      "mainClass": "com.robin.gateway.GatewayApplication",
      "projectName": "gateway",
      "args": "",
      "env": {
        "SPRING_PROFILES_ACTIVE": "dev",
        "JWT_SECRET": "your-secret-key",
        "DB_HOST": "localhost"
      }
    }
  ]
}
```

---

## 🪝 Pre-commit Hooks Setup

```bash
# Install pre-commit
pip install pre-commit

# Install hooks
pre-commit install

# Test hooks
pre-commit run --all-files

# Expected: All hooks pass ✅
```

If hooks fail, see `docs/TROUBLESHOOTING.md`.

---

## 🔍 Common Setup Issues

### Issue 1: Java Version Mismatch

**Error:**
```
[ERROR] Source option 21 is no longer supported. Use 21 or later.
```

**Solution:**
```bash
# Check Java version
java -version

# If wrong version, set JAVA_HOME
export JAVA_HOME="/path/to/openjdk-21"
export PATH="$JAVA_HOME/bin:$PATH"

# Verify
java -version
# Should show: openjdk 21.x.x
```

### Issue 2: Docker Not Running

**Error:**
```
Cannot connect to the Docker daemon
```

**Solution:**
```bash
# Start Docker Desktop (macOS)
open -a Docker

# Or start Docker service (Linux)
sudo systemctl start docker

# Verify
docker ps
```

### Issue 3: Port Already in Use

**Error:**
```
Port 8080 already in use
```

**Solution:**
```bash
# Find process using port 8080
lsof -i :8080

# Kill process
kill -9 <PID>

# Or change port in application.yml
server:
  port: 8081
```

### Issue 4: Maven Dependencies Fail

**Error:**
```
Could not resolve dependencies
```

**Solution:**
```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Retry with debug output
mvn clean install -U -X

# Check ~/.m2/settings.xml for proxy issues
```

---

## 📊 Baseline Metrics Collection

After setup, collect baseline metrics:

```bash
# 1. Run full compliance suite
./verify-compliance.sh

# 2. Copy template
cp docs/BASELINE_METRICS_TEMPLATE.md docs/BASELINE_METRICS.md

# 3. Fill in actual values
# - Test results: From mvn test output
# - Coverage: From target/site/jacoco/index.html
# - Violations: From checkstyle/PMD reports
# - Security: From OWASP report

# 4. Commit baseline
git add docs/BASELINE_METRICS.md
git commit -m "docs: add baseline compliance metrics"
```

---

## 🎓 Next Steps

After successful setup:

1. **Read Documentation**
   - [COMPLIANCE_README.md](COMPLIANCE_README.md)
   - [docs/COMPLIANCE_QUICK_START.md](docs/COMPLIANCE_QUICK_START.md)
   - [docs/DEVELOPER_CHECKLIST.md](docs/DEVELOPER_CHECKLIST.md)

2. **Explore Codebase**
   - Review package structure
   - Read `ArchitectureTest.java`
   - Understand existing patterns

3. **Run Example Commands**
   ```bash
   # Run tests
   mvn test

   # Check coverage
   mvn jacoco:report && open target/site/jacoco/index.html

   # Run architecture tests
   mvn test -Dtest=ArchitectureTest

   # Full compliance check
   ./verify-compliance.sh
   ```

4. **Make First Contribution**
   - Pick an issue from `docs/GAP_TRACKING.md`
   - Follow [CONTRIBUTING.md](CONTRIBUTING.md)
   - Submit a pull request

---

## 🆘 Getting Help

**Documentation**:
- Quick Start: [COMPLIANCE_README.md](COMPLIANCE_README.md)
- Troubleshooting: [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)
- Developer Guide: [docs/COMPLIANCE_QUICK_START.md](docs/COMPLIANCE_QUICK_START.md)

**Common Issues**:
- See [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)
- Check GitHub Issues
- Search Stack Overflow

**Get Support**:
- GitHub Issues (bugs/features)
- GitHub Discussions (questions)
- Team Slack (quick help)

---

## ✅ Setup Checklist

Use this checklist to verify complete setup:

### Prerequisites
- [ ] Java 21 installed and configured
- [ ] Maven 3.9+ installed
- [ ] Git installed and configured
- [ ] Docker installed and running
- [ ] Pre-commit installed (optional)

### Project Setup
- [ ] Repository cloned
- [ ] Dependencies installed (`mvn clean install`)
- [ ] Environment variables configured
- [ ] PostgreSQL running (Docker or local)
- [ ] Redis running (Docker or local)

### IDE Setup
- [ ] IDE installed (IntelliJ or VS Code)
- [ ] Lombok plugin installed
- [ ] Annotation processing enabled
- [ ] Code style configured
- [ ] Run configuration created

### Verification
- [ ] Tests pass (`mvn test`)
- [ ] Coverage meets threshold (`mvn jacoco:check`)
- [ ] Compliance checks pass (`./verify-compliance.sh`)
- [ ] Pre-commit hooks work (`pre-commit run --all-files`)
- [ ] Baseline metrics documented

### Documentation
- [ ] Read COMPLIANCE_README.md
- [ ] Read CONTRIBUTING.md
- [ ] Read docs/COMPLIANCE_QUICK_START.md
- [ ] Bookmarked docs/TROUBLESHOOTING.md

---

**Setup complete! You're ready to contribute to Robin Gateway.** 🎉

---

**Last Updated**: 2026-02-06
**Version**: 1.0
**Estimated Time**: 30-60 minutes (first time)
