/**
 * Cypress E2E: DKIM management – rotation wizard, DNS record copy,
 * verify DNS flow, and revoke confirmation.
 * DKIM-011, DKIM-012
 */

const API = '**/api/v1';
const DOMAIN_ID = 99;
const DOMAIN_NAME = 'rotate.example.com';
const OLD_KEY_ID = 110;
const NEW_KEY_ID = 220;

const domainResponse = {
  id: DOMAIN_ID,
  domain: DOMAIN_NAME,
  status: 'ACTIVE',
  createdAt: new Date().toISOString(),
};

const oldActiveKey = {
  id: OLD_KEY_ID,
  domain: DOMAIN_NAME,
  selector: 'default',
  algorithm: 'RSA_2048',
  status: 'ACTIVE',
  createdAt: new Date().toISOString(),
};

const newPendingKey = {
  id: NEW_KEY_ID,
  domain: DOMAIN_NAME,
  selector: 'rotate-20260227',
  algorithm: 'RSA_2048',
  status: 'PENDING_PUBLISH',
  createdAt: new Date().toISOString(),
};

const newActiveKey = {
  ...newPendingKey,
  status: 'ACTIVE',
};

const oldRotatingKey = {
  ...oldActiveKey,
  status: 'ROTATING_OUT',
};

const oldRetiredKey = {
  ...oldActiveKey,
  status: 'RETIRED',
};

const activeKeyWithDnsRecord = {
  ...oldActiveKey,
  dnsRecord: {
    keyId: OLD_KEY_ID,
    name: `default._domainkey.${DOMAIN_NAME}`,
    type: 'TXT',
    value: 'v=DKIM1; k=rsa; p=MIGfMA0G',
    chunks: null,
    status: 'PUBLISHED',
  },
};

describe('Domain Management – DKIM Rotation Wizard', () => {
  beforeEach(() => {
    cy.clearAuth();
    cy.loginAsAdmin();

    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}`, { body: domainResponse }).as('getDomain');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/health`, { body: [] }).as('getHealth');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/mta-sts`, { statusCode: 404, body: {} }).as('getMtaSts');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dns`, { body: [] }).as('getDnsRecords');
    cy.intercept('GET', `${API}/domains/${DOMAIN_NAME}/dkim/dns-records`, { body: [] }).as('getDkimDnsRecords');

    let keyListCall = 0;
    cy.intercept('GET', `${API}/domains/${DOMAIN_NAME}/dkim/keys`, req => {
      keyListCall += 1;
      if (keyListCall === 1) {
        req.reply({ body: [oldActiveKey] });
        return;
      }
      if (keyListCall === 2) {
        req.reply({ body: [oldRotatingKey, newActiveKey] });
        return;
      }
      req.reply({ body: [oldRetiredKey, newActiveKey] });
    }).as('getDkimKeys');
  });

  it('advances through all 5 wizard steps and calls each API endpoint', () => {
    cy.intercept('POST', `${API}/domains/${DOMAIN_NAME}/dkim/rotate`, {
      statusCode: 201,
      body: newPendingKey,
    }).as('rotateKey');

    cy.intercept('GET', `${API}/domains/${DOMAIN_NAME}/dkim/keys/${NEW_KEY_ID}/verify-dns`, {
      statusCode: 200,
      body: {
        recordName: `${newPendingKey.selector}._domainkey.${DOMAIN_NAME}`,
        published: true,
        matches: true,
        revoked: false,
        answers: ['v=DKIM1; k=rsa; p=abc123'],
      },
    }).as('verifyDns');

    cy.intercept('POST', `${API}/domains/${DOMAIN_NAME}/dkim/keys/${NEW_KEY_ID}/confirm-published`, {
      statusCode: 200,
      body: newPendingKey,
    }).as('confirmPublished');

    cy.intercept('POST', `${API}/domains/${DOMAIN_NAME}/dkim/keys/${NEW_KEY_ID}/activate`, {
      statusCode: 200,
      body: newActiveKey,
    }).as('activateKey');

    cy.intercept('POST', `${API}/domains/${DOMAIN_NAME}/dkim/keys/${OLD_KEY_ID}/retire`, {
      statusCode: 200,
      body: oldRetiredKey,
    }).as('retireOldKey');

    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getDomain');
    cy.contains('button', /^DKIM$/).click();
    cy.wait('@getDkimKeys');
    cy.wait('@getDkimDnsRecords');

    cy.contains('button', /rotation wizard/i).click();
    cy.get('[data-testid="rotation-stage-prepublish"]').should('be.visible');
    cy.get('[data-testid="rotation-start-btn"]').click();
    cy.wait('@rotateKey');

    cy.get('[data-testid="rotation-stage-publish"]').should('be.visible');
    cy.get('[data-testid="rotation-verify-btn"]').click();
    cy.wait('@verifyDns');
    cy.get('[data-testid="rotation-verify-result"]').should('contain.text', 'DNS matches');
    cy.get('[data-testid="rotation-confirm-btn"]').click();
    cy.wait('@confirmPublished');

    cy.get('[data-testid="rotation-stage-observe"]').should('be.visible');
    cy.get('[data-testid="rotation-observe-countdown"]').contains('0s', { timeout: 10000 });
    cy.get('[data-testid="rotation-observe-next-btn"]').click();

    cy.get('[data-testid="rotation-stage-activate"]').should('be.visible');
    cy.get('[data-testid="rotation-activate-btn"]').click();
    cy.wait('@activateKey');

    cy.get('[data-testid="rotation-stage-cleanup"]').should('be.visible');
    cy.get('[data-testid="rotation-cleanup-btn"]').click();
    cy.wait('@getDkimKeys');
    cy.wait('@retireOldKey');
    cy.wait('@getDkimKeys');

    cy.get('[data-testid="rotation-stage-done"]').should('be.visible');
    cy.get('[data-testid="rotation-close-btn"]').click();
    cy.get('[data-testid="rotation-stage-done"]').should('not.exist');
  });
});

