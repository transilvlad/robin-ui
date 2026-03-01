/**
 * Cypress E2E: Existing domain verify-then-manage
 * DM-110
 */

const API = '**/api/v1';
const DOMAIN_ID = 42;

const mockDomain = {
  id: DOMAIN_ID,
  domain: 'managed.example.com',
  status: 'ACTIVE',
  createdAt: new Date().toISOString(),
};

const mockHealth = [
  { id: 1, domainId: DOMAIN_ID, checkType: 'SPF',  status: 'OK',      lastChecked: new Date().toISOString() },
  { id: 2, domainId: DOMAIN_ID, checkType: 'DKIM', status: 'WARN',    lastChecked: new Date().toISOString() },
  { id: 3, domainId: DOMAIN_ID, checkType: 'DMARC', status: 'ERROR', lastChecked: new Date().toISOString() },
];

describe('Domain Management – Verify then Manage', () => {
  beforeEach(() => {
    cy.clearAuth();
    cy.loginAsAdmin();

    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}`, { body: mockDomain }).as('getDomain');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/health`, { body: mockHealth }).as('getHealth');
    cy.intercept('POST', `${API}/domains/${DOMAIN_ID}/health/verify`, { body: mockHealth }).as('verifyHealth');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dkim`, { body: [] }).as('getDkim');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/mta-sts`, { statusCode: 404, body: {} }).as('getMtaSts');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dns`, { body: [] }).as('getDnsRecords');
  });

  it('should navigate to domain detail page', () => {
    cy.intercept('GET', `${API}/domains*`, {
      body: {
        content: [mockDomain],
        totalElements: 1, totalPages: 1, number: 0, size: 20,
      },
    }).as('getDomains');

    cy.visit('/domains');
    cy.wait('@getDomains');
    cy.contains('managed.example.com').click();
    cy.url().should('include', `/domains/${DOMAIN_ID}`);
  });

  it('should display domain status and name on detail page', () => {
    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getDomain');
    cy.contains('managed.example.com').should('be.visible');
    cy.contains(/active/i).should('be.visible');
  });

  it('should display health check results', () => {
    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getDomain');
    cy.wait('@getHealth');
    cy.contains(/spf/i).should('be.visible');
    cy.contains(/dkim/i).should('be.visible');
    cy.contains(/dmarc/i).should('be.visible');
  });

  it('should trigger on-demand verify and refresh health', () => {
    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getDomain');
    cy.wait('@getHealth');

    cy.contains('button', /verify|check now/i).click();
    cy.wait('@verifyHealth');

    // Health section should still show results after verify
    cy.contains(/spf/i).should('be.visible');
  });

  it('should display health status indicators', () => {
    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getHealth');

    // At least one status badge should be visible
    cy.get('[class*="green"], [class*="yellow"], [class*="red"], .badge, .status')
      .should('have.length.greaterThan', 0);
  });

  it('should show MTA-STS section with not-deployed state', () => {
    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getMtaSts');
    cy.contains(/mta-sts/i).should('be.visible');
  });

  it('should display DKIM section', () => {
    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getDkim');
    cy.contains(/dkim/i).should('be.visible');
  });
});
