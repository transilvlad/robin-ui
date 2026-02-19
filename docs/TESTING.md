# Robin Testing Strategy

This document outlines the testing methodology and procedures for the Robin UI and Robin Gateway.

## 1. Testing Pyramid

The project follows a multi-layered testing strategy:

1. **Unit Tests**: Fast, isolated tests for business logic and components.
2. **Integration Tests**: Verify interactions between components and external systems (Database, Redis, MTA).
3. **E2E Tests**: Complete user flow verification using Cypress.
4. **Performance Tests**: Benchmarks for throughput and latency.

## 2. Robin Gateway (Backend)

### 2.1 Unit Tests
Located in `robin-gateway/src/test/java/com/robin/gateway/service/` and `.../controller/`.
- **Framework**: JUnit 5, Mockito.
- **Run command**: 
  ```bash
  cd robin-gateway
  mvn test
  ```

### 2.2 Integration Tests
Located in `robin-gateway/src/test/java/com/robin/gateway/integration/`.
- **Technology**: Testcontainers (Postgres, Redis).
- **Tag**: `@Tag("docker-integration")`
- **Run command**:
  ```bash
  cd robin-gateway
  mvn test -Dgroups=docker-integration
  ```
*Note: These tests require a local Docker daemon.*

### 2.3 Performance Tests
Located in `robin-gateway/src/test/java/com/robin/gateway/performance/`.
- **Technology**: Mock backend servers, throughput measurements.
- **Run command**:
  ```bash
  cd robin-gateway
  mvn test -Dtest=GatewayPerformanceTest -Dgroups=docker-integration
  ```

### 2.4 Security Scanning
- **OWASP Dependency Check**: Scans for known vulnerabilities (CVEs).
- **Run command**:
  ```bash
  cd robin-gateway
  mvn org.owasp:dependency-check-maven:check
  ```

## 3. Robin UI (Frontend)

### 3.1 Unit Tests
- **Framework**: Jasmine/Karma.
- **Run command**:
  ```bash
  npm test
  ```

### 3.2 End-to-End (E2E) Tests
- **Framework**: Cypress.
- **Location**: `cypress/e2e/`.
- **Run command (Interactive)**:
  ```bash
  npm run test:e2e
  ```
- **Run command (Headless)**:
  ```bash
  npm run test:e2e:headless
  ```

## 4. CI/CD Pipeline

The project uses GitHub Actions for automated verification:
- `gateway-compliance.yml`: Runs Gateway unit tests, integration tests, checkstyle, PMD, SpotBugs, and security scans.
- `e2e-tests.yml`: Runs full stack UI tests.

## 5. Coverage Requirements

The target code coverage for the Gateway is **60%** (enforced by JaCoCo).
To generate a local coverage report:
```bash
cd robin-gateway
mvn jacoco:report
# Open target/site/jacoco/index.html
```

---

**Author**: Robin Development Team
**Date**: 2026-02-15
