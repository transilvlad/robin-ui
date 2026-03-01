import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { DomainService } from './domain.service';
import { DnsProviderService } from './dns-provider.service';
import { DkimService } from './dkim.service';
import { DomainHealthService } from './domain-health.service';
import { MtaStsService } from './mta-sts.service';
import { DnsTemplateService } from './dns-template.service';
import { environment } from '@environments/environment';
import {
  Domain, DomainStatus, DnsProvider, DnsProviderType,
  DkimKey, DkimAlgorithm, DkimKeyStatus,
  DomainHealth, DomainHealthCheckType, DomainHealthStatus,
  MtaStsWorker, MtaStsWorkerStatus, MtaStsPolicyMode,
  DnsTemplate,
} from '../models/domain.models';
import { DomainStore } from '../state/domain.store';

// ─────────────────────────────────────────────────────────────────────────────
// Test helpers
// ─────────────────────────────────────────────────────────────────────────────

const BASE_URL = environment.apiUrl;

const mockDomain: Domain = {
  id: 1,
  domain: 'example.com',
  status: DomainStatus.ACTIVE,
  createdAt: '2024-01-01T00:00:00Z',
};

const mockPageResponse = {
  content: [mockDomain],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

const mockProvider: DnsProvider = {
  id: 1,
  name: 'cf-test',
  type: DnsProviderType.CLOUDFLARE,
  createdAt: '2024-01-01T00:00:00Z',
};

const mockDkimKey: DkimKey = {
  id: 1,
  domainId: 1,
  selector: 'default',
  algorithm: DkimAlgorithm.RSA_2048,
  status: DkimKeyStatus.ACTIVE,
};

const mockHealth: DomainHealth = {
  id: 1,
  domainId: 1,
  checkType: DomainHealthCheckType.SPF,
  status: DomainHealthStatus.OK,
  lastChecked: '2024-01-01T00:00:00Z',
};

const mockWorker: MtaStsWorker = {
  id: 1,
  domainId: 1,
  workerName: 'mta-sts-example',
  policyMode: MtaStsPolicyMode.TESTING,
  status: MtaStsWorkerStatus.DEPLOYED,
};

const mockTemplate: DnsTemplate = {
  id: 1,
  name: 'mx-template',
  records: '[]',
  createdAt: '2024-01-01T00:00:00Z',
};

// ─────────────────────────────────────────────────────────────────────────────
// DomainService
// ─────────────────────────────────────────────────────────────────────────────

describe('DomainService', () => {
  let service: DomainService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(DomainService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getDomains returns Ok with page', fakeAsync(() => {
    let result: any;
    service.getDomains().subscribe(r => (result = r));
    http.expectOne(req => req.url.includes('/domains')).flush(mockPageResponse);
    tick();
    expect(result.ok).toBeTrue();
    expect(result.value.content).toHaveSize(1);
  }));

  it('getDomain returns Ok with single domain', fakeAsync(() => {
    let result: any;
    service.getDomain(1).subscribe(r => (result = r));
    http.expectOne(`${BASE_URL}/domains/1`).flush(mockDomain);
    tick();
    expect(result.ok).toBeTrue();
    expect(result.value.domain).toBe('example.com');
  }));

  it('createDomain returns Ok with created domain', fakeAsync(() => {
    let result: any;
    service.createDomain('example.com').subscribe(r => (result = r));
    const req = http.expectOne(`${BASE_URL}/domains`);
    expect(req.request.method).toBe('POST');
    req.flush(mockDomain);
    tick();
    expect(result.ok).toBeTrue();
  }));

  it('deleteDomain returns Ok on success', fakeAsync(() => {
    let result: any;
    service.deleteDomain(1).subscribe(r => (result = r));
    const req = http.expectOne(`${BASE_URL}/domains/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    tick();
    expect(result.ok).toBeTrue();
  }));

  it('getDomains returns Err on HTTP error', fakeAsync(() => {
    let result: any;
    service.getDomains().subscribe(r => (result = r));
    http.expectOne(req => req.url.includes('/domains')).flush(null, { status: 500, statusText: 'Error' });
    tick();
    expect(result.ok).toBeFalse();
  }));
});

// ─────────────────────────────────────────────────────────────────────────────
// DnsProviderService
// ─────────────────────────────────────────────────────────────────────────────

describe('DnsProviderService', () => {
  let service: DnsProviderService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(DnsProviderService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getProviders returns Ok with list', fakeAsync(() => {
    let result: any;
    service.getProviders().subscribe(r => (result = r));
    http.expectOne(`${BASE_URL}/dns-providers`).flush([mockProvider]);
    tick();
    expect(result.ok).toBeTrue();
    expect(result.value).toHaveSize(1);
  }));

  it('createProvider sends POST and returns Ok', fakeAsync(() => {
    let result: any;
    service.createProvider({ name: 'cf', type: 'CLOUDFLARE', credentials: '{}' }).subscribe(r => (result = r));
    const req = http.expectOne(`${BASE_URL}/dns-providers`);
    expect(req.request.method).toBe('POST');
    req.flush(mockProvider);
    tick();
    expect(result.ok).toBeTrue();
  }));

  it('testConnection sends POST and returns Ok', fakeAsync(() => {
    let result: any;
    service.testConnection(1).subscribe(r => (result = r));
    const req = http.expectOne(`${BASE_URL}/dns-providers/1/test`);
    expect(req.request.method).toBe('POST');
    req.flush({ success: true, message: 'Connected' });
    tick();
    expect(result.ok).toBeTrue();
  }));
});

// ─────────────────────────────────────────────────────────────────────────────
// DkimService
// ─────────────────────────────────────────────────────────────────────────────

describe('DkimService', () => {
  let service: DkimService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(DkimService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getKeys returns Ok', fakeAsync(() => {
    let result: any;
    service.getKeys(1).subscribe(r => (result = r));
    http.expectOne(`${BASE_URL}/domains/1/dkim/keys`).flush([mockDkimKey]);
    tick();
    expect(result.ok).toBeTrue();
    expect(result.value).toHaveSize(1);
  }));

  it('generateKey sends POST', fakeAsync(() => {
    let result: any;
    service.generateKey(1, { algorithm: 'RSA_2048' }).subscribe(r => (result = r));
    const req = http.expectOne(`${BASE_URL}/domains/1/dkim/generate`);
    expect(req.request.method).toBe('POST');
    req.flush(mockDkimKey);
    tick();
    expect(result.ok).toBeTrue();
  }));

  it('rotateKey sends POST to /rotate', fakeAsync(() => {
    let result: any;
    service.rotateKey(1).subscribe(r => (result = r));
    const req = http.expectOne(`${BASE_URL}/domains/1/dkim/rotate`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...mockDkimKey, id: 2 });
    tick();
    expect(result.ok).toBeTrue();
  }));

  it('retireKey sends DELETE to /dkim/keys/{id}', fakeAsync(() => {
    let result: any;
    service.retireKey(1, 1).subscribe(r => (result = r));
    const req = http.expectOne(`${BASE_URL}/domains/1/dkim/keys/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    tick();
    expect(result.ok).toBeTrue();
  }));
});

