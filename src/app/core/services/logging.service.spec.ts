import { TestBed } from '@angular/core/testing';
import { LoggingService } from './logging.service';

describe('LoggingService', () => {
  let service: LoggingService;
  let consoleErrorSpy: jasmine.Spy;
  let consoleWarnSpy: jasmine.Spy;
  let consoleInfoSpy: jasmine.Spy;
  let consoleDebugSpy: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(LoggingService);

    // Spy on console methods
    consoleErrorSpy = spyOn(console, 'error');
    consoleWarnSpy = spyOn(console, 'warn');
    consoleInfoSpy = spyOn(console, 'info');
    consoleDebugSpy = spyOn(console, 'debug');
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('error()', () => {
    it('should log error with message only', () => {
      service.error('Test error message');
      expect(consoleErrorSpy).toHaveBeenCalledWith('[ERROR] Test error message');
    });

    it('should log error with message and error object', () => {
      const error = new Error('Test error');
      service.error('Something went wrong', error);
      expect(consoleErrorSpy).toHaveBeenCalledWith('[ERROR] Something went wrong', error);
    });

    it('should handle undefined error', () => {
      service.error('Test error', undefined);
      expect(consoleErrorSpy).toHaveBeenCalledWith('[ERROR] Test error');
    });
  });

  describe('warn()', () => {
    it('should log warning with message only', () => {
      service.warn('Test warning');
      expect(consoleWarnSpy).toHaveBeenCalledWith('[WARN] Test warning');
    });

    it('should log warning with message and data', () => {
      const data = { foo: 'bar' };
      service.warn('Warning occurred', data);
      expect(consoleWarnSpy).toHaveBeenCalledWith('[WARN] Warning occurred', data);
    });
  });

  describe('info()', () => {
    it('should log info with message only', () => {
      service.info('Test info');
      expect(consoleInfoSpy).toHaveBeenCalledWith('[INFO] Test info');
    });

    it('should log info with message and data', () => {
      const data = { status: 'success' };
      service.info('Operation completed', data);
      expect(consoleInfoSpy).toHaveBeenCalledWith('[INFO] Operation completed', data);
    });
  });

  describe('debug()', () => {
    it('should log debug with message only', () => {
      service.debug('Test debug');
      expect(consoleDebugSpy).toHaveBeenCalledWith('[DEBUG] Test debug');
    });

    it('should log debug with message and data', () => {
      const data = { value: 42 };
      service.debug('Debug value', data);
      expect(consoleDebugSpy).toHaveBeenCalledWith('[DEBUG] Debug value', data);
    });
  });

  describe('Production mode', () => {
    beforeEach(() => {
      // Mock production environment
      (service as any).isProduction = true;
    });

    it('should still call sendToExternalService in production', () => {
      const sendSpy = spyOn<any>(service, 'sendToExternalService');
      service.error('Production error');
      expect(sendSpy).toHaveBeenCalledWith('error', 'Production error', undefined);
    });

    it('should not log info in production', () => {
      service.info('Production info');
      // Should not call console.info directly in production
      // (sendToExternalService is private, so we just verify console wasn't called)
      expect(consoleInfoSpy).not.toHaveBeenCalled();
    });

    it('should not log debug in production', () => {
      service.debug('Production debug');
      expect(consoleDebugSpy).not.toHaveBeenCalled();
    });
  });
});
