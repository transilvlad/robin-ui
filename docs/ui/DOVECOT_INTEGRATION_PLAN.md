# Dovecot Integration Plan

**Version**: 1.0.0
**Status**: Draft
**Target**: Robin UI & Gateway

---

## 1. Overview
This plan outlines the steps to integrate Dovecot configuration management into the Robin UI. Currently, Dovecot is a critical component for IMAP/POP3 services and authentication, but its configuration is not exposed in the UI.

## 2. Backend Implementation (Robin Gateway)

### 2.1 Configuration Model
- Verify `dovecot.json5` exists in the configuration volume.
- Ensure `ConfigurationService` can read/write this file.

### 2.2 API Endpoints
The existing `ConfigurationController` (`/api/v1/config/{section}`) is generic and likely sufficient, provided the section name "dovecot" maps correctly to `dovecot.json5`.

- **GET /api/v1/config/dovecot**: Retrieve current configuration.
- **PUT /api/v1/config/dovecot**: Update configuration.

*Verification needed*: Ensure the generic controller handles the "dovecot" section correctly without additional code changes, or add specific validation if needed.

## 3. Frontend Implementation (Robin UI)

### 3.1 Data Models (`src/app/core/models/config.model.ts`)
Add `DovecotConfig` interface to the existing config models.

```typescript
export interface DovecotConfig {
  protocols: ('imap' | 'pop3' | 'lmtp')[];
  listen: string;
  authentication: {
    mechanisms: string[];
    defaultRealm: string;
  };
  mailLocation: string;
  ssl: {
    enabled: boolean;
    certFile: string;
    keyFile: string;
  };
  limits: {
    maxConnections: number;
    maxUserConnections: number;
  };
}
```

### 3.2 Service Layer
Update `SecurityService` or potentially create/use a `ConfigService` to handle the generic config endpoints if strictly for configuration, but `SecurityService` seems overloaded.
*Better approach*: Use a dedicated `ConfigService` (which already exists in `ConfigurationService` on backend, need to confirm frontend equivalent).
*Decision*: Create/Update `src/app/core/services/config.service.ts` to handle generic config fetching if it doesn't exist, or specific Dovecot methods.

### 3.3 UI Component (`src/app/features/settings/dovecot/`)
Create a new component `DovecotConfigComponent`.

- **Layout**: Card-based (like Server Config).
- **Sections**:
    1.  **General**: Protocols (IMAP/POP3), Listen Address.
    2.  **Authentication**: Mechanisms (plain, login), Realm.
    3.  **SSL/TLS**: Cert paths, enforcement.
    4.  **Limits**: Connection limits.
- **Actions**: Save, Reset.

### 3.4 Routing (`src/app/features/settings/settings-routing.module.ts`)
Add route: `settings/dovecot` -> `DovecotConfigComponent`.

### 3.5 Navigation (`src/app/shared/components/sidebar/sidebar.component.ts`)
Add "Dovecot" link under the "Settings" section of the sidebar.

## 4. Implementation Steps

1.  **Verify Backend**: Test `GET /api/v1/config/dovecot` with `curl` to ensure it returns the expected JSON structure from the file system.
2.  **Define Models**: Update `config.model.ts` with Dovecot interfaces.
3.  **Create Service**: Implement `ConfigService` in Angular (if not present) to consume the backend API.
4.  **Scaffold Component**: Generate `DovecotConfigComponent`.
5.  **Implement Form**: Build the Reactive Form for editing configuration.
6.  **Register Route**: Add to Settings routing.
7.  **Update Sidebar**: Add navigation item.
8.  **Test**: Verify load and save functionality.

## 5. Security Considerations
- Dovecot config contains sensitive paths (SSL keys), but usually not the keys themselves.
- Ensure only `ROLE_ADMIN` can access these endpoints (Already covered by Backend `@PreAuthorize`).

## 6. Rollback Plan
- Backup `dovecot.json5` before writing.
- If UI fails, manual edit of JSON file on server restores functionality.
