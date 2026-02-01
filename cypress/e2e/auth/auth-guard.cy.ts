describe('Authentication - Auth Guard & Protected Routes', () => {
  beforeEach(() => {
    cy.clearAuth();
  });

  describe('Unauthenticated Access', () => {
    const protectedRoutes = [
      '/dashboard',
      '/email/queue',
      '/email/storage',
      '/security/clamav',
      '/security/rspamd',
      '/security/blocklist',
      '/routing/relay',
      '/routing/webhooks',
      '/monitoring/metrics',
      '/monitoring/logs',
      '/settings/server',
      '/settings/users',
    ];

    protectedRoutes.forEach((route) => {
      it(`should redirect ${route} to login when not authenticated`, () => {
        cy.visit(route);

        // Should redirect to login
        cy.shouldBeOnLoginPage();

        // Should preserve return URL
        cy.url().should('include', `returnUrl=${encodeURIComponent(route)}`);
      });
    });

    it('should allow access to public routes without authentication', () => {
      cy.visit('/auth/login');
      cy.url().should('include', '/auth/login');

      // Should not redirect
      cy.get('input[name="username"]').should('be.visible');
    });
  });

  describe('Authenticated Access', () => {
    beforeEach(() => {
      cy.loginAsAdmin();
    });

    it('should allow access to all routes when authenticated as admin', () => {
      const routes = [
        '/dashboard',
        '/email/queue',
        '/settings/users',
      ];

      routes.forEach((route) => {
        cy.visit(route);
        cy.url().should('include', route);
        cy.url().should('not.include', '/auth/login');
      });
    });

    it('should redirect to dashboard when accessing login page while authenticated', () => {
      cy.visit('/auth/login');

      // Should redirect to dashboard
      cy.url().should('include', '/dashboard');
    });

    it('should redirect to dashboard when accessing root while authenticated', () => {
      cy.visit('/');

      // Should redirect to dashboard
      cy.url().should('include', '/dashboard');
    });

    it('should maintain authentication across page reloads', () => {
      cy.visit('/dashboard');
      cy.url().should('include', '/dashboard');

      cy.reload();

      // Should still be authenticated
      cy.url().should('include', '/dashboard');
      cy.shouldBeAuthenticated();
    });

    it('should maintain authentication across route navigation', () => {
      cy.visit('/dashboard');
      cy.url().should('include', '/dashboard');

      // Navigate to different route
      cy.visit('/email/queue');
      cy.url().should('include', '/email/queue');

      // Should still be authenticated
      cy.shouldBeAuthenticated();

      // Navigate back
      cy.visit('/dashboard');
      cy.url().should('include', '/dashboard');
      cy.shouldBeAuthenticated();
    });
  });

  describe('Return URL Functionality', () => {
    it('should redirect to original URL after login', () => {
      // Try to access protected route
      cy.visit('/settings/users');

      // Should redirect to login with returnUrl
      cy.shouldBeOnLoginPage();
      cy.url().should('include', 'returnUrl=%2Fsettings%2Fusers');

      // Login
      cy.get('input[name="username"]').type(Cypress.env('adminUsername'));
      cy.get('input[name="password"]').type(Cypress.env('adminPassword'));
      cy.get('button[type="submit"]').click();

      // Should redirect back to original URL
      cy.url().should('include', '/settings/users', { timeout: 10000 });
    });

    it('should redirect to dashboard when returnUrl is login page', () => {
      cy.visit('/auth/login?returnUrl=%2Fauth%2Flogin');

      cy.get('input[name="username"]').type(Cypress.env('adminUsername'));
      cy.get('input[name="password"]').type(Cypress.env('adminPassword'));
      cy.get('button[type="submit"]').click();

      // Should redirect to dashboard, not back to login
      cy.url().should('include', '/dashboard');
      cy.url().should('not.include', '/auth/login');
    });

    it('should handle deep-linked URLs with query parameters', () => {
      cy.visit('/email/queue?status=failed&page=2');

      cy.shouldBeOnLoginPage();

      // Login
      cy.get('input[name="username"]').type(Cypress.env('adminUsername'));
      cy.get('input[name="password"]').type(Cypress.env('adminPassword'));
      cy.get('button[type="submit"]').click();

      // Should redirect to original URL with query params
      cy.url().should('include', '/email/queue');
      cy.url().should('include', 'status=failed');
      cy.url().should('include', 'page=2');
    });
  });

  describe('Guard Edge Cases', () => {
    it('should handle concurrent guard checks', () => {
      // Open multiple tabs/windows (simulated by rapid navigation)
      cy.visit('/dashboard');
      cy.shouldBeOnLoginPage();

      cy.visit('/email/queue');
      cy.shouldBeOnLoginPage();

      // Login
      cy.loginAsAdmin();

      // Both routes should now be accessible
      cy.visit('/dashboard');
      cy.url().should('include', '/dashboard');

      cy.visit('/email/queue');
      cy.url().should('include', '/email/queue');
    });

    it('should handle navigation during token verification', () => {
      cy.intercept('GET', '**/auth/verify', {
        delay: 2000,
        statusCode: 200,
        body: { valid: true },
      }).as('verifyToken');

      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Navigate while verification is pending
      cy.visit('/email/queue');

      // Should eventually allow access
      cy.url().should('include', '/email/queue');
    });

    it('should clear pending navigation on logout', () => {
      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Start navigation
      cy.get('a[href="/email/queue"]').click();

      // Logout during navigation
      cy.get('[data-testid="user-menu"]').click();
      cy.get('[data-testid="logout-button"]').click();

      // Should redirect to login, not continue to email queue
      cy.shouldBeOnLoginPage();
    });
  });

  describe('Session Validation', () => {
    it('should re-validate session on route change', () => {
      cy.loginAsAdmin();
      cy.visit('/dashboard');

      cy.intercept('GET', '**/dashboard/stats', (req) => {
        // Verify Authorization header is present
        expect(req.headers).to.have.property('authorization');
        expect(req.headers.authorization).to.include('Bearer');
        req.reply({
          statusCode: 200,
          body: {},
        });
      }).as('dashboardRequest');

      cy.wait('@dashboardRequest');

      // Navigate to another route
      cy.visit('/email/queue');

      cy.intercept('GET', '**/email/queue', (req) => {
        // Verify Authorization header is still present
        expect(req.headers).to.have.property('authorization');
        expect(req.headers.authorization).to.include('Bearer');
        req.reply({
          statusCode: 200,
          body: { items: [] },
        });
      }).as('queueRequest');

      cy.wait('@queueRequest');
    });

    it('should logout when session becomes invalid during navigation', () => {
      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Manually invalidate token
      cy.window().then((win) => {
        win.sessionStorage.removeItem('robin_access_token');
      });

      // Try to navigate
      cy.visit('/email/queue');

      // Should redirect to login
      cy.shouldBeOnLoginPage();
    });
  });

  describe('Browser Back/Forward Navigation', () => {
    it('should handle browser back button correctly', () => {
      cy.loginAsAdmin();
      cy.visit('/dashboard');
      cy.visit('/email/queue');

      // Click back
      cy.go('back');

      // Should still be authenticated
      cy.url().should('include', '/dashboard');
      cy.shouldBeAuthenticated();
    });

    it('should handle browser forward button correctly', () => {
      cy.loginAsAdmin();
      cy.visit('/dashboard');
      cy.visit('/email/queue');
      cy.go('back');

      // Click forward
      cy.go('forward');

      // Should still be authenticated
      cy.url().should('include', '/email/queue');
      cy.shouldBeAuthenticated();
    });

    it('should redirect to login when using back button after logout', () => {
      cy.loginAsAdmin();
      cy.visit('/dashboard');
      cy.visit('/email/queue');

      // Logout
      cy.get('[data-testid="user-menu"]').click();
      cy.get('[data-testid="logout-button"]').click();

      cy.shouldBeOnLoginPage();

      // Try to go back
      cy.go('back');

      // Should still be on login page
      cy.shouldBeOnLoginPage();
    });
  });
});
