# Robin UI + Gateway - Documentation Index

**Last Updated**: 2026-01-29
**Project Status**: Gateway 98%, UI 98% Complete

---

## 📚 Documentation Structure

This documentation suite provides comprehensive guidance for developing, deploying, and operating Robin UI and Gateway.

---

## 🚀 Getting Started

### 🐳 Docker Setup (Recommended - 5 Minutes)

**Complete Suite with One Command**:
```bash
# Run the automated setup script
chmod +x scripts/setup.sh
./scripts/setup.sh
```

**Access the Application**:
- **Robin UI**: http://localhost (production) or http://localhost:4200 (development)
- **Robin Gateway**: http://localhost:8888
- **Rspamd Admin**: http://localhost:11334
- **Default Credentials**: `admin@localhost` / `admin`

**Docker Documentation**:
- **[Quick Start - Docker](./QUICKSTART_DOCKER.md)** ⭐ NEW - Get started in 5 minutes
- **[Docker Setup Guide](./DOCKER_SETUP.md)** - Complete Docker documentation
- **[Docker Architecture](./DOCKER_ARCHITECTURE.md)** - Architecture deep dive
- **[Port Reference](./PORTS_REFERENCE.md)** - All ports and endpoints

**What You Get**:
- ✅ Robin UI (Angular frontend)
- ✅ Robin Gateway (API Gateway + Auth)
- ✅ Robin MTA (Mail server)
- ✅ PostgreSQL (Database)
- ✅ Redis (Cache)
- ✅ ClamAV (Antivirus)
- ✅ Rspamd (Spam detection)

---

### Manual Development Setup (30 Seconds)

**Start the system**:
```bash
# 1. Start Robin MTA Suite
cd ~/development/workspace/open-source/transilvlad-robin/.suite
docker-compose up -d

# 2. Start Gateway
cd ~/development/workspace/open-source/robin-ui/robin-gateway/docker
docker-compose up -d

# 3. Test
curl http://localhost:8080/api/v1/health/aggregate
```

**Default Credentials**: `admin@robin.local` / `admin123`

**OpenAPI Docs**: http://localhost:8080/swagger-ui.html

For detailed setup instructions, see [QUICK_START.md](./QUICK_START.md)

---

## 📖 Core Documentation

### ⭐ NEW: Master Development Plan (1,324 lines)

**File**: [MASTER_DEVELOPMENT_PLAN.md](./MASTER_DEVELOPMENT_PLAN.md)

**Purpose**: Single source of truth for all development activities. Contains everything needed to continue development in future sessions without investigation.

**Contents**:
1. Quick Start Guide - 30-second setup
2. Project Status & Progress - Current state (98% complete)
3. Architecture Overview - System design
4. **API Endpoint Reference** - All endpoints documented
5. **Data Models & Database Schema** - Complete schema with ERD
6. **Error Code Catalog** - All error codes with resolutions
7. **Configuration Reference** - All config parameters
8. **Implementation Guides** - Step-by-step instructions
9. Testing Strategy - Unit, integration, E2E
10. **Deployment Procedures** - Production deployment
11. **Troubleshooting Runbook** - Common issues & solutions
12. **Monitoring & Alerting** - Prometheus setup
13. **Common Commands** - Cheat sheet
14. Appendices - Related docs

**Use this document for**: Understanding the complete system, implementing features, troubleshooting, deployment

---

### ⭐ NEW: API Reference (1,117 lines)

**File**: [API_REFERENCE.md](./API_REFERENCE.md)

**Purpose**: Complete REST API documentation with examples for all 25+ endpoints.

**Contents**:
- **Authentication Endpoints** (4 endpoints)
  - POST /api/v1/auth/login
  - POST /api/v1/auth/refresh
  - POST /api/v1/auth/logout
  - GET /api/v1/auth/me

- **Domain Management** (5 endpoints)
  - GET /api/v1/domains
  - GET /api/v1/domains/{id}
  - POST /api/v1/domains
  - DELETE /api/v1/domains/{id}
  - GET /api/v1/domains/{id}/aliases

- **Alias Management** (6 endpoints)
  - GET /api/v1/domains/aliases
  - GET /api/v1/domains/aliases/{id}
  - POST /api/v1/domains/aliases
  - PUT /api/v1/domains/aliases/{id}
  - DELETE /api/v1/domains/aliases/{id}

- **Health** (1 endpoint)
  - GET /api/v1/health/aggregate

- **Configuration** (8 endpoints, proxied)
  - GET/PUT /api/v1/config/{storage,queue,relay,etc.}

