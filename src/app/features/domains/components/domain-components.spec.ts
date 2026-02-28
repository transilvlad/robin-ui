import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { DomainListComponent } from './domain-list/domain-list.component';
import { DomainDetailComponent } from './domain-detail/domain-detail.component';
import { DkimManagementComponent } from './dkim-management/dkim-management.component';
import { DomainHealthComponent } from './domain-health/domain-health.component';
import { MtaStsStatusComponent } from './mta-sts-status/mta-sts-status.component';
import { DnsProvidersComponent } from './dns-providers/dns-providers.component';
import { DnsTemplatesComponent } from './dns-templates/dns-templates.component';
import { DnsRecordsComponent } from './dns-records/dns-records.component';

import { DomainService } from '../services/domain.service';
import { DkimService } from '../services/dkim.service';
import { DomainHealthService } from '../services/domain-health.service';
import { MtaStsService } from '../services/mta-sts.service';
import { DnsProviderService } from '../services/dns-provider.service';

import {
  Domain, DomainStatus, DkimKey, DkimAlgorithm, DkimKeyStatus,
  DomainHealth, DomainHealthCheckType, DomainHealthStatus,
  MtaStsWorker, MtaStsWorkerStatus, MtaStsPolicyMode,
  DnsProvider, DnsProviderType,
} from '../models/domain.models';
import { Ok, Err } from '@core/models/auth.model';

// ─────────────────────────────────────────────────────────────────────────────
// Shared mock data
// ─────────────────────────────────────────────────────────────────────────────

const mockDomain: Domain = {
  id: 1,
  domain: 'example.com',
  status: DomainStatus.ACTIVE,
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

const mockProvider: DnsProvider = {
  id: 1,
  name: 'cf-test',
  type: DnsProviderType.CLOUDFLARE,
  createdAt: '2024-01-01T00:00:00Z',
};

// ─────────────────────────────────────────────────────────────────────────────
// DomainListComponent
// ─────────────────────────────────────────────────────────────────────────────

describe('DomainListComponent', () => {
  let fixture: ComponentFixture<DomainListComponent>;
  let component: DomainListComponent;
  let domainService: jasmine.SpyObj<DomainService>;

  beforeEach(async () => {
    domainService = jasmine.createSpyObj('DomainService', ['getDomains', 'createDomain', 'deleteDomain']);
    domainService.getDomains.and.returnValue(of(Ok({ content: [mockDomain], totalElements: 1, totalPages: 1, number: 0, size: 20 })));
    domainService.createDomain.and.returnValue(of(Ok(mockDomain)));
    domainService.deleteDomain.and.returnValue(of(Ok(undefined as void)));

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, FormsModule],
      declarations: [DomainListComponent],
      providers: [{ provide: DomainService, useValue: domainService }],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(DomainListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load domains on init', fakeAsync(() => {
    tick();
    expect(domainService.getDomains).toHaveBeenCalledTimes(1);
    expect(component.domains).toHaveSize(1);
    expect(component.domains[0].domain).toBe('example.com');
  }));

  it('should open and close add modal', () => {
    component.openAddModal();
    expect(component.showAddModal).toBeTrue();
    component.closeAddModal();
    expect(component.showAddModal).toBeFalse();
    expect(component.newDomainName).toBe('');
  });

  it('should not call createDomain when name is empty', () => {
    component.newDomainName = '   ';
    component.addDomain();
    expect(domainService.createDomain).not.toHaveBeenCalled();
  });

  it('getStatusClass returns correct class for ACTIVE', () => {
    expect(component.getStatusClass('ACTIVE')).toContain('green');
  });

  it('getStatusClass returns correct class for ERROR', () => {
    expect(component.getStatusClass('ERROR')).toContain('red');
  });

  it('getStatusClass returns yellow for PENDING/unknown', () => {
    expect(component.getStatusClass('PENDING')).toContain('yellow');
    expect(component.getStatusClass(undefined)).toContain('yellow');
  });

  it('should set error when getDomains fails', fakeAsync(() => {
    domainService.getDomains.and.returnValue(of(Err(new Error('Network error'))));
    component.loadDomains();
    tick();
    expect(component.error).toBe('Failed to load domains');
  }));
});

// ─────────────────────────────────────────────────────────────────────────────
// DkimManagementComponent
// ─────────────────────────────────────────────────────────────────────────────

