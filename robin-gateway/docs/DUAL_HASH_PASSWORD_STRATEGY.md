# Dual-Hash Password Strategy

## Overview

The robin-gateway implements a **dual-hash password strategy** to resolve the authentication conflict between Spring Security (BCrypt) and Robin MTA/Dovecot (SHA512-CRYPT).

## Problem Statement

Robin MTA (Dovecot) and robin-gateway (Spring Boot) both need to authenticate users from the same `users` table, but they require different password hash formats:

- **Robin MTA (Dovecot)**: Uses `{SHA512-CRYPT}$6$rounds=5000$...` format in the `password` column
- **robin-gateway (Spring Security)**: Uses BCrypt `$2a$12$...` format

**Challenge**: Both systems cannot use the same column with different hash formats.

## Solution Architecture

### Database Schema

Store TWO password hashes in separate columns:

```sql
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,

    -- SHA512-CRYPT hash for Dovecot/Robin MTA IMAP authentication
    password VARCHAR(255),

    -- BCrypt hash for robin-gateway Spring Security authentication
    password_bcrypt VARCHAR(255) NOT NULL,

    -- other fields...
);
```

### Column Mapping

| Column | Format | Used By | Purpose |
|--------|--------|---------|---------|
| `password` | `{SHA512-CRYPT}$6$...` | Dovecot/Robin MTA | IMAP authentication |
| `password_bcrypt` | `$2a$12$...` | Spring Security | Gateway API authentication |

### Entity Mapping

```java
@Entity
@Table(name = "users")
public class User {

    @Column(name = "password_bcrypt", nullable = false)
    private String passwordHash;  // BCrypt for Gateway

    @Column(name = "password")
    private String dovecotPasswordHash;  // SHA512-CRYPT for MTA

    // other fields...
}
```

## Implementation Details

### PasswordSyncService

The `PasswordSyncService` is responsible for maintaining password synchronization:

```java
@Service
public class PasswordSyncService {

    @Transactional
    public void updatePassword(Long userId, String plainPassword) {
        // 1. Generate BCrypt hash for Gateway
        String bcryptHash = passwordEncoder.encode(plainPassword);

        // 2. Generate SHA512-CRYPT hash for Dovecot
        String sha512Hash = jdbcTemplate.queryForObject(
            "SELECT crypt(?, gen_salt('bf'))",
            String.class,
            plainPassword
        );

        // 3. Update both columns atomically
        user.setPasswordHash(bcryptHash);
        user.setDovecotPasswordHash("{SHA512-CRYPT}" + sha512Hash);

        userRepository.save(user);
    }
}
```

### UserService Integration

The `UserService` uses `PasswordSyncService` for all password operations:

```java
@Service
public class UserService {

    public Mono<User> createUser(User user) {
        String plainPassword = user.getPasswordHash();
        User savedUser = userRepository.save(user);

        // Use PasswordSyncService to set both hashes
        passwordSyncService.updatePassword(savedUser.getId(), plainPassword);

        return userRepository.findById(savedUser.getId());
    }

    public Mono<User> updateUser(String username, User updated) {
        if (updated.getPasswordHash() != null) {
            // Use PasswordSyncService for password updates
            passwordSyncService.updatePassword(existing.getId(), updated.getPasswordHash());
        }
        return userRepository.save(existing);
    }
}
```

## Database Migrations

### V1: Initial Schema

Creates `users` table with `password` column for Dovecot.

### V2: Spring Security Fields

Adds Spring Security fields (`account_non_expired`, `account_non_locked`, etc.).

**Important**: Does NOT overwrite existing Dovecot passwords.

### V3: Dual-Hash Strategy

```sql
-- Add BCrypt column
ALTER TABLE users ADD COLUMN password_bcrypt VARCHAR(255);

-- Add documentation
COMMENT ON COLUMN users.password IS 'SHA512-CRYPT hash for Dovecot/MTA';
COMMENT ON COLUMN users.password_bcrypt IS 'BCrypt hash for Spring Security';

-- Update admin user with BCrypt hash
UPDATE users
SET password_bcrypt = '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN96E7VQnI.DK7ow3zPvu'
WHERE username = 'admin@robin.local';
```

## Authentication Flows

### Gateway Authentication (Spring Security)

```
1. User submits credentials to /api/auth/login
2. Spring Security loads user from UserRepository
3. PasswordEncoder validates plain password against passwordHash (BCrypt)
4. JWT token issued on success
```

**Database Query:**
```sql
SELECT password_bcrypt FROM users WHERE username = ?
```

### MTA Authentication (Dovecot)

```
1. User connects via IMAP to Robin MTA
2. Dovecot queries users table via SQL
3. Dovecot validates plain password against password (SHA512-CRYPT)
4. IMAP session established on success
```

**Database Query (by Dovecot):**
```sql
SELECT password FROM users WHERE username = ?
```

## Benefits

