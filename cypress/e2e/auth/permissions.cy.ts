describe('Authentication - Permission-Based Access Control', () => {
  beforeEach(() => {
    cy.clearAuth();
  });

  describe('Admin User Permissions', () => {
    beforeEach(() => {
      cy.loginAsAdmin();
    });

    it('should have access to all features', () => {
      // Dashboard
      cy.visit('/dashboard');
      cy.url().should('include', '/dashboard');

      // User management
      cy.visit('/settings/users');
      cy.url().should('include', '/settings/users');

      // Server configuration
      cy.visit('/settings/server');
      cy.url().should('include', '/settings/server');

      // Email queue
      cy.visit('/email/queue');
      cy.url().should('include', '/email/queue');
    });

    it('should display admin-only UI elements', () => {
      cy.visit('/settings/users');

      // Should see create user button
      cy.get('[data-testid="create-user-button"]').should('be.visible');

      // Should see delete buttons
      cy.get('[data-testid="delete-user-button"]').should('exist');

      // Should see edit buttons
      cy.get('[data-testid="edit-user-button"]').should('exist');
    });

    it('should be able to perform admin actions', () => {
      cy.intercept('POST', '**/users', {
        statusCode: 201,
        body: {
          id: '123',
          username: 'newuser',
          email: 'new@example.com',
          roles: ['USER'],
        },
      }).as('createUser');

      cy.visit('/settings/users');

      cy.get('[data-testid="create-user-button"]').click();

      // Fill form
      cy.get('input[name="username"]').type('newuser');
      cy.get('input[name="email"]').type('new@example.com');
      cy.get('input[name="password"]').type('password123');

      cy.get('button[type="submit"]').click();

      cy.wait('@createUser');

      // Should show success message
      cy.contains('User created successfully').should('be.visible');
    });
  });

  describe('Regular User Permissions', () => {
    beforeEach(() => {
      cy.loginAsUser();
    });

    it('should have limited access to features', () => {
      // Can access dashboard
      cy.visit('/dashboard');
      cy.url().should('include', '/dashboard');

      // Can access email queue
      cy.visit('/email/queue');
      cy.url().should('include', '/email/queue');

      // Cannot access user management
      cy.visit('/settings/users');
      cy.url().should('include', '/unauthorized');
      cy.contains('You do not have permission').should('be.visible');
    });

    it('should not display admin-only UI elements', () => {
      cy.visit('/email/queue');

      // Should not see delete all button
      cy.get('[data-testid="delete-all-button"]').should('not.exist');

      // Should see view-only elements
      cy.get('[data-testid="queue-list"]').should('be.visible');
    });

    it('should not be able to perform admin actions', () => {
      cy.intercept('DELETE', '**/email/queue/*', {
        statusCode: 403,
        body: { error: 'Forbidden' },
      }).as('deleteQueue');

      cy.visit('/email/queue');

      // Try to delete (if button exists, it should fail)
      cy.get('body').then(($body) => {
        if ($body.find('[data-testid="delete-queue-item"]').length > 0) {
          cy.get('[data-testid="delete-queue-item"]').first().click();
          cy.wait('@deleteQueue');
          cy.contains('Access forbidden').should('be.visible');
        }
      });
    });
  });

  describe('Read-Only User Permissions', () => {
    it('should only view data without modification', () => {
      // Mock read-only login
      cy.intercept('POST', '**/auth/login', {
        statusCode: 200,
        body: {
          user: {
            id: '123',
            username: 'readonly',
            email: 'readonly@example.com',
            roles: ['READ_ONLY'],
            permissions: ['VIEW_DASHBOARD', 'VIEW_QUEUE', 'VIEW_LOGS'],
          },
          tokens: {
            accessToken: 'readonly_token',
            refreshToken: 'readonly_refresh',
            expiresIn: 3600,
            tokenType: 'Bearer',
          },
          permissions: ['VIEW_DASHBOARD', 'VIEW_QUEUE', 'VIEW_LOGS'],
        },
      }).as('loginReadOnly');

      cy.visit('/auth/login');
      cy.get('input[name="username"]').type('readonly');
      cy.get('input[name="password"]').type('readonly123');
      cy.get('button[type="submit"]').click();

      cy.wait('@loginReadOnly');

      cy.visit('/dashboard');
      cy.url().should('include', '/dashboard');

      // Should not see any action buttons
      cy.get('[data-testid="refresh-button"]').should('not.exist');
      cy.get('[data-testid="delete-button"]').should('not.exist');
      cy.get('[data-testid="edit-button"]').should('not.exist');
    });
  });

  describe('Permission Directives', () => {
    beforeEach(() => {
      cy.loginAsAdmin();
    });

    it('should show elements with correct permissions', () => {
      cy.visit('/dashboard');

      // Elements with admin permission should be visible
      cy.get('[data-permission="MANAGE_SERVER_CONFIG"]').should('be.visible');
      cy.get('[data-permission="MANAGE_USERS"]').should('be.visible');
    });

    it('should hide elements without required permissions', () => {
      cy.loginAsUser();

      cy.visit('/dashboard');

      // Elements requiring admin permissions should not exist
      cy.get('[data-permission="MANAGE_USERS"]').should('not.exist');
      cy.get('[data-permission="DELETE_QUEUE"]').should('not.exist');
    });

    it('should handle multiple permission requirements (AND)', () => {
      cy.visit('/settings/server');

      // Element requiring multiple permissions
      cy.get('[data-permissions-all="MANAGE_SERVER_CONFIG,MANAGE_QUEUE"]')
        .should('be.visible');
    });

    it('should handle any permission requirements (OR)', () => {
      cy.visit('/dashboard');

      // Element requiring any of the permissions
      cy.get('[data-permissions-any="VIEW_DASHBOARD,VIEW_QUEUE"]')
        .should('be.visible');
    });
  });

  describe('API Permission Enforcement', () => {
    beforeEach(() => {
      cy.loginAsUser();
    });

    it('should handle 403 Forbidden responses', () => {
      cy.intercept('POST', '**/settings/server', {
        statusCode: 403,
        body: {
          error: 'Insufficient permissions',
          code: 'FORBIDDEN',
        },
      }).as('forbiddenRequest');

      cy.visit('/settings/server');

      // Try to save (if form exists)
      cy.get('body').then(($body) => {
        if ($body.find('button[type="submit"]').length > 0) {
          cy.get('button[type="submit"]').click();
          cy.wait('@forbiddenRequest');

          // Should show error message
          cy.contains('You do not have permission').should('be.visible');

          // Should stay on same page
          cy.url().should('include', '/settings/server');
        }
      });
    });

    it('should show appropriate error for permission denied', () => {
      cy.intercept('DELETE', '**/users/*', {
        statusCode: 403,
        body: { error: 'Forbidden' },
      }).as('deleteUser');

      cy.visit('/settings/users');

      // Should show unauthorized page
      cy.contains('You do not have permission').should('be.visible');
    });
  });

  describe('Role Hierarchy', () => {
    it('should respect role hierarchy (ADMIN > USER > READ_ONLY)', () => {
      const roleTests = [
        {
          role: 'ADMIN',
          canAccess: ['/dashboard', '/settings/users', '/settings/server'],
          cannotAccess: [],
        },
        {
          role: 'USER',
          canAccess: ['/dashboard', '/email/queue'],
          cannotAccess: ['/settings/users', '/settings/server'],
        },
        {
          role: 'READ_ONLY',
          canAccess: ['/dashboard'],
          cannotAccess: ['/settings/users', '/email/queue'],
        },
      ];

      roleTests.forEach(({ role, canAccess, cannotAccess }) => {
        cy.clearAuth();

        if (role === 'ADMIN') {
          cy.loginAsAdmin();
        } else if (role === 'USER') {
          cy.loginAsUser();
        }

        canAccess.forEach((route) => {
          cy.visit(route);
          cy.url().should('include', route);
        });

        cannotAccess.forEach((route) => {
          cy.visit(route);
          cy.url().should('include', '/unauthorized');
        });
      });
    });
  });

  describe('Permission Changes', () => {
    it('should update UI when user permissions change', () => {
      cy.loginAsUser();

      cy.visit('/dashboard');

      // Should not see admin features
      cy.get('[data-permission="MANAGE_USERS"]').should('not.exist');

      // Simulate permission upgrade (e.g., user promoted to admin)
      cy.intercept('GET', '**/auth/me', {
        statusCode: 200,
        body: {
          id: '123',
          username: 'user',
          email: 'user@example.com',
          roles: ['ADMIN'], // Changed from USER to ADMIN
          permissions: [
            'VIEW_DASHBOARD',
            'VIEW_QUEUE',
            'MANAGE_USERS',
            'MANAGE_SERVER_CONFIG',
          ],
        },
      }).as('getUpdatedUser');

      // Reload or refresh user data
      cy.reload();

      cy.wait('@getUpdatedUser');

      // Should now see admin features
      cy.get('[data-permission="MANAGE_USERS"]').should('be.visible');
    });
  });

  describe('Unauthorized Page', () => {
    beforeEach(() => {
      cy.loginAsUser();
    });

    it('should display unauthorized page for forbidden routes', () => {
      cy.visit('/settings/users');

      // Should show unauthorized page
      cy.url().should('include', '/unauthorized');
      cy.contains('Access Denied').should('be.visible');
      cy.contains('You do not have permission').should('be.visible');

      // Should show back button
      cy.get('[data-testid="back-to-dashboard"]').should('be.visible');
    });

    it('should allow navigation back from unauthorized page', () => {
      cy.visit('/settings/users');
      cy.url().should('include', '/unauthorized');

      cy.get('[data-testid="back-to-dashboard"]').click();

      cy.url().should('include', '/dashboard');
    });
  });
});
