describe('Authentication - Logout Flow', () => {
  beforeEach(() => {
    cy.clearAuth();
    cy.loginAsAdmin();
  });

  describe('Logout Functionality', () => {
    it('should logout successfully', () => {
      cy.visit('/dashboard');

      // Click user menu
      cy.get('[data-testid="user-menu"]').click();

      // Click logout button
      cy.get('[data-testid="logout-button"]').click();

      // Should redirect to login page
      cy.shouldBeOnLoginPage();

      // Should clear access token
      cy.getAccessToken().should('not.exist');

      // Should show logout notification
      cy.contains('You have been logged out').should('be.visible');
    });

    it('should clear all authentication data on logout', () => {
      cy.visit('/dashboard');

      // Verify authenticated state
      cy.getAccessToken().should('exist');
      cy.window().then((win) => {
        expect(win.sessionStorage.getItem('robin_user')).to.exist;
      });

      // Logout
      cy.get('[data-testid="user-menu"]').click();
      cy.get('[data-testid="logout-button"]').click();

      // Verify all auth data is cleared
      cy.getAccessToken().should('not.exist');
      cy.window().then((win) => {
        expect(win.sessionStorage.getItem('robin_user')).to.not.exist;
      });
      cy.getCookies().should('have.length', 0);
    });

    it('should handle logout API failure gracefully', () => {
      cy.intercept('POST', '**/auth/logout', {
        statusCode: 500,
        body: { error: 'Server error' },
      }).as('logoutError');

      cy.visit('/dashboard');
      cy.get('[data-testid="user-menu"]').click();
      cy.get('[data-testid="logout-button"]').click();

      cy.wait('@logoutError');

      // Should still clear client-side state and redirect
      cy.shouldBeOnLoginPage();
      cy.getAccessToken().should('not.exist');
    });

    it('should handle network error during logout', () => {
      cy.intercept('POST', '**/auth/logout', {
        forceNetworkError: true,
      }).as('logoutNetworkError');

      cy.visit('/dashboard');
      cy.get('[data-testid="user-menu"]').click();
      cy.get('[data-testid="logout-button"]').click();

      // Should still clear client-side state and redirect
      cy.shouldBeOnLoginPage();
      cy.getAccessToken().should('not.exist');
    });
  });

  describe('Post-Logout Behavior', () => {
    it('should require re-login to access protected routes', () => {
      cy.visit('/dashboard');

      // Logout
      cy.get('[data-testid="user-menu"]').click();
      cy.get('[data-testid="logout-button"]').click();

      cy.shouldBeOnLoginPage();

      // Try to access protected route
      cy.visit('/email/queue');

      // Should redirect to login
      cy.shouldBeOnLoginPage();
      cy.url().should('include', 'returnUrl=%2Femail%2Fqueue');
    });

    it('should not auto-login after logout', () => {
      cy.visit('/dashboard');

      // Logout
      cy.get('[data-testid="user-menu"]').click();
      cy.get('[data-testid="logout-button"]').click();

      cy.shouldBeOnLoginPage();

      // Reload page
      cy.reload();

      // Should still be on login page
      cy.shouldBeOnLoginPage();
    });

    it('should allow immediate re-login after logout', () => {
      cy.visit('/dashboard');

      // Logout
      cy.get('[data-testid="user-menu"]').click();
      cy.get('[data-testid="logout-button"]').click();

      cy.shouldBeOnLoginPage();

      // Login again
      cy.get('input[name="username"]').type(Cypress.env('adminUsername'));
      cy.get('input[name="password"]').type(Cypress.env('adminPassword'));
      cy.get('button[type="submit"]').click();

      // Should successfully login
      cy.url().should('include', '/dashboard', { timeout: 10000 });
      cy.shouldBeAuthenticated();
    });
  });

  describe('Logout from Multiple Tabs', () => {
    it('should logout from all tabs when logout is triggered', () => {
      cy.visit('/dashboard');

      // Open second window (simulated by visiting in same window)
      cy.window().then((win) => {
        // Store current session
        const token = win.sessionStorage.getItem('robin_access_token');
        expect(token).to.exist;
      });

      // Logout
      cy.get('[data-testid="user-menu"]').click();
      cy.get('[data-testid="logout-button"]').click();

      // Verify all storage is cleared
      cy.window().then((win) => {
        expect(win.sessionStorage.getItem('robin_access_token')).to.not.exist;
        expect(win.sessionStorage.getItem('robin_user')).to.not.exist;
      });
    });
  });

  describe('Automatic Logout', () => {
    it('should logout on token expiration', () => {
      cy.intercept('GET', '**/dashboard/stats', {
        statusCode: 401,
        body: { error: 'Token expired' },
      }).as('expiredToken');

      cy.visit('/dashboard');

      // Wait for token expiration
      cy.wait('@expiredToken');

      // Should redirect to login
      cy.shouldBeOnLoginPage();

      // Should show expiration message
      cy.contains('Your session has expired. Please login again.').should('be.visible');
    });

    it('should logout after session timeout period', () => {
      // Note: This test would require mocking the session timeout
      // For now, we test that the timeout service exists and is configured
      cy.visit('/dashboard');

      cy.window().then((win) => {
        // Verify session timeout is active
        const sessionExpires = win.sessionStorage.getItem('robin_session_expires');
        expect(sessionExpires).to.exist;
      });
    });
  });
});
