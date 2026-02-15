# Robin Modernization Architecture

This document describes the high-level architecture and design of the modernized Robin MTA suite, including the Robin UI and Robin Gateway.

## 1. Overview

The Robin modernization project transforms the legacy Robin MTA into a modern, containerized, and secure mail management platform. The architecture follows a microservices-inspired pattern with a clear separation between frontend, API gateway, and core mail services.

## 2. Component Architecture

The system consists of three primary layers:

### 2.1 Robin UI (Frontend)
- **Technology**: Angular 18+, Standalone Components, TypeScript.
- **Role**: Provides a modern, responsive web interface for domain management, user administration, queue monitoring, and server configuration.
- **Key Features**: OnPush change detection, strict type safety, Tailwind CSS styling, responsive layout.

### 2.2 Robin Gateway (API Gateway & Security)
- **Technology**: Java 21 (Corretto), Spring Boot 3.2, Spring Cloud Gateway (Reactive).
- **Role**: Single entry point for all UI requests. Handles authentication, rate limiting, and request routing to backend services.
- **Key Features**: 
  - JWT Authentication with HttpOnly refresh tokens.
  - Redis-backed rate limiting.
  - Resilience4j circuit breakers.
  - dual-hash password synchronization between Gateway (BCrypt) and MTA (SHA512-CRYPT).
  - Centralized exception handling and request validation.

### 2.3 Robin MTA (Core Services)
- **Technology**: Java 21, Custom MTA engine.
- **Role**: Handles SMTP traffic, mail queue management, virus/spam scanning, and storage.
- **Interfaces**: Provides REST APIs (Service API on 8080, Client API on 8090) for management.

## 3. Data Architecture

### 3.1 Database Schema
The system uses a shared PostgreSQL database with different schema responsibilities:
- **Gateway Tables**: `users`, `user_roles`, `user_permissions`, `provider_configs`, `domains`, `dns_records`.
- **MTA Tables**: `mail_queue`, `mail_store`, `relay_configs`.

### 3.2 Dual-Hash Password Strategy
To maintain compatibility between Spring Security (Gateway) and Dovecot (MTA):
- **Gateway**: Stores BCrypt hashes (`password_bcrypt` column) for standard web login.
- **MTA/Dovecot**: Stores SHA512-CRYPT hashes (`password` column) for IMAP/SMTP authentication.
- **Synchronization**: The `PasswordSyncService` in the Gateway ensures both hashes are updated simultaneously when a user changes their password.

## 4. Security Model

- **Authentication**: JWT-based. Access tokens (30m) in session, Refresh tokens (7d) in HttpOnly cookies.
- **Authorization**: Role-Based Access Control (RBAC). Roles include `ROLE_ADMIN`, `ROLE_USER`. Permissions include `READ_DOMAINS`, `WRITE_DOMAINS`, etc.
- **Sensitive Data**: Provider credentials (API keys, secrets) are stored encrypted in the database using AES-256-GCM.
- **Network**: Only the UI and Gateway are exposed to the host/public network. MTA and infrastructure (Postgres, Redis, ClamAV) reside in an isolated Docker bridge network.

## 5. External Integrations

The Robin Gateway integrates with multiple external DNS and Registrar providers to automate domain configuration:
- **Cloudflare**: DNS management and proxy settings.
- **AWS Route53**: Scalable DNS management.
- **GoDaddy**: Registrar and DNS management.
- **Manual**: Fallback for providers without API support.

## 6. Deployment Architecture

For detailed information on Docker orchestration, networking, and volume management, see:
- [Docker Architecture](DOCKER_ARCHITECTURE.md)
- [Docker Setup Guide](DOCKER_SETUP.md)

## 7. Performance & Scalability

- **Throughput**: Optimized for high concurrency using Spring WebFlux (non-blocking I/O).
- **Latency**: Gateway overhead is minimal (<5ms p95).
- **Scalability**: The Gateway and UI components are stateless and can be scaled horizontally.

---

**Author**: Robin Development Team
**Date**: 2026-02-15
**Version**: 1.0.0
