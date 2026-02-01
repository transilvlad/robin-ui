# Database Schema Ownership Documentation

## Overview

This document defines the ownership and coordination rules for the shared PostgreSQL database used by multiple Robin ecosystem components.

**Last Updated:** 2026-01-29

---

## Systems Using the Database

| System | Purpose | Authentication Method | Password Column |
|--------|---------|----------------------|-----------------|
| **Robin MTA** | Mail Transfer Agent | SMTP AUTH (Dovecot) | `users.password` (SHA512-CRYPT) |
| **Robin Gateway** | Spring Boot API Gateway | JWT + Spring Security | `users.password_bcrypt` (BCrypt) |
| **Dovecot** | IMAP/POP3 Server | IMAP/POP3 AUTH | `users.password` (SHA512-CRYPT) |

---

## Table Ownership Matrix

| Table | Owner | Write Access | Read Access | Purpose |
|-------|-------|--------------|-------------|---------|
| `users` | **Shared** | MTA + Gateway | All systems | User accounts and authentication |
| `aliases` | **MTA** | MTA only | All systems | Email aliases |
| `domains` | **MTA** | MTA only | All systems | Managed domains |
| `sessions` | **MTA** | MTA only | All systems | SMTP sessions (audit trail) |
| `relay_queue` | **MTA** | MTA only | Gateway (read) | Email relay queue |

**Legend:**
- **Shared**: Multiple systems can write to this table (requires coordination)
- **MTA**: Robin MTA has exclusive write access
- **Gateway**: Robin Gateway has exclusive write access

---

## Password Hash Strategy

### Problem Statement

Two systems need different password hash formats for the same users:

1. **Robin MTA + Dovecot**: Requires SHA512-CRYPT hashes with `{SHA512-CRYPT}` prefix for IMAP/SMTP authentication
2. **Robin Gateway**: Requires BCrypt hashes for Spring Security JWT authentication

### Solution: Dual-Hash Strategy

Store **TWO separate password hashes** in the `users` table:

```sql
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255),

    -- SHA512-CRYPT hash for Dovecot/MTA IMAP/SMTP authentication
    password VARCHAR(255) NOT NULL,

    -- BCrypt hash for Gateway/Spring Security authentication
    password_bcrypt VARCHAR(255),

    ...
);
```

### Column Usage

| Column | Format | Used By | Example |
|--------|--------|---------|---------|
| `password` | `{SHA512-CRYPT}$6$...` | MTA, Dovecot (IMAP/SMTP) | `{SHA512-CRYPT}$6$rounds=5000$...` |
| `password_bcrypt` | `$2a$12$...` | Gateway (Spring Security) | `$2a$12$LQv3c1yqBWVHxkd0LHAkCO...` |
| `username` | email format | All systems (unified identifier) | `admin@robin.local` |
| `email` | email format | Legacy (Dovecot compatibility) | `admin@robin.local` |

### Hash Generation

**Robin Gateway** uses `PasswordSyncService`:

```java
@Service
public class PasswordSyncService {
    @Transactional
    public void updatePassword(Long userId, String plainPassword) {
        // Generate BCrypt for Gateway
        String bcryptHash = passwordEncoder.encode(plainPassword);

        // Generate SHA512-CRYPT for Dovecot via PostgreSQL crypt()
        String sha512Hash = jdbcTemplate.queryForObject(
            "SELECT crypt(?, gen_salt('bf'))",
            String.class,
            plainPassword
        );

        // Update both columns atomically
        user.setPasswordHash(bcryptHash);
        user.setDovecotPasswordHash("{SHA512-CRYPT}" + sha512Hash);
        userRepository.save(user);
    }
}
```

**Robin MTA** must preserve existing SHA512-CRYPT hashes when updating user records.

---

## Authentication Flows

### 1. Gateway Authentication (Web UI → Gateway)

```
User Login (Web UI)
     ↓
POST /api/v1/auth/login {username, password}
     ↓
Spring Security → UserDetailsService
     ↓
Query: SELECT password_bcrypt FROM users WHERE username = ?
     ↓
BCrypt.matches(plainPassword, password_bcrypt)
     ↓
Generate JWT token → Return to client
```

**Password Column Used:** `password_bcrypt` (BCrypt)

---

### 2. MTA SMTP Authentication (Mail Client → MTA)