**Each endpoint includes**:
- Request/response schemas
- Authentication requirements
- Authorization (RBAC)
- Rate limiting
- Error responses with codes
- cURL examples
- JavaScript Fetch examples
- Implementation file reference

**Use this document for**: API integration, client development, testing

---

### Development Plan (1,517 lines)

**File**: [DEVELOPMENT_PLAN.md](./DEVELOPMENT_PLAN.md)

**Purpose**: Original comprehensive 7-week development roadmap created at project start.

**Contents**:
- Executive summary
- 7-phase implementation plan
- Architecture diagrams
- Technology stack decisions
- Critical integration points
- Testing strategy
- Risk assessment
- Success criteria
- Timeline (7 weeks / 35 days)

**Status**: Mostly superseded by MASTER_DEVELOPMENT_PLAN.md, but retained for historical reference and original timeline.

**Use this document for**: Understanding original planning, architectural decisions, timeline estimation

---

### Implementation Progress (680 lines)

**File**: [IMPLEMENTATION_PROGRESS.md](./IMPLEMENTATION_PROGRESS.md)

**Purpose**: Real-time progress tracking updated after each development session.

**Contents**:
- ✅ Completed tasks (Phase 1 & 2: 98% each)
- 🔄 In-progress tasks
- 📋 Next steps (priority order)
- 🎯 Success metrics
- 📁 Files modified/created (27 files)
- 🧪 Testing commands
- 📝 Architecture notes
- 🎉 Achievement summaries

**Latest Update**: 2026-01-29 (Integration tests complete)

**Use this document for**: Tracking progress, understanding what's been done, planning next session

---

### Authentication Implementation Plan (60KB)

**File**: [AUTH_IMPLEMENTATION_PLAN.md](./AUTH_IMPLEMENTATION_PLAN.md)

**Purpose**: Deep dive into authentication architecture, covering both Gateway (Spring Security + JWT) and UI (@ngrx/signals).

**Contents**:
- JWT authentication flow
- HttpOnly cookie strategy
- Spring Security configuration
- @ngrx/signals state management
- Token refresh mechanism
- RBAC implementation
- Security best practices
- Testing strategy
- 100+ code examples

**Use this document for**: Understanding auth architecture, implementing auth features, security audits

---

## 🔧 Component-Specific Documentation

### Gateway (Spring Boot)

**Directory**: [gateway/](./gateway/)

**Files**:
- **Architecture Overview** - Spring WebFlux, reactive patterns
- **Database Schema** - Flyway migrations, entity relationships
- **Security Configuration** - JWT, CORS, rate limiting
- **Circuit Breakers** - Resilience4j configuration
- **Testing** - 69 integration tests with TestContainers

**Technologies**: Spring Boot 3.2.2, Java 21, PostgreSQL 15, Redis 7, WebFlux, JWT

---

### UI (Angular)

**Directory**: [ui/](./ui/)

**Files**:
- **Architecture Overview** - Angular 21 signals, standalone components
- **State Management** - @ngrx/signals (replaces traditional NgRx)
- **Authentication** - JWT handling, token refresh
- **Component Library** - Angular Material
- **Testing** - Unit tests with Jasmine/Karma

**Technologies**: Angular 21, TypeScript 5.x, @ngrx/signals, Zod, Angular Material, Tailwind CSS

---

## 🛠️ Reference Documentation

### Configuration Reference