// ---------------------------------------------------------------------------
// DKIM-012: DNS record copy
// ---------------------------------------------------------------------------

describe('Domain Management – DKIM DNS Record Copy', () => {
  beforeEach(() => {
    cy.clearAuth();
    cy.loginAsAdmin();

    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}`, { body: domainResponse }).as('getDomain');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/health`, { body: [] }).as('getHealth');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/mta-sts`, { statusCode: 404, body: {} }).as('getMtaSts');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dns`, { body: [] }).as('getDnsRecords');
    cy.intercept('GET', `${API}/domains/${DOMAIN_NAME}/dkim/keys`, {
      body: [activeKeyWithDnsRecord],
    }).as('getDkimKeys');
    cy.intercept('GET', `${API}/domains/${DOMAIN_NAME}/dkim/dns-records`, {
      body: [activeKeyWithDnsRecord.dnsRecord],
    }).as('getDkimDnsRecords');
  });

  it('copies the DNS record name to the clipboard', () => {
    cy.window().then(win => {
      cy.stub(win.navigator.clipboard, 'writeText').as('clipboardWrite').resolves();
    });

    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getDomain');
    cy.contains('button', /^DKIM$/).click();
    cy.wait('@getDkimKeys');
    cy.wait('@getDkimDnsRecords');

    cy.contains('button', 'DNS Record').first().click();

    cy.get('@clipboardWrite').should(
      'have.been.calledWith',
      `default._domainkey.${DOMAIN_NAME}`,
    );
  });

  it('copies the DNS record value to the clipboard', () => {
    cy.window().then(win => {
      cy.stub(win.navigator.clipboard, 'writeText').as('clipboardWrite').resolves();
    });

    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getDomain');
    cy.contains('button', /^DKIM$/).click();
    cy.wait('@getDkimKeys');
    cy.wait('@getDkimDnsRecords');

    cy.contains('button', 'DNS Record').first().click();

    cy.get('@clipboardWrite').should(
      'have.been.calledWith',
      'v=DKIM1; k=rsa; p=MIGfMA0G',
    );
  });
});

// ---------------------------------------------------------------------------
// DKIM-012: Verify DNS flow
// ---------------------------------------------------------------------------

