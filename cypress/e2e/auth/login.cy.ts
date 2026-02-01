describe('Authentication - Login Flow', () => {
  beforeEach(() => {
    cy.clearAuth();
  });

  describe('Login Page UI', () => {
    beforeEach(() => {
      cy.visit('/auth/login');
    });

    it('should display login form elements', () => {
      cy.get('input[name="username"]').should('be.visible');
      cy.get('input[name="password"]').should('be.visible');
      cy.get('input[type="checkbox"][name="rememberMe"]').should('be.visible');
      cy.get('button[type="submit"]').should('be.visible');
    });

    it('should have username field focused by default', () => {
      cy.get('input[name="username"]').should('have.focus');
    });

    it('should toggle password visibility', () => {
      cy.get('input[name="password"]').should('have.attr', 'type', 'password');
      cy.get('[data-testid="toggle-password-visibility"]').click();
      cy.get('input[name="password"]').should('have.attr', 'type', 'text');
      cy.get('[data-testid="toggle-password-visibility"]').click();
      cy.get('input[name="password"]').should('have.attr', 'type', 'password');
    });

    it('should have submit button disabled when form is empty', () => {
      cy.get('button[type="submit"]').should('be.disabled');
    });

    it('should enable submit button when form is valid', () => {
      cy.get('input[name="username"]').type('testuser');
      cy.get('input[name="password"]').type('password123');
      cy.get('button[type="submit"]').should('not.be.disabled');
    });
  });

  describe('Form Validation', () => {
    beforeEach(() => {
      cy.visit('/auth/login');
    });

    it('should show error when username is empty', () => {
      cy.get('input[name="username"]').focus().blur();
      cy.contains('Username is required').should('be.visible');
    });

    it('should show error when username is too short', () => {
      cy.get('input[name="username"]').type('ab').blur();
      cy.contains('Username must be at least 3 characters').should('be.visible');
    });

    it('should show error when password is empty', () => {
      cy.get('input[name="password"]').focus().blur();
      cy.contains('Password is required').should('be.visible');
    });

    it('should show error when password is too short', () => {
      cy.get('input[name="password"]').type('12345').blur();
      cy.contains('Password must be at least 6 characters').should('be.visible');
    });

    it('should clear errors when valid input is entered', () => {
      cy.get('input[name="username"]').focus().blur();
      cy.contains('Username is required').should('be.visible');

      cy.get('input[name="username"]').type('testuser');
      cy.contains('Username is required').should('not.exist');
    });
  });

  describe('Successful Login', () => {
    it('should login successfully with valid admin credentials', () => {
      cy.visit('/auth/login');

      cy.get('input[name="username"]').type(Cypress.env('adminUsername'));
      cy.get('input[name="password"]').type(Cypress.env('adminPassword'));
      cy.get('button[type="submit"]').click();

      // Should redirect to dashboard
      cy.url().should('include', '/dashboard', { timeout: 10000 });

      // Should store access token
      cy.getAccessToken().should('exist');

      // Should show success notification
      cy.contains('Login successful').should('be.visible');
    });

    it('should login successfully with valid user credentials', () => {
      cy.visit('/auth/login');

      cy.get('input[name="username"]').type(Cypress.env('userUsername'));
      cy.get('input[name="password"]').type(Cypress.env('userPassword'));
      cy.get('button[type="submit"]').click();

      cy.url().should('include', '/dashboard', { timeout: 10000 });
      cy.getAccessToken().should('exist');
    });

    it('should redirect to returnUrl after login', () => {
      cy.visit('/email/queue');

      // Should redirect to login with returnUrl
      cy.shouldBeOnLoginPage();
      cy.url().should('include', 'returnUrl=%2Femail%2Fqueue');

      // Login
      cy.get('input[name="username"]').type(Cypress.env('adminUsername'));
      cy.get('input[name="password"]').type(Cypress.env('adminPassword'));
      cy.get('button[type="submit"]').click();

      // Should redirect back to original URL
      cy.url().should('include', '/email/queue', { timeout: 10000 });
    });

    it('should persist session with remember me checked', () => {
      cy.visit('/auth/login');

      cy.get('input[name="username"]').type(Cypress.env('adminUsername'));
      cy.get('input[name="password"]').type(Cypress.env('adminPassword'));
      cy.get('input[type="checkbox"][name="rememberMe"]').check();
      cy.get('button[type="submit"]').click();

      cy.url().should('include', '/dashboard', { timeout: 10000 });

      // Verify refresh token cookie exists (HttpOnly, can't read directly)
      cy.getCookies().should('have.length.greaterThan', 0);
    });
  });

  describe('Failed Login', () => {
    beforeEach(() => {
      cy.visit('/auth/login');
    });

    it('should show error for invalid credentials', () => {
      cy.get('input[name="username"]').type('invaliduser');
      cy.get('input[name="password"]').type('wrongpassword');
      cy.get('button[type="submit"]').click();

      // Should stay on login page
      cy.url().should('include', '/auth/login');

      // Should show error message
      cy.contains('Invalid username or password').should('be.visible');

      // Should not store token
      cy.getAccessToken().should('not.exist');
    });

    it('should show loading state during login', () => {
      cy.intercept('POST', '**/auth/login', {
        delay: 1000,
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
            accessToken: 'mock_token',
            refreshToken: 'mock_refresh',
            expiresIn: 3600,
            tokenType: 'Bearer',
          },
          permissions: ['VIEW_DASHBOARD'],
        },
      }).as('loginRequest');

      cy.get('input[name="username"]').type('testuser');
      cy.get('input[name="password"]').type('password123');
      cy.get('button[type="submit"]').click();

      // Should show loading state
      cy.get('button[type="submit"]').should('be.disabled');
      cy.contains('Logging in...').should('be.visible');

      cy.wait('@loginRequest');
    });

    it('should handle network errors gracefully', () => {
      cy.intercept('POST', '**/auth/login', {
        forceNetworkError: true,
      }).as('loginNetworkError');

      cy.get('input[name="username"]').type('testuser');
      cy.get('input[name="password"]').type('password123');
      cy.get('button[type="submit"]').click();

      // Should show network error
      cy.contains('Network error. Please check your connection.').should('be.visible');

      // Should not redirect
      cy.url().should('include', '/auth/login');
    });

    it('should handle server errors gracefully', () => {
      cy.intercept('POST', '**/auth/login', {
        statusCode: 500,
        body: {
          error: 'Internal server error',
        },
      }).as('loginServerError');

      cy.get('input[name="username"]').type('testuser');
      cy.get('input[name="password"]').type('password123');
      cy.get('button[type="submit"]').click();

      // Should show server error
      cy.contains('An unexpected error occurred').should('be.visible');
    });
  });

  describe('Auto Login on Page Load', () => {
    it('should auto-login when valid token exists in storage', () => {
      // First login to get valid token
      cy.loginAsAdmin();

      // Visit root URL
      cy.visit('/');

      // Should automatically redirect to dashboard without showing login page
      cy.url().should('include', '/dashboard');
      cy.shouldBeAuthenticated();
    });

    it('should redirect to login when no token exists', () => {
      cy.visit('/dashboard');

      // Should redirect to login
      cy.shouldBeOnLoginPage();
      cy.url().should('include', 'returnUrl=%2Fdashboard');
    });

    it('should redirect to login when token is expired', () => {
      // Manually set expired token
      cy.window().then((win) => {
        win.sessionStorage.setItem('robin_access_token', 'expired_token');
        win.sessionStorage.setItem('robin_user', JSON.stringify({
          id: '123',
          username: 'test',
          email: 'test@example.com',
          roles: ['USER'],
          permissions: [],
        }));
      });

      cy.intercept('GET', '**/auth/verify', {
        statusCode: 401,
        body: { error: 'Token expired' },
      }).as('verifyToken');

      cy.visit('/dashboard');

      // Should redirect to login
      cy.shouldBeOnLoginPage();

      // Token should be cleared
      cy.getAccessToken().should('not.exist');
    });
  });

  describe('Keyboard Navigation', () => {
    beforeEach(() => {
      cy.visit('/auth/login');
    });

    it('should allow tab navigation between fields', () => {
      cy.get('input[name="username"]').should('have.focus');
      cy.get('input[name="username"]').tab();
      cy.get('input[name="password"]').should('have.focus');
      cy.get('input[name="password"]').tab();
      cy.get('input[type="checkbox"][name="rememberMe"]').should('have.focus');
    });

    it('should submit form with Enter key', () => {
      cy.get('input[name="username"]').type(Cypress.env('adminUsername'));
      cy.get('input[name="password"]').type(Cypress.env('adminPassword')).type('{enter}');

      cy.url().should('include', '/dashboard', { timeout: 10000 });
    });
  });

  describe('Accessibility', () => {
    beforeEach(() => {
      cy.visit('/auth/login');
    });

    it('should have proper ARIA labels', () => {
      cy.get('input[name="username"]').should('have.attr', 'aria-label');
      cy.get('input[name="password"]').should('have.attr', 'aria-label');
      cy.get('button[type="submit"]').should('have.attr', 'aria-label');
    });

    it('should associate error messages with inputs', () => {
      cy.get('input[name="username"]').focus().blur();

      cy.get('input[name="username"]')
        .should('have.attr', 'aria-invalid', 'true')
        .should('have.attr', 'aria-describedby');
    });
  });
});
