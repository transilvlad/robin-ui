-- V4: Domain Management Schema

-- 1. Update domains table with configuration fields
ALTER TABLE domains 
ADD COLUMN status VARCHAR(20) DEFAULT 'PENDING',
ADD COLUMN dns_provider_type VARCHAR(20) DEFAULT 'MANUAL',
ADD COLUMN dns_provider_config TEXT,
ADD COLUMN registrar_provider_type VARCHAR(20) DEFAULT 'NONE',
ADD COLUMN registrar_provider_config TEXT,
ADD COLUMN renewal_date DATE,
ADD COLUMN nameservers TEXT, -- Stored as JSON string
ADD COLUMN dnssec_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN mta_sts_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN mta_sts_mode VARCHAR(20) DEFAULT 'NONE',
ADD COLUMN dane_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN bimi_selector VARCHAR(50),
ADD COLUMN bimi_logo_url VARCHAR(255),
ADD COLUMN dkim_selector_prefix VARCHAR(50) DEFAULT 'robin',
ADD COLUMN updated_at TIMESTAMP DEFAULT NOW();

-- 2. Create DNS Records table
CREATE TABLE dns_records (
    id SERIAL PRIMARY KEY,
    domain_id BIGINT NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    type VARCHAR(10) NOT NULL, -- MX, TXT, A, CNAME, etc.
    name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    ttl INTEGER DEFAULT 300,
    priority INTEGER,
    purpose VARCHAR(50) NOT NULL, -- DKIM, SPF, DMARC, etc.
    sync_status VARCHAR(20) DEFAULT 'PENDING',
    external_id VARCHAR(255),
    last_synced_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_dns_records_domain_id ON dns_records(domain_id);

-- 3. Create DKIM Keys table
CREATE TABLE dkim_keys (
    id SERIAL PRIMARY KEY,
    domain_id BIGINT NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    selector VARCHAR(50) NOT NULL,
    private_key TEXT NOT NULL, -- Encrypted
    public_key TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    activated_at TIMESTAMP,
    status VARCHAR(20) DEFAULT 'STANDBY'
);

CREATE INDEX idx_dkim_keys_domain_id ON dkim_keys(domain_id);
