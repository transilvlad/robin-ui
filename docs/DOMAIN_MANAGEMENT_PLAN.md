# Domain Management & DNS Integration Plan

## 1. Overview
This feature enables the Robin system to manage email domains comprehensively. It includes:
*   **DNS Management**: Automatic generation and syncing of DNS records (MX, SPF, DKIM, DMARC, TLSA, MTA-STS, BIMI).
*   **Registrar Integration**: Integration with Registrars (Cloudflare, AWS Route53, GoDaddy) to manage Nameservers (NS), fetch domain details (expiration), and handle DNSSEC.
*   **Protocol Support**: Full support for modern email security standards:
    *   **SPF, DKIM, DMARC**: Authentication and policy.
    *   **DNSSEC**: Domain name system security extensions.
    *   **DANE**: TLSA records for SMTP transport security.
    *   **MTA-STS**: Enforced TLS policies via DNS and HTTPS.
    *   **BIMI**: Brand Indicators for Message Identification.

## 2. Architecture

### 2.1 Backend (robin-gateway)

#### Entities
*   **Domain (Updated)**:
    *   `id` (PK)
    *   `domain` (VARCHAR, Unique)
    *   `status` (Enum: PENDING, VERIFIED, FAILED, ACTIVE)
    *   **DNS Configuration**:
        *   `dns_provider_type` (Enum: MANUAL, CLOUDFLARE, AWS_ROUTE53)
        *   `dns_provider_config` (Encrypted JSON)
    *   **Registrar Configuration**:
        *   `registrar_provider_type` (Enum: NONE, MANUAL, CLOUDFLARE, AWS_ROUTE53, GODADDY)
        *   `registrar_provider_config` (Encrypted JSON)
    *   **Domain Info**:
        *   `renewal_date` (DATE)
        *   `nameservers` (String Array/JSON) - Current active NS.
    *   **Security Settings**:
        *   `dnssec_enabled` (BOOLEAN)
        *   `mta_sts_enabled` (BOOLEAN)
        *   `mta_sts_mode` (Enum: TESTING, ENFORCE, NONE)
        *   `dane_enabled` (BOOLEAN)
        *   `bimi_selector` (VARCHAR)
        *   `bimi_logo_url` (VARCHAR)
    *   `dkim_selector_prefix` (VARCHAR, Default: "robin")
    *   `created_at`, `updated_at`

*   **DnsRecord**:
    *   `id` (PK)
    *   `domain_id` (FK -> Domain)
    *   `type` (Enum: MX, TXT, CNAME, A, TLSA, NS, DS)
    *   `name` (VARCHAR)
    *   `content` (TEXT)
    *   `ttl` (INTEGER)
    *   `priority` (INTEGER, nullable)
    *   `purpose` (Enum: DKIM, SPF, DMARC, MTA_STS_RECORD, MTA_STS_POLICY_HOST, DANE, BIMI, DNSSEC, NS, VERIFICATION, MX)
    *   `sync_status` (Enum: SYNCED, PENDING, ERROR)
    *   `external_id` (VARCHAR)
    *   `last_synced_at` (TIMESTAMP)

*   **DkimKey**: (Unchanged from previous plan)

#### Services
*   **DomainService**: Core CRUD.
*   **RegistrarService**: Orchestrates registrar interactions.
    *   `fetchDomainInfo(domain)`: Updates renewal date, status.
    *   `updateNameservers(domain, nsList)`: Changes NS at registrar.
    *   `getDnssecRecords(domain)`: Fetches DS records.
*   **DnsRecordGenerator**: Logic to construct expected records. Updated to include:
    *   *TLSA*: `_25._tcp.<domain> IN TLSA ...` (Requires Cert Hash).
    *   *MTA-STS*:
        *   TXT `_mta-sts.<domain>` `v=STSv1; id=...`
        *   A/CNAME `mta-sts.<domain>` -> Pointing to Robin Gateway (or configured proxy).
    *   *BIMI*: TXT `default._bimi.<domain>` `v=BIMI1; l=<url>;`.
*   **MtaStsHandler**:
    *   Serves `https://mta-sts.<domain>/.well-known/mta-sts.txt`.
    *   Dynamically generates policy based on `Domain` configuration.
*   **CertService**:
    *   Retrieves the public key/certificate used by the Postfix/Dovecot instance to generate TLSA records.
*   **Provider Factories**:
    *   `DnsProviderFactory`: Returns `DnsProvider`.
    *   `RegistrarProviderFactory`: Returns `RegistrarProvider`.
*   **Integrations (Interfaces)**:
    *   `DnsProvider`: `listRecords`, `createRecord`, `updateRecord`, `deleteRecord`.
    *   `RegistrarProvider`:
        *   `DomainInfo getDomainDetails(String domainName)`
        *   `void updateNameservers(String domainName, List<String> nameservers)`
        *   `List<DnsRecord> getDsRecords(String domainName)` (For DNSSEC)

### 2.2 Frontend (robin-ui)

