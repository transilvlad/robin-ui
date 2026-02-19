# Centralized Providers Management Plan

## 1. Overview
This feature introduces a centralized management system for external service providers (Cloudflare, AWS Route53, GoDaddy). Instead of configuring API credentials for every single domain, users will configure "Providers" once in a new **Settings > Integrations > Providers** section. Domains will then simply reference these configured providers.

## 2. Architecture

### 2.1 Backend (robin-gateway)

#### New Entity: `ProviderConfig`
A centralized entity to store credentials securely.

*   `id` (PK)
*   `name` (VARCHAR): Friendly name (e.g., "Company Cloudflare", "Personal GoDaddy").
*   `type` (Enum): `CLOUDFLARE`, `AWS_ROUTE53`, `GODADDY`.
*   `credentials` (TEXT): Encrypted JSON containing provider-specific secrets (API Tokens, Keys, Secrets).
*   `created_at`, `updated_at`

#### Updated Entity: `Domain`
Refactor `Domain` to reference `ProviderConfig` instead of storing raw JSON config.

*   **Remove**: `dns_provider_config`, `registrar_provider_config`.
*   **Add**:
    *   `dns_provider_id` (FK -> ProviderConfig, Nullable).
    *   `registrar_provider_id` (FK -> ProviderConfig, Nullable).
*   **Logic**:
    *   If `dns_provider_type` is `MANUAL`, `dns_provider_id` is null.
    *   If `dns_provider_type` is `CLOUDFLARE`, `dns_provider_id` must point to a valid Cloudflare provider.

#### Services
*   **`ProviderConfigService`**: CRUD operations for providers. encrypts credentials on save.
*   **`DnsProviderFactory` / `RegistrarProviderFactory`**: Updated to fetch credentials from the linked `ProviderConfig` entity instead of the `Domain` entity.

#### API Endpoints
*   `GET /api/v1/providers`: List all configured providers.
*   `GET /api/v1/providers/{id}`: Get details (redacting secrets).
*   `POST /api/v1/providers`: Create new provider.
*   `PUT /api/v1/providers/{id}`: Update provider.
*   `DELETE /api/v1/providers/{id}`: Delete provider (prevent if in use by domains).
*   `POST /api/v1/providers/{id}/test`: Verify credentials.

### 2.2 Frontend (robin-ui)

#### New Module: `Features/Settings/Integrations`
*   **Routing**: `/settings/integrations/providers`
*   **Sidebar**: Add "Integrations" group to Settings sidebar.

#### Components
1.  **`ProvidersListComponent`**:
    *   Displays cards/table of configured providers.
    *   Icons for AWS, Cloudflare, GoDaddy.
    *   Status badges (Valid/Invalid - result of last test).
2.  **`ProviderFormDialog`**:
    *   Modal or Page to add/edit a provider.
    *   **Dynamic Fields** based on type:
        *   *Cloudflare*: API Token.
        *   *AWS*: Access Key ID, Secret Access Key, Region.
        *   *GoDaddy*: API Key, API Secret.
3.  **`DomainWizard` / `DomainSettings` Update**:
    *   Replace manual credential input fields with a **Dropdown** selecting from configured Providers.
    *   Add "Create New Provider" shortcut link.

## 3. Implementation Phases

### Phase 1: Backend Core
1.  **Migration**: Create `V5__provider_management.sql`.
    *   Create `provider_configs`.
    *   Alter `domains` to add FKs and drop old config columns.
2.  **Entity/Repository**: Create `ProviderConfig` and `ProviderConfigRepository`.
3.  **Service**: Implement `ProviderConfigService` with encryption logic.
4.  **Refactor Factories**: Update `DnsProviderFactory` to use the new relation.

### Phase 2: API Layer
1.  **Controller**: Implement `ProviderController`.
2.  **Validation**: Ensure validation for provider specific fields (e.g. AWS requires Key+Secret).
3.  **Connection Testing**: Implement `testConnection()` logic for each provider type.

### Phase 3: Frontend - Providers Management
1.  **Settings Sidebar**: Update to include "Integrations".
2.  **Provider List**: Build the management UI using the existing "Card" and "Table" styles.
3.  **Provider Form**: Build the dynamic form for adding credentials.

### Phase 4: Frontend - Domain Integration
1.  **Update Domain Wizard**: Step 2 (Provider Selection) now fetches the list of available providers.
2.  **Update Domain Settings**: Allow switching providers for an existing domain.

## 4. UI/UX Design

*   **Location**: Settings > Integrations > Providers.
*   **Style**: Matches `server-config.component.html` and `domain-list.component.ts`.
    *   **Header**: "Providers", "Manage external service connections".
    *   **List**: Grid of cards. Each card shows the Provider Logo (Icon), Name, Type, and usage count (e.g., "Used by 3 domains").
    *   **Add Button**: Standard `btn-primary`.
*   **Forms**: `shadcn`-style inputs with floating labels or standard labels. Secrets masked by default.

## 5. Migration Strategy
*   Since this is a new feature in dev, we can drop/recreate the `domains` columns. If preserving data is needed, we would need a script to migrate existing JSON configs to new Provider entities (omitted for simplicity unless requested).
