/**
 * Cypress E2E: Add new domain wizard
 * DM-109
 */

const API = '**/api/v1';

describe('Domain Management – Add New Domain Wizard', () => {
  beforeEach(() => {
    cy.clearAuth();
    cy.loginAsAdmin();
  });

  describe('Navigate to domain list', () => {
    it('should reach /domains page', () => {
      cy.visit('/domains');
      cy.url().should('include', '/domains');
    });

    it('should display the Add Domain button', () => {
      cy.visit('/domains');
      cy.contains('button', /add domain/i).should('be.visible');
    });
  });

  describe('Add domain – success path', () => {
    beforeEach(() => {
      cy.intercept('GET', `${API}/domains*`, {
        statusCode: 200,
        body: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 },
      }).as('getDomains');

      cy.intercept('POST', `${API}/domains`, {
        statusCode: 201,
        body: {
          id: 1,
          domain: 'newdomain.example.com',
          status: 'PENDING',
          createdAt: new Date().toISOString(),
        },
      }).as('createDomain');

      cy.visit('/domains');
      cy.wait('@getDomains');
    });

    it('should open the add domain modal', () => {
      cy.contains('button', /add domain/i).click();
      cy.get('[data-testid="add-domain-modal"], .modal, dialog').should('be.visible');
    });

    it('should require a domain name', () => {
      cy.contains('button', /add domain/i).click();
      cy.contains('button', /save|add|create/i).click();
      // Form should stay open / show validation
      cy.get('[data-testid="add-domain-modal"], .modal, dialog').should('be.visible');
    });

    it('should create a domain and close modal on success', () => {
      cy.contains('button', /add domain/i).click();
      cy.get('input[name="domain"], input[placeholder*="domain" i], [data-testid="domain-input"]')
        .first()
        .type('newdomain.example.com');
      cy.contains('button', /save|add|create/i).click();
      cy.wait('@createDomain');
      cy.get('[data-testid="add-domain-modal"], .modal, dialog').should('not.exist');
    });
  });

  describe('Add domain – error path', () => {
    beforeEach(() => {
      cy.intercept('GET', `${API}/domains*`, {
        body: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 },
      }).as('getDomains');

      cy.intercept('POST', `${API}/domains`, {
        statusCode: 400,
        body: { error: 'Domain already exists' },
      }).as('createDomainFail');

      cy.visit('/domains');
      cy.wait('@getDomains');
    });

    it('should show error when creation fails', () => {
      cy.contains('button', /add domain/i).click();
      cy.get('input[name="domain"], input[placeholder*="domain" i], [data-testid="domain-input"]')
        .first()
        .type('duplicate.example.com');
      cy.contains('button', /save|add|create/i).click();
      cy.wait('@createDomainFail');
      // Expect an error message to appear
      cy.contains(/failed|error/i, { timeout: 5000 }).should('be.visible');
    });
  });

  describe('Domain list rendering', () => {
    it('should display domains from API', () => {
      cy.intercept('GET', `${API}/domains*`, {
        body: {
          content: [
            { id: 1, domain: 'existing.example.com', status: 'ACTIVE', createdAt: new Date().toISOString() },
          ],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        },
      }).as('getDomains');

      cy.visit('/domains');
      cy.wait('@getDomains');
      cy.contains('existing.example.com').should('be.visible');
    });

    it('should guard /domains requiring auth', () => {
      cy.clearAuth();
      cy.visit('/domains');
      cy.url().should('include', '/auth/login');
    });
  });
});
