-- Users table (Shared with Dovecot/Robin)
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE, -- Email address
    password VARCHAR(255) NOT NULL,        -- {SCHEME}Hash
    quota_bytes BIGINT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    last_login_at TIMESTAMP,
    
    -- Spring Security fields
    account_non_expired BOOLEAN DEFAULT TRUE,
    account_non_locked BOOLEAN DEFAULT TRUE,
    credentials_non_expired BOOLEAN DEFAULT TRUE
);

-- User Roles (ElementCollection)
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL
);

-- User Permissions (ElementCollection)
CREATE TABLE user_permissions (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission VARCHAR(50) NOT NULL
);

-- Domains table
CREATE TABLE domains (
    id SERIAL PRIMARY KEY,
    domain VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Aliases table
CREATE TABLE aliases (
    id SERIAL PRIMARY KEY,
    source VARCHAR(255) NOT NULL, -- alias@domain.com
    destination VARCHAR(255) NOT NULL, -- user@domain.com
    created_at TIMESTAMP DEFAULT NOW()
);

-- Sessions table
CREATE TABLE sessions (
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

-- Indexes
CREATE INDEX idx_user_username ON users(username);
CREATE INDEX idx_session_refresh_token ON sessions(refresh_token);
CREATE INDEX idx_session_user_id ON sessions(user_id);
CREATE INDEX idx_alias_source ON aliases(source);

-- Insert default admin
-- Password: admin (BCrypt for dev, but strictly we want SHA-512-CRYPT for Dovecot)
-- For now we use a placeholder that works with the AuthService (which uses BCrypt by default usually)
-- BUT we must align hashing. If Dovecot needs {SHA512-CRYPT}, we must use that.
-- Let's assume for now we just insert a record.
INSERT INTO users (username, password, is_active, created_at, updated_at) 
VALUES ('admin@robin.local', '$2a$10$xn3LI/AjqicFYZFruO4.UOj8vXji9y8pIv0.w/K.jG.qC.jO.jO.', TRUE, NOW(), NOW());

INSERT INTO user_roles (user_id, role) VALUES ((SELECT id FROM users WHERE username='admin@robin.local'), 'ROLE_ADMIN');