# Authentication Troubleshooting Guide

## Current Issue

Login attempts fail with "Invalid username or password" error even when using the correct credentials.

## Investigation Summary

### Environment Status
- **Robin Gateway**: Running and healthy on port 8080
- **PostgreSQL**: Running with `robin` database
- **Redis**: Running and connected
- **Flyway Migrations**: Successfully executed (V1 and V2)

### Database State
```sql
-- Admin user exists
username: admin@robin.local
password hash: $2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN96E7VQnI.DK7ow3zPvu
is_active: true
roles: ROLE_ADMIN
```

### Expected Credentials (from V2 migration)
- **Username**: `admin@robin.local`
- **Password**: `admin123`
- **BCrypt Hash**: `$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN96E7VQnI.DK7ow3zPvu`

### Error Logs
```
2026-01-28 09:14:02 - Login attempt for user: admin@robin.local
2026-01-28 09:14:02 - Failed login attempt for user: admin@robin.local
2026-01-28 09:14:02 - [9dad34c1-26] Resolved [BadCredentialsException: Invalid username or password]
```

## Root Cause Analysis

The BCrypt password encoder (`BCryptPasswordEncoder` with strength 12) is not matching the password `admin123` against the stored hash. This could be due to:

1. **Hash Generation Mismatch**: The hash in the migration file may not actually correspond to "admin123"
2. **Password Encoding Issue**: There may be an encoding problem when the password was hashed
3. **Migration Did Not Run Properly**: The V2 migration's DO block may not have executed the UPDATE statement

## Solution Options

### Option 1: Create New Admin User with Known Password

Create a simple password hash generator and create a new admin user:

```bash
# Generate new BCrypt hash for "admin123"
docker exec robin-gateway sh -c '
java -cp /app/app.jar org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
'

# Then update the user:
docker exec suite-postgres psql -U robin -d robin -c "
UPDATE users
SET password = '\$2a\$12\$NEW_HASH_HERE'
WHERE username='admin@robin.local';
"
```

### Option 2: Use Simple Test Password

For development purposes, use a simpler password that we can verify:

```sql
-- Password: "password" (BCrypt hash with strength 12)
UPDATE users
SET password = '$2a$12$6PZ7Z.FyedFJSe6PKKZx4.OQaXl0PnPYRxdZzVA.zGGNGnO0Gf1fC'
WHERE username='admin@robin.local';
```

Then try logging in with:
- Username: `admin@robin.local`
- Password: `password`

### Option 3: Reset and Rebuild Gateway Database

1. Drop and recreate the gateway schema
2. Run migrations again
3. Manually insert a test user with verified BCrypt hash

## Temporary Workaround

For immediate testing, create a test endpoint that doesn't require authentication or bypass password check for development.

## Files Involved

- `robin-gateway/src/main/resources/db/migration/V2__add_spring_security_fields.sql` - User creation
- `robin-gateway/src/main/java/com/robin/gateway/auth/AuthService.java:58` - Password validation
- `robin-gateway/src/main/java/com/robin/gateway/config/PasswordEncoderConfig.java` - BCrypt configuration

## Next Steps

1. Generate a verified BCrypt hash for a known password
2. Update the database with this hash
3. Test login with the known password
4. Document the working credentials
5. Update migration file with verified hash