**Location**: [MASTER_DEVELOPMENT_PLAN.md#configuration-reference](./MASTER_DEVELOPMENT_PLAN.md#configuration-reference)

**Contents**:
- Environment variables (11 required variables)
- Spring Boot configuration (YAML)
- JWT settings
- Rate limiting configuration
- Circuit breaker configuration
- Port mapping (6 services)

---

### Error Code Catalog

**Location**: [MASTER_DEVELOPMENT_PLAN.md#error-code-catalog](./MASTER_DEVELOPMENT_PLAN.md#error-code-catalog)

**Contents**:
- Authentication errors (AUTH_0xx) - 7 codes
- Domain management errors (DOMAIN_0xx) - 7 codes
- Validation errors (VALIDATION_0xx) - 5 codes
- System errors (SYSTEM_0xx) - 6 codes

**Each error includes**:
- HTTP status code
- Error code
- Message
- Cause
- Resolution steps

---

### Database Schema Reference

**Location**: [MASTER_DEVELOPMENT_PLAN.md#data-models--database-schema](./MASTER_DEVELOPMENT_PLAN.md#data-models--database-schema)

**Contents**:
- Entity relationship diagram
- **users** table (11 fields)
- **sessions** table (8 fields)
- **domains** table (4 fields)
- **aliases** table (6 fields)
- Business rules for each table
- Migration files (Flyway)

---

## 📋 Operational Documentation

### Troubleshooting Runbook

**Location**: [MASTER_DEVELOPMENT_PLAN.md#troubleshooting-runbook](./MASTER_DEVELOPMENT_PLAN.md#troubleshooting-runbook)

**Common Issues**:
- 🔴 Gateway fails to start - PostgreSQL connection refused
- 🟡 Authentication fails - Invalid JWT signature
- 🟢 Rate limiting - 429 Too Many Requests

**Each issue includes**:
- Symptoms
- Diagnosis steps
- Resolution options
- Prevention tips

---

### Deployment Procedures

**Location**: [MASTER_DEVELOPMENT_PLAN.md#deployment-procedures](./MASTER_DEVELOPMENT_PLAN.md#deployment-procedures)

**Contents**:
- Production configuration
- Docker deployment
- Environment variables
- Health checks
- Database backups
- Nginx reverse proxy (optional)

---

### Monitoring & Alerting

**Location**: [MASTER_DEVELOPMENT_PLAN.md#monitoring--alerting](./MASTER_DEVELOPMENT_PLAN.md#monitoring--alerting)

**Contents**:
- Prometheus scraping configuration
- Key metrics to monitor
- Grafana dashboards
- Alerting rules

---

## 🧪 Testing Documentation

### Integration Tests (69 tests)

**Location**: `robin-gateway/src/test/java/com/robin/gateway/integration/`

**Test Suites**:
1. **AuthIntegrationTest** (11 tests) - Login, refresh, logout flows
2. **DomainManagementIntegrationTest** (15 tests) - Domain/alias CRUD
3. **HealthAggregationIntegrationTest** (10 tests) - Health monitoring
4. **CorsIntegrationTest** (13 tests) - CORS configuration
5. **CircuitBreakerIntegrationTest** (10 tests) - Resilience patterns
6. **RateLimitingIntegrationTest** (10 tests) - Rate limiting

**Infrastructure**: TestContainers (PostgreSQL 15 + Redis 7)

**Coverage**: 85%+ estimated

---

### Unit Tests

**Gateway** (JUnit 5 + Mockito):
- Target: 80% coverage
- Service layer tests
- Controller tests
- Security tests

**UI** (Jasmine + Karma):
- Target: 70% coverage
- 7 comprehensive test files
- ~2,310 lines of test code
- Coverage: 80-90%

---

## 🎓 Implementation Guides

### Adding a New REST Endpoint

**Location**: [MASTER_DEVELOPMENT_PLAN.md#guide-1-adding-a-new-rest-endpoint-to-gateway](./MASTER_DEVELOPMENT_PLAN.md#guide-1-adding-a-new-rest-endpoint-to-gateway)

**Steps** (10 steps, 30-60 minutes):
1. Define specification
2. Create DTOs
3. Add repository method
4. Create service method
5. Add controller endpoint
6. Add OpenAPI documentation
7. Write integration test
8. Test manually
9. Update documentation
10. Commit changes

**Includes**: Complete code examples for each step

---

### Other Guides (Planned)

- Adding a New Angular Component
- Implementing a New Feature Module
- Adding Database Migrations
- Configuring Circuit Breakers
- Setting Up Rate Limiting
- Implementing Real-Time Updates (WebSocket)

---

## 📊 Project Metrics

### Code Statistics

| Metric | Gateway | UI | Total |
|--------|---------|-----|-------|
| Lines of Code | ~3,500 | ~4,200 | ~7,700 |
| Files Created | 27 | 22 | 49 |
| Integration Tests | 69 | - | 69 |
| Unit Tests | - | 7 files | ~2,310 lines |
| Test Coverage | 85%+ | 80%+ | ~82% |

### Documentation Statistics

| Document | Lines | Status |
|----------|-------|--------|
| MASTER_DEVELOPMENT_PLAN.md | 1,324 | ✅ Complete |
| API_REFERENCE.md | 1,117 | ✅ Complete |
| DEVELOPMENT_PLAN.md | 1,517 | ✅ Complete |
| IMPLEMENTATION_PROGRESS.md | 680 | 🔄 Updated 2026-01-29 |
| AUTH_IMPLEMENTATION_PLAN.md | ~1,700 (60KB) | ✅ Complete |
| **Total Documentation** | **6,338+ lines** | ✅ Comprehensive |

---

## 🗂️ File Organization

```
docs/
├── README.md                           # This file
│
├── 🐳 Docker Deployment (NEW)
│   ├── QUICKSTART_DOCKER.md            ⭐ 5-minute setup
│   ├── DOCKER_SETUP.md                 # Complete Docker guide
│   ├── DOCKER_ARCHITECTURE.md          # Architecture deep dive
│   └── PORTS_REFERENCE.md              # Port mapping reference
│
├── 📚 Development Documentation
│   ├── MASTER_DEVELOPMENT_PLAN.md      ⭐ Complete reference (1,324 lines)
│   ├── API_REFERENCE.md                ⭐ All endpoints (1,117 lines)
│   ├── DEVELOPMENT_PLAN.md             # Original 7-week plan (1,517 lines)
│   ├── IMPLEMENTATION_PROGRESS.md      # Current progress (680 lines)
│   ├── AUTH_IMPLEMENTATION_PLAN.md     # Auth architecture (60KB)
│   └── QUICK_START.md                  # Quick start guide
│
├── gateway/                            # Gateway-specific docs
│   ├── architecture.md
│   ├── database.md
│   ├── security.md
│   └── testing.md
│
└── ui/                                 # UI-specific docs
    ├── architecture.md
    ├── state-management.md
    ├── components.md
    └── testing.md
```

---

## 🚀 Quick Command Reference

### Development

```bash
# Gateway
cd robin-gateway
./mvnw spring-boot:run
./mvnw test

# UI
cd robin-ui
npm start
npm test
```

### Docker

```bash
# Start full stack
docker-compose up -d

# View logs
docker logs -f robin-gateway

# Stop all
docker-compose down
```

### Testing

```bash
# Integration tests
./mvnw test -Dtest="**/*IntegrationTest"

# Coverage report
./mvnw jacoco:report
open target/site/jacoco/index.html
```

---

## 📞 Support & Contact

**Issues**: [GitHub Issues](https://github.com/your-org/robin-ui/issues)
**Email**: transilvlad@gmail.com, https://github.com/foxglovelabs

---

## 📝 Document Maintenance

### Update Frequency

- **MASTER_DEVELOPMENT_PLAN.md**: Update when adding major features
- **API_REFERENCE.md**: Update when endpoints change
- **IMPLEMENTATION_PROGRESS.md**: Update after each session
- **This README**: Update when documentation structure changes

### Version Control

All documentation is version-controlled with the codebase. Major documentation updates should be committed with clear commit messages.

---

**Documentation Version**: 2.0.0
**Last Updated**: 2026-01-29
**Next Review**: 2026-02-05

---

## 📖 Reading Path

### For Quick Deployment

1. Read [Quick Start - Docker](./QUICKSTART_DOCKER.md) (5 minutes)
2. Run setup script (5 minutes)
3. Review [Port Reference](./PORTS_REFERENCE.md) (5 minutes)

**Total Time**: ~15 minutes to running system

### For New Developers

1. Start with [Quick Start - Docker](./QUICKSTART_DOCKER.md) (5 minutes)
2. Read [MASTER_DEVELOPMENT_PLAN.md](./MASTER_DEVELOPMENT_PLAN.md) sections 1-3 (30 minutes)
3. Review [API_REFERENCE.md](./API_REFERENCE.md) (30 minutes)
4. Check [IMPLEMENTATION_PROGRESS.md](./IMPLEMENTATION_PROGRESS.md) (10 minutes)
5. Explore component-specific docs as needed

**Total Time**: ~75 minutes to full context

### For API Integration

1. Read [API_REFERENCE.md](./API_REFERENCE.md) (30 minutes)
2. Review authentication section in [AUTH_IMPLEMENTATION_PLAN.md](./AUTH_IMPLEMENTATION_PLAN.md) (20 minutes)
3. Check [Error Code Catalog](./MASTER_DEVELOPMENT_PLAN.md#error-code-catalog) (10 minutes)

**Total Time**: ~60 minutes to API proficiency

### For Operations/DevOps

1. Read [Deployment Procedures](./MASTER_DEVELOPMENT_PLAN.md#deployment-procedures) (15 minutes)
2. Review [Troubleshooting Runbook](./MASTER_DEVELOPMENT_PLAN.md#troubleshooting-runbook) (20 minutes)
3. Set up [Monitoring & Alerting](./MASTER_DEVELOPMENT_PLAN.md#monitoring--alerting) (30 minutes)

**Total Time**: ~65 minutes to operational readiness
