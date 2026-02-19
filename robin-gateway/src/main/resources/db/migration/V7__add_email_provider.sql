-- Add email_provider_id to domains table
ALTER TABLE domains ADD COLUMN email_provider_id BIGINT;
ALTER TABLE domains ADD CONSTRAINT fk_domains_email_provider FOREIGN KEY (email_provider_id) REFERENCES provider_configs(id);

-- Update provider_configs enum type check if it exists (some DBs don't enforce it at SQL level if it's just a VARCHAR)
-- In V5, it was created as:
-- type VARCHAR(20) NOT NULL
-- So no strict check constraint to update, just the application level Enum.
