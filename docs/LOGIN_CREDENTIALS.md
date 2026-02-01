# Robin UI Login Credentials

## Default Admin Account

### Credentials
- **Username**: `admin@robin.local`
- **Password**: `admin123`

> **Note**: The password is set to `admin123` by the V3 migration (`V3__add_bcrypt_password.sql`) which adds the `password_bcrypt` column used by the Gateway for authentication.

## Access URLs

- **Robin UI**: http://localhost:4200
- **Robin Gateway API**: http://localhost:8080
- **Gateway Health Check**: http://localhost:8080/actuator/health

## Quick Test

```bash
# Test login via API
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@robin.local","password":"admin123"}'
```

## Troubleshooting

If login still fails:

1. **Check Services Status**:
   ```bash
   docker ps | grep -E "robin|suite"
   ```

2. **Verify Gateway Health**:
   ```bash
   curl http://localhost:8080/actuator/health
   ```

3. **Check Gateway Logs**:
   ```bash
   docker logs robin-gateway --tail 50
   ```

4. **Verify Database**:
   ```bash
   docker exec suite-postgres psql -U robin -d robin \
     -c "SELECT username, is_active FROM users WHERE username='admin@robin.local';"
   ```

## Password Hash Details

The current BCrypt hash in the database:
- **Algorithm**: BCrypt with strength 12
- **Hash**: `$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN96E7VQnI.DK7ow3zPvu`
- **Plain Password**: `admin123`

## Security Note

⚠️ **For Development Only**: These are default development credentials. In production:
- Change the default password immediately
- Use strong, unique passwords
- Enable additional security measures (2FA, IP whitelist, etc.)
