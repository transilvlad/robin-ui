-- credentials stores AES-256 encrypted ciphertext, not raw JSON.
-- Change from JSONB to TEXT so PostgreSQL stops validating JSON syntax.
ALTER TABLE dns_providers
    ALTER COLUMN credentials TYPE TEXT USING credentials::TEXT;
