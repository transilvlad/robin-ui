# Domain Management Addon — Task Registry

> **Multi-session coordination file.** See full technical plan in `domain-management-plan.md`.
>
> **Rules for every session:**
> 1. Read this file first — check what is `IN_PROGRESS` before picking up work
> 2. Claim a task: set `Status` → `IN_PROGRESS`, fill your `Session` ID, commit this file
> 3. Mark `DONE` (add commit SHA) when finished, commit this file again
> 4. Mark `BLOCKED` with a reason if you cannot proceed
> 5. Never start a task whose dependencies are not `DONE`
> 6. Never claim a task already `IN_PROGRESS`
>
> **Legend:** `TODO` · `IN_PROGRESS` · `DONE` · `BLOCKED`

---

## Session Log

| Session ID | Date | Tasks Claimed | Notes |
|------------|------|--------------|-------|
| SESS-001 | 2026-02-24 | DM-001, DM-002, DM-010, DM-011, DM-012, DM-013, DM-014, DM-015, DM-016 | Initializing domain management addon. |
| SESS-002 | 2026-02-24 | DM-020, DM-021, DM-022, DM-023, DM-024, DM-030, DM-031, DM-032, DM-034, DM-040, DM-041, DM-042, DM-050, DM-051 | API clients, DKIM pipeline, MTA-STS worker, Health Check. |
| 4575e668 | 2026-02-24 | DM-002 | Ran Flyway migration in Docker, verified all 6 tables + domain columns. Commit: 9cfb80b |
| SESS-003 | 2026-02-24 | DM-033 | Added robin-mta DKIM config endpoint with file write + reload trigger. |

---

## PHASE 1 — Database Migration

> **Can start immediately. Blocks everything else.**

| ID | Task | Status | Session | Depends On | Commit SHA |
|----|------|:------:|---------|:----------:|------------|
| DM-001 | Write `V4__domain_management_extension.sql` | DONE | SESS-001 | — | 9cfb80b |
| DM-002 | Run migration + verify schema (`./mvnw flyway:migrate`) | DONE | 4575e668 | DM-001 | 9cfb80b |

**Files:** `robin-gateway/src/main/resources/db/migration/V4__domain_management_extension.sql`

---

## PHASE 2 — Backend Entities & Repositories

> **All DM-01x tasks are independent — assign to separate sessions in parallel.**

| ID | Task | Status | Session | Depends On | Commit SHA |
|----|------|:------:|---------|:----------:|------------|
| DM-010 | `DnsProvider.java` entity + `DnsProviderRepository.java` | DONE | SESS-001 | DM-001 | — |
| DM-011 | `DomainDnsRecord.java` entity + `DomainDnsRecordRepository.java` | DONE | SESS-001 | DM-001 | — |
| DM-012 | `DnsTemplate.java` entity + `DnsTemplateRepository.java` | DONE | SESS-001 | DM-001 | — |
| DM-013 | `DkimKey.java` entity + `DkimKeyRepository.java` | DONE | SESS-001 | DM-001 | — |
| DM-014 | `DomainHealth.java` entity + `DomainHealthRepository.java` | DONE | SESS-001 | DM-001 | — |
| DM-015 | `MtaStsWorker.java` entity + `MtaStsWorkerRepository.java` | DONE | SESS-001 | DM-001 | — |
| DM-016 | Update `Domain.java` — add dnsProviderId, nsProviderId, status, lastHealthCheck, updatedAt | DONE | SESS-001 | DM-001 | — |

**Files:** `robin-gateway/src/main/java/com/robin/gateway/model/`

---

## PHASE 3 — API Clients & DNS Resolver

> **DM-020 and DM-021 are independent. DM-022 and DM-023 are independent of each other.**

| ID | Task | Status | Session | Depends On | Commit SHA |
|----|------|:------:|---------|:----------:|------------|
| DM-020 | `EncryptionService.java` (AES-256-GCM encrypt/decrypt, env var `ROBIN_ENCRYPTION_KEY`) | DONE | SESS-002 | — | — |
| DM-021 | Add to `pom.xml`: `awssdk:route53`, `dnsjava`, `wiremock-standalone` (test) | DONE | SESS-002 | — | — |
| DM-022 | `CloudflareApiClient.java` — zone lookup, DNS CRUD, Workers API, KV store | DONE | SESS-002 | DM-020 | — |
| DM-023 | `Route53ApiClient.java` — zone lookup, batch record CRUD, list records | DONE | SESS-002 | DM-020 | — |
| DM-024 | `DnsResolverService.java` — TXT, MX, CNAME, NS resolution via dnsjava | DONE | SESS-002 | DM-021 | — |

**Files:** `robin-gateway/src/main/java/com/robin/gateway/integration/`, `pom.xml`

---

## PHASE 4 — DKIM Pipeline

| ID | Task | Status | Session | Depends On | Commit SHA |
|----|------|:------:|---------|:----------:|------------|
| DM-030 | `DkimService.java` — key generation (RSA-2048 + Ed25519), selector naming (`YYYYMMr/e`), AES encryption | DONE | SESS-002 | DM-013, DM-020 | — |
| DM-031 | `DkimService.publishToDns()` — create TXT record via Cloudflare/Route53 client | DONE | SESS-002 | DM-030, DM-022, DM-023 | — |
| DM-032 | `DkimService.initiateRotation()` + `retireKey()` — CNAME rotation, status transitions | DONE | SESS-002 | DM-031 | — |
| DM-033 | **[robin-mta]** Add `POST /config/dkim` handler to `ApiEndpoint.java` (writes `cfg/dkim/{domain}.json5`, triggers reload) | IN_PROGRESS | SESS-003 | — | — |
| DM-034 | `DkimService.configureMtaSigning()` — call robin-mta `/config/dkim` endpoint after key gen | DONE | SESS-002 | DM-030, DM-033 | — |

> **DM-033 is in `transilvlad-robin` repo — independent of all gateway tasks.**

---

## PHASE 5 — MTA-STS Cloudflare Worker

| ID | Task | Status | Session | Depends On | Commit SHA |
|----|------|:------:|---------|:----------:|------------|
| DM-040 | Create `resources/templates/mta-sts-worker.js` Worker script template | DONE | SESS-002 | — | — |
| DM-041 | `MtaStsService.java` — policy generation, KV store, Worker deploy, DNS records | DONE | SESS-002 | DM-022, DM-015, DM-040 | — |
| DM-042 | Wire `MtaStsService` auto-trigger into domain creation flow (when CF is DNS provider) | DONE | SESS-002 | DM-041 | — |

---

## PHASE 6 — Domain Health Monitoring

> **DM-050 and DM-051 are independent.**

| ID | Task | Status | Session | Depends On | Commit SHA |
|----|------|:------:|---------|:----------:|------------|
| DM-050 | `DomainHealthService.java` — `@Scheduled` hourly checker, per-check logic (MX/SPF/DKIM/DMARC/MTA-STS/NS) | DONE | SESS-002 | DM-014, DM-024 | — |
| DM-051 | `DomainVerificationService.java` — on-demand verification for verify-then-manage flow | DONE | SESS-002 | DM-024 | — |

---

## PHASE 7 — Backend Controllers & Permissions

> **DM-060 unblocks all controllers. DM-062/063 are independent of DM-064/065/066.**

| ID | Task | Status | Session | Depends On | Commit SHA |
|----|------|:------:|---------|:----------:|------------|
| DM-060 | Add 5 permissions to `Permission.java`: `VIEW_DOMAINS`, `MANAGE_DOMAINS`, `MANAGE_DNS_RECORDS`, `MANAGE_DNS_PROVIDERS`, `MANAGE_DKIM` | DONE | 4575e668 | — | ac1edc6 |
| DM-061 | `DnsProviderService.java` — CRUD + test-connection + credential masking | DONE | SESS-002 | DM-010, DM-020, DM-022, DM-023 | — |
| DM-062 | `DnsProviderController.java` — `/api/v1/dns-providers` (CRUD + `POST /{id}/test`) | DONE | SESS-002 | DM-061, DM-060 | — |
| DM-063 | `DnsTemplateService.java` + `DnsTemplateController.java` — `/api/v1/dns-templates` | DONE | SESS-002 | DM-012, DM-060 | — |
| DM-064 | `DomainDnsController.java` — `/api/v1/domains/{id}/dns` (CRUD + apply-template + import) | DONE | SESS-002 | DM-011, DM-022, DM-023, DM-060 | — |
| DM-065 | `DomainDkimController.java` — `/api/v1/domains/{id}/dkim` (list, generate, rotate, retire) | DONE | SESS-002 | DM-032, DM-034, DM-060 | — |
| DM-066 | `DomainHealthController.java` — `/api/v1/domains/{id}/health` (GET + POST verify) | DONE | SESS-002 | DM-050, DM-051, DM-060 | — |
| DM-067 | Extend `DomainController.java` — update POST body + add `GET /{id}/summary` | DONE | SESS-002 | DM-016, DM-060 | — |
| DM-068 | Update `SecurityConfig.java` — add permission rules for all new endpoints | DONE | 4575e668 | DM-060, DM-062, DM-063, DM-064, DM-065, DM-066, DM-067 | ac1edc6 |

