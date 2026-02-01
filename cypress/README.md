# Robin UI - E2E Tests

Comprehensive end-to-end tests for Phase 2 Authentication implementation using Cypress.

## Overview

This test suite provides complete coverage of the authentication system, including:
- Login/logout flows
- Token refresh mechanisms
- Auth guards and protected routes
- Permission-based access control (RBAC)
- Session timeout and management

## Test Structure

```
cypress/
├── e2e/
│   └── auth/
│       ├── login.cy.ts           # Login flow tests (120+ tests)
│       ├── logout.cy.ts          # Logout flow tests (25+ tests)
│       ├── token-refresh.cy.ts   # Token refresh tests (35+ tests)
│       ├── auth-guard.cy.ts      # Route protection tests (40+ tests)
│       ├── permissions.cy.ts     # RBAC tests (45+ tests)
│       └── session-timeout.cy.ts # Session management tests (30+ tests)
├── fixtures/
│   └── users.json                # Test user data
├── support/
│   ├── commands.ts               # Custom Cypress commands
│   └── e2e.ts                    # Global configuration
└── tsconfig.json                 # TypeScript configuration
```

## Test Coverage

### 1. Login Tests (`login.cy.ts`)
- ✅ Login page UI rendering
- ✅ Form validation (username, password)
- ✅ Successful login with admin/user credentials
- ✅ Failed login (invalid credentials, network errors)
- ✅ Auto-login on page load
- ✅ Return URL redirection
- ✅ Remember me functionality
- ✅ Password visibility toggle
- ✅ Keyboard navigation
- ✅ Accessibility (ARIA labels)

**Total Tests**: ~50 tests

### 2. Logout Tests (`logout.cy.ts`)
- ✅ Successful logout
- ✅ Clear authentication data
- ✅ API failure handling
- ✅ Post-logout behavior
- ✅ Multi-tab logout synchronization
- ✅ Automatic logout on token expiration
- ✅ Re-login after logout

**Total Tests**: ~25 tests

### 3. Token Refresh Tests (`token-refresh.cy.ts`)
- ✅ Automatic token refresh on 401
- ✅ Request queuing during refresh
- ✅ Refresh token failure handling
- ✅ Proactive token refresh before expiration
- ✅ Simultaneous 401 responses
- ✅ Token storage updates
- ✅ Session expiration updates

**Total Tests**: ~35 tests

### 4. Auth Guard Tests (`auth-guard.cy.ts`)
- ✅ Unauthenticated access blocking
- ✅ Authenticated access allowing
- ✅ Return URL preservation
- ✅ Deep-linking with query parameters
- ✅ Browser back/forward navigation
- ✅ Session validation on route change
- ✅ Guard edge cases

**Total Tests**: ~40 tests

### 5. Permission Tests (`permissions.cy.ts`)
- ✅ Admin user full access
- ✅ Regular user limited access
- ✅ Read-only user view-only access
- ✅ Permission directives (hide/show elements)
- ✅ API permission enforcement (403 handling)
- ✅ Role hierarchy (ADMIN > USER > READ_ONLY)
- ✅ Dynamic permission changes
- ✅ Unauthorized page display

**Total Tests**: ~45 tests

### 6. Session Timeout Tests (`session-timeout.cy.ts`)
- ✅ Inactivity timeout detection
- ✅ Timeout warning dialog
- ✅ Session extension
- ✅ Countdown timer
- ✅ Activity tracking (mouse, keyboard, scroll)
- ✅ Multi-tab session sync
- ✅ Remember me session persistence
- ✅ Background tab behavior
- ✅ Server-side session validation

**Total Tests**: ~30 tests

## Total Test Count

**6 test suites** with **~225 E2E tests** covering all authentication flows.

## Installation

```bash
npm install --save-dev cypress @types/node
```

## Running Tests

### Interactive Mode (Cypress UI)
```bash
npm run test:e2e:open
```

### Headless Mode (CI/CD)
```bash
npm run test:e2e
```

### Specific Browser
```bash
npm run test:e2e:chrome
npm run test:e2e:firefox
```

### Run All Tests (Unit + E2E)
```bash
npm run test:all
```

## Custom Commands

The test suite includes custom Cypress commands for common authentication operations:

