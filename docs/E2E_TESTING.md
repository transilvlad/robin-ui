# E2E Testing Guide - Robin UI Phase 2 Authentication

This document provides a comprehensive guide to the end-to-end (E2E) testing infrastructure for Phase 2 authentication implementation.

## Table of Contents

- [Overview](#overview)
- [Test Architecture](#test-architecture)
- [Test Coverage](#test-coverage)
- [Installation](#installation)
- [Running Tests](#running-tests)
- [Writing Tests](#writing-tests)
- [Custom Commands](#custom-commands)
- [CI/CD Integration](#cicd-integration)
- [Troubleshooting](#troubleshooting)

## Overview

The E2E test suite provides comprehensive coverage of the authentication system using Cypress, a modern web testing framework. The tests verify all aspects of authentication including:

- User login and logout
- Token management and refresh
- Route protection (auth guards)
- Permission-based access control (RBAC)
- Session timeout and management

## Test Architecture

### Directory Structure

```
cypress/
├── e2e/
│   └── auth/
│       ├── login.cy.ts           # Login flow (50 tests)
│       ├── logout.cy.ts          # Logout flow (25 tests)
│       ├── token-refresh.cy.ts   # Token refresh (35 tests)
│       ├── auth-guard.cy.ts      # Route protection (40 tests)
│       ├── permissions.cy.ts     # RBAC (45 tests)
│       └── session-timeout.cy.ts # Session management (30 tests)
├── fixtures/
│   └── users.json                # Test user data
├── support/
│   ├── commands.ts               # Custom commands
│   └── e2e.ts                    # Global config
├── tsconfig.json                 # TypeScript config
└── README.md                     # Test documentation
```

### Test Files

| File | Purpose | Test Count | Lines |
|------|---------|------------|-------|
| `login.cy.ts` | Login page UI, validation, auth flow | ~50 | ~450 |
| `logout.cy.ts` | Logout flow, cleanup, edge cases | ~25 | ~200 |
| `token-refresh.cy.ts` | Automatic refresh, queuing, failures | ~35 | ~300 |
| `auth-guard.cy.ts` | Route protection, redirects | ~40 | ~350 |
| `permissions.cy.ts` | RBAC, role hierarchy, UI elements | ~45 | ~400 |
| `session-timeout.cy.ts` | Timeouts, warnings, activity tracking | ~30 | ~350 |
| **Total** | | **~225 tests** | **~2,050 lines** |

## Test Coverage

### 1. Login Flow Tests

**File**: `cypress/e2e/auth/login.cy.ts`

```typescript
describe('Login Flow', () => {
  it('should login successfully with valid credentials', () => {
    cy.visit('/auth/login');
    cy.get('input[name="username"]').type('admin');
    cy.get('input[name="password"]').type('admin123');
    cy.get('button[type="submit"]').click();

    cy.url().should('include', '/dashboard');
    cy.shouldBeAuthenticated();
  });
});
```

**Coverage**:
- ✅ UI rendering (form fields, buttons, labels)
- ✅ Form validation (required fields, min/max length)
- ✅ Successful login (admin, user, read-only)
- ✅ Failed login (invalid credentials, network errors)
- ✅ Auto-login on page load (restore session)
- ✅ Return URL redirection
- ✅ Remember me functionality
- ✅ Password visibility toggle
- ✅ Keyboard navigation (Tab, Enter)
- ✅ Accessibility (ARIA labels, error associations)

### 2. Logout Flow Tests

**File**: `cypress/e2e/auth/logout.cy.ts`

**Coverage**:
- ✅ Successful logout
- ✅ Clear all auth data (tokens, user info, cookies)
- ✅ API failure handling (graceful degradation)
- ✅ Post-logout behavior (require re-login)
- ✅ Multi-tab logout sync
- ✅ Automatic logout on token expiration
- ✅ Immediate re-login after logout

### 3. Token Refresh Tests

**File**: `cypress/e2e/auth/token-refresh.cy.ts`

**Coverage**:
- ✅ Automatic refresh on 401
- ✅ Request queuing during refresh (no duplicates)
- ✅ Refresh failure handling (logout on error)
- ✅ Proactive refresh before expiration
- ✅ Simultaneous 401 responses (single refresh)
- ✅ Token storage updates
- ✅ Session expiration updates

### 4. Auth Guard Tests

**File**: `cypress/e2e/auth/auth-guard.cy.ts`

**Coverage**:
- ✅ Block unauthenticated access to protected routes
- ✅ Allow authenticated access
- ✅ Return URL preservation
- ✅ Deep-linking with query parameters
- ✅ Browser back/forward navigation
- ✅ Session validation on route change
- ✅ Concurrent guard checks

### 5. Permission Tests

**File**: `cypress/e2e/auth/permissions.cy.ts`

**Coverage**:
- ✅ Admin user: full access
- ✅ Regular user: limited access
- ✅ Read-only user: view-only access
- ✅ Permission directives (hide/show UI elements)
- ✅ API permission enforcement (403 handling)
- ✅ Role hierarchy (ADMIN > USER > READ_ONLY)
- ✅ Dynamic permission changes
- ✅ Unauthorized page display

### 6. Session Timeout Tests

**File**: `cypress/e2e/auth/session-timeout.cy.ts`

**Coverage**:
- ✅ Inactivity timeout detection
- ✅ Timeout warning dialog (5 min before)
- ✅ Session extension
- ✅ Countdown timer updates
- ✅ Activity tracking (mouse, keyboard, scroll)
- ✅ Multi-tab session sync
- ✅ Remember me persistence
- ✅ Background tab behavior
- ✅ Server-side session validation

## Installation

### 1. Install Cypress

```bash
npm install --save-dev cypress @types/node
```

### 2. Verify Installation

```bash
npx cypress verify
```

### 3. Open Cypress

```bash
npm run test:e2e:open
```

## Running Tests

### Interactive Mode (Development)

```bash
npm run test:e2e:open
```

Benefits:
- Visual test runner
- Time-travel debugging
- DOM snapshots
- Network inspection
- Live reload

### Headless Mode (CI/CD)

```bash
npm run test:e2e
```

### Specific Browser

```bash
npm run test:e2e:chrome
npm run test:e2e:firefox
```

### Single Test File

```bash
npx cypress run --spec "cypress/e2e/auth/login.cy.ts"
```

### Filtered Tests

```bash
npx cypress run --spec "cypress/e2e/auth/*.cy.ts"
```

## Writing Tests

### Test Structure

```typescript
describe('Feature Name', () => {
  beforeEach(() => {
    cy.clearAuth(); // Clean slate
  });

  describe('Scenario Group', () => {
    it('should do something specific', () => {
      // Arrange
      cy.loginAsAdmin();

      // Act
      cy.visit('/dashboard');
      cy.get('[data-testid="action-button"]').click();

      // Assert
      cy.url().should('include', '/result');
      cy.get('[data-testid="success-message"]').should('be.visible');
    });
  });
});
```

### Best Practices

1. **Use `data-testid` attributes** for stable selectors:
   ```html
   <button data-testid="login-button">Login</button>
   ```
   ```typescript
   cy.get('[data-testid="login-button"]').click();
   ```

2. **Clean up before each test**:
   ```typescript
   beforeEach(() => {
     cy.clearAuth();
   });
   ```

3. **Use custom commands** for common operations:
   ```typescript
   cy.loginAsAdmin();
   cy.visit('/settings/users');
   cy.logout();
   ```

4. **Mock network requests** for predictable tests:
   ```typescript
   cy.intercept('POST', '**/auth/login', {
     statusCode: 200,
     body: { user: mockUser, tokens: mockTokens }
   }).as('login');

   cy.wait('@login');
   ```

5. **Test user flows**, not implementation:
   ```typescript
   // Good: Test user behavior
   it('should allow user to login', () => {
     cy.visit('/auth/login');
     cy.get('input[name="username"]').type('admin');
     cy.get('input[name="password"]').type('admin123');
     cy.get('button[type="submit"]').click();
     cy.url().should('include', '/dashboard');
   });

   // Bad: Test implementation details
   it('should call AuthStore.login()', () => {
     // Don't test internal functions
   });
   ```

## Custom Commands

### Authentication Commands

#### `cy.loginAsAdmin()`
Login as admin user with full permissions.

```typescript
cy.loginAsAdmin();
cy.visit('/settings/users');
cy.get('[data-testid="create-user-button"]').should('be.visible');
```

#### `cy.loginAsUser()`
Login as regular user with limited permissions.

```typescript
cy.loginAsUser();
cy.visit('/dashboard');
cy.get('[data-testid="user-management"]').should('not.exist');
```

#### `cy.login(username, password, rememberMe?)`
Login with custom credentials.

```typescript
cy.login('custom_user', 'password123', true);
```

#### `cy.logout()`
Logout current user.

```typescript
cy.logout();
cy.shouldBeOnLoginPage();
```

### Assertion Commands

#### `cy.shouldBeOnLoginPage()`
Assert user is on login page.

```typescript
cy.shouldBeOnLoginPage();
cy.url().should('include', '/auth/login');
```

#### `cy.shouldBeAuthenticated()`
Assert user is authenticated.

```typescript
cy.shouldBeAuthenticated();
cy.getAccessToken().should('exist');
```

### Storage Commands

#### `cy.getAccessToken()`
Get access token from session storage.

```typescript
cy.getAccessToken().should('exist');
cy.getAccessToken().should('not.equal', oldToken);
```

#### `cy.clearAuth()`
Clear all authentication data.

```typescript
cy.clearAuth();
cy.getAccessToken().should('not.exist');
```

## CI/CD Integration

### GitHub Actions

The project includes a GitHub Actions workflow (`.github/workflows/e2e-tests.yml`) that:

1. Starts PostgreSQL and Redis services
2. Builds and starts Robin Gateway
3. Starts Robin UI dev server
4. Runs E2E tests in Chrome and Firefox
5. Uploads videos and screenshots on failure

### Running in CI

```bash
# Install dependencies
npm ci

# Start services
docker-compose up -d postgres redis
npm run dev &

# Wait for services
npx wait-on http://localhost:8080/actuator/health
npx wait-on http://localhost:4200

# Run tests
npm run test:e2e:headless
```

### Test Reports

After test run:
- **Videos**: `cypress/videos/` (all test runs)
- **Screenshots**: `cypress/screenshots/` (failures only)
- **Console output**: Detailed test results

## Troubleshooting

### Common Issues

#### 1. Tests Timing Out

**Problem**: Tests fail with timeout errors.

**Solution**:
```typescript
// Increase timeout for slow operations
cy.get('[data-testid="slow-element"]', { timeout: 10000 })
  .should('be.visible');

// Or globally in cypress.config.ts
defaultCommandTimeout: 10000
```

#### 2. Flaky Tests

**Problem**: Tests fail intermittently.

**Solution**:
```typescript
// Use .should() with retry logic
cy.get('[data-testid="element"]')
  .should('be.visible')
  .should('contain', 'Expected text');

// Wait for network requests
cy.intercept('GET', '**/api/data').as('getData');
cy.wait('@getData');
```

#### 3. Element Not Found

**Problem**: `cy.get()` fails to find element.

**Solution**:
```typescript
// Check if element exists first
cy.get('body').then(($body) => {
  if ($body.find('[data-testid="optional-element"]').length > 0) {
    cy.get('[data-testid="optional-element"]').click();
  }
});

// Or use conditional testing
cy.get('[data-testid="element"]').should('exist');
```

#### 4. Network Request Issues

**Problem**: API requests fail in tests.

**Solution**:
```typescript
// Mock failing requests
cy.intercept('GET', '**/api/data', {
  statusCode: 500,
  body: { error: 'Server error' }
}).as('failedRequest');

// Handle in test
cy.wait('@failedRequest');
cy.contains('Error loading data').should('be.visible');
```

### Debug Mode

```typescript
// Add breakpoints
cy.debug();
cy.pause();

// Log to console
cy.log('Checkpoint reached');

// Inspect element
cy.get('[data-testid="element"]').then(($el) => {
  console.log('Element:', $el);
});
```

### Running Specific Tests

```bash
# Single test file
npx cypress run --spec "cypress/e2e/auth/login.cy.ts"

# Specific test
npx cypress run --spec "cypress/e2e/auth/login.cy.ts" --grep "should login successfully"
```

## Performance

### Test Execution Time

| Test File | Approx. Time |
|-----------|--------------|
| login.cy.ts | ~2-3 minutes |
| logout.cy.ts | ~1-2 minutes |
| token-refresh.cy.ts | ~2-3 minutes |
| auth-guard.cy.ts | ~2-3 minutes |
| permissions.cy.ts | ~3-4 minutes |
| session-timeout.cy.ts | ~2-3 minutes |
| **Total** | **~12-18 minutes** |

### Optimization Tips

1. **Use cy.session()** for login caching
2. **Parallelize tests** in CI with `--parallel`
3. **Mock network requests** to avoid delays
4. **Use data-testid** for faster selectors
5. **Avoid unnecessary waits**

## Maintenance

### Adding New Tests

1. Create test file in `cypress/e2e/auth/`
2. Follow naming convention: `feature.cy.ts`
3. Use existing custom commands
4. Add to test suite documentation
5. Ensure tests pass before merging

### Updating Tests

1. Update tests when auth functionality changes
2. Maintain backward compatibility
3. Update README and documentation
4. Run full test suite before committing

## Resources

- [Cypress Documentation](https://docs.cypress.io)
- [Cypress Best Practices](https://docs.cypress.io/guides/references/best-practices)
- [Cypress Recipes](https://github.com/cypress-io/cypress-example-recipes)
- [Robin UI Auth Implementation](./AUTH_IMPLEMENTATION_PLAN.md)

## Support

For questions or issues:
1. Check this guide
2. Review existing tests
3. Consult Cypress documentation
4. Open an issue in the repository
