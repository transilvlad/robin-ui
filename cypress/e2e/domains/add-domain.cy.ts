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

  describe('Add domain – detected DKIM selectors step', () => {
    const DOMAIN = 'dkim-test.example.com';

    beforeEach(() => {
      cy.intercept('GET', `${API}/domains*`, {
        body: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 },
      }).as('getDomains');

      cy.intercept('GET', `${API}/domains/lookup*`, {
        statusCode: 200,
        body: {
          domain: DOMAIN,
          nsRecords: ['ns1.cloudflare.com'],
          mxRecords: [],
          spfRecords: [],
          dmarcRecords: [],
          mtaStsRecords: [],
          smtpTlsRecords: [],
          detectedNsProviderType: 'CLOUDFLARE',
          suggestedProvider: null,
          availableProviders: [],
          allRecords: [],
          detectedDkimSelectors: [
            { domain: DOMAIN, selector: 'google', algorithm: 'RSA_2048', revoked: false, detectedAt: new Date().toISOString() },
            { domain: DOMAIN, selector: 'selector1', algorithm: 'RSA_2048', revoked: true, detectedAt: new Date().toISOString() },
          ],
        },
      }).as('lookupDomain');

      cy.intercept('POST', `${API}/domains`, {
        statusCode: 201,
        body: { id: 42, domain: DOMAIN, status: 'PENDING', createdAt: new Date().toISOString() },
      }).as('createDomain');

      cy.intercept('POST', `${API}/domains/${DOMAIN}/dkim/generate`, {
        statusCode: 201,
        body: {
          id: 1, domain: DOMAIN, selector: '20260227', algorithm: 'RSA_2048',
          status: 'PENDING_PUBLISH', testMode: true, createdAt: new Date().toISOString(),
        },
      }).as('generateDkim');

      cy.visit('/domains');
      cy.wait('@getDomains');
    });

    it('should show detected DKIM selectors after DNS detection', () => {
      cy.contains('button', /add domain/i).click();
      cy.get('input[placeholder*="example.com" i], [data-testid="domain-input"]')
        .first().type(DOMAIN);
      cy.contains('button', /detect dns/i).click();
      cy.wait('@lookupDomain');

      cy.get('[data-testid="detected-dkim-panel"]').should('be.visible');
      cy.get('[data-testid="detected-selector-google"]').should('be.visible');
      cy.get('[data-testid="detected-selector-selector1"]').should('be.visible');
      cy.contains('Existing DKIM selectors detected').should('be.visible');
    });

    it('should show active/revoked status badges for detected selectors', () => {
      cy.contains('button', /add domain/i).click();
      cy.get('input[placeholder*="example.com" i], [data-testid="domain-input"]')
        .first().type(DOMAIN);
      cy.contains('button', /detect dns/i).click();
      cy.wait('@lookupDomain');

      cy.get('[data-testid="detected-selector-google"]').contains('Active');
      cy.get('[data-testid="detected-selector-selector1"]').contains('Revoked');
    });

    it('should show DKIM generation step after domain is added', () => {
      cy.contains('button', /add domain/i).click();
      cy.get('input[placeholder*="example.com" i], [data-testid="domain-input"]')
        .first().type(DOMAIN);
      cy.contains('button', /detect dns/i).click();
      cy.wait('@lookupDomain');
      cy.contains('button', /add domain/i).last().click();
      cy.wait('@createDomain');

      cy.get('[data-testid="dkim-generation-step"]').should('be.visible');
      cy.get('[data-testid="generate-dkim-btn"]').should('be.visible');
      cy.contains('Domain added successfully').should('be.visible');
    });

    it('should call generate DKIM API when Generate DKIM Key is clicked', () => {
      cy.contains('button', /add domain/i).click();
      cy.get('input[placeholder*="example.com" i], [data-testid="domain-input"]')
        .first().type(DOMAIN);
      cy.contains('button', /detect dns/i).click();
      cy.wait('@lookupDomain');
      cy.contains('button', /add domain/i).last().click();
      cy.wait('@createDomain');

      cy.get('[data-testid="generate-dkim-btn"]').click();
      cy.wait('@generateDkim');

      cy.get('[data-testid="dkim-generated-success"]').should('be.visible');
      cy.get('[data-testid="go-to-domain-btn"]').should('be.visible');
    });

    it('should close modal when Skip for now is clicked', () => {
      cy.contains('button', /add domain/i).click();
      cy.get('input[placeholder*="example.com" i], [data-testid="domain-input"]')
        .first().type(DOMAIN);
      cy.contains('button', /detect dns/i).click();
      cy.wait('@lookupDomain');
      cy.contains('button', /add domain/i).last().click();
      cy.wait('@createDomain');

      cy.get('[data-testid="dkim-generation-step"]').should('be.visible');
      cy.contains('button', /skip for now/i).click();
      cy.get('[data-testid="dkim-generation-step"]').should('not.exist');
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