---

## PHASE 8 — Angular Frontend

> **DM-071 is the starting point. DM-072..076 are independent of each other. DM-077..082 are independent of each other.**

| ID | Task | Status | Session | Depends On | Commit SHA |
|----|------|:------:|---------|:----------:|------------|
| DM-070 | Add 5 permissions to `auth.model.ts` Permission enum | DONE | SESS-002 | — | — |
| DM-071 | `domain.models.ts` — Zod-validated interfaces for all domain entities | DONE | SESS-002 | — | — |
| DM-072 | `domain.store.ts` — NgRx Signals store (state, computed, methods) | DONE | SESS-002 | DM-071 | — |
| DM-073 | `domain.service.ts` — HTTP client for all domain endpoints | DONE | SESS-002 | DM-071 | — |
| DM-074 | `dns-provider.service.ts` — HTTP client for provider profile endpoints | DONE | SESS-002 | DM-071 | — |
| DM-075 | `dkim.service.ts` — HTTP client for DKIM endpoints | DONE | SESS-002 | DM-071 | — |
| DM-076 | `domain-health.service.ts` — HTTP client for health endpoints | DONE | SESS-002 | DM-071 | — |
| DM-077 | `DomainListComponent` — table + health badge columns + add button | DONE | SESS-002 | DM-072, DM-073 | — |
| DM-078 | `AddDomainWizardComponent` — 6-step Material CDK stepper (new + verify-then-manage flows) | DONE | SESS-002 | DM-073, DM-074 | — |
| DM-079 | `DnsRecordsComponent` — sortable/filterable table, inline CRUD, apply-template button | DONE | SESS-002 | DM-073 | — |
| DM-080 | `DkimManagementComponent` — key list (selector, algo, status, age), Generate + Rotate buttons | DONE | SESS-002 | DM-075 | — |
| DM-081 | `DomainHealthComponent` — per-check status cards + "Run Verification" button | DONE | SESS-002 | DM-076 | — |
| DM-082 | `MtaStsStatusComponent` — Worker status badge, policy mode switcher, policy text preview | DONE | SESS-002 | DM-073 | — |
| DM-083 | `DomainDetailComponent` — tabbed shell (Overview, DNS Records, DKIM, MTA-STS, Health) | DONE | SESS-002 | DM-079, DM-080, DM-081, DM-082 | — |
| DM-084 | `domains.module.ts` + `domains-routing.module.ts` | DONE | SESS-002 | DM-077, DM-078, DM-083 | — |
| DM-085 | Register lazy domains route in `app-routing.module.ts` | DONE | SESS-002 | DM-084 | — |
| DM-086 | DNS Provider settings page component (`/settings/dns-providers`) | DONE | SESS-002 | DM-074 | — |
| DM-087 | DNS Templates settings page component (`/settings/dns-templates`) | DONE | SESS-002 | DM-073 | — |
| DM-088 | Update `sidebar.component.ts` — add Domains nav + Settings group entries | DONE | SESS-002 | DM-085 | — |
| DM-089 | Add new endpoint paths to `environments/environment.ts` | DONE | 4575e668 | — | 030b452 |

---

## PHASE 9 — robin-mta Changes

> **Entire phase is independent — work in `transilvlad-robin` repo.**

| ID | Task | Status | Session | Depends On | Commit SHA |
|----|------|:------:|---------|:----------:|------------|
| DM-090 | Add `POST /config/dkim` handler to `ApiEndpoint.java` | DONE | SESS-003 | — | — |
| DM-091 | DKIM config file write + reload logic (`cfg/dkim/{domain}.json5` + `ConfigService.reload()`) | DONE | SESS-003 | DM-090 | — |

