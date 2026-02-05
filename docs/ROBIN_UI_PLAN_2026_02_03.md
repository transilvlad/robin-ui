# Robin UI & Gateway - Domain Management & Providers Implementation Plan

## 1. Overview
This plan details the implementation of enhanced Domain Management features (Add Existing Domain with DNS Discovery) and a new Email Providers configuration section in the Settings.

## 2. Backend Implementation (`robin-gateway`)

### 2.1. Provider Management Updates
*   **Goal:** Support "Email Providers" in addition to DNS and Registrar providers.
*   **Changes:**
    *   Modify `com.robin.gateway.model.ProviderConfig.ProviderType` enum to include `EMAIL`.
    *   Update `ProviderConfigService` to handle `EMAIL` type (likely no specific validation needed, just name storage).
    *   Ensure `ProviderController` can list providers by type (optional, but good for UI filtering).

### 2.2. Domain Entity Updates
*   **Goal:** Link domains to specific Email Providers.
*   **Changes:**
    *   Modify `com.robin.gateway.model.Domain` entity:
        *   Add `emailProvider` field (`@ManyToOne` relationship to `ProviderConfig`).
        *   Add `email_provider_id` column to database schema (via Flyway or Hibernate auto-ddl if used in dev).

### 2.3. DNS Discovery Service
*   **Goal:** Auto-discover existing DNS records and parse DMARC/SPF configurations.
*   **New Service:** `com.robin.gateway.service.DnsDiscoveryService`
    *   **Method:** `List<DnsRecord> discoverRecords(String domainName)`
        *   Use Java's `javax.naming` or `InetAddress` or an external library (like `dnsjava`) to query:
            *   MX Records
            *   TXT Records (SPF, DMARC, DKIM if selector known - *Note: DKIM discovery is hard without knowing selector, usually we skip or try default selectors like 'default', 'google', etc. For this plan, we focus on SPF/DMARC as requested.*)
            *   A Records (mail host)
    *   **Method:** `DomainConfiguration parseConfiguration(List<DnsRecord> records)`
        *   Extract SPF `include`s and `softFail` status from TXT records.
        *   Extract DMARC `p`, `sp`, `pct`, `rua` from `_dmarc` TXT record.
        *   Return a partial `Domain` object or DTO with these settings.

### 2.4. Domain Controller Updates
*   **Goal:** Expose discovery to UI.
*   **Changes:**
    *   Add `POST /api/v1/domains/discover` endpoint.
        *   Input: `{ domain: "example.com" }`
        *   Output: 
            ```json
            {
              "records": [ ... ], // Found records
              "configuration": { ... } // Parsed DMARC/SPF settings
            }
            ```
    *   Update `createDomain` (or ensure it handles) passing of DMARC/SPF settings in the initial request. (Existing `Domain` DTO likely already supports this, just need to ensure the UI sends it).

## 3. Frontend Implementation (`robin-ui`)

### 3.1. Settings > Providers
*   **Goal:** Manage Email Providers.
*   **Changes:**
    *   Create `ProvidersService` (if not fully capable yet) to handle CRUD for `ProviderConfig`.
    *   Create `SettingsProvidersComponent` (List View).
    *   Create `ProviderDialogComponent` (Add/Edit Provider).
        *   Fields: Name, Type (Dropdown: Email, DNS, Registrar).
        *   For "Email", only "Name" is required.
    *   Add route `settings/providers` in `SettingsRoutingModule`.

### 3.2. Domain Management > Add Domain Wizard
*   **Goal:** Split flow into "New" vs "Existing" and implement Discovery.
*   **Changes:**
    *   Refactor `DomainWizardComponent`.
    *   **Step 0: Selection**
        *   Radio buttons: "Add New Domain" vs "Add Existing Domain".
    *   **Flow A: New Domain**
        *   (Keep existing logic: Input -> Create -> Auto-generate everything).
    *   **Flow B: Existing Domain**
        *   **Step 1: Input**
            *   Enter Domain Name.
            *   Select Email Provider (optional).
            *   Click "Discover".
        *   **Step 2: Processing (Loading)**
            *   Call `POST /api/v1/domains/discover`.
        *   **Step 3: Review & Confirm**
            *   Display "Discovered Configuration":
                *   Show detected SPF settings (Includes, SoftFail).
                *   Show detected DMARC settings (Policy, alignment, etc.).
            *   Display "DNS Records Status":
                *   Table showing "Found" records (from discovery).
                *   Table showing "Missing/Proposed" records (generated locally by calling `DomainService.generateExpectedRecords` mock or separate preview endpoint? *Better approach: The backend `discover` endpoint could also return the "expected" records for comparison, or the UI calculates diff if it knows the rules. Given complexity, backend returning both "found" and "expected" in the discover response is best.*)
            *   User reviews and can adjust imported settings (e.g., if they want to override the discovered DMARC policy).
        *   **Step 4: Create**
            *   Call `createDomain` with the *confirmed* configuration (merged from discovery and user edits).
            *   Backend creates domain and saves the specific DMARC/SPF settings.
            *   (Note: Existing records won't be overwritten on DNS provider unless user syncs, but `robin-gateway` will now know about them).

### 3.3. Domain Detail
*   **Goal:** Assign Email Provider to Domain.
*   **Changes:**
    *   Update `DomainDetailComponent` (Settings tab).
    *   Add "Email Provider" dropdown.
    *   Allow saving/updating this field.

## 4. Implementation Steps & Dependencies

1.  **Backend Core:** Update `ProviderConfig` enum and `Domain` entity.
2.  **Backend Logic:** Implement `DnsDiscoveryService` and Controller endpoint.
3.  **Frontend Service:** Update `DomainService` and create `ProviderService`.
4.  **Frontend Settings:** Implement Providers UI.
5.  **Frontend Wizard:** Refactor `DomainWizardComponent` for the new flow.
6.  **Testing:** Verify discovery against real domains (e.g., `gmail.com`, `google.com`) to ensure parsing works.

## 5. Verification Plan
*   **Unit Tests:** Java tests for `DnsDiscoveryService` parsing logic.
*   **E2E Tests:** Cypress test for "Add Existing Domain" flow (mocking the backend discovery response).
