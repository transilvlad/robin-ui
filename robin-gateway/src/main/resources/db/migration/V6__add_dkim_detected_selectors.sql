-- DKIM detected selectors from DNS pre-flight
CREATE TABLE dkim_detected_selectors (
    id BIGSERIAL PRIMARY KEY,
    domain VARCHAR(253) NOT NULL,
    selector VARCHAR(63) NOT NULL,
    public_key_dns TEXT,
    algorithm VARCHAR(10),
    test_mode BOOLEAN,
    revoked BOOLEAN DEFAULT FALSE,
    detected_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_dkim_detected_selectors_domain_selector UNIQUE (domain, selector)
);