#### Module: `Features/Domains`
*   **DomainList**: Enhanced with columns for Renewal Date, Registrar, DNSSEC status.
*   **DomainWizard**: Updated steps:
    *   ...
    *   **Step 2a**: Registrar Selection (GoDaddy, AWS, Cloudflare, Manual).
    *   **Step 2b**: DNS Selection.
    *   **Step 3**: Credentials (for both).
*   **DomainDetail**:
    *   **Overview**: Renewal countdown, Status.
    *   **Registrar Tab**: Manage Nameservers, View/Sync Renewal Date.
    *   **DNS Tab**: Records management.
    *   **Security Tab (New)**:
        *   **DNSSEC**: Toggle (Instructions if Manual, Auto-setup if supported).
        *   **MTA-STS**: Mode selector (Testing/Enforce), Policy ID.
        *   **DANE**: Enable/Disable (Auto-generates TLSA).
        *   **BIMI**: Upload Logo / Set URL.
    *   **DKIM Tab**: (Same as before).

## 3. Implementation Phases

### Phase 1: Database & Core Entities (Enhanced)
1.  **Dependencies**: AWS SDK (Route53 + Route53Domains), Cloudflare API client (custom WebClient), GoDaddy API client.
2.  **Schema**: Create/Update tables with new fields (Registrar config, Security flags).
3.  **Entities**: JPA mappings.

### Phase 2: Core Logic (DANE, MTA-STS, BIMI)
1.  **MtaStsController**: Create an endpoint to serve `.well-known/mta-sts.txt`.
    *   Must handle multi-tenancy (check Host header).
2.  **CertHelper**: Utility to read the system's SSL cert (from file path or KeyStore) and generate SHA-256 hash for TLSA.
3.  **DnsRecordGenerator Update**: Implement logic for:
    *   `_mta-sts` TXT record.
    *   `mta-sts` A record (pointing to ingress).
    *   `_25._tcp` TLSA record.
    *   `default._bimi` TXT record.

### Phase 3: Registrar Integrations
1.  **Interface**: `RegistrarProvider`.
2.  **GoDaddy**: Implement using their REST API (Key/Secret).
3.  **AWS Route53 Domains**: Use `Route53DomainsClient`.
4.  **Cloudflare**: Use existing WebClient setup for Registrar endpoints.

### Phase 4: DNS Integrations (Update)
1.  Existing Cloudflare/AWS/Manual DNS work.
2.  Ensure they support new record types (TLSA might be tricky on some UIs but usually standard via API).

### Phase 5: API & Orchestration
1.  **DomainController**: Add endpoints for:
    *   `PUT /api/domains/{id}/nameservers`
    *   `POST /api/domains/{id}/sync-registrar` (Fetch renewal, DS records)
    *   `PUT /api/domains/{id}/security` (Update MTA-STS, DANE, BIMI settings)
2.  **Sync Logic**:
    *   Sync DNS: Push records.
    *   Sync Registrar: Pull info.

### Phase 6: Frontend
1.  **UI Components**:
    *   `RegistrarCard`: Shows provider, renewal date, nameservers.
    *   `SecuritySettings`: Form for MTA-STS, DANE, BIMI.
    *   `DnssecBadge`: Visual indicator.

## 4. Advanced Protocol Details

### MTA-STS
*   Robin Gateway acts as the policy host.
*   Requires a valid SSL certificate for `mta-sts.<domain>`.
*   User must configure DNS `mta-sts.<domain>` to point to Robin (automatically handled if DNS is managed).

### DANE (TLSA)
*   We will generate usage `3` (DANE-EE) selector `1` (SPKI) matching `1` (SHA-256).
*   Requires reading the cert file used by Postfix.

### DNSSEC
*   **Management Strategy**: DNSSEC is managed directly via the DNS Provider's API (AWS Route53 or Cloudflare). Robin will not attempt to manually construct or sign zones but will instruct the provider to enable/disable it.
*   **Cloudflare**:
    *   API: `PATCH /zones/:id/dnssec` (status: active).
    *   Cloudflare automatically handles key generation and rotation.
    *   If the registrar is *also* Cloudflare, the DS record is auto-published.
    *   If Registrar is external (e.g., GoDaddy), Robin will fetch the DS record from Cloudflare API and push it to the Registrar.
*   **AWS Route53**:
    *   API: `EnableHostedZoneDNSSEC`, `CreateKeySigningKey`.
    *   Requires a KMS Key (Customer Managed Key). Robin must potentially create/manage this KMS key or accept a key ID.
    *   Robin fetches the DS record (`GetDNSSEC`) and pushes it to the Registrar (if external).

## 5. Security & Dependencies
*   **GoDaddy API**: Requires `Authorization: sso-key [Key]:[Secret]`.
*   **AWS**: Requires `route53:EnableHostedZoneDNSSEC`, `route53:CreateKeySigningKey`, `kms:CreateGrant` (if managing keys), `route53domains:*`.
*   **Certificates**: DANE/MTA-STS heavily rely on stable, valid certificates (Let's Encrypt).
