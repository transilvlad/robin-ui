# DKIM Key Management Guide

This document describes how DKIM key generation, rotation, and DNS record handling work in Robin UI and the Robin Gateway.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Key Generation](#key-generation)
   - [Manual generation from domain settings](#manual-generation-from-domain-settings)
   - [Auto-generation during domain creation](#auto-generation-during-domain-creation)
   - [Selector naming](#selector-naming)
   - [Key storage and private key encryption](#key-storage-and-private-key-encryption)
   - [Post-generation side effects](#post-generation-side-effects)
4. [Key Statuses](#key-statuses)
5. [DNS Record Handling](#dns-record-handling)
   - [Automatic DNS publish](#automatic-dns-publish)
   - [Manual DNS push from the UI](#manual-dns-push-from-the-ui)
   - [DNS record format](#dns-record-format)
6. [Pre-existing DKIM DNS Records](#pre-existing-dkim-dns-records)
   - [Domain DNS pre-flight lookup](#domain-dns-pre-flight-lookup)
   - [Selector detection algorithm](#selector-detection-algorithm)
   - [Detected selectors storage](#detected-selectors-storage)
   - [What happens to detected selectors](#what-happens-to-detected-selectors)
7. [Key Rotation](#key-rotation)
   - [Rotation Wizard steps](#rotation-wizard-steps)
   - [What the gateway does on rotate](#what-the-gateway-does-on-rotate)
   - [Cleanup and retirement](#cleanup-and-retirement)
8. [Retiring a Key](#retiring-a-key)
9. [API Reference](#api-reference)
10. [Database Schema](#database-schema)
11. [Known Limitations](#known-limitations)

---

## Overview

DKIM (DomainKeys Identified Mail) allows a mail server to cryptographically sign outgoing messages. The signing key pair is generated and managed by the Robin Gateway. The public key is published as a DNS TXT record; the private key is stored encrypted in PostgreSQL and pushed to the Robin MTA for active signing.

---

## Architecture

```
Browser (Angular UI)
        │  REST (JSON)
        ▼
Robin Gateway  :8888 (dev) / :8080 (prod)   ← Spring Boot WebFlux
        │
        ├── PostgreSQL  :5432
        │     └── dkim_keys table
        │
        ├── DNS Provider API  (Cloudflare / AWS Route 53)
        │     └── creates TXT record on key generation
        │
        └── Robin MTA  :8090
              └── POST /config/dkim  (pushes private key for signing)
```

The Angular UI never touches the Robin MTA directly. All DKIM operations go through the Gateway at `/api/v1/domains/{domainId}/dkim/*`.

---

## Key Generation

### Manual generation from domain settings

In the domain detail view, navigate to the **DKIM Keys** tab and click **+ Generate Key**.

The modal exposes two fields:

| Field | Required | Description |
|-------|----------|-------------|
| Selector | No | Custom DNS selector name. Leave blank to auto-generate. |
| Algorithm | Yes | `RSA 2048-bit` (default) or `Ed25519`. |

On submit, the UI calls:

```
POST /api/v1/domains/{domainId}/dkim/generate
Content-Type: application/json

{
  "algorithm": "RSA_2048",
  "selector": "mykey"   // optional
}
```

### Auto-generation during domain creation

After a domain is created via the **Add Domain** modal, a follow-up step offers to immediately generate a DKIM key with RSA 2048-bit and no custom selector. This calls the same `POST /generate` endpoint using the newly created domain's numeric ID.

### Selector naming

When no custom selector is provided, the gateway auto-generates one using this pattern:

```
{yyyyMM}{algorithmSuffix}{randomHex}
```

| Part | Example | Notes |
|------|---------|-------|
| `yyyyMM` | `202602` | Year and month of generation |
| `algorithmSuffix` | `r` for RSA, `e` for Ed25519 | Single character |
| `randomHex` | `3a9f` | Lower hex digits of `System.currentTimeMillis()` to ensure uniqueness |

Example auto-generated selector: `202602r3a9f`

The corresponding DNS TXT record name becomes: `202602r3a9f._domainkey.example.com`

The uniqueness suffix prevents duplicate-key constraint violations when generating multiple keys in the same month, or when retrying after a failed attempt that already persisted a key.

### Key storage and private key encryption

The gateway generates the key pair using the Java `KeyPairGenerator`:

- **RSA 2048-bit**: `KeyPairGenerator.getInstance("RSA")` with 2048-bit initialisation
- **Ed25519**: `KeyPairGenerator.getInstance("Ed25519")`

Both keys are Base64-encoded. The private key is then encrypted using AES-256 (`EncryptionService`) before being written to the `dkim_keys` table. The API response always returns `"****"` in place of the private key — it is never exposed over the wire.

### Post-generation side effects

After the key is saved, the gateway attempts two additional operations. Both are **non-fatal**: if either fails, the key remains saved in the database and a `201 Created` response is returned. Failures are logged as warnings.

1. **DNS publish** — If the domain has a configured DNS provider (Cloudflare or AWS Route 53), the gateway automatically creates the TXT record (see [Automatic DNS publish](#automatic-dns-publish)).

2. **MTA signing configuration** — The gateway calls `POST {robinServiceUrl}/config/dkim` on the Robin MTA with the decrypted private key, domain, selector, and algorithm so the MTA starts signing immediately.

---

## Key Statuses

| Status | Meaning |
|--------|---------|
| `ACTIVE` | The key is in normal signing use. |
| `ROTATING` | Rotation has been initiated. This key is being phased out; a new `ACTIVE` key was generated to replace it. The old key continues signing until cutover. |
| `RETIRED` | The key is no longer used. The `retired_at` timestamp is set. Retired keys remain in the database for audit purposes. |

The UI uses these statuses for badge colour coding:

| Status | Badge colour |
|--------|-------------|
| ACTIVE | Green |
| ROTATING | Yellow |
| RETIRED | Grey (dimmed) |

---

## DNS Record Handling

### Automatic DNS publish

If the domain has a DNS provider configured, key generation automatically publishes the TXT record via the provider's API:

**Cloudflare:**
1. Looks up the zone ID for the domain using the stored API token.
2. Creates a TXT record via `POST /zones/{zoneId}/dns_records`.
3. Saves a local `domain_dns_records` entry with the Cloudflare record ID for tracking.

**AWS Route 53:**
1. Resolves the hosted zone ID for the domain.
2. Issues a `UPSERT` change against the `ResourceRecordSet` with TTL 3600.
3. Saves a local `domain_dns_records` entry with the Route 53 change ID.

If the domain has **no DNS provider configured**, the publish step is silently skipped. The key is still saved and the user must publish the DNS record manually.

### Manual DNS push from the UI

On the DKIM Keys table, clicking **DNS Record** opens a side drawer showing the full TXT record details. From the drawer:

- Copy the record name or value to the clipboard.
- Click **Push to DNS** to trigger an on-demand `POST /api/v1/domains/{domainId}/dns/records` call through the gateway, which creates the record via the configured DNS provider.

The DNS record details are constructed client-side from the key's `publicKey` field:

```
Record name:  {selector}._domainkey.{domain}
Record type:  TXT
Record value: v=DKIM1; k={rsa|ed25519}; p={base64PublicKey}
```

### DNS record format

The TXT record value always follows the DKIM1 format:

```
v=DKIM1; k=rsa; p=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...
```

For Ed25519 keys the `k` tag is `ed25519`:

```
v=DKIM1; k=ed25519; p=MCowBQYDK2VwAyEA...
```

---

## Pre-existing DKIM DNS Records

### Domain DNS pre-flight lookup

When adding a new domain, the UI offers a **Detect DNS** step before creating the domain. This calls:

```
GET /api/v1/domains/lookup?domain=example.com
```

The gateway performs a full DNS resolution of the domain including a DKIM selector probe.

### Selector detection algorithm

The lookup probes DNS for DKIM TXT records using a two-phase approach:

**Phase 1 — known selectors** (probed unconditionally):

```
default, selector1, selector2, google, k1, k2, k3, dkim, dkim1, dkim2,
smtp, s1, s2, key1, key2, mta, mta1, mta2,
robin, robin1..robin5, email, outbound, primary, main, a, b
```

For each selector, the gateway resolves `{selector}._domainkey.{domain}` for TXT records.

**Phase 2 — extended prefix probing**:

For every selector that was found in Phase 1, the non-numeric prefix is extracted (e.g. `dkim` from `dkim1`) and selectors `{prefix}1` through `{prefix}20` are probed sequentially. Probing stops at the first gap (empty result).

### Detected selectors storage

Each detected TXT record is parsed for DKIM tags (`v`, `k`, `p`, `t`):

| Tag | Meaning |
|-----|---------|
| `k` | Algorithm (`rsa` or `ed25519`). Defaults to `rsa` if absent. |
| `p` | Base64-encoded public key. Empty value means the key is revoked. |
| `t=y` | Test mode flag. |

Detected selectors are persisted in the `dkim_detected_selectors` table with an upsert strategy — if a selector for that domain already exists, its fields are refreshed.

The lookup response includes a `detectedDkimSelectors` array:

```json
{
  "detectedDkimSelectors": [
    {
      "selector": "default",
      "algorithm": "rsa",
      "publicKeyPreview": "MIIBIjANBgkqhk...",
      "testMode": false,
      "revoked": false,
      "detectedAt": "2026-02-28T07:00:00"
    }
  ]
}
```

### What happens to detected selectors

Detected selectors are **informational only**. They are stored for reference but are not automatically imported into the `dkim_keys` table. This means:

- Robin does not take over management of a pre-existing key it did not generate.
- The operator is responsible for deciding whether to retire the old external key or leave it alongside newly generated Robin-managed keys.
- Multiple DKIM keys can coexist in DNS for the same domain simultaneously; receiving mail servers will validate against whichever selector the sending server used.

If you want Robin to manage signing for a domain that already has DKIM keys in DNS, simply generate a new key through Robin. Update your MTA configuration (or let the auto-configuration handle it) so the new selector is used for signing. The old external records can remain in DNS without causing problems until you remove them.

---

## Key Rotation

Key rotation is the process of replacing an `ACTIVE` key with a new one while maintaining continuous mail signing. The **Rotation Wizard** in the UI guides the operator through the following steps.

### Rotation Wizard steps

| Step | Label | What happens |
|------|-------|-------------|
| 1 | Pre-publish | Start rotation — calls the gateway to generate the new key. |
| 2 | Publish | Verify the DNS TXT record for the new key. Confirm publication to continue. |
| 3 | Observation | A countdown timer (default 3 seconds in dev; increase for production) allows time for DNS propagation and traffic monitoring before cutover. |
| 4 | Activate | Acknowledge the cutover. The new key is already `ACTIVE` from Step 1. |
| 5 | Cleanup | Retire the old `ROTATING` key(s). |
| Done | — | Rotation complete. |

### What the gateway does on rotate

Calling `POST /api/v1/domains/{domainId}/dkim/rotate` triggers the following sequence:

1. **Find the current active key.** If no `ACTIVE` key exists, the call returns an error.
2. **Mark it `ROTATING`.** The existing active key's status is updated to `ROTATING` in the database. Its selector is saved as `oldSelector`.
3. **Generate a new key pair** by internally calling `generateKeyPair(domainId, RSA_2048, null)`. This goes through the full generation flow: key generation, DB save, DNS publish, and MTA configuration (all non-fatal on failure).
4. **Set `cnameSelector`** on the new key to `oldSelector`. This records the transition path — the new key "points back" to the old one for audit purposes.
5. Return the new key (with masked private key) to the caller.

At this point two keys exist for the domain:

| Key | Status | Signing |
|-----|--------|---------|
| Old key | ROTATING | Still in DNS; MTA signs with new key |
| New key | ACTIVE | DNS TXT record published; MTA configured |

### Cleanup and retirement

In Step 5 (Cleanup), the wizard:

1. Calls `GET /api/v1/domains/{domainId}/dkim/keys` to fetch the current list.
2. Identifies retirement candidates: keys with status `ROTATING` or `ACTIVE` that are **not** the new key returned in Step 1.
3. Calls `DELETE /api/v1/domains/{domainId}/dkim/keys/{keyId}` for each candidate sequentially.

The retire endpoint sets `status = RETIRED` and records `retired_at = NOW()`. The key record is preserved in the database for auditing; it is not deleted.

After cleanup, the key table for the domain should contain exactly one `ACTIVE` key and one or more `RETIRED` keys.

---

## Retiring a Key

A key can be retired manually from the DKIM Keys table by clicking **Retire**. A browser confirmation dialog prevents accidental retirement.

Only keys that are not already `RETIRED` show the Retire button. Retiring a key that is currently in active use will break DKIM signing for mail sent with that selector. Always ensure a new `ACTIVE` key is in place before retiring.

---

## API Reference

All endpoints require authentication (`Bearer` token or session cookie) and the `MANAGE_DKIM` authority or the `ADMIN` role.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/domains/{domainId}/dkim/keys` | List all DKIM keys for a domain. Private keys are masked. |
| `POST` | `/api/v1/domains/{domainId}/dkim/generate` | Generate a new key pair. Body: `{ algorithm, selector? }`. Returns `201 Created`. |
| `POST` | `/api/v1/domains/{domainId}/dkim/rotate` | Initiate key rotation. No request body. Returns the new `ACTIVE` key. |
| `DELETE` | `/api/v1/domains/{domainId}/dkim/keys/{keyId}` | Retire a key. Returns `200 OK` with a confirmation message. |

**`DkimKey` response schema:**

```json
{
  "id": 42,
  "domainId": 7,
  "selector": "202602r3a9f",
  "algorithm": "RSA_2048",
  "privateKey": "****",
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...",
  "cnameSelector": null,
  "status": "ACTIVE",
  "createdAt": "2026-02-28T07:34:32",
  "retiredAt": null
}
```

---

## Database Schema

```sql
CREATE TABLE dkim_keys (
    id          SERIAL PRIMARY KEY,
    domain_id   BIGINT NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    selector    VARCHAR(255) NOT NULL,
    algorithm   VARCHAR(20) NOT NULL CHECK (algorithm IN ('RSA_2048', 'ED25519')),
    private_key TEXT NOT NULL,         -- AES-256 encrypted
    public_key  TEXT NOT NULL,
    cname_selector VARCHAR(255),       -- populated on rotation: old selector name
    status      VARCHAR(20) DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'ROTATING', 'RETIRED')),
    created_at  TIMESTAMP DEFAULT NOW(),
    retired_at  TIMESTAMP,
    UNIQUE(domain_id, selector)
);

CREATE TABLE dkim_detected_selectors (
    id           BIGSERIAL PRIMARY KEY,
    domain       VARCHAR(253) NOT NULL,
    selector     VARCHAR(63) NOT NULL,
    public_key_dns TEXT,               -- raw base64 value from DNS p= tag
    algorithm    VARCHAR(10),
    test_mode    BOOLEAN,
    revoked      BOOLEAN DEFAULT FALSE,
    detected_at  TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_dkim_detected_selectors_domain_selector UNIQUE (domain, selector)
);
```

The `UNIQUE(domain_id, selector)` constraint on `dkim_keys` ensures that two keys for the same domain can never share the same selector name, regardless of their status.

---

## Known Limitations

- **Rotation always uses RSA 2048-bit.** The `POST /rotate` endpoint generates the new key with `RSA_2048` hardcoded regardless of the algorithm of the key being rotated. Ed25519 rotation is not yet supported through the wizard; use manual generation instead.

- **Post-generation steps are best-effort.** DNS publish and MTA signing configuration run after the key is saved. If either fails (provider credentials wrong, MTA unreachable), the key exists in the database but DNS and signing may be incomplete. The error is logged as a warning. The operator must manually push the DNS record from the drawer and check the MTA signing configuration.

- **Detected selectors are not imported.** Pre-existing DKIM selectors found during domain DNS lookup are stored in `dkim_detected_selectors` for reference only. They are not automatically added to `dkim_keys` and Robin will not sign with them.

- **No automatic TTL wait.** The Rotation Wizard's observation window is a fixed countdown (3 seconds in the development environment). In production this should be set to a value equal to or greater than the DNS TTL of the outgoing DKIM TXT record (typically 300–3600 seconds) to ensure DNS propagation completes before cutover.
