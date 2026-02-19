# Security Documentation - Robin Gateway

## Overview

Robin Gateway implements enterprise-grade security for the Robin MTA ecosystem, providing authentication, authorization, and protection against common security threats.

## Table of Contents

1. [Authentication](#authentication)
2. [Authorization](#authorization)
3. [Password Management](#password-management)
4. [Encryption](#encryption)
5. [Security Headers](#security-headers)
6. [CORS Policy](#cors-policy)
7. [Rate Limiting](#rate-limiting)
8. [Incident Response](#incident-response)

---

## Authentication

### JWT-Based Authentication

Robin Gateway uses JSON Web Tokens (JWT) for stateless authentication.

**Token Types:**
- **Access Token**: Short-lived token (30 minutes) for API access
- **Refresh Token**: Long-lived token (7 days) for obtaining new access tokens

**Token Flow:**
```
1. User submits credentials → /api/v1/auth/login
2. Gateway validates credentials against PostgreSQL
3. Gateway generates JWT tokens (access + refresh)
4. Client includes access token in Authorization header: Bearer <token>
5. Gateway validates token on each request
6. Client uses refresh token to obtain new access token when expired
```

**Implementation Details:**
- **Algorithm**: HS512 (HMAC with SHA-512)
- **Secret Key**: Stored in environment variable `JWT_SECRET` (min 512 bits)
- **Claims**: `sub` (username), `roles` (array), `iat` (issued at), `exp` (expiration)
- **Validation**: Signature verification, expiration check, issuer validation

**Security Considerations:**
- JWT secret must be cryptographically random and ≥64 characters
- Access tokens are short-lived to limit exposure window
- Refresh tokens stored securely and can be revoked
- No session state stored server-side (fully stateless)

**Configuration:**
```yaml
jwt:
  secret: ${JWT_SECRET}
  access-token-expiration: 1800000  # 30 minutes (milliseconds)
  refresh-token-expiration: 604800000  # 7 days (milliseconds)
```

---

## Authorization

### Role-Based Access Control (RBAC)

Robin Gateway uses Spring Security's method-level security with role-based authorization.

**Roles:**
- `ADMIN`: Full access to all endpoints
- `USER`: Limited access to non-administrative endpoints

**Authorization Annotations:**
```java
@PreAuthorize("hasRole('ADMIN')")  // Admin-only endpoints
@PreAuthorize("hasRole('USER')")   // User endpoints
@PreAuthorize("isAuthenticated()") // Any authenticated user
```

**Endpoint Authorization Matrix:**

| Endpoint Pattern | Required Role | Purpose |
|------------------|---------------|---------|
| `/api/v1/auth/**` | None (public) | Authentication endpoints |
| `/api/v1/health/**` | None (public) | Health checks |
| `/actuator/health` | None (public) | Spring Boot health |
| `/actuator/prometheus` | None (public) | Metrics (secure in prod with firewall) |
| `/api/v1/users/**` | ADMIN | User management |
| `/api/v1/domains/**` | ADMIN | Domain management |
| `/api/v1/dns/**` | ADMIN | DNS record management |
| `/api/v1/queue/**` | USER | Email queue access |
| `/api/v1/storage/**` | USER | Email storage browser |
| `/api/v1/monitoring/**` | ADMIN | Logs and metrics |
| `/actuator/**` | ADMIN | Actuator endpoints |

**Implementation:**
```java
.authorizeExchange(exchanges -> exchanges
    .pathMatchers("/api/v1/auth/**").permitAll()
    .pathMatchers("/api/v1/**").authenticated()
    .pathMatchers("/actuator/**").hasRole("ADMIN")
    .anyExchange().denyAll()
)
```

---

## Password Management

### Password Hashing

**Algorithm**: BCrypt with cost factor 12

**Security Properties:**
- Adaptive hashing (computational cost increases over time)
- Built-in salting (random salt per password)
- Slow by design (prevents brute-force attacks)

**Implementation:**
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
```

**Cost Factor Justification:**
- Cost 12 ≈ 300ms per hash on modern hardware
- Balances security vs. UX (login time <500ms acceptable)
- Recommended by OWASP for production systems

### Password Synchronization

Robin Gateway synchronizes passwords with Robin MTA's Dovecot mail server.

**Synchronization Flow:**
```
1. User changes password via Gateway API
2. Gateway hashes password with BCrypt (for Gateway auth)
3. Gateway generates Dovecot-compatible hash (SHA512-CRYPT)
4. Both hashes stored in PostgreSQL
5. Dovecot uses SHA512-CRYPT hash for IMAP/POP3 auth
6. Gateway uses BCrypt hash for API auth
```

**Security Considerations:**
- Passwords never stored in plaintext
- Password sync is atomic (transaction-based)
- Failed sync triggers rollback (consistency guaranteed)
- Dovecot hash algorithm meets mail server requirements

**Related Files:**
- `PasswordSyncService.java`: Password synchronization logic
- `PasswordConfig.java`: BCrypt configuration

---

## Encryption

### Encryption at Rest

Robin Gateway encrypts sensitive data before storing in PostgreSQL.

**Algorithm**: AES-256-GCM (Galois/Counter Mode)

**Use Cases:**
- API keys for DNS providers (Cloudflare, AWS Route53)
- Registrar credentials (GoDaddy)
- OAuth tokens for external integrations

**Implementation:**
```java
public class EncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
}
```

**Key Management:**
- Encryption key stored in environment variable `ENCRYPTION_KEY`
- Key must be 256 bits (32 bytes) base64-encoded
- Key rotation supported (dual-key decryption for migration)
- Key never logged or exposed in error messages

**Security Properties:**
- Authenticated encryption (prevents tampering)
- Unique IV per encryption operation (prevents replay)
- Tag-based authentication (detects modifications)

**Configuration:**
```yaml
encryption:
  key: ${ENCRYPTION_KEY}
```

**Key Generation:**
```bash
# Generate new encryption key
openssl rand -base64 32
```

---

## Security Headers

Robin Gateway configures security headers to protect against common web attacks.

**Configured Headers:**

### X-Content-Type-Options
```
X-Content-Type-Options: nosniff
```
Prevents MIME-sniffing attacks.

### X-Frame-Options
```
X-Frame-Options: DENY
```
Prevents clickjacking by blocking iframe embedding.

### X-XSS-Protection
```
X-XSS-Protection: 0
```
Disabled (modern browsers use CSP instead).

### Cache-Control
```
Cache-Control: no-cache, no-store, must-revalidate
```
Prevents caching of sensitive API responses.

**Implementation:**
```java
.headers(headers -> headers
    .frameOptions(frameOptions -> frameOptions.disable())
    .contentTypeOptions(contentTypeOptions -> {})
    .xssProtection(xss -> xss.disable())
    .cache(cache -> cache.disable())
)
```

**Production Recommendations:**
- Add Content-Security-Policy (CSP) header via reverse proxy
- Enable HSTS (HTTP Strict Transport Security) at load balancer
- Configure referrer policy for privacy

---

## CORS Policy

### Cross-Origin Resource Sharing

Robin Gateway allows cross-origin requests from the Angular UI.

**Allowed Origins:**
- **Development**: `http://localhost:4200`, `http://localhost:8080`
- **Production**: Configured via environment variable `CORS_ALLOWED_ORIGINS`

**Allowed Methods:**
```
GET, POST, PUT, DELETE, OPTIONS
```

**Allowed Headers:**
```
Authorization, Content-Type, X-Requested-With, Accept, Origin
```

**Exposed Headers:**
```
X-Total-Count, X-Page-Number, X-Page-Size
```

**Credentials:**
```
Allow-Credentials: true
```

**Max Age:**
```
3600 seconds (1 hour)
```

**Configuration:**
```yaml
# application.yml (development)
cors:
  allowed-origins: http://localhost:4200,http://localhost:8080

# application-prod.yml (production)
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:https://robin-ui.production.com}
```

**Security Considerations:**
- Never use wildcard (`*`) for `allowed-origins` when `allowCredentials: true`
- Validate origin against whitelist
- Use HTTPS in production
- Limit allowed methods to minimum required

---

## Rate Limiting

Robin Gateway implements rate limiting to prevent abuse and DoS attacks.

**Implementation**: Resilience4j RateLimiter

**Default Limits:**
- **Authentication endpoints**: 5 requests per 60 seconds per IP
- **General API**: 100 requests per 60 seconds per user
- **Admin endpoints**: 50 requests per 60 seconds per user

**Configuration:**
```yaml
resilience4j:
  ratelimiter:
    instances:
      auth:
        limit-for-period: 5
        limit-refresh-period: 60s
        timeout-duration: 0
      api:
        limit-for-period: 100
        limit-refresh-period: 60s
        timeout-duration: 0
```

**Response Format (Rate Limit Exceeded):**
```json
{
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again in 45 seconds.",
  "timestamp": "2026-02-06T10:30:00Z",
  "path": "/api/v1/auth/login"
}
```

**HTTP Status Code:** `429 Too Many Requests`

**Testing Rate Limits:**
```bash
# Test auth rate limit (should block after 5 requests)
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"wrong"}'
  sleep 1
done
```

---

## Incident Response

### Security Incident Procedures

**Detection:**
1. Monitor Prometheus metrics for anomalies
2. Review logs for suspicious activity
3. Check rate limiting violations
4. Monitor failed authentication attempts

**Response Steps:**

**1. Immediate Response (0-15 minutes):**
- Identify affected systems/users
- Isolate compromised accounts (disable via `/api/v1/users/{username}`)
- Review recent access logs
- Block malicious IPs at firewall/gateway

**2. Investigation (15-60 minutes):**
- Analyze logs: `/api/v1/monitoring/logs`
- Check authentication attempts
- Review JWT token issuance
- Identify attack vector

**3. Containment (1-4 hours):**
- Revoke compromised JWT tokens (if refresh token leaked)
- Reset passwords for affected users
- Apply temporary rate limiting rules
- Update firewall rules

**4. Recovery (4-24 hours):**
- Restore normal operations
- Verify data integrity
- Re-enable affected accounts
- Deploy security patches if needed

**5. Post-Incident (24-72 hours):**
- Document incident timeline
- Root cause analysis
- Update security procedures
- Notify affected users (if required by GDPR)

### Logging and Monitoring

**Security-Relevant Logs:**
- Authentication attempts (success/failure)
- Authorization failures
- Rate limit violations
- JWT validation errors
- Password changes
- Admin actions

**Log Retention:**
- Security logs: 90 days minimum
- Audit logs: 1 year
- Compliance logs: As required by regulations

**Log Format:**
```json
{
  "timestamp": "2026-02-06T10:30:00Z",
  "level": "WARN",
  "event": "AUTHENTICATION_FAILED",
  "username": "john.doe",
  "ip": "192.168.1.100",
  "reason": "Invalid credentials"
}
```

### Contact Information

**Security Team:**
- Email: security@robin-mta.org
- Slack: #security-incidents
- On-call: Via PagerDuty

**Vulnerability Reporting:**
- Email: security@robin-mta.org
- PGP Key: [Link to PGP key]

---

## Security Checklist

### Pre-Production Security Audit

- [ ] JWT secret is cryptographically random (≥64 chars)
- [ ] Encryption key is 256-bit AES key
- [ ] BCrypt cost factor is ≥12
- [ ] CORS origins are production URLs (no localhost)
- [ ] HTTPS enabled with valid certificate
- [ ] Security headers configured (X-Frame-Options, etc.)
- [ ] Rate limiting enabled and tested
- [ ] OWASP dependency check passes (no high/critical CVEs)
- [ ] Password policy enforced (min length, complexity)
- [ ] Logging configured and tested
- [ ] Incident response plan documented
- [ ] Security team contacts updated

### Monthly Security Review

- [ ] Review OWASP dependency check results
- [ ] Check for expired certificates
- [ ] Audit user accounts (remove inactive)
- [ ] Review access logs for anomalies
- [ ] Test rate limiting rules
- [ ] Verify backup encryption
- [ ] Update security documentation

---

## References

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Spring Security Documentation](https://docs.spring.io/spring-security/reference/)
- [JWT Best Practices](https://datatracker.ietf.org/doc/html/rfc8725)
- [NIST Password Guidelines](https://pages.nist.gov/800-63-3/)
- [GDPR Security Requirements](https://gdpr.eu/data-security/)

---

**Last Updated**: 2026-02-06
**Owner**: Robin Gateway Security Team
**Version**: 1.0