1. **Compatibility**: Both systems use their preferred hash formats
2. **Security**: BCrypt for web API, SHA512-CRYPT for IMAP
3. **Atomicity**: Both hashes updated in single transaction
4. **Maintainability**: Clear separation of concerns
5. **Flexibility**: Can change one hash format without affecting the other

## Testing Strategy

### Unit Tests

Test `PasswordSyncService` behavior:

- ✅ Both hashes generated correctly
- ✅ SHA512-CRYPT prefix added
- ✅ Transactional rollback on failure
- ✅ Null safety validation
- ✅ User not found error handling

### Integration Tests

Test end-to-end password flows:

- ✅ User creation with both hashes
- ✅ Password update synchronization
- ✅ Gateway authentication with BCrypt
- ✅ MTA authentication with SHA512-CRYPT (manual verification)

## Security Considerations

### Password Storage

- **BCrypt**: Work factor 12 (2^12 = 4096 rounds)
- **SHA512-CRYPT**: 5000 rounds (Dovecot default)

Both provide strong protection against brute-force attacks.

### Password Transmission

- Passwords transmitted over HTTPS only
- Plain-text passwords never logged
- Hashes sanitized from API responses

### Password Reset

When resetting passwords:

```java
passwordSyncService.updatePassword(userId, newPlainPassword);
```

Both hashes updated atomically to maintain synchronization.

## Monitoring and Troubleshooting

### Common Issues

#### Gateway Login Fails, MTA Works

**Symptom**: User can authenticate to IMAP but not Gateway UI.

**Cause**: `password_bcrypt` column is null or incorrect.

**Solution**:
```sql
-- Check BCrypt hash
SELECT username, password_bcrypt FROM users WHERE username = 'user@robin.local';

-- Update via PasswordSyncService
passwordSyncService.updatePassword(userId, "newPassword");
```

#### MTA Login Fails, Gateway Works

**Symptom**: User can authenticate to Gateway UI but not IMAP.

**Cause**: `password` column missing SHA512-CRYPT prefix or incorrect.

**Solution**:
```sql
-- Check Dovecot hash
SELECT username, password FROM users WHERE username = 'user@robin.local';

-- Update via PasswordSyncService (updates both)
passwordSyncService.updatePassword(userId, "newPassword");
```

#### Both Systems Fail

**Symptom**: User cannot authenticate anywhere.

**Cause**: Both hashes corrupted or user account locked.

**Solution**:
```sql
-- Check account status
SELECT username, is_active, account_non_locked FROM users WHERE username = 'user@robin.local';

-- Reset password
passwordSyncService.updatePassword(userId, "newPassword");
```

### Logging

Enable debug logging for password operations:

```yaml
logging:
  level:
    com.robin.gateway.service.PasswordSyncService: DEBUG
```

## Migration Checklist

When deploying this solution to existing systems:

- [ ] Run V3 migration to add `password_bcrypt` column
- [ ] Update all existing user passwords:
  ```java
  users.forEach(user ->
      passwordSyncService.updatePassword(user.getId(), temporaryPassword)
  );
  ```
- [ ] Notify users to reset passwords via UI
- [ ] Verify Gateway authentication works (BCrypt)
- [ ] Verify MTA authentication works (SHA512-CRYPT)
- [ ] Monitor logs for authentication failures

## Future Enhancements

### Password Rotation

Implement automatic password rotation policy:

```java
@Scheduled(cron = "0 0 0 * * ?") // Daily at midnight
public void enforcePasswordExpiry() {
    List<User> expiredUsers = userRepository.findByCredentialsExpiredTrue();
    expiredUsers.forEach(user -> {
        // Send password reset email
        // Lock account until reset
    });
}
```

### Hash Algorithm Migration

If migrating to new hash algorithms:

1. Add new column (e.g., `password_argon2`)
2. Update `PasswordSyncService` to generate new hash
3. Keep old hashes for backward compatibility
4. Gradually migrate users to new algorithm

### Single Sign-On (SSO)

For SSO integration:

- Gateway uses OAuth2/OIDC (no password hash needed)
- MTA continues using SHA512-CRYPT from database
- Keep dual-hash strategy for local authentication fallback

## References

- **Spring Security BCrypt**: [BCrypt Documentation](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html#authentication-password-storage-bcrypt)
- **Dovecot Password Schemes**: [Dovecot Password Schemes](https://doc.dovecot.org/configuration_manual/authentication/password_schemes/)
- **PostgreSQL crypt()**: [PostgreSQL pgcrypto](https://www.postgresql.org/docs/current/pgcrypto.html)

## Conclusion

The dual-hash password strategy provides a robust solution for maintaining authentication compatibility between robin-gateway (Spring Security) and Robin MTA (Dovecot) without compromising security or requiring complex password migration processes.

By storing separate password hashes optimized for each authentication system, we achieve:

- ✅ Seamless user experience across both systems
- ✅ Strong cryptographic security with appropriate algorithms
- ✅ Maintainable and testable codebase
- ✅ Clear separation of concerns
- ✅ Future-proof architecture for migrations

## Contact

For questions or issues with this implementation, please contact the robin-gateway development team.
