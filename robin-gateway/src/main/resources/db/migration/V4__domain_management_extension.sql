-- DNS/NS provider profiles
CREATE TABLE dns_providers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL CHECK (type IN ('CLOUDFLARE', 'AWS_ROUTE53')),
    credentials JSONB NOT NULL,        -- AES-256 encrypted at rest
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Extend domains table
ALTER TABLE domains
    ADD COLUMN dns_provider_id BIGINT REFERENCES dns_providers(id),
    ADD COLUMN ns_provider_id BIGINT REFERENCES dns_providers(id),
    ADD COLUMN status VARCHAR(50) DEFAULT 'PENDING',  -- PENDING, ACTIVE, ERROR
    ADD COLUMN last_health_check TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP DEFAULT NOW();

-- DNS templates
CREATE TABLE dns_templates (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    records JSONB NOT NULL,  -- array of {type, name, value, ttl, priority?}
    created_at TIMESTAMP DEFAULT NOW()
);

-- DNS records managed per domain
CREATE TABLE domain_dns_records (
    id SERIAL PRIMARY KEY,
    domain_id BIGINT NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    record_type VARCHAR(10) NOT NULL,   -- MX, TXT, CNAME, A, AAAA
    name VARCHAR(255) NOT NULL,
    value TEXT NOT NULL,
    ttl INTEGER DEFAULT 3600,
    priority INTEGER,
    provider_record_id VARCHAR(255),    -- Cloudflare/AWS record ID for updates
    managed BOOLEAN DEFAULT true,       -- false = imported/read-only
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- DKIM key pairs per domain
CREATE TABLE dkim_keys (
    id SERIAL PRIMARY KEY,
    domain_id BIGINT NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    selector VARCHAR(255) NOT NULL,
    algorithm VARCHAR(20) NOT NULL CHECK (algorithm IN ('RSA_2048', 'ED25519')),
    private_key TEXT NOT NULL,          -- AES-256 encrypted
    public_key TEXT NOT NULL,
    cname_selector VARCHAR(255),        -- for CNAME rotation: points to this selector
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ROTATING', 'RETIRED')),
    created_at TIMESTAMP DEFAULT NOW(),
    retired_at TIMESTAMP,
    UNIQUE(domain_id, selector)
);

-- Domain health status per check type
CREATE TABLE domain_health (
    id SERIAL PRIMARY KEY,
    domain_id BIGINT NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    check_type VARCHAR(20) NOT NULL,    -- SPF, DKIM, DMARC, MTA_STS, MX, NS
    status VARCHAR(10) NOT NULL,        -- OK, WARN, ERROR, UNKNOWN
    message TEXT,
    last_checked TIMESTAMP DEFAULT NOW(),
    UNIQUE(domain_id, check_type)
);

-- Cloudflare Worker deployments
CREATE TABLE mta_sts_workers (
    id SERIAL PRIMARY KEY,
    domain_id BIGINT NOT NULL REFERENCES domains(id) ON DELETE CASCADE UNIQUE,
    worker_name VARCHAR(255) NOT NULL,
    worker_id VARCHAR(255),             -- Cloudflare Worker script ID
    policy_mode VARCHAR(20) DEFAULT 'testing' CHECK (policy_mode IN ('testing', 'enforce', 'none')),
    policy_version VARCHAR(50),
    deployed_at TIMESTAMP,
    status VARCHAR(20) DEFAULT 'PENDING'  -- PENDING, DEPLOYED, ERROR
);
