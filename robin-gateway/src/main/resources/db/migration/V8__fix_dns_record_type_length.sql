ALTER TABLE dns_records ALTER COLUMN type TYPE VARCHAR(10);
-- It seems it was already VARCHAR(10), but if there was a check constraint it should be removed or updated.
-- Since in V4 it was defined as VARCHAR(10) without an explicit CHECK constraint in the SQL provided (unless Hibernate generated one during ddl-auto which we don't use if flyway is active, or if the EnumType.STRING in JPA triggered something if using hbm2ddl).
-- However, standard VARCHAR doesn't enforce ENUM values unless a constraint is added.
-- The error 500 might be due to something else if DB is fine.
-- But wait! DnsRecordGenerator generates records.
-- If I click Sync, DomainSyncService calls generateExpectedRecords.
-- generateExpectedRecords logic:
-- ...
-- It adds CNAME, A, TXT, MX, SRV, PTR.
-- It does NOT seem to add AAAA records in the code I read.
-- So the `expectedRecords` list will NOT contain AAAA records.
-- `dnsRecordRepository.deleteByDomain(domain)` deletes all existing records (including the discovered AAAA ones).
-- `dnsRecordRepository.saveAll(expectedRecords)` saves the new list (without AAAA).
-- This should work fine and just remove the AAAA records.
-- WHY 500?
-- Maybe `dnsRecordRepository.deleteByDomain(domain)` fails?
-- Or `dnsRecordGenerator` fails?
-- Or `dnsProvider.createRecord` fails?
-- If provider is MANUAL, it skips createRecord.
-- If provider is Cloudflare/Route53, it calls createRecord.
-- The user domain has `dns_provider_type`?
-- In "Add Existing Domain", we default to MANUAL if they didn't select one.
-- But if they selected one?
-- Let's assume MANUAL for now.

-- Potential Issue: `dnsRecordRepository.deleteByDomain(domain)`
-- JPA repository method `deleteByDomain` usually requires a transaction. `syncDomain` has `@Transactional`.
-- Maybe `dnsRecordRepository` is not correctly defined?
-- It's an interface extending JpaRepository.

-- Wait! I see `DnsRecord` has `type` field mapped to `RecordType` enum.
-- If the DB contains `AAAA` (from discovery save), and we read it back?
-- `deleteByDomain` might fetch entities first then delete them.
-- If fetching fails because `AAAA` is in DB but not in Enum...
-- BUT I added `AAAA` to Enum in `DnsRecord.java`.
-- So fetching should work.

-- Let's check `DnsDiscoveryService.java` again. I updated it to use `AAAA` in enum.
-- The discovery worked and SAVED the records. So `AAAA` is in DB.
-- When we call `syncDomain`, it calls `dnsRecordGenerator`.
-- `dnsRecordGenerator` does NOT produce AAAA.
-- Then `deleteByDomain` is called.
-- If I added `AAAA` to Java Enum, reading from DB is fine.

-- What if `DnsRecordGenerator` fails?
-- It uses `configService.getConfig("email_reporting")`.
-- It uses `dkimService`.
-- It uses `certService`.
-- If any of these throw, it fails.
-- The user logs show 500.

-- Another possibility: The `type` column length.
-- `AAAA` is 4 chars. `VARCHAR(10)` is fine.

-- What about `DnsRecord.RecordPurpose`?
-- I didn't change it.

-- Let's look at `DomainSyncService.java` again.
-- `dnsRecordRepository.deleteByDomain(domain);`
-- `dnsRecordRepository.saveAll(expectedRecords);`

-- If `deleteByDomain` is a derived query method, does it need `@Modifying`?
-- Yes, if it's a custom query. But `deleteBy...` is standard in Spring Data JPA.
-- However, standard `deleteBy` fetches and deletes one by one (inefficient) or in batch?
-- If it fetches, and there is a mapping issue, it fails.
-- But `AAAA` is in Enum.

-- Let's consider `dnsRecordGenerator`.
-- Does it fail if `serverConfig` is missing? 
-- It catches exception and warns.
-- Does it fail if `dkimService.getKeysForDomain` fails?
-- It calls `dkimService.generateKey`.

-- Maybe the issue is `ProviderConfig`?
-- I added `EMAIL` to `ProviderType`.
-- If `Domain` has `emailProvider` set, does `syncDomain` touch it?
-- `DomainSyncService` uses `dnsProviderFactory.getProvider(domain.getDnsProviderType())`.
-- If `dnsProviderType` is null? 
-- `Domain` has default `MANUAL`.
-- But if `Add Existing Domain` set it to something else?
-- In `DomainWizardComponent`, if user selects "Manual", `dnsProviderId` is null.
-- But `dnsProviderType` should be set to `MANUAL`.
-- Let's check `DomainService.createDomain`.
-- It sets `dnsProviderType` based on the provider config type if `dnsProviderId` is passed.
-- If `dnsProviderId` is NULL, it stays default `MANUAL`.
-- So that should be fine.

-- Wait, in `DomainService.createDomain`, I added:
/*
            if (dnsProviderId != null) {
                providerConfigRepository.findById(dnsProviderId).ifPresent(p -> {
                    builder.dnsProvider(p);
                    builder.dnsProviderType(Domain.DnsProviderType.valueOf(p.getType().name()));
                });
            }
*/
-- If `p.getType()` is `EMAIL`?
-- `DnsProviderType` enum only has `MANUAL`, `CLOUDFLARE`, `AWS_ROUTE53`.
-- `ProviderType` has `EMAIL` now.
-- If a user somehow selected an EMAIL provider as a DNS provider (UI filters it out, so unlikely), `valueOf` would crash.
-- But the UI filters `dnsProviders` to only include CLOUDFLARE/AWS.

-- Back to `syncDomain`.
-- `dnsRecordRepository.deleteByDomain(domain)`
-- Could it be foreign key constraint?
-- `dns_records` references `domains`. Deleting records is fine.

-- Is it possible `DnsRecord` entity `type` enum mapping?
-- I added `AAAA` to `RecordType`.
-- Did I restart the application?
-- The user environment "Hot Module Replacement disabled...". That's frontend.
-- The backend (Java) needs a restart/recompile.
-- I ran `RUN mvn clean package` in the Dockerfile check earlier and it failed.
-- Then I fixed the syntax error.
-- Then I tried `mvn compile` again and it failed because `mvn` not found.
-- I *assumed* the user/environment handles the rebuild/restart if I change files?
-- "The project is configured to run on port 8880 locally via Docker."
-- If I change code, does the running container update?
-- Usually not unless there is devtools/hotswap.
-- If the backend is running a PREVIOUS version of the code (without AAAA in Enum), and the DB contains "AAAA" (how? maybe it doesn't yet?),
-- Wait, if the previous code didn't have AAAA, `discovery` service wouldn't compile or wouldn't run the new logic.
-- So the discovery request worked, meaning the code IS updated and running.
-- So `AAAA` IS in the Enum.

-- Let's look closer at `DomainSyncService`.
-- `dnsRecordRepository.deleteByDomain(domain);`
-- If this deletes *by entity*, it might try to cascade or something?
-- `DnsRecord` owns the relation `domain_id`.

-- What if `expectedRecords` contains a duplicate?
-- `saveAll` might fail if there is a unique constraint?
-- `dns_records` has no unique constraint on `(domain_id, name, type)`.

-- What about `dkimService.generateKey`?
-- If `syncDomain` runs, it generates records.
-- `DnsRecordGenerator` calls `dkimService.getKeysForDomain`.
-- If keys don't exist, it calls `dkimService.generateKey`.
-- If `generateKey` fails? (e.g. database lock, or key generation error).

-- I suspect the error might be simpler.
-- `DnsRecord` has `purpose` column.
-- I didn't change `RecordPurpose` enum.
-- Discovery service sets `purpose` to `MX` for AAAA records. `DnsRecord.RecordPurpose.MX`.
-- `RecordPurpose` has `MX`.

-- Let's double check `DnsRecord.java` enum vs `V4__domain_management.sql`.
-- V4: `purpose VARCHAR(50)`.
-- Enum: `DKIM, SPF, DMARC, ... MX`.
-- All good.

-- Is there any other field?
-- `content` TEXT.
-- `name` VARCHAR(255).

-- Wait! I noticed `DnsDiscoveryService` uses `DnsRecord.builder()...build()`.
-- It sets `syncStatus(DnsRecord.SyncStatus.SYNCED)`.
-- `syncStatus` is `VARCHAR(20)`. Enum `PENDING, SYNCED, ERROR`.

-- What if `DnsRecordGenerator` creates records with null fields?
-- `DnsRecordGenerator` uses `builder`.
-- `syncStatus` is not set in `DnsRecordGenerator`.
-- `DnsRecord` class: `private SyncStatus syncStatus;` (default null).
-- Database: `sync_status VARCHAR(20) DEFAULT 'PENDING'`.
-- If Hibernate inserts null, does it default?
-- Usually JPA inserts null unless `@DynamicInsert` is used or pre-persist sets it.
-- `DnsRecord` has `@PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }`.
-- It does NOT set default `syncStatus`.
-- So it inserts NULL.
-- DB column allows NULL? `DEFAULT 'PENDING'` only works if column is omitted in insert, or explicit `DEFAULT` keyword used. Hibernate inserts explicit `NULL`.
-- Is the column nullable?
-- `V4`: `sync_status VARCHAR(20) DEFAULT 'PENDING'`. It does NOT say `NOT NULL`.
-- So NULL is allowed.

-- So what is failing?
-- Maybe `DnsDiscoveryService` returns a list of records.
-- The user said "Autodicovery only gets..." (the short list).
-- I fixed it.
-- Now user says "domain-detail.component.ts:97 POST ... 500".
-- This is `syncDomain`.
-- `syncDomain` calls `dnsRecordGenerator`.
-- `dnsRecordGenerator` does NOT call `discovery`.
-- `dnsRecordGenerator` generates *expected* records based on config.
-- It generates A, MX, SPF, DMARC, DKIM, MTA-STS, BIMI, TLSA, PTR, Client Config, SRV.
-- It creates `A` record with `gatewayIp`.
-- It does NOT create `AAAA` record.
-- So `saveAll` saves A, MX, etc.
-- But it deleted existing `AAAA` records (from discovery).
-- This logic seems "correct" (it enforces expected state), even if aggressive (deletes discovered records that aren't expected).
-- But why 500?

-- Could it be `dnsRecordRepository.deleteByDomain(domain)`?
-- If `domain` is detached?
-- `Domain` object comes from `domainRepository.findById(domainId)`. It is managed.

-- Maybe `DnsProviderFactory`?
-- `dnsProviderFactory.getProvider(domain.getDnsProviderType())`.
-- `domain.getDnsProviderType()` is `MANUAL`.
-- `DnsProviderFactory`:
-- I should check `DnsProviderFactory.java`.

-- Also, `DnsRecordRepository` is likely an interface.
-- `deleteByDomain` needs to be defined.
-- If it's `void deleteByDomain(Domain domain)`, it should work.

-- Let's check `DnsProviderFactory.java`.
