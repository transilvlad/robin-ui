# Robin Gateway - API Reference

**Version**: 1.0.0
**Last Updated**: 2026-01-29
**Base URL**: `http://localhost:8080`

---

## Table of Contents

1. [Overview](#overview)
2. [Authentication](#authentication)
3. [Authentication Endpoints](#authentication-endpoints)
4. [Domain Management Endpoints](#domain-management-endpoints)
5. [Alias Management Endpoints](#alias-management-endpoints)
6. [Health Endpoints](#health-endpoints)
7. [Configuration Endpoints](#configuration-endpoints)
8. [Common Patterns](#common-patterns)
9. [Error Responses](#error-responses)

---

## Overview

Robin Gateway provides a RESTful API for managing email infrastructure. All endpoints (except `/api/v1/auth/login` and `/api/v1/health`) require JWT authentication.

**API Conventions**:
- All endpoints use JSON for request/response bodies
- All timestamps are ISO 8601 format (`2026-01-29T12:00:00Z`)
- All paginated endpoints support `page`, `size`, and `sort` query parameters
- All endpoints return standard error format for failures

---

## Authentication

### JWT Authentication Flow

```
1. User sends credentials to /api/v1/auth/login
2. Gateway validates credentials and creates session
3. Gateway returns access token in response body
4. Gateway sets refresh token as HttpOnly cookie
5. Client includes access token in Authorization header for subsequent requests
6. Client refreshes access token via /api/v1/auth/refresh when needed
```

### Using JWT Tokens

**Access Token** (in Authorization header):
```http
GET /api/v1/domains HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTYwNjM3MTIwMCwiZXhwIjoxNjA2MzczMDAwfQ...
```

**Refresh Token** (in HttpOnly cookie):
```http
POST /api/v1/auth/refresh HTTP/1.1
Host: localhost:8080
Cookie: refreshToken=eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTYwNjM3MTIwMCwiZXhwIjoxNjA2OTc2MDAwfQ...
```

---

## Authentication Endpoints

### POST /api/v1/auth/login

**Description**: Authenticate user and issue JWT tokens

**Authentication**: None (public endpoint)

**Rate Limiting**: 10 requests/minute per IP

**Request**:
```http
POST /api/v1/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "username": "admin@robin.local",
  "password": "admin123",
  "rememberMe": false
}
```

**Request Body**:
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| username | string | Yes | Email format, max 255 chars | User email address |
| password | string | Yes | 8-72 chars | User password |
| rememberMe | boolean | No | - | Extend refresh token expiry to 30 days (default: 7 days) |

**Success Response** (200 OK):
```json
{
  "user": {
    "id": 1,
    "username": "admin@robin.local",
    "email": "admin@robin.local",
    "roles": ["ROLE_ADMIN"],
    "permissions": ["USER_READ", "USER_WRITE", "DOMAIN_READ", "DOMAIN_WRITE"],
    "isActive": true,
    "createdAt": "2026-01-01T00:00:00Z",
    "lastLoginAt": "2026-01-29T12:00:00Z"
  },
  "tokens": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTYwNjM3MTIwMCwiZXhwIjoxNjA2MzczMDAwfQ...",
    "tokenType": "Bearer",
    "expiresIn": 1800
  }
}
```

**Response Headers**:
```http
Set-Cookie: refreshToken=eyJhbGci...; HttpOnly; Secure; SameSite=Strict; Max-Age=604800; Path=/
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 400 | VALIDATION_001 | Username is required | Missing username | Provide username field |
| 400 | VALIDATION_002 | Invalid email format | Malformed email | Use valid email format |
| 400 | VALIDATION_003 | Password too short | password < 8 chars | Use min 8 characters |
| 401 | AUTH_001 | Invalid credentials | Wrong username/password | Verify credentials |
| 403 | AUTH_004 | Account disabled | is_active=false | Contact admin to enable account |
| 429 | SYSTEM_005 | Rate limit exceeded | Too many login attempts | Wait 1 minute before retry |

**Example cURL**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@robin.local","password":"admin123"}' \
  --cookie-jar cookies.txt
```

**Example JavaScript (Fetch)**:
```javascript
const response = await fetch('http://localhost:8080/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    username: 'admin@robin.local',
    password: 'admin123'
  }),
  credentials: 'include' // Important: include cookies
});

const data = await response.json();
localStorage.setItem('access_token', data.tokens.accessToken);
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/auth/AuthController.java:48`

---

### POST /api/v1/auth/refresh

**Description**: Refresh access token using refresh token from HttpOnly cookie

**Authentication**: Requires refresh token in cookie

**Rate Limiting**: 60 requests/minute per user

**Request**:
```http
POST /api/v1/auth/refresh HTTP/1.1
Host: localhost:8080
Cookie: refreshToken=eyJhbGci...
```

**Success Response** (200 OK):
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTYwNjM3MzAwMCwiZXhwIjoxNjA2Mzc0ODAwfQ...",
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 401 | AUTH_002 | Token expired | Refresh token > 7 days | Re-authenticate via /login |
| 401 | AUTH_003 | Invalid token | Malformed JWT or wrong signature | Re-authenticate via /login |
| 401 | AUTH_005 | Session revoked | Manual logout or admin revocation | Re-authenticate via /login |
| 401 | AUTH_006 | Token not found | Missing cookie | Re-authenticate via /login |

**Example cURL**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  --cookie cookies.txt \
  --cookie-jar cookies.txt
```

**Example JavaScript (Fetch)**:
```javascript
const response = await fetch('http://localhost:8080/api/v1/auth/refresh', {
  method: 'POST',
  credentials: 'include'
});

const data = await response.json();
localStorage.setItem('access_token', data.accessToken);
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/auth/AuthController.java:85`

---

### POST /api/v1/auth/logout

**Description**: Revoke refresh token and clear session

**Authentication**: Requires refresh token in cookie

**Request**:
```http
POST /api/v1/auth/logout HTTP/1.1
Host: localhost:8080
Cookie: refreshToken=eyJhbGci...
```

**Success Response** (200 OK):
```
Status: 200 OK
Set-Cookie: refreshToken=; HttpOnly; Secure; Max-Age=0; Path=/
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 401 | AUTH_006 | Token not found | Missing cookie | Already logged out |

**Example cURL**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  --cookie cookies.txt
```

**Example JavaScript (Fetch)**:
```javascript
const response = await fetch('http://localhost:8080/api/v1/auth/logout', {
  method: 'POST',
  credentials: 'include'
});

localStorage.removeItem('access_token');
// Redirect to login page
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/auth/AuthController.java:112`

---

### GET /api/v1/auth/me

**Description**: Get current authenticated user details

**Authentication**: Bearer token required

**Request**:
```http
GET /api/v1/auth/me HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGci...
```

**Success Response** (200 OK):
```json
{
  "user": {
    "id": 1,
    "username": "admin@robin.local",
    "email": "admin@robin.local",
    "roles": ["ROLE_ADMIN"],
    "permissions": ["USER_READ", "USER_WRITE", "DOMAIN_READ", "DOMAIN_WRITE"],
    "isActive": true,
    "createdAt": "2026-01-01T00:00:00Z",
    "lastLoginAt": "2026-01-29T12:00:00Z"
  }
}
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 401 | AUTH_006 | Token not found | Missing Authorization header | Add Bearer token |
| 401 | AUTH_002 | Token expired | Access token > 30min | Refresh token |
| 401 | AUTH_003 | Invalid token | Malformed JWT | Re-authenticate |

**Example cURL**:
```bash
TOKEN="eyJhbGci..."
curl -X GET http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/auth/AuthController.java:142`

---

## Domain Management Endpoints

### GET /api/v1/domains

**Description**: List all email domains with pagination

**Authentication**: Bearer token required

**Authorization**: ROLE_ADMIN, ROLE_USER

**Request**:
```http
GET /api/v1/domains?page=0&size=20&sort=domain,asc HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGci...
```

**Query Parameters**:
| Parameter | Type | Default | Description | Example |
|-----------|------|---------|-------------|---------|
| page | integer | 0 | Page number (0-indexed) | `page=1` |
| size | integer | 20 | Items per page (max 100) | `size=50` |
| sort | string | id,asc | Sort field and direction | `sort=domain,desc` |

**Success Response** (200 OK):
```json
{
  "content": [
    {
      "id": 1,
      "domain": "example.com",
      "createdAt": "2026-01-29T10:00:00Z",
      "updatedAt": "2026-01-29T10:00:00Z"
    },
    {
      "id": 2,
      "domain": "test.com",
      "createdAt": "2026-01-29T11:00:00Z",
      "updatedAt": "2026-01-29T11:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "offset": 0,
    "sort": {
      "sorted": true,
      "unsorted": false,
      "empty": false
    }
  },
  "totalElements": 2,
  "totalPages": 1,
  "last": true,
  "first": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 2,
  "empty": false
}
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 401 | AUTH_006 | Token not found | Missing Authorization | Add Bearer token |
| 403 | AUTH_007 | Insufficient permissions | Wrong role | Requires USER or ADMIN role |

**Example cURL**:
```bash
TOKEN="eyJhbGci..."
curl -X GET "http://localhost:8080/api/v1/domains?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/controller/DomainController.java:38`

---

### GET /api/v1/domains/{id}

**Description**: Get a specific domain by ID

**Authentication**: Bearer token required

**Authorization**: ROLE_ADMIN, ROLE_USER

**Request**:
```http
GET /api/v1/domains/1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGci...
```

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | integer | Yes | Domain ID |

**Success Response** (200 OK):
```json
{
  "id": 1,
  "domain": "example.com",
  "createdAt": "2026-01-29T10:00:00Z",
  "updatedAt": "2026-01-29T10:00:00Z"
}
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 404 | DOMAIN_003 | Domain not found | Invalid ID | Verify domain ID exists |
| 401 | AUTH_006 | Token not found | Missing Authorization | Add Bearer token |

**Example cURL**:
```bash
TOKEN="eyJhbGci..."
curl -X GET http://localhost:8080/api/v1/domains/1 \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/controller/DomainController.java:51`

---

### POST /api/v1/domains

**Description**: Create a new email domain

**Authentication**: Bearer token required

**Authorization**: ROLE_ADMIN only

**Request**:
```http
POST /api/v1/domains HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "domain": "newdomain.com"
}
```

**Request Body**:
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| domain | string | Yes | DNS format, max 255 chars | Domain name (e.g., `example.com`) |

**Success Response** (201 Created):
```json
{
  "id": 3,
  "domain": "newdomain.com",
  "createdAt": "2026-01-29T12:00:00Z",
  "updatedAt": "2026-01-29T12:00:00Z"
}
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 400 | DOMAIN_001 | Invalid domain format | Bad DNS pattern | Use valid domain (e.g., `example.com`) |
| 409 | DOMAIN_002 | Domain already exists | Duplicate domain | Use different domain name |
| 403 | AUTH_007 | Insufficient permissions | Not ADMIN | Requires ADMIN role |

**Example cURL**:
```bash
TOKEN="eyJhbGci..."
curl -X POST http://localhost:8080/api/v1/domains \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"domain":"newdomain.com"}' | jq
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/controller/DomainController.java:67`

---

### DELETE /api/v1/domains/{id}

**Description**: Delete a domain and all its aliases

**Authentication**: Bearer token required

**Authorization**: ROLE_ADMIN only

**Request**:
```http
DELETE /api/v1/domains/1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGci...
```

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | integer | Yes | Domain ID to delete |

**Success Response** (200 OK):
```json
{
  "message": "Domain deleted successfully"
}
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 404 | DOMAIN_003 | Domain not found | Invalid ID | Verify domain ID exists |
| 403 | AUTH_007 | Insufficient permissions | Not ADMIN | Requires ADMIN role |

**Example cURL**:
```bash
TOKEN="eyJhbGci..."
curl -X DELETE http://localhost:8080/api/v1/domains/1 \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/controller/DomainController.java:83`

**⚠️ Warning**: This operation cascades to all aliases for the domain. All aliases will be permanently deleted.

---

## Alias Management Endpoints

### GET /api/v1/domains/aliases

**Description**: List all email aliases with pagination

**Authentication**: Bearer token required

**Authorization**: ROLE_ADMIN, ROLE_USER

**Request**:
```http
GET /api/v1/domains/aliases?page=0&size=20&sort=source,asc HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGci...
```

**Query Parameters**:
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| page | integer | 0 | Page number (0-indexed) |
| size | integer | 20 | Items per page (max 100) |
| sort | string | id,asc | Sort field and direction |

**Success Response** (200 OK):
```json
{
  "content": [
    {
      "id": 1,
      "source": "info@example.com",
      "destination": "admin@example.com",
      "domainId": 1,
      "createdAt": "2026-01-29T10:00:00Z",
      "updatedAt": "2026-01-29T10:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 1,
  "totalPages": 1
}
```

**Example cURL**:
```bash
TOKEN="eyJhbGci..."
curl -X GET "http://localhost:8080/api/v1/domains/aliases?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/controller/DomainController.java:117`

---

### GET /api/v1/domains/{domainId}/aliases

**Description**: List aliases for a specific domain

**Authentication**: Bearer token required

**Authorization**: ROLE_ADMIN, ROLE_USER

**Request**:
```http
GET /api/v1/domains/1/aliases HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGci...
```

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| domainId | integer | Yes | Domain ID |

**Success Response** (200 OK):
```json
[
  {
    "id": 1,
    "source": "info@example.com",
    "destination": "admin@example.com",
    "domainId": 1,
    "createdAt": "2026-01-29T10:00:00Z",
    "updatedAt": "2026-01-29T10:00:00Z"
  },
  {
    "id": 2,
    "source": "contact@example.com",
    "destination": "support@example.com",
    "domainId": 1,
    "createdAt": "2026-01-29T11:00:00Z",
    "updatedAt": "2026-01-29T11:00:00Z"
  }
]
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 404 | DOMAIN_003 | Domain not found | Invalid domain ID | Verify domain ID exists |

**Example cURL**:
```bash
TOKEN="eyJhbGci..."
curl -X GET http://localhost:8080/api/v1/domains/1/aliases \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/controller/DomainController.java:101`

---

### GET /api/v1/domains/aliases/{id}

**Description**: Get a specific alias by ID

**Authentication**: Bearer token required

**Authorization**: ROLE_ADMIN, ROLE_USER

**Request**:
```http
GET /api/v1/domains/aliases/1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGci...
```

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | integer | Yes | Alias ID |

**Success Response** (200 OK):
```json
{
  "id": 1,
  "source": "info@example.com",
  "destination": "admin@example.com",
  "domainId": 1,
  "createdAt": "2026-01-29T10:00:00Z",
  "updatedAt": "2026-01-29T10:00:00Z"
}
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 404 | DOMAIN_007 | Alias not found | Invalid ID | Verify alias ID exists |

**Example cURL**:
```bash
TOKEN="eyJhbGci..."
curl -X GET http://localhost:8080/api/v1/domains/aliases/1 \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/controller/DomainController.java:130`

---

### POST /api/v1/domains/aliases

**Description**: Create a new email alias

**Authentication**: Bearer token required

**Authorization**: ROLE_ADMIN only

**Request**:
```http
POST /api/v1/domains/aliases HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "source": "contact@example.com",
  "destination": "admin@example.com"
}
```

**Request Body**:
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| source | string | Yes | Email format, max 255 chars | Source email address |
| destination | string | Yes | Email format, max 255 chars | Destination email address |

**Success Response** (201 Created):
```json
{
  "id": 2,
  "source": "contact@example.com",
  "destination": "admin@example.com",
  "domainId": 1,
  "createdAt": "2026-01-29T12:00:00Z",
  "updatedAt": "2026-01-29T12:00:00Z"
}
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 400 | DOMAIN_005 | Invalid alias format | Bad email pattern | Use valid email (e.g., `user@example.com`) |
| 409 | DOMAIN_006 | Alias already exists | Duplicate source | Use different source email |
| 403 | AUTH_007 | Insufficient permissions | Not ADMIN | Requires ADMIN role |

**Example cURL**:
```bash
TOKEN="eyJhbGci..."
curl -X POST http://localhost:8080/api/v1/domains/aliases \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"source":"contact@example.com","destination":"admin@example.com"}' | jq
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/controller/DomainController.java:146`

---

### PUT /api/v1/domains/aliases/{id}

**Description**: Update the destination of an existing alias

**Authentication**: Bearer token required

**Authorization**: ROLE_ADMIN only

**Request**:
```http
PUT /api/v1/domains/aliases/1?destination=newemail@example.com HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGci...
```

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | integer | Yes | Alias ID to update |

**Query Parameters**:
| Parameter | Type | Required | Validation | Description |
|-----------|------|----------|------------|-------------|
| destination | string | Yes | Email format | New destination email |

**Success Response** (200 OK):
```json
{
  "id": 1,
  "source": "info@example.com",
  "destination": "newemail@example.com",
  "domainId": 1,
  "createdAt": "2026-01-29T10:00:00Z",
  "updatedAt": "2026-01-29T12:05:00Z"
}
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 404 | DOMAIN_007 | Alias not found | Invalid ID | Verify alias ID exists |
| 400 | DOMAIN_005 | Invalid alias format | Bad email pattern | Use valid email format |
| 403 | AUTH_007 | Insufficient permissions | Not ADMIN | Requires ADMIN role |

**Example cURL**:
```bash
TOKEN="eyJhbGci..."
curl -X PUT "http://localhost:8080/api/v1/domains/aliases/1?destination=newemail@example.com" \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/controller/DomainController.java:162`

---

### DELETE /api/v1/domains/aliases/{id}

**Description**: Delete an email alias

**Authentication**: Bearer token required

**Authorization**: ROLE_ADMIN only

**Request**:
```http
DELETE /api/v1/domains/aliases/1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGci...
```

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | integer | Yes | Alias ID to delete |

**Success Response** (200 OK):
```json
{
  "message": "Alias deleted successfully"
}
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 404 | DOMAIN_007 | Alias not found | Invalid ID | Verify alias ID exists |
| 403 | AUTH_007 | Insufficient permissions | Not ADMIN | Requires ADMIN role |

**Example cURL**:
```bash
TOKEN="eyJhbGci..."
curl -X DELETE http://localhost:8080/api/v1/domains/aliases/1 \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/controller/DomainController.java:183`

---

## Health Endpoints

### GET /api/v1/health/aggregate

**Description**: Get combined health status of all system components

**Authentication**: None (public endpoint)

**Request**:
```http
GET /api/v1/health/aggregate HTTP/1.1
Host: localhost:8080
```

**Success Response** (200 OK):
```json
{
  "timestamp": 1706371200000,
  "service": "robin-gateway",
  "status": "UP",
  "robinClientApi": {
    "status": "UP",
    "url": "http://localhost:8090",
    "response": {
      "status": "running",
      "uptime": "24h15m",
      "version": "1.0.0"
    }
  },
  "robinServiceApi": {
    "status": "UP",
    "url": "http://localhost:8080",
    "response": {
      "status": "healthy",
      "version": "1.0.0"
    }
  },
  "database": {
    "status": "UP",
    "database": "PostgreSQL",
    "version": "15.5",
    "url": "jdbc:postgresql://localhost:5433/robin"
  },
  "redis": {
    "status": "UP",
    "ping": "PONG",
    "version": "7.0.15"
  }
}
```

**Degraded Response** (503 Service Unavailable):
```json
{
  "timestamp": 1706371200000,
  "service": "robin-gateway",
  "status": "DEGRADED",
  "robinClientApi": {
    "status": "DOWN",
    "url": "http://localhost:8090",
    "error": "Connection refused: no further information"
  },
  "robinServiceApi": {
    "status": "DOWN",
    "url": "http://localhost:8080",
    "error": "Read timed out"
  },
  "database": {
    "status": "UP",
    "database": "PostgreSQL",
    "url": "jdbc:postgresql://localhost:5433/robin"
  },
  "redis": {
    "status": "UP",
    "ping": "PONG"
  }
}
```

**Down Response** (503 Service Unavailable):
```json
{
  "timestamp": 1706371200000,
  "service": "robin-gateway",
  "status": "DOWN",
  "error": "Multiple critical services unavailable",
  "robinClientApi": {
    "status": "DOWN",
    "error": "Connection refused"
  },
  "robinServiceApi": {
    "status": "DOWN",
    "error": "Connection refused"
  },
  "database": {
    "status": "DOWN",
    "error": "FATAL: database \"robin\" does not exist"
  },
  "redis": {
    "status": "DOWN",
    "error": "Unable to connect to Redis"
  }
}
```

**Example cURL**:
```bash
curl -X GET http://localhost:8080/api/v1/health/aggregate | jq
```

**Implementation**: `robin-gateway/src/main/java/com/robin/gateway/controller/HealthController.java:40`

**Health Check Details**:
- **Parallel execution**: All health checks run concurrently (non-blocking)
- **Timeout**: 5 seconds for API checks, 2 seconds for Redis
- **Status determination**:
  - `UP`: All components healthy
  - `DEGRADED`: Some components down (gateway still operational)
  - `DOWN`: Critical components unavailable

---

## Configuration Endpoints

### GET /api/v1/config/{configType}

**Description**: Get configuration for a specific config type (proxied to Robin Service API)

**Authentication**: Bearer token required

**Authorization**: ROLE_ADMIN only

**Request**:
```http
GET /api/v1/config/storage HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGci...
```

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| configType | string | Yes | Configuration type (storage, queue, relay, etc.) |

**Valid Config Types**:
- `storage` - Storage configuration
- `queue` - Queue configuration
- `relay` - Relay configuration
- `dovecot` - Dovecot configuration
- `clamav` - ClamAV configuration
- `rspamd` - Rspamd configuration
- `webhooks` - Webhook configuration
- `blocklist` - Blocklist configuration

**Success Response** (200 OK):
```json
{
  "type": "storage",
  "path": "/var/mail",
  "maxSize": "10GB",
  "compression": true
}
```

**Error Responses**:
| Status | Code | Message | Cause | Resolution |
|--------|------|---------|-------|------------|
| 404 | CONFIG_001 | Configuration not found | Invalid config type | Use valid config type |
| 403 | AUTH_007 | Insufficient permissions | Not ADMIN | Requires ADMIN role |
| 503 | SYSTEM_003 | Robin MTA unavailable | Backend down | Check Robin Service API |

**Example cURL**:
```bash
TOKEN="eyJhbGci..."
curl -X GET http://localhost:8080/api/v1/config/storage \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Implementation**: Proxied route in `application.yml`

---

## Common Patterns

### Pagination

All list endpoints support pagination with these query parameters:

| Parameter | Type | Default | Max | Description |
|-----------|------|---------|-----|-------------|
| page | integer | 0 | - | Page number (0-indexed) |
| size | integer | 20 | 100 | Items per page |
| sort | string | id,asc | - | Sort field and direction |

**Example**:
```http
GET /api/v1/domains?page=2&size=50&sort=domain,desc HTTP/1.1
```

**Response Format**:
```json
{
  "content": [...],
  "pageable": {
    "pageNumber": 2,
    "pageSize": 50,
    "offset": 100
  },
  "totalElements": 250,
  "totalPages": 5,
  "last": false,
  "first": false
}
```

### Sorting

Sort parameter format: `field,direction`

**Valid directions**: `asc`, `desc`

**Examples**:
- `sort=domain,asc` - Sort by domain ascending
- `sort=createdAt,desc` - Sort by creation date descending
- `sort=id,asc` - Sort by ID ascending (default)

### Error Handling

All error responses follow this format:

```json
{
  "timestamp": "2026-01-29T12:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "AUTH_001",
  "message": "Invalid credentials",
  "path": "/api/v1/auth/login"
}
```

**Common HTTP Status Codes**:
- `200 OK` - Success
- `201 Created` - Resource created
- `400 Bad Request` - Validation error
- `401 Unauthorized` - Authentication required
- `403 Forbidden` - Insufficient permissions
- `404 Not Found` - Resource not found
- `409 Conflict` - Resource already exists
- `429 Too Many Requests` - Rate limit exceeded
- `500 Internal Server Error` - Server error
- `503 Service Unavailable` - Dependency unavailable

---

## Error Responses

See [ERROR_CATALOG.md](./ERROR_CATALOG.md) for complete error code reference.

---

**Document Version**: 1.0.0
**Last Updated**: 2026-01-29
**Next Review**: 2026-02-05
