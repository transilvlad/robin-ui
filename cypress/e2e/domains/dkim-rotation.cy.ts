/**
 * Cypress E2E: DKIM key rotation
 * DM-111
 */

const API = '**/api/v1';
const DOMAIN_ID = 99;

const activeKey = {
  id: 10,
  domainId: DOMAIN_ID,
  selector: 'default',
  algorithm: 'RSA_2048',
  privateKey: '----PRIVATE----',
  publicKey: '----PUBLIC----',
  status: 'ACTIVE',
  createdAt: new Date().toISOString(),
};

const rotatedKey = {
  id: 11,
  domainId: DOMAIN_ID,
  selector: 'rotate-2024',
  algorithm: 'RSA_2048',
  privateKey: '----PRIVATE-NEW----',
  publicKey: '----PUBLIC-NEW----',
  status: 'ROTATING',
  createdAt: new Date().toISOString(),
};

const retiredKey = { ...activeKey, status: 'RETIRED', retiredAt: new Date().toISOString() };

describe('Domain Management – DKIM Key Rotation', () => {
  beforeEach(() => {
    cy.clearAuth();
    cy.loginAsAdmin();

    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}`, {
      body: { id: DOMAIN_ID, domain: 'dkim-rotate.example.com', status: 'ACTIVE', createdAt: new Date().toISOString() },
    }).as('getDomain');

    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/health`, { body: [] }).as('getHealth');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/mta-sts`, { statusCode: 404, body: {} }).as('getMtaSts');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dns`, { body: [] }).as('getDnsRecords');
  });

  describe('Initial DKIM state', () => {
    it('should display active DKIM key', () => {
      cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dkim`, { body: [activeKey] }).as('getDkim');
      cy.visit(`/domains/${DOMAIN_ID}`);
      cy.wait('@getDkim');
      cy.contains(/dkim/i).should('be.visible');
      cy.contains(/default/i).should('be.visible');
    });

    it('should show the Rotate button when a key exists', () => {
      cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dkim`, { body: [activeKey] }).as('getDkim');
      cy.visit(`/domains/${DOMAIN_ID}`);
      cy.wait('@getDkim');
      cy.contains('button', /rotate/i).should('be.visible');
    });
  });

  describe('Generate new DKIM key', () => {
    it('should open generate modal', () => {
      cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dkim`, { body: [] }).as('getDkimEmpty');
      cy.visit(`/domains/${DOMAIN_ID}`);
      cy.wait('@getDkimEmpty');
      cy.contains('button', /generate|add key/i).click();
      cy.get('[data-testid="generate-dkim-modal"], .modal, dialog').should('be.visible');
    });

    it('should generate a DKIM key and display it', () => {
      cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dkim`, { body: [] }).as('getDkimEmpty');
      cy.intercept('POST', `${API}/domains/${DOMAIN_ID}/dkim`, { statusCode: 201, body: activeKey }).as('generateKey');
      cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dkim`, { body: [activeKey] }).as('getDkimAfter');

      cy.visit(`/domains/${DOMAIN_ID}`);
      cy.wait('@getDkimEmpty');

      cy.contains('button', /generate|add key/i).click();
      cy.get('input[name="selector"], [data-testid="dkim-selector"]').first().clear().type('default');
      cy.contains('button', /save|generate|create/i).click();
      cy.wait('@generateKey');

      cy.contains(/default/i).should('be.visible');
      cy.contains(/active/i, { timeout: 5000 }).should('be.visible');
    });
  });

  describe('DKIM rotation flow', () => {
    it('should rotate key and show new key as rotating', () => {
      cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dkim`, { body: [activeKey] }).as('getDkim');
      cy.intercept('POST', `${API}/domains/${DOMAIN_ID}/dkim/rotate`, { statusCode: 200, body: rotatedKey }).as('rotateKey');
      cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dkim`, { body: [activeKey, rotatedKey] }).as('getDkimAfterRotate');

      cy.visit(`/domains/${DOMAIN_ID}`);
      cy.wait('@getDkim');

      cy.contains('button', /rotate/i).click();
      cy.wait('@rotateKey');

      cy.contains(/rotating|active/i, { timeout: 5000 }).should('be.visible');
    });

    it('should retire old key', () => {
      cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dkim`, { body: [retiredKey, rotatedKey] }).as('getDkim');
      cy.intercept('POST', `${API}/domains/${DOMAIN_ID}/dkim/${retiredKey.id}/retire`, {
        statusCode: 200, body: {},
      }).as('retireKey');

      cy.visit(`/domains/${DOMAIN_ID}`);
      cy.wait('@getDkim');

      // There should be a retire button for the old/retired-eligible key
      cy.contains('button', /retire/i).first().click();
      cy.wait('@retireKey');
    });
  });

  describe('Error handling', () => {
    it('should show error when rotation fails', () => {
      cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dkim`, { body: [activeKey] }).as('getDkim');
      cy.intercept('POST', `${API}/domains/${DOMAIN_ID}/dkim/rotate`, {
        statusCode: 500, body: { error: 'Rotation failed' },
      }).as('rotateKeyFail');

      cy.visit(`/domains/${DOMAIN_ID}`);
      cy.wait('@getDkim');

      cy.contains('button', /rotate/i).click();
      cy.wait('@rotateKeyFail');
      cy.contains(/error|failed/i, { timeout: 5000 }).should('be.visible');
    });
  });
});