```
SMTP Client (Thunderbird, etc.)
     ↓
SMTP AUTH PLAIN <base64(username, password)>
     ↓
Robin MTA → Dovecot SASL
     ↓
Query: SELECT password FROM users WHERE username = ?
     ↓
crypt(plainPassword, password) == password
     ↓
SMTP session authenticated
```

**Password Column Used:** `password` (SHA512-CRYPT)

---

### 3. Dovecot IMAP Authentication (Mail Client → Dovecot)

```
IMAP Client
     ↓
LOGIN username password
     ↓
Dovecot auth driver (PostgreSQL)
     ↓
Query: SELECT password FROM users WHERE username = ?
     ↓
crypt(plainPassword, password) == password
     ↓
IMAP session authenticated
```

**Password Column Used:** `password` (SHA512-CRYPT)

---

## Migration Coordination Rules

### Flyway Migration Naming

All database migrations use Flyway versioned migrations:

| Version | Purpose | Owner |
|---------|---------|-------|
| `V1__*` | Initial schema | MTA |
| `V2__add_spring_security_fields.sql` | Add Spring Security columns | Gateway |
| `V3__add_bcrypt_password.sql` | Add BCrypt password column | Gateway |
| `V4+` | Future migrations | Coordinate between teams |

### Migration Coordination Protocol

1. **Before Creating Migration:**
   - Check for conflicting migrations in other systems
   - Coordinate version numbers to avoid conflicts
   - Document changes in this file

2. **Creating Migrations:**
   - Use descriptive names: `V{N}__{description}.sql`
   - Add comprehensive comments explaining purpose
   - Never drop columns used by other systems
   - Never alter columns without coordination

3. **Testing Migrations:**
   - Test against a shared dev database
   - Verify all systems can still authenticate
   - Check both BCrypt and SHA512-CRYPT authentication paths

4. **Deploying Migrations:**
   - Coordinate deployment timing between systems
   - Deploy during maintenance window
   - Monitor authentication logs for both systems
   - Have rollback plan ready

---

## Conflict Prevention Guidelines

### Adding Columns

✅ **Safe:**
```sql
-- Add new column with NULL allowed or DEFAULT value
ALTER TABLE users ADD COLUMN new_field VARCHAR(255);
```

❌ **Unsafe:**
```sql
-- Breaking change: NOT NULL without DEFAULT
ALTER TABLE users ADD COLUMN new_field VARCHAR(255) NOT NULL;
```

### Modifying Columns

✅ **Safe:**
```sql
-- Increase size (backward compatible)
ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(512);
```

❌ **Unsafe:**
```sql
-- Decrease size (can fail if data exceeds new limit)
ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(100);

-- Change column type (can break queries)
ALTER TABLE users ALTER COLUMN email TYPE TEXT;
```

### Dropping Columns

❌ **Never drop columns without coordination:**
```sql
-- DO NOT DO THIS without team agreement
ALTER TABLE users DROP COLUMN password;  -- Breaks Dovecot auth!
```

### Updating Data

⚠️ **Coordinate data updates:**
```sql
-- Example: Updating admin password
-- WRONG: Overwrites SHA512-CRYPT hash
UPDATE users SET password = '$2a$12$...' WHERE username = 'admin@robin.local';

-- CORRECT: Update both hashes
UPDATE users
SET
    password_bcrypt = '$2a$12$...',  -- BCrypt for Gateway
    password = '{SHA512-CRYPT}$6$...'  -- SHA512-CRYPT for MTA
WHERE username = 'admin@robin.local';
```

---

## Password Synchronization

### When Passwords Change

All password updates **MUST** update both columns atomically:

**Via Gateway (PasswordSyncService):**
- Automatically handles both hashes
- Transactional update ensures consistency
- Logs password changes

**Via MTA (if needed):**
- Must preserve Gateway BCrypt hash
- Only update `password` column if Gateway not affected
- Consider using Gateway API for password changes

### Password Change Coordination

**Recommended:** Always use Gateway API for password changes.

```bash
# User changes password via Web UI
POST /api/v1/users/{id}/password
{
  "oldPassword": "old123",
  "newPassword": "new456"
}

# Gateway PasswordSyncService updates:
# - password_bcrypt (BCrypt)
# - password (SHA512-CRYPT)
# Both updated in same transaction
```

---

## Troubleshooting

