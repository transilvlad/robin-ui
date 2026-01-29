import { TestBed } from '@angular/core/testing';
import { TokenStorageService } from './token-storage.service';
import { User, UserRole, Permission, AccessToken, UserId } from '../models/auth.model';

describe('TokenStorageService', () => {
  let service: TokenStorageService;
  let sessionStorageMock: { [key: string]: string };

  beforeEach(() => {
    // Mock sessionStorage
    sessionStorageMock = {};
    spyOn(sessionStorage, 'getItem').and.callFake((key: string) => {
      return sessionStorageMock[key] || null;
    });
    spyOn(sessionStorage, 'setItem').and.callFake((key: string, value: string) => {
      sessionStorageMock[key] = value;
    });
    spyOn(sessionStorage, 'removeItem').and.callFake((key: string) => {
      delete sessionStorageMock[key];
    });
    spyOn(console, 'error'); // Suppress error logs during tests

    TestBed.configureTestingModule({
      providers: [TokenStorageService],
    });
    service = TestBed.inject(TokenStorageService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Access Token Management', () => {
    it('should store access token', () => {
      const token = 'test_access_token' as AccessToken;
      service.setAccessToken(token);
      expect(sessionStorage.setItem).toHaveBeenCalledWith('robin_access_token', token);
    });

    it('should retrieve access token', () => {
      const token = 'test_access_token' as AccessToken;
      sessionStorageMock['robin_access_token'] = token;
      const retrieved = service.getAccessToken();
      expect(retrieved).toBe(token);
    });

    it('should return null when no access token exists', () => {
      const retrieved = service.getAccessToken();
      expect(retrieved).toBeNull();
    });
  });

  describe('User Management', () => {
    const validUser: User = {
      id: '123e4567-e89b-12d3-a456-426614174000' as UserId,
      username: 'testuser',
      email: 'test@example.com',
      firstName: 'Test',
      lastName: 'User',
      roles: [UserRole.USER],
      permissions: [Permission.VIEW_DASHBOARD, Permission.VIEW_QUEUE],
      createdAt: new Date('2024-01-01'),
      lastLoginAt: new Date('2024-01-02'),
    };

    it('should store user object', () => {
      service.setUser(validUser);
      expect(sessionStorage.setItem).toHaveBeenCalledWith(
        'robin_user',
        JSON.stringify(validUser)
      );
    });

    it('should retrieve and validate user object', () => {
      sessionStorageMock['robin_user'] = JSON.stringify(validUser);
      const retrieved = service.getUser();
      expect(retrieved).toBeTruthy();
      expect(retrieved?.username).toBe('testuser');
      expect(retrieved?.email).toBe('test@example.com');
    });

    it('should return null when no user exists', () => {
      const retrieved = service.getUser();
      expect(retrieved).toBeNull();
    });

    it('should return null and clear storage on corrupted user data', () => {
      sessionStorageMock['robin_user'] = 'invalid json{';
      const retrieved = service.getUser();
      expect(retrieved).toBeNull();
      expect(console.error).toHaveBeenCalledWith(
        'Corrupted user data in storage',
        jasmine.any(Error)
      );
      expect(sessionStorage.removeItem).toHaveBeenCalledWith('robin_access_token');
      expect(sessionStorage.removeItem).toHaveBeenCalledWith('robin_user');
    });

    it('should return null and clear storage on invalid user schema', () => {
      const invalidUser = {
        id: 'not-a-uuid', // Invalid UUID
        username: 'test',
        email: 'invalid-email', // Invalid email
        roles: [UserRole.USER],
        permissions: [Permission.VIEW_DASHBOARD],
        createdAt: new Date(),
      };
      sessionStorageMock['robin_user'] = JSON.stringify(invalidUser);
      const retrieved = service.getUser();
      expect(retrieved).toBeNull();
      expect(console.error).toHaveBeenCalled();
    });

    it('should handle user without optional fields', () => {
      const minimalUser = {
        id: '123e4567-e89b-12d3-a456-426614174000',
        username: 'testuser',
        email: 'test@example.com',
        roles: [UserRole.USER],
        permissions: [Permission.VIEW_DASHBOARD],
        createdAt: '2024-01-01T00:00:00Z',
      };
      sessionStorageMock['robin_user'] = JSON.stringify(minimalUser);
      const retrieved = service.getUser();
      expect(retrieved).toBeTruthy();
      expect(retrieved?.firstName).toBeUndefined();
      expect(retrieved?.lastName).toBeUndefined();
      expect(retrieved?.lastLoginAt).toBeUndefined();
    });

    it('should coerce date strings to Date objects', () => {
      const userWithDateStrings = {
        ...validUser,
        createdAt: '2024-01-01T00:00:00Z',
        lastLoginAt: '2024-01-02T00:00:00Z',
      };
      sessionStorageMock['robin_user'] = JSON.stringify(userWithDateStrings);
      const retrieved = service.getUser();
      expect(retrieved).toBeTruthy();
      expect(retrieved?.createdAt instanceof Date).toBe(true);
      expect(retrieved?.lastLoginAt instanceof Date).toBe(true);
    });
  });

  describe('Clear Storage', () => {
    it('should clear all auth data', () => {
      sessionStorageMock['robin_access_token'] = 'token';
      sessionStorageMock['robin_user'] = JSON.stringify({
        id: '123e4567-e89b-12d3-a456-426614174000',
        username: 'test',
        email: 'test@example.com',
        roles: [UserRole.USER],
        permissions: [Permission.VIEW_DASHBOARD],
        createdAt: new Date(),
      });

      service.clear();

      expect(sessionStorage.removeItem).toHaveBeenCalledWith('robin_access_token');
      expect(sessionStorage.removeItem).toHaveBeenCalledWith('robin_user');
    });

    it('should be safe to call clear multiple times', () => {
      service.clear();
      service.clear();
      expect(sessionStorage.removeItem).toHaveBeenCalledTimes(4); // 2 calls × 2 items
    });
  });

  describe('Integration Tests', () => {
    it('should handle complete store/retrieve/clear cycle', () => {
      const token = 'test_token' as AccessToken;
      const user: User = {
        id: '123e4567-e89b-12d3-a456-426614174000' as UserId,
        username: 'testuser',
        email: 'test@example.com',
        roles: [UserRole.ADMIN],
        permissions: [Permission.VIEW_DASHBOARD, Permission.MANAGE_USERS],
        createdAt: new Date(),
      };

      // Store
      service.setAccessToken(token);
      service.setUser(user);

      // Retrieve
      expect(service.getAccessToken()).toBe(token);
      const retrievedUser = service.getUser();
      expect(retrievedUser?.username).toBe('testuser');

      // Clear
      service.clear();
      expect(service.getAccessToken()).toBeNull();
      expect(service.getUser()).toBeNull();
    });
  });
});