describe('DkimManagementComponent', () => {
  let fixture: ComponentFixture<DkimManagementComponent>;
  let component: DkimManagementComponent;
  let dkimService: jasmine.SpyObj<DkimService>;

  beforeEach(async () => {
    dkimService = jasmine.createSpyObj('DkimService', ['getKeys', 'generateKey', 'rotateKey', 'retireKey']);
    dkimService.getKeys.and.returnValue(of(Ok([mockDkimKey])));
    dkimService.generateKey.and.returnValue(of(Ok(mockDkimKey)));
    dkimService.rotateKey.and.returnValue(of(Ok({ ...mockDkimKey, id: 2 })));
    dkimService.retireKey.and.returnValue(of(Ok(undefined as void)));

    const domainServiceStub = jasmine.createSpyObj('DomainService', ['createDnsRecord']);

    await TestBed.configureTestingModule({
      declarations: [DkimManagementComponent],
      providers: [
        { provide: DkimService, useValue: dkimService },
        { provide: DomainService, useValue: domainServiceStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(DkimManagementComponent);
    component = fixture.componentInstance;
    component.domainId = 1;
    component.domain = 'example.com';
    fixture.detectChanges();
  });

  it('should create', () => expect(component).toBeTruthy());

  it('should load DKIM keys on init', fakeAsync(() => {
    tick();
    expect(dkimService.getKeys).toHaveBeenCalledWith(1);
    expect(component.keys).toHaveSize(1);
    expect(component.keys[0].selector).toBe('default');
  }));

  it('should not load keys when domainId is absent', () => {
    component.domainId = undefined as any;
    dkimService.getKeys.calls.reset();
    component.loadKeys();
    expect(dkimService.getKeys).not.toHaveBeenCalled();
  });

  it('should set error when getKeys fails', fakeAsync(() => {
    dkimService.getKeys.and.returnValue(of(Err(new Error('fail'))));
    component.loadKeys();
    tick();
    expect(component.error).toBeTruthy();
  }));
});

// ─────────────────────────────────────────────────────────────────────────────
// DomainHealthComponent
// ─────────────────────────────────────────────────────────────────────────────

describe('DomainHealthComponent', () => {
  let fixture: ComponentFixture<DomainHealthComponent>;
  let component: DomainHealthComponent;
  let healthService: jasmine.SpyObj<DomainHealthService>;

  beforeEach(async () => {
    healthService = jasmine.createSpyObj('DomainHealthService', ['getHealth', 'triggerVerification']);
    healthService.getHealth.and.returnValue(of(Ok([mockHealth])));
    healthService.triggerVerification.and.returnValue(of(Ok([mockHealth])));

    await TestBed.configureTestingModule({
      declarations: [DomainHealthComponent],
      providers: [{ provide: DomainHealthService, useValue: healthService }],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(DomainHealthComponent);
    component = fixture.componentInstance;
    component.domainId = 1;
    fixture.detectChanges();
  });

  it('should create', () => expect(component).toBeTruthy());

  it('should load health checks on init', fakeAsync(() => {
    tick();
    expect(healthService.getHealth).toHaveBeenCalledWith(1);
    expect(component.healthChecks).toHaveSize(1);
  }));

  it('runVerification calls triggerVerification', fakeAsync(() => {
    component.runVerification();
    tick();
    expect(healthService.triggerVerification).toHaveBeenCalledWith(1);
    expect(component.verifying).toBeFalse();
  }));
});

// ─────────────────────────────────────────────────────────────────────────────
// MtaStsStatusComponent
// ─────────────────────────────────────────────────────────────────────────────

describe('MtaStsStatusComponent', () => {
  let fixture: ComponentFixture<MtaStsStatusComponent>;
  let component: MtaStsStatusComponent;
  let mtaStsService: jasmine.SpyObj<MtaStsService>;

  beforeEach(async () => {
    mtaStsService = jasmine.createSpyObj('MtaStsService', ['getWorker', 'deploy', 'updatePolicy']);
    mtaStsService.getWorker.and.returnValue(of(Ok(mockWorker)));
    mtaStsService.deploy.and.returnValue(of(Ok(mockWorker)));
    mtaStsService.updatePolicy.and.returnValue(of(Ok(mockWorker)));

    await TestBed.configureTestingModule({
      declarations: [MtaStsStatusComponent],
      providers: [{ provide: MtaStsService, useValue: mtaStsService }],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(MtaStsStatusComponent);
    component = fixture.componentInstance;
    component.domainId = 1;
    fixture.detectChanges();
  });

  it('should create', () => expect(component).toBeTruthy());

  it('should load worker on init', fakeAsync(() => {
    tick();
    expect(mtaStsService.getWorker).toHaveBeenCalledWith(1);
    expect(component.worker).toEqual(mockWorker);
    expect(component.selectedPolicyMode).toBe('testing');
  }));

  it('should handle worker not found gracefully', fakeAsync(() => {
    mtaStsService.getWorker.and.returnValue(of(Err(new Error('Not found'))));
    component.loadWorker();
    tick();
    expect(component.worker).toBeNull();
    expect(component.error).toBeNull();
  }));
});

// ─────────────────────────────────────────────────────────────────────────────
// DnsProvidersComponent
// ─────────────────────────────────────────────────────────────────────────────

describe('DnsProvidersComponent', () => {
  let fixture: ComponentFixture<DnsProvidersComponent>;
  let component: DnsProvidersComponent;
  let providerService: jasmine.SpyObj<DnsProviderService>;

  beforeEach(async () => {
    providerService = jasmine.createSpyObj('DnsProviderService', [
      'getProviders', 'createProvider', 'deleteProvider', 'testConnection',
    ]);
    providerService.getProviders.and.returnValue(of(Ok([mockProvider])));
    providerService.testConnection.and.returnValue(of(Ok({ success: true, message: 'OK' })));
    providerService.deleteProvider.and.returnValue(of(Ok(undefined as void)));

    await TestBed.configureTestingModule({
      declarations: [DnsProvidersComponent],
      providers: [{ provide: DnsProviderService, useValue: providerService }],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(DnsProvidersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => expect(component).toBeTruthy());

  it('should load providers on init', fakeAsync(() => {
    tick();
    expect(providerService.getProviders).toHaveBeenCalled();
  }));
});
