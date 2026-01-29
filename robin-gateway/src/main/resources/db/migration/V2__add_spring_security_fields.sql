-- Add Spring Security and additional fields to users table
-- This migration makes the existing Dovecot users table compatible with Spring Security

-- Add username column (copy from email if doesn't exist)
ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(255);
UPDATE users SET username = email WHERE username IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON users(username);

-- Add Spring Security fields
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_non_expired BOOLEAN DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_non_locked BOOLEAN DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS credentials_non_expired BOOLEAN DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;

-- Add timestamp fields
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW();
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

-- Add quota field
ALTER TABLE users ADD COLUMN IF NOT EXISTS quota_bytes BIGINT DEFAULT 0;

-- Create user_roles table if not exists
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL
);

-- Create user_permissions table if not exists
CREATE TABLE IF NOT EXISTS user_permissions (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission VARCHAR(50) NOT NULL
);

-- Create sessions table if not exists
CREATE TABLE IF NOT EXISTS sessions (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    revoked BOOLEAN DEFAULT FALSE,
    revoked_at TIMESTAMP
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_session_refresh_token ON sessions(refresh_token);
CREATE INDEX IF NOT EXISTS idx_session_user_id ON sessions(user_id);

-- Ensure admin user exists with proper setup
-- First check if admin@robin.local exists
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@robin.local' OR username = 'admin@robin.local') THEN
        -- Insert admin user with BCrypt password for 'admin123'
        INSERT INTO users (email, username, password, is_active, account_non_expired, account_non_locked, credentials_non_expired, created_at, updated_at, uid, gid)
        VALUES (
            'admin@robin.local',
            'admin@robin.local',
            '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN96E7VQnI.DK7ow3zPvu', -- admin123
            TRUE,
            TRUE,
            TRUE,
            TRUE,
            NOW(),
            NOW(),
            5000,
            5000
        );
    ELSE
        -- Update existing admin user
        UPDATE users
        SET username = 'admin@robin.local',
            password = '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN96E7VQnI.DK7ow3zPvu',
            is_active = TRUE,
            account_non_expired = TRUE,
            account_non_locked = TRUE,
            credentials_non_expired = TRUE
        WHERE email = 'admin@robin.local' OR username = 'admin@robin.local';
    END IF;
END $$;

-- Add ROLE_ADMIN to admin user if not exists
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_ADMIN'
FROM users
WHERE username = 'admin@robin.local'
AND NOT EXISTS (
    SELECT 1 FROM user_roles
    WHERE user_id = (SELECT id FROM users WHERE username = 'admin@robin.local')
    AND role = 'ROLE_ADMIN'
);