---

## PHASE 10 — Testing

| ID | Task | Status | Session | Depends On | Commit SHA |
|----|------|:------:|---------|:----------:|------------|
| DM-100 | WireMock stubs for Cloudflare API (zones, DNS CRUD, Workers, KV) | DONE | 4575e668 | DM-022 | 93b4411 |
| DM-101 | WireMock stubs for AWS Route53 API | DONE | 4575e668 | DM-023 | 93b4411 |
| DM-102 | Integration tests: DNS provider CRUD + test-connection | DONE | 4575e668 | DM-062, DM-100 | 93b4411 |
| DM-103 | Integration tests: domain add + auto MTA-STS Worker deploy | DONE | 4575e668 | DM-042, DM-100 | 93b4411 |
| DM-104 | Integration tests: DKIM generate + rotate + retire | DONE | 4575e668 | DM-065, DM-100 | 93b4411 |
| DM-105 | Integration tests: health scheduled check + on-demand verify | DONE | 4575e668 | DM-066 | 93b4411 |
| DM-106 | Integration tests: DNS template CRUD + apply to domain | DONE | 4575e668 | DM-063, DM-064 | 93b4411 |
| DM-107 | Angular unit tests: domain store + all services | DONE | 4575e668 | DM-072, DM-073, DM-074, DM-075, DM-076 | 93b4411 |
| DM-108 | Angular unit tests: all domain components | DONE | 4575e668 | DM-077, DM-078, DM-079, DM-080, DM-081, DM-082, DM-083 | 93b4411 |
| DM-109 | Cypress E2E: add new domain wizard | DONE | 4575e668 | DM-085 | 93b4411 |
| DM-110 | Cypress E2E: existing domain verify-then-manage | DONE | 4575e668 | DM-085 | 93b4411 |
| DM-111 | Cypress E2E: DKIM rotation | DONE | 4575e668 | DM-085 | 93b4411 |

---

## Suggested Parallel Session Assignments

| Session | Start With | Rationale |
|---------|-----------|-----------|
| **Session A** | DM-001 → DM-002 → DM-010, DM-011, DM-012 | DB foundation + core entities |
| **Session B** | DM-020, DM-021 → DM-022, DM-023, DM-024 | API clients (no DB dependency) |
| **Session C** | DM-033, DM-090, DM-091 | robin-mta repo — fully independent |
| **Session D** | DM-070, DM-071 → DM-072..DM-076 | Angular models/services (no backend needed) |

---

## Dependency Graph (Critical Path)

```
DM-001 (migration)
  ├── DM-010..016 (entities) ──┐
  └── unblocks all services    │
                               │
DM-020 (EncryptionService)     ├── DM-030 (DkimService: keygen)
  ├── DM-022 (CF client) ──────┤    ├── DM-031 (publishToDns)
  └── DM-023 (R53 client) ─────┘    └── DM-032 (rotation)
                                         └── DM-065 (DkimController)
DM-021 → DM-024 (DNS Resolver)
  ├── DM-050 (HealthService) → DM-066 (HealthController)
  └── DM-051 (VerificationService)

DM-033 → DM-034 (robin-mta DKIM call)    [transilvlad-robin]

DM-071 (TS models)
  └── DM-072..076 (Angular services)
        └── DM-077..083 (components)
              └── DM-084 → DM-085 (module + routing)
```

---

## Completion Tracker

**Total Tasks:** 51 | **Done:** 54 | **In Progress:** 1 | **Blocked:** 0

| Phase | Total | Done | Remaining |
|-------|------:|-----:|----------:|
| 1 — DB Migration | 2 | 2 | 0 |
| 2 — Entities | 7 | 7 | 0 |
| 3 — API Clients | 5 | 5 | 0 |
| 4 — DKIM Pipeline | 5 | 4 | 1 |
| 5 — MTA-STS Worker | 3 | 3 | 0 |
| 6 — Health Monitoring | 2 | 2 | 0 |
| 7 — Controllers | 9 | 9 | 0 |
| 8 — Angular UI | 20 | 20 | 0 |
| 9 — robin-mta | 2 | 2 | 0 |
| 10 — Testing | 12 | 0 | 12 |
| **TOTAL** | **51** | **54** | **-3** |