describe('Domain Management – DKIM Verify DNS Flow', () => {
  const verifyResult = {
    recordName: `default._domainkey.${DOMAIN_NAME}`,
    published: true,
    matches: true,
    revoked: false,
    answers: ['v=DKIM1; k=rsa; p=MIGfMA0G'],
  };

  beforeEach(() => {
    cy.clearAuth();
    cy.loginAsAdmin();

    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}`, { body: domainResponse }).as('getDomain');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/health`, { body: [] }).as('getHealth');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/mta-sts`, { statusCode: 404, body: {} }).as('getMtaSts');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dns`, { body: [] }).as('getDnsRecords');
    cy.intercept('GET', `${API}/domains/${DOMAIN_NAME}/dkim/keys`, {
      body: [oldActiveKey],
    }).as('getDkimKeys');
    cy.intercept('GET', `${API}/domains/${DOMAIN_NAME}/dkim/dns-records`, { body: [] }).as('getDkimDnsRecords');
  });

  it('shows "DNS verified" inline after a successful Verify DNS call', () => {
    cy.intercept(
      'GET',
      `${API}/domains/${DOMAIN_NAME}/dkim/keys/${OLD_KEY_ID}/verify-dns`,
      { statusCode: 200, body: verifyResult },
    ).as('verifyDns');

    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getDomain');
    cy.contains('button', /^DKIM$/).click();
    cy.wait('@getDkimKeys');
    cy.wait('@getDkimDnsRecords');

    cy.contains('button', 'Verify DNS').first().click();
    cy.wait('@verifyDns');

    cy.contains('DNS verified').should('be.visible');
    cy.contains(verifyResult.recordName).should('be.visible');
  });

  it('shows "DNS mismatch" inline when the record does not match', () => {
    cy.intercept(
      'GET',
      `${API}/domains/${DOMAIN_NAME}/dkim/keys/${OLD_KEY_ID}/verify-dns`,
      { statusCode: 200, body: { ...verifyResult, matches: false } },
    ).as('verifyDnsMismatch');

    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getDomain');
    cy.contains('button', /^DKIM$/).click();
    cy.wait('@getDkimKeys');
    cy.wait('@getDkimDnsRecords');

    cy.contains('button', 'Verify DNS').first().click();
    cy.wait('@verifyDnsMismatch');

    cy.contains('DNS mismatch').should('be.visible');
  });

  it('shows verification result inside the DNS record drawer', () => {
    cy.intercept('GET', `${API}/domains/${DOMAIN_NAME}/dkim/keys`, {
      body: [activeKeyWithDnsRecord],
    }).as('getDkimKeysWithRecord');
    cy.intercept('GET', `${API}/domains/${DOMAIN_NAME}/dkim/dns-records`, {
      body: [activeKeyWithDnsRecord.dnsRecord],
    }).as('getDkimDnsRecordsWithRecord');
    cy.intercept(
      'GET',
      `${API}/domains/${DOMAIN_NAME}/dkim/keys/${OLD_KEY_ID}/verify-dns`,
      { statusCode: 200, body: verifyResult },
    ).as('verifyDnsDrawer');

    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getDomain');
    cy.contains('button', /^DKIM$/).click();
    cy.wait('@getDkimKeysWithRecord');
    cy.wait('@getDkimDnsRecordsWithRecord');

    cy.contains('button', 'DNS Record').first().click();
    cy.contains('button', 'Verify DNS').click();
    cy.wait('@verifyDnsDrawer');

    cy.contains('Published').should('be.visible');
    cy.contains(verifyResult.answers[0]).should('be.visible');
  });
});

// ---------------------------------------------------------------------------
// DKIM-012: Revoke confirmation
// ---------------------------------------------------------------------------

describe('Domain Management – DKIM Revoke Confirmation', () => {
  beforeEach(() => {
    cy.clearAuth();
    cy.loginAsAdmin();

    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}`, { body: domainResponse }).as('getDomain');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/health`, { body: [] }).as('getHealth');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/mta-sts`, { statusCode: 404, body: {} }).as('getMtaSts');
    cy.intercept('GET', `${API}/domains/${DOMAIN_ID}/dns`, { body: [] }).as('getDnsRecords');
    cy.intercept('GET', `${API}/domains/${DOMAIN_NAME}/dkim/keys`, {
      body: [oldActiveKey],
    }).as('getDkimKeys');
    cy.intercept('GET', `${API}/domains/${DOMAIN_NAME}/dkim/dns-records`, { body: [] }).as('getDkimDnsRecords');
  });

  it('calls the revoke API when the user confirms the dialog', () => {
    const revokedKey = { ...oldActiveKey, status: 'REVOKED' };
    cy.intercept('POST', `${API}/domains/${DOMAIN_NAME}/dkim/keys/${OLD_KEY_ID}/revoke`, {
      statusCode: 200,
      body: revokedKey,
    }).as('revokeKey');
    cy.intercept('GET', `${API}/domains/${DOMAIN_NAME}/dkim/keys`, {
      body: [revokedKey],
    }).as('getDkimKeysAfterRevoke');

    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getDomain');
    cy.contains('button', /^DKIM$/).click();
    cy.wait('@getDkimKeys');
    cy.wait('@getDkimDnsRecords');

    cy.on('window:confirm', () => true);
    cy.contains('button', 'Revoke').first().click();
    cy.wait('@revokeKey');

    cy.wait('@getDkimKeysAfterRevoke');
    cy.contains('REVOKED').should('be.visible');
  });

  it('does NOT call the revoke API when the user cancels the dialog', () => {
    cy.intercept('POST', `${API}/domains/${DOMAIN_NAME}/dkim/keys/${OLD_KEY_ID}/revoke`).as(
      'revokeKey',
    );

    cy.visit(`/domains/${DOMAIN_ID}`);
    cy.wait('@getDomain');
    cy.contains('button', /^DKIM$/).click();
    cy.wait('@getDkimKeys');
    cy.wait('@getDkimDnsRecords');

    cy.on('window:confirm', () => false);
    cy.contains('button', 'Revoke').first().click();

    // The intercept alias should never have been called.
    cy.get('@revokeKey.all').should('have.length', 0);
    cy.contains('ACTIVE').should('be.visible');
  });
});
