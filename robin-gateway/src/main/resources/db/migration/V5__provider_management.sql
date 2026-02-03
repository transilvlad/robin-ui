-- V5: Provider Management Schema

-- 1. Create Provider Configs table
CREATE TABLE provider_configs (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL, -- CLOUDFLARE, AWS_ROUTE53, GODADDY
    credentials TEXT NOT NULL, -- Encrypted JSON
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 2. Update Domains table
-- Drop old config columns
ALTER TABLE domains DROP COLUMN dns_provider_config;
ALTER TABLE domains DROP COLUMN registrar_provider_config;

-- Add Foreign Keys to provider_configs
ALTER TABLE domains ADD COLUMN dns_provider_id BIGINT REFERENCES provider_configs(id) ON DELETE SET NULL;
ALTER TABLE domains ADD COLUMN registrar_provider_id BIGINT REFERENCES provider_configs(id) ON DELETE SET NULL;

CREATE INDEX idx_domains_dns_provider ON domains(dns_provider_id);
CREATE INDEX idx_domains_registrar_provider ON domains(registrar_provider_id);
