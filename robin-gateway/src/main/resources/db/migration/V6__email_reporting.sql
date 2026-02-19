-- V5: Email Reporting Fields

ALTER TABLE domains
ADD COLUMN dmarc_policy VARCHAR(20),
ADD COLUMN dmarc_subdomain_policy VARCHAR(20),
ADD COLUMN dmarc_percentage INTEGER,
ADD COLUMN dmarc_alignment VARCHAR(10),
ADD COLUMN dmarc_reporting_email VARCHAR(255),
ADD COLUMN spf_includes TEXT,
ADD COLUMN spf_soft_fail BOOLEAN;