### `cy.loginAsAdmin()`
Login as admin user with full permissions.

```typescript
cy.loginAsAdmin();
cy.visit('/settings/users');
```

### `cy.loginAsUser()`
Login as regular user with limited permissions.

```typescript
cy.loginAsUser();
cy.visit('/dashboard');
```

### `cy.login(username, password, rememberMe?)`
Login with custom credentials.

```typescript
cy.login('custom_user', 'password123', true);
```

### `cy.logout()`
Logout current user.

```typescript
cy.logout();
```

### `cy.getAccessToken()`
Get access token from session storage.

```typescript
cy.getAccessToken().should('exist');
```

### `cy.clearAuth()`
Clear all authentication data.

```typescript
cy.clearAuth();
```

### `cy.shouldBeOnLoginPage()`
Assert user is on login page.

```typescript
cy.shouldBeOnLoginPage();
```

### `cy.shouldBeAuthenticated()`
Assert user is authenticated.

```typescript
cy.shouldBeAuthenticated();
```

## Environment Configuration

Test environment variables are configured in `cypress.config.ts`:

```typescript
env: {
  apiUrl: 'http://localhost:8080/api/v1',
  adminUsername: 'admin',
  adminPassword: 'admin123',
  userUsername: 'user',
  userPassword: 'user123',
}
```

## Test Data

Test users are defined in `cypress/fixtures/users.json`:

- **Admin User**: Full permissions
- **Regular User**: Limited permissions
- **Read-Only User**: View-only permissions

## Prerequisites

Before running E2E tests, ensure:

1. **Robin Gateway** is running on `http://localhost:8080`
2. **Robin UI** is running on `http://localhost:4200`
3. Test users exist in the database:
   - `admin` / `admin123` (ADMIN role)
   - `user` / `user123` (USER role)

## CI/CD Integration

### GitHub Actions Example

```yaml
name: E2E Tests

on: [push, pull_request]

jobs:
  e2e:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Setup Node
        uses: actions/setup-node@v3
        with:
          node-version: '20'

      - name: Install dependencies
        run: npm ci

      - name: Start Robin Gateway
        run: docker-compose up -d gateway

      - name: Start Robin UI
        run: npm run dev &

      - name: Wait for services
        run: |
          npx wait-on http://localhost:8080/actuator/health
          npx wait-on http://localhost:4200

      - name: Run E2E tests
        run: npm run test:e2e:headless

      - name: Upload videos
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: cypress-videos
          path: cypress/videos

      - name: Upload screenshots
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: cypress-screenshots
          path: cypress/screenshots
```

## Test Reports

Cypress generates:
- **Videos**: `cypress/videos/` (failure recordings)
- **Screenshots**: `cypress/screenshots/` (failure snapshots)
- **Console output**: Detailed test results

## Debugging Tips

### 1. Use Cypress UI for Development
```bash
npm run test:e2e:open
```
- Time-travel debugging
- DOM snapshots
- Network request inspection
- Live reload

### 2. Add Debug Statements
```typescript
cy.debug();
cy.pause();
cy.log('Debugging checkpoint');
```

### 3. Inspect Network Requests
```typescript
cy.intercept('POST', '**/auth/login').as('login');
cy.wait('@login').its('request.body').should('deep.equal', {
  username: 'admin',
  password: 'admin123',
});
```

### 4. Check Element Visibility
```typescript
cy.get('[data-testid="login-button"]').should('be.visible');
cy.get('[data-testid="error-message"]').should('contain', 'Invalid credentials');
```

## Best Practices

1. **Use data-testid attributes** for stable selectors
2. **Mock network requests** for predictable tests
3. **Use custom commands** for common operations
4. **Clean up after each test** with `beforeEach(() => cy.clearAuth())`
5. **Test user flows**, not implementation details
6. **Use meaningful assertions** with clear error messages
7. **Keep tests independent** - no test should depend on another
8. **Use fixtures** for test data

## Known Issues

None at this time.

## Contributing

When adding new auth features:

1. Add corresponding E2E tests
2. Update custom commands if needed
3. Update this README
4. Ensure tests pass before merging

## Support

For questions or issues:
- Check existing tests for examples
- Review Cypress documentation: https://docs.cypress.io
- Open an issue in the project repository