### Issue: User can login via Web UI but not via IMAP

**Cause:** `password_bcrypt` is set but `password` (SHA512-CRYPT) is missing or invalid.

**Solution:**
```sql
-- Check both password columns
SELECT username,
       password LIKE '{SHA512-CRYPT}%' AS has_sha512,
       password_bcrypt LIKE '$2a$%' AS has_bcrypt
FROM users
WHERE username = 'user@example.com';

-- If has_sha512 is false, reset password via Gateway:
-- Use Web UI or API to trigger PasswordSyncService
```

---

### Issue: User can login via IMAP but not via Web UI

**Cause:** `password` (SHA512-CRYPT) is set but `password_bcrypt` is missing or invalid.

**Solution:**
```sql
-- Check both password columns
SELECT username,
       password LIKE '{SHA512-CRYPT}%' AS has_sha512,
       password_bcrypt LIKE '$2a$%' AS has_bcrypt
FROM users
WHERE username = 'user@example.com';

-- If has_bcrypt is false, reset password via Gateway API
-- This will generate both hashes
```

---

### Issue: Migration V3 fails with "column already exists"

**Cause:** V3 migration already applied or manual column creation.

**Solution:**
```sql
-- Check if column exists
SELECT column_name FROM information_schema.columns
WHERE table_name = 'users' AND column_name = 'password_bcrypt';

-- If exists, mark V3 as applied in Flyway
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
VALUES (
    (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history),
    '3',
    'add bcrypt password',
    'SQL',
    'V3__add_bcrypt_password.sql',
    NULL,
    'manual',
    0,
    true
);
```

---

### Issue: Authentication fails for all systems

**Cause:** Database connection issues or password columns corrupted.

**Solution:**
```sql
-- 1. Check database connectivity
SELECT current_database(), current_user, now();

-- 2. Check password column integrity
SELECT
    username,
    LENGTH(password) AS sha512_length,
    LENGTH(password_bcrypt) AS bcrypt_length,
    password LIKE '{SHA512-CRYPT}%' AS valid_sha512,
    password_bcrypt LIKE '$2a$%' AS valid_bcrypt
FROM users;

-- 3. Reset admin password via direct SQL (emergency only)
-- Generate new hash at: https://bcrypt-generator.com/
UPDATE users
SET
    password_bcrypt = '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN96E7VQnI.DK7ow3zPvu',
    password = '{SHA512-CRYPT}' || crypt('admin123', gen_salt('bf'))
WHERE username = 'admin@robin.local';
```

---

## Security Considerations

### Password Storage

- **BCrypt**: Adaptive cost factor (currently 12 rounds), resistant to brute force
- **SHA512-CRYPT**: Salted with 5000 rounds, Dovecot standard
- Both hashes use random salts (not reversible)
- Plain passwords **never** stored or logged

### Database Access

- **MTA**: Read/write access to all tables (trusted system)
- **Gateway**: Read/write access to `users` table only
- **Dovecot**: Read-only access to `users` table
- Use separate database users with minimal permissions

### Audit Trail

All password changes should be logged (application layer, not database):

```java
log.info("Password updated for user: {} by: {}", username, currentUser);
```

---

## References

### Gateway Documentation
- [DUAL_HASH_PASSWORD_STRATEGY.md](../../robin-gateway/DUAL_HASH_PASSWORD_STRATEGY.md)
- [IMPLEMENTATION_SUMMARY.md](../../robin-gateway/IMPLEMENTATION_SUMMARY.md)

### Database Schema
- [V2__add_spring_security_fields.sql](../../robin-gateway/src/main/resources/db/migration/V2__add_spring_security_fields.sql)
- [V3__add_bcrypt_password.sql](../../robin-gateway/src/main/resources/db/migration/V3__add_bcrypt_password.sql)

### Code References
- [PasswordSyncService.java](../../robin-gateway/src/main/java/com/robin/gateway/service/PasswordSyncService.java)
- [User.java](../../robin-gateway/src/main/java/com/robin/gateway/model/User.java)

---

## Contact

For questions or coordination of database changes:

- **Gateway Team**: Update robin-gateway repository
- **MTA Team**: Update robin-mta repository
- **Database Changes**: Coordinate via GitHub issues before implementing

---

**Document Version:** 1.0
**Last Updated:** 2026-01-29
**Next Review:** 2026-04-29
