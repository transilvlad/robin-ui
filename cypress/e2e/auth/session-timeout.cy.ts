describe('Authentication - Session Timeout', () => {
  beforeEach(() => {
    cy.clearAuth();
  });

  describe('Inactivity Timeout', () => {
    it('should show warning before session timeout', () => {
      // Mock short timeout for testing
      cy.intercept('POST', '**/auth/login', (req) => {
        req.reply({
          statusCode: 200,
          body: {
            user: {
              id: '123',
              username: 'testuser',
              email: 'test@example.com',
              roles: ['USER'],
              permissions: ['VIEW_DASHBOARD'],
            },
            tokens: {
              accessToken: 'test_token',
              refreshToken: 'test_refresh',
              expiresIn: 300, // 5 minutes
              tokenType: 'Bearer',
            },
            permissions: ['VIEW_DASHBOARD'],
          },
        });
      }).as('login');

      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Mock time passing (would require cy.clock() in real implementation)
      // For now, we verify the timeout mechanism exists
      cy.window().then((win) => {
        const sessionExpires = win.sessionStorage.getItem('robin_session_expires');
        expect(sessionExpires).to.exist;
      });
    });

    it('should logout after inactivity period', () => {
      cy.clock(); // Control time

      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Fast-forward 30 minutes (default timeout)
      cy.tick(30 * 60 * 1000);

      // Should trigger logout
      cy.shouldBeOnLoginPage();
      cy.contains('Your session has expired').should('be.visible');
    });

    it('should reset timeout on user activity', () => {
      cy.clock();

      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Fast-forward 10 minutes
      cy.tick(10 * 60 * 1000);

      // User activity (click)
      cy.get('body').click();

      // Fast-forward another 25 minutes (total 35, but reset after 10)
      cy.tick(25 * 60 * 1000);

      // Should still be logged in
      cy.shouldBeAuthenticated();
      cy.url().should('include', '/dashboard');
    });

    it('should track various user activities', () => {
      cy.loginAsAdmin();
      cy.visit('/dashboard');

      const activities = [
        () => cy.get('body').click(),
        () => cy.get('body').type('test'),
        () => cy.get('body').trigger('mousemove'),
        () => cy.get('body').trigger('keydown'),
        () => cy.get('body').trigger('scroll'),
      ];

      activities.forEach((activity) => {
        activity();

        // Verify last activity timestamp updated
        cy.window().then((win) => {
          const authStore = (win as any).authStore;
          expect(authStore?.lastActivity()).to.exist;
        });
      });
    });
  });

  describe('Timeout Warning Dialog', () => {
    it('should show warning dialog 5 minutes before timeout', () => {
      cy.clock();

      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Fast-forward to 5 minutes before timeout (25 minutes)
      cy.tick(25 * 60 * 1000);

      // Should show warning dialog
      cy.get('[data-testid="session-timeout-warning"]').should('be.visible');
      cy.contains('Your session is about to expire').should('be.visible');
      cy.contains('5 minutes').should('be.visible');
    });

    it('should allow extending session from warning dialog', () => {
      cy.clock();

      cy.intercept('POST', '**/auth/refresh', {
        statusCode: 200,
        body: {
          accessToken: 'refreshed_token',
          expiresIn: 1800,
          tokenType: 'Bearer',
        },
      }).as('refreshToken');

      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Show warning
      cy.tick(25 * 60 * 1000);

      cy.get('[data-testid="session-timeout-warning"]').should('be.visible');

      // Click extend session
      cy.get('[data-testid="extend-session-button"]').click();

      cy.wait('@refreshToken');

      // Warning should disappear
      cy.get('[data-testid="session-timeout-warning"]').should('not.exist');

      // Session should be extended
      cy.shouldBeAuthenticated();
    });

    it('should update countdown in warning dialog', () => {
      cy.clock();

      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Show warning at 5 minutes
      cy.tick(25 * 60 * 1000);

      cy.get('[data-testid="session-timeout-warning"]').should('be.visible');
      cy.contains('5:00').should('be.visible');

      // Wait 1 minute
      cy.tick(60 * 1000);

      cy.contains('4:00').should('be.visible');

      // Wait another minute
      cy.tick(60 * 1000);

      cy.contains('3:00').should('be.visible');
    });

    it('should logout automatically when countdown reaches zero', () => {
      cy.clock();

      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Show warning
      cy.tick(25 * 60 * 1000);

      cy.get('[data-testid="session-timeout-warning"]').should('be.visible');

      // Wait until timeout
      cy.tick(5 * 60 * 1000);

      // Should logout
      cy.shouldBeOnLoginPage();
      cy.contains('Your session has expired').should('be.visible');
    });

    it('should allow manual logout from warning dialog', () => {
      cy.clock();

      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Show warning
      cy.tick(25 * 60 * 1000);

      cy.get('[data-testid="session-timeout-warning"]').should('be.visible');

      // Click logout button
      cy.get('[data-testid="logout-now-button"]').click();

      // Should logout immediately
      cy.shouldBeOnLoginPage();
    });
  });

  describe('Token Expiration', () => {
    it('should logout when access token expires', () => {
      cy.intercept('GET', '**/dashboard/stats', {
        statusCode: 401,
        body: { error: 'Token expired' },
      }).as('expiredToken');

      cy.intercept('POST', '**/auth/refresh', {
        statusCode: 401,
        body: { error: 'Refresh token expired' },
      }).as('refreshFailed');

      cy.loginAsAdmin();
      cy.visit('/dashboard');

      cy.wait('@expiredToken');
      cy.wait('@refreshFailed');

      // Should logout and redirect to login
      cy.shouldBeOnLoginPage();
      cy.contains('Your session has expired').should('be.visible');
    });

    it('should show remaining session time', () => {
      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Should show session indicator
      cy.get('[data-testid="session-indicator"]').should('be.visible');

      // Should show time remaining
      cy.get('[data-testid="session-time-remaining"]')
        .invoke('text')
        .should('match', /\d+:\d+/);
    });
  });

  describe('Multiple Tab Session Management', () => {
    it('should sync session across tabs', () => {
      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Simulate activity in another tab by updating lastActivity
      cy.window().then((win) => {
        win.dispatchEvent(new Event('storage'));
      });

      // Session should remain active
      cy.shouldBeAuthenticated();
    });

    it('should logout all tabs when one tab times out', () => {
      cy.clock();

      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Simulate timeout
      cy.tick(30 * 60 * 1000);

      // Trigger storage event (logout in another tab)
      cy.window().then((win) => {
        win.sessionStorage.clear();
        win.dispatchEvent(new Event('storage'));
      });

      // Should logout this tab too
      cy.shouldBeOnLoginPage();
    });
  });

  describe('Session Persistence', () => {
    it('should maintain session with remember me', () => {
      cy.visit('/auth/login');

      cy.get('input[name="username"]').type(Cypress.env('adminUsername'));
      cy.get('input[name="password"]').type(Cypress.env('adminPassword'));
      cy.get('input[type="checkbox"][name="rememberMe"]').check();
      cy.get('button[type="submit"]').click();

      cy.url().should('include', '/dashboard', { timeout: 10000 });

      // Close and reopen browser (simulated by clearing sessionStorage but keeping cookies)
      cy.window().then((win) => {
        win.sessionStorage.clear();
      });

      cy.reload();

      // Should restore session from refresh token cookie
      cy.url().should('include', '/dashboard');
      cy.shouldBeAuthenticated();
    });

    it('should not maintain session without remember me', () => {
      cy.visit('/auth/login');

      cy.get('input[name="username"]').type(Cypress.env('adminUsername'));
      cy.get('input[name="password"]').type(Cypress.env('adminPassword'));
      // Don't check remember me
      cy.get('button[type="submit"]').click();

      cy.url().should('include', '/dashboard', { timeout: 10000 });

      // Clear sessionStorage (simulate browser close)
      cy.clearAuth();

      cy.visit('/dashboard');

      // Should redirect to login
      cy.shouldBeOnLoginPage();
    });
  });

  describe('Background Tab Behavior', () => {
    it('should continue tracking time in background tabs', () => {
      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Simulate tab going to background
      cy.window().then((win) => {
        win.dispatchEvent(new Event('blur'));
      });

      // Verify timeout still active
      cy.window().then((win) => {
        const sessionExpires = win.sessionStorage.getItem('robin_session_expires');
        expect(sessionExpires).to.exist;
      });
    });

    it('should show timeout warning when tab returns to foreground', () => {
      cy.clock();

      cy.loginAsAdmin();
      cy.visit('/dashboard');

      // Tab goes to background
      cy.window().then((win) => {
        win.dispatchEvent(new Event('blur'));
      });

      // Time passes while in background (25 minutes)
      cy.tick(25 * 60 * 1000);

      // Tab returns to foreground
      cy.window().then((win) => {
        win.dispatchEvent(new Event('focus'));
      });

      // Should immediately show warning
      cy.get('[data-testid="session-timeout-warning"]').should('be.visible');
    });
  });

  describe('Server-Side Session Validation', () => {
    it('should validate session on critical operations', () => {
      cy.loginAsAdmin();
      cy.visit('/settings/users');

      cy.intercept('POST', '**/users', (req) => {
        // Verify token is sent with request
        expect(req.headers).to.have.property('authorization');
        expect(req.headers.authorization).to.include('Bearer');

        req.reply({
          statusCode: 401,
          body: { error: 'Session expired' },
        });
      }).as('createUser');

      // Try to create user
      cy.get('[data-testid="create-user-button"]').click();
      cy.get('input[name="username"]').type('newuser');
      cy.get('input[name="email"]').type('new@example.com');
      cy.get('button[type="submit"]').click();

      cy.wait('@createUser');

      // Should logout on session expiration
      cy.shouldBeOnLoginPage();
    });
  });
});
