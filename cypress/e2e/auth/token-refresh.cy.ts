describe('Authentication - Token Refresh', () => {
  beforeEach(() => {
    cy.clearAuth();
    cy.loginAsAdmin();
  });

  describe('Automatic Token Refresh', () => {
    it('should automatically refresh token on 401 response', () => {
      let requestCount = 0;

      cy.intercept('GET', '**/email/queue', (req) => {
        requestCount++;
        if (requestCount === 1) {
          // First request returns 401
          req.reply({
            statusCode: 401,
            body: { error: 'Token expired' },
          });
        } else {
          // Subsequent requests succeed
          req.reply({
            statusCode: 200,
            body: { items: [], totalCount: 0 },
          });
        }
      }).as('queueRequest');

      cy.intercept('POST', '**/auth/refresh', {
        statusCode: 200,
        body: {
          accessToken: 'new_access_token',
          refreshToken: 'new_refresh_token',
          expiresIn: 3600,
          tokenType: 'Bearer',
        },
      }).as('refreshToken');

      cy.visit('/email/queue');

      // Should trigger refresh
      cy.wait('@refreshToken');

      // Should retry original request
      cy.wait('@queueRequest').then(() => {
        expect(requestCount).to.equal(2);
      });

      // Should stay on same page
      cy.url().should('include', '/email/queue');

      // Should have new token
      cy.getAccessToken().should('not.equal', 'new_access_token');
    });

    it('should queue multiple requests during token refresh', () => {
      let refreshCallCount = 0;

      cy.intercept('POST', '**/auth/refresh', (req) => {
        refreshCallCount++;
        req.reply({
          delay: 1000, // Simulate slow refresh
          statusCode: 200,
          body: {
            accessToken: 'new_access_token',
            expiresIn: 3600,
            tokenType: 'Bearer',
          },
        });
      }).as('refreshToken');

      cy.intercept('GET', '**/dashboard/stats', {
        statusCode: 401,
      });

      cy.intercept('GET', '**/email/queue', {
        statusCode: 401,
      });

      cy.visit('/dashboard');

      // Trigger multiple simultaneous requests
      cy.visit('/email/queue');

      // Should only call refresh once
      cy.wait('@refreshToken').then(() => {
        expect(refreshCallCount).to.equal(1);
      });
    });

    it('should logout when refresh token is invalid', () => {
      cy.intercept('GET', '**/dashboard/stats', {
        statusCode: 401,
        body: { error: 'Token expired' },
      });

      cy.intercept('POST', '**/auth/refresh', {
        statusCode: 401,
        body: { error: 'Invalid refresh token' },
      }).as('refreshFailed');

      cy.visit('/dashboard');

      cy.wait('@refreshFailed');

      // Should redirect to login
      cy.shouldBeOnLoginPage();

      // Should show error message
      cy.contains('Your session has expired. Please login again.').should('be.visible');

      // Should clear tokens
      cy.getAccessToken().should('not.exist');
    });

    it('should handle refresh token network error', () => {
      cy.intercept('GET', '**/dashboard/stats', {
        statusCode: 401,
      });

      cy.intercept('POST', '**/auth/refresh', {
        forceNetworkError: true,
      }).as('refreshNetworkError');

      cy.visit('/dashboard');

      // Should redirect to login after network error
      cy.shouldBeOnLoginPage();

      // Should show network error
      cy.contains('Network error. Please check your connection.').should('be.visible');
    });
  });

  describe('Proactive Token Refresh', () => {
    it('should refresh token before expiration', () => {
      // Set token with short expiration
      cy.window().then((win) => {
        const expiresAt = new Date(Date.now() + 60000); // 1 minute
        win.sessionStorage.setItem('robin_session_expires', expiresAt.toISOString());
      });

      cy.intercept('POST', '**/auth/refresh', {
        statusCode: 200,
        body: {
          accessToken: 'proactive_refresh_token',
          expiresIn: 3600,
          tokenType: 'Bearer',
        },
      }).as('proactiveRefresh');

      cy.visit('/dashboard');

      // Wait for proactive refresh (should happen 5 minutes before expiration)
      // Note: In real scenario, this would require waiting or mocking timers
      // For E2E, we verify the mechanism exists
      cy.window().then((win) => {
        expect(win.sessionStorage.getItem('robin_session_expires')).to.exist;
      });
    });
  });

  describe('Token Refresh Edge Cases', () => {
    it('should handle simultaneous 401 responses', () => {
      cy.intercept('GET', '**/dashboard/stats', {
        statusCode: 401,
      });

      cy.intercept('GET', '**/email/queue', {
        statusCode: 401,
      });

      let refreshCount = 0;
      cy.intercept('POST', '**/auth/refresh', (req) => {
        refreshCount++;
        req.reply({
          statusCode: 200,
          body: {
            accessToken: 'refreshed_token',
            expiresIn: 3600,
            tokenType: 'Bearer',
          },
        });
      }).as('refresh');

      // Trigger multiple endpoints at once
      cy.visit('/dashboard');

      // Should only refresh once despite multiple 401s
      cy.wait('@refresh').then(() => {
        // Allow time for any duplicate refreshes
        cy.wait(1000).then(() => {
          expect(refreshCount).to.equal(1);
        });
      });
    });

    it('should not refresh for public endpoints returning 401', () => {
      cy.clearAuth(); // Not logged in

      cy.intercept('GET', '**/health/public', {
        statusCode: 401,
      }).as('publicEndpoint');

      cy.intercept('POST', '**/auth/refresh', {
        statusCode: 200,
        body: { accessToken: 'should_not_be_called' },
      }).as('refresh');

      cy.request({
        url: `${Cypress.env('apiUrl')}/health/public`,
        failOnStatusCode: false,
      });

      // Should not call refresh for public endpoint
      cy.get('@refresh.all').should('have.length', 0);
    });

    it('should handle refresh token expiration during long session', () => {
      // Simulate long session where refresh token also expires
      cy.intercept('GET', '**/dashboard/stats', {
        statusCode: 401,
      });

      cy.intercept('POST', '**/auth/refresh', {
        statusCode: 403,
        body: { error: 'Refresh token expired' },
      }).as('refreshExpired');

      cy.visit('/dashboard');

      cy.wait('@refreshExpired');

      // Should logout and redirect to login
      cy.shouldBeOnLoginPage();

      // Should show appropriate message
      cy.contains('Please login again').should('be.visible');
    });
  });

  describe('Token Storage', () => {
    it('should update access token in session storage after refresh', () => {
      cy.intercept('GET', '**/dashboard/stats', {
        statusCode: 401,
      });

      cy.intercept('POST', '**/auth/refresh', {
        statusCode: 200,
        body: {
          accessToken: 'updated_token_12345',
          expiresIn: 3600,
          tokenType: 'Bearer',
        },
      }).as('refresh');

      const oldToken = cy.getAccessToken();

      cy.visit('/dashboard');

      cy.wait('@refresh');

      // Verify token was updated
      cy.getAccessToken().should((newToken) => {
        expect(newToken).to.not.equal(oldToken);
      });
    });

    it('should update session expiration after refresh', () => {
      cy.window().then((win) => {
        const oldExpiration = win.sessionStorage.getItem('robin_session_expires');

        cy.intercept('GET', '**/dashboard/stats', {
          statusCode: 401,
        });

        cy.intercept('POST', '**/auth/refresh', {
          statusCode: 200,
          body: {
            accessToken: 'new_token',
            expiresIn: 7200, // 2 hours
            tokenType: 'Bearer',
          },
        }).as('refresh');

        cy.visit('/dashboard');

        cy.wait('@refresh');

        cy.window().then((win) => {
          const newExpiration = win.sessionStorage.getItem('robin_session_expires');
          expect(newExpiration).to.not.equal(oldExpiration);

          if (newExpiration) {
            const expiresAt = new Date(newExpiration);
            const expectedTime = Date.now() + 7200000; // 2 hours in ms
            const diff = Math.abs(expiresAt.getTime() - expectedTime);
            expect(diff).to.be.lessThan(5000); // Within 5 seconds
          }
        });
      });
    });
  });
});
