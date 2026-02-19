describe('DMARC Feature', () => {
  beforeEach(() => {
    // Mock login and auth
    cy.loginAsAdmin();

    // Mock API responses
    cy.intercept('GET', '**/dmarc/reports*', {
      body: {
        total: 2,
        page: 0,
        size: 20,
        reports: [
          {
            id: 'report-1',
            metadata: {
              orgName: 'google.com',
              email: 'dmarc@google.com',
              reportId: '12345',
              dateBegin: 1672531200,
              dateEnd: 1672617600
            },
            policy: {
              domain: 'example.com',
              p: 'reject',
              pct: 100
            },
            totalCount: 150,
            ingestedAt: '2023-01-01T12:00:00Z'
          },
          {
            id: 'report-2',
            metadata: {
              orgName: 'yahoo.com',
              email: 'dmarc@yahoo.com',
              reportId: '67890',
              dateBegin: 1672617600,
              dateEnd: 1672704000
            },
            policy: {
              domain: 'example.com',
              p: 'none',
              pct: 100
            },
            totalCount: 50,
            ingestedAt: '2023-01-02T12:00:00Z'
          }
        ]
      }
    }).as('getReports');

    cy.intercept('GET', '**/dmarc/reports/report-1', {
      body: {
        id: 'report-1',
        metadata: {
          orgName: 'google.com',
          email: 'dmarc@google.com',
          reportId: '12345',
          dateBegin: 1672531200,
          dateEnd: 1672617600
        },
        policy: {
          domain: 'example.com',
          p: 'reject',
          pct: 100
        },
        records: [
          {
            sourceIp: '1.2.3.4',
            ptrHostname: 'mail.google.com',
            count: 100,
            disposition: 'none',
            dkimResult: 'pass',
            spfResult: 'pass',
            ipClass: 'AUTHORIZED'
          },
          {
            sourceIp: '5.6.7.8',
            ptrHostname: 'unknown.com',
            count: 50,
            disposition: 'reject',
            dkimResult: 'fail',
            spfResult: 'fail',
            ipClass: 'UNKNOWN'
          }
        ]
      }
    }).as('getReportDetail');

    cy.intercept('GET', '**/dmarc/validate*', {
      body: {
        domain: 'example.com',
        valid: true,
        record: {
          v: 'DMARC1',
          p: 'reject',
          rua: 'mailto:dmarc@example.com'
        },
        errors: []
      }
    }).as('validateDomain');

    cy.intercept('POST', '**/dmarc/ingest/xml', {
      statusCode: 201,
      body: {
        id: 'new-report-id'
      }
    }).as('ingestXml');
  });

  it('should navigate to DMARC reports and display list', () => {
    cy.visit('/dmarc/reports');
    cy.wait('@getReports');

    cy.contains('DMARC Reports').should('be.visible');
    cy.get('table').should('exist');
    cy.contains('google.com').should('be.visible');
    cy.contains('yahoo.com').should('be.visible');
  });

  it('should view report details', () => {
    cy.visit('/dmarc/reports');
    cy.wait('@getReports');

    // Click the first view button
    cy.contains('View').click(); 
    // Or be more specific: cy.get('tbody tr').first().find('button').click();

    cy.url().should('include', '/dmarc/reports/report-1');
    cy.wait('@getReportDetail');

    cy.contains('Report: 12345').should('be.visible');
    cy.contains('1.2.3.4').should('be.visible');
    cy.contains('AUTHORIZED').should('be.visible');
  });

  it('should validate a domain', () => {
    cy.visit('/dmarc/validate');
    
    cy.get('input[name="domain"]').type('example.com');
    cy.get('button[type="submit"]').click();

    cy.wait('@validateDomain');
    cy.contains('Record is valid for example.com').should('be.visible');
    cy.contains('rua').should('be.visible');
  });

  it('should ingest XML', () => {
    cy.visit('/dmarc/ingest');

    cy.get('textarea[name="xmlPayload"]').type('<feedback>...</feedback>');
    cy.get('button').contains('Process XML').click();

    cy.wait('@ingestXml');
    cy.url().should('include', '/dmarc/reports/new-report-id');
  });
});
