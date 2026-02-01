/// <reference types="cypress" />

declare global {
  namespace Cypress {
    interface Chainable {
      /**
       * Custom command to login as admin user
       * @example cy.loginAsAdmin()
       */
      loginAsAdmin(): Chainable<void>;

      /**
       * Custom command to login as regular user
       * @example cy.loginAsUser()
       */
      loginAsUser(): Chainable<void>;

      /**
       * Custom command to login with custom credentials
       * @example cy.login('username', 'password')
       */
      login(username: string, password: string, rememberMe?: boolean): Chainable<void>;

      /**
       * Custom command to logout
       * @example cy.logout()
       */
      logout(): Chainable<void>;

      /**
       * Custom command to get access token from session storage
       * @example cy.getAccessToken()
       */
      getAccessToken(): Chainable<string | null>;

      /**
       * Custom command to clear authentication state
       * @example cy.clearAuth()
       */
      clearAuth(): Chainable<void>;

      /**
       * Custom command to check if user is on login page
       * @example cy.shouldBeOnLoginPage()
       */
      shouldBeOnLoginPage(): Chainable<void>;

      /**
       * Custom command to check if user is authenticated
       * @example cy.shouldBeAuthenticated()
       */
      shouldBeAuthenticated(): Chainable<void>;
    }
  }
}

// Login as admin user
Cypress.Commands.add('loginAsAdmin', () => {
  cy.login(
    Cypress.env('adminUsername'),
    Cypress.env('adminPassword')
  );
});

// Login as regular user
Cypress.Commands.add('loginAsUser', () => {
  cy.login(
    Cypress.env('userUsername'),
    Cypress.env('userPassword')
  );
});

// Login with custom credentials
Cypress.Commands.add('login', (username: string, password: string, rememberMe = false) => {
  cy.session(
    [username, password],
    () => {
      cy.visit('/auth/login');
      cy.get('input[name="username"]').type(username);
      cy.get('input[name="password"]').type(password);

      if (rememberMe) {
        cy.get('input[type="checkbox"][name="rememberMe"]').check();
      }

      cy.get('button[type="submit"]').click();

      // Wait for redirect to dashboard
      cy.url().should('include', '/dashboard', { timeout: 10000 });

      // Verify token exists in session storage
      cy.getAccessToken().should('exist');
    },
    {
      validate() {
        // Validate session is still valid
        cy.getAccessToken().should('exist');
      },
    }
  );
});

// Logout
Cypress.Commands.add('logout', () => {
  cy.get('[data-testid="user-menu"]').click();
  cy.get('[data-testid="logout-button"]').click();
  cy.url().should('include', '/auth/login');
  cy.getAccessToken().should('not.exist');
});

// Get access token from session storage
Cypress.Commands.add('getAccessToken', () => {
  return cy.window().then((win) => {
    return win.sessionStorage.getItem('robin_access_token');
  });
});

// Clear authentication state
Cypress.Commands.add('clearAuth', () => {
  cy.window().then((win) => {
    win.sessionStorage.clear();
    win.localStorage.clear();
  });
  cy.clearCookies();
});

// Check if on login page
Cypress.Commands.add('shouldBeOnLoginPage', () => {
  cy.url().should('include', '/auth/login');
  cy.get('input[name="username"]').should('be.visible');
  cy.get('input[name="password"]').should('be.visible');
  cy.get('button[type="submit"]').should('be.visible');
});

// Check if authenticated
Cypress.Commands.add('shouldBeAuthenticated', () => {
  cy.getAccessToken().should('exist');
  cy.url().should('not.include', '/auth/login');
});

export {};
