-- Add dual-hash password strategy for Spring Security (BCrypt) + Dovecot (SHA512-CRYPT)
-- This migration resolves the hash format conflict:
-- - password_bcrypt → BCrypt hash for robin-gateway Spring Security authentication
-- - password → SHA512-CRYPT hash for Dovecot/Robin MTA IMAP authentication

-- Add BCrypt password column for Spring Security
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_bcrypt VARCHAR(255);

COMMENT ON COLUMN users.password IS 'SHA512-CRYPT hash with {SHA512-CRYPT} prefix for Dovecot/Robin MTA IMAP authentication';
COMMENT ON COLUMN users.password_bcrypt IS 'BCrypt hash for robin-gateway Spring Security authentication';

-- Create index on BCrypt password for performance
CREATE INDEX IF NOT EXISTS idx_users_password_bcrypt ON users(password_bcrypt);

-- Update admin user with BCrypt hash (password: admin123)
-- This allows admin to authenticate via robin-gateway
UPDATE users
SET password_bcrypt = '$2b$12$mfZiAXG7t4gW9G3hspPrnugU2F2gcnG3YyF70W49PiP8i7AqJRMt.'
WHERE username = 'admin@robin.local';

-- Note: The 'password' column still contains the original hash from V1/V2 migrations
-- When setting passwords via PasswordSyncService, both columns are updated:
-- 1. password_bcrypt = BCrypt hash (for Gateway authentication)
-- 2. password = {SHA512-CRYPT}hash (for Dovecot IMAP authentication)