// ─────────────────────────────────────────────────────────────────────────────
// DomainHealthService
// ─────────────────────────────────────────────────────────────────────────────

describe('DomainHealthService', () => {
  let service: DomainHealthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(DomainHealthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getHealth returns Ok with checks', fakeAsync(() => {
    let result: any;
    service.getHealth(1).subscribe(r => (result = r));
    http.expectOne(`${BASE_URL}/domains/1/health`).flush([mockHealth]);
    tick();
    expect(result.ok).toBeTrue();
  }));

  it('triggerVerification sends POST', fakeAsync(() => {
    let result: any;
    service.triggerVerification(1).subscribe(r => (result = r));
    const req = http.expectOne(`${BASE_URL}/domains/1/health/verify`);
    expect(req.request.method).toBe('POST');
    req.flush([mockHealth]);
    tick();
    expect(result.ok).toBeTrue();
  }));
});

// ─────────────────────────────────────────────────────────────────────────────
// MtaStsService
// ─────────────────────────────────────────────────────────────────────────────

describe('MtaStsService', () => {
  let service: MtaStsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(MtaStsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getWorker returns Ok with worker', fakeAsync(() => {
    let result: any;
    service.getWorker(1).subscribe(r => (result = r));
    http.expectOne(`${BASE_URL}/domains/1/mta-sts`).flush(mockWorker);
    tick();
    expect(result.ok).toBeTrue();
    expect(result.value.policyMode).toBe(MtaStsPolicyMode.TESTING);
  }));

  it('deploy sends POST to /mta-sts/deploy', fakeAsync(() => {
    let result: any;
    service.deploy(1, { policyMode: 'testing' }).subscribe(r => (result = r));
    const req = http.expectOne(`${BASE_URL}/domains/1/mta-sts/deploy`);
    expect(req.request.method).toBe('POST');
    req.flush(mockWorker);
    tick();
    expect(result.ok).toBeTrue();
  }));

  it('updatePolicy sends PUT', fakeAsync(() => {
    let result: any;
    service.updatePolicy(1, 'enforce').subscribe(r => (result = r));
    const req = http.expectOne(`${BASE_URL}/domains/1/mta-sts`);
    expect(req.request.method).toBe('PUT');
    req.flush({ ...mockWorker, policyMode: MtaStsPolicyMode.ENFORCE });
    tick();
    expect(result.ok).toBeTrue();
  }));
});
