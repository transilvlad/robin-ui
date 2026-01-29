import {
  UserSchema,
  AuthTokensSchema,
  AuthResponseSchema,
  LoginRequestSchema,
  UserRole,
  Permission,
  AuthErrorCode,
  Ok,
  Err,
  User,
  AuthTokens,
  AuthResponse,
  LoginRequest,
  AccessToken,
  RefreshToken,
  UserId,
} from './auth.model';
import { z } from 'zod';

describe('Auth Models', () => {
  describe('UserSchema', () => {
    it('should validate a valid user object', () => {
      const validUser = {
        id: '123e4567-e89b-12d3-a456-426614174000',
        username: 'testuser',
        email: 'test@example.com',
        firstName: 'Test',
        lastName: 'User',
        roles: [UserRole.USER],
        permissions: [Permission.VIEW_DASHBOARD],
        createdAt: new Date('2024-01-01'),
        lastLoginAt: new Date('2024-01-02'),
      };

      const result = UserSchema.safeParse(validUser);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.username).toBe('testuser');
        expect(result.data.email).toBe('test@example.com');
      }
    });

    it('should reject invalid email format', () => {
      const invalidUser = {
        id: '123e4567-e89b-12d3-a456-426614174000',
        username: 'testuser',
        email: 'invalid-email',
        roles: [UserRole.USER],
        permissions: [Permission.VIEW_DASHBOARD],
        createdAt: new Date(),
      };

      const result = UserSchema.safeParse(invalidUser);
      expect(result.success).toBe(false);
    });

    it('should reject username shorter than 3 characters', () => {
      const invalidUser = {
        id: '123e4567-e89b-12d3-a456-426614174000',
        username: 'ab',
        email: 'test@example.com',
        roles: [UserRole.USER],
        permissions: [Permission.VIEW_DASHBOARD],
        createdAt: new Date(),
      };

      const result = UserSchema.safeParse(invalidUser);
      expect(result.success).toBe(false);
    });

    it('should reject username longer than 50 characters', () => {
      const invalidUser = {
        id: '123e4567-e89b-12d3-a456-426614174000',
        username: 'a'.repeat(51),
        email: 'test@example.com',
        roles: [UserRole.USER],
        permissions: [Permission.VIEW_DASHBOARD],
        createdAt: new Date(),
      };

      const result = UserSchema.safeParse(invalidUser);
      expect(result.success).toBe(false);
    });

    it('should reject invalid UUID format', () => {
      const invalidUser = {
        id: 'not-a-uuid',
        username: 'testuser',
        email: 'test@example.com',
        roles: [UserRole.USER],
        permissions: [Permission.VIEW_DASHBOARD],
        createdAt: new Date(),
      };

      const result = UserSchema.safeParse(invalidUser);
      expect(result.success).toBe(false);
    });

    it('should accept optional firstName and lastName', () => {
      const userWithoutNames = {
        id: '123e4567-e89b-12d3-a456-426614174000',
        username: 'testuser',
        email: 'test@example.com',
        roles: [UserRole.USER],
        permissions: [Permission.VIEW_DASHBOARD],
        createdAt: new Date(),
      };

      const result = UserSchema.safeParse(userWithoutNames);
      expect(result.success).toBe(true);
    });

    it('should coerce date strings to Date objects', () => {
      const userWithDateStrings = {
        id: '123e4567-e89b-12d3-a456-426614174000',
        username: 'testuser',
        email: 'test@example.com',
        roles: [UserRole.USER],
        permissions: [Permission.VIEW_DASHBOARD],
        createdAt: '2024-01-01T00:00:00Z',
        lastLoginAt: '2024-01-02T00:00:00Z',
      };

      const result = UserSchema.safeParse(userWithDateStrings);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.createdAt instanceof Date).toBe(true);
        expect(result.data.lastLoginAt instanceof Date).toBe(true);
      }
    });
  });

  describe('AuthTokensSchema', () => {
    it('should validate valid auth tokens', () => {
      const validTokens = {
        accessToken: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...',
        refreshToken: 'refresh_token_here',
        expiresIn: 3600,
        tokenType: 'Bearer' as const,
      };

      const result = AuthTokensSchema.safeParse(validTokens);
      expect(result.success).toBe(true);
    });

    it('should reject negative expiresIn', () => {
      const invalidTokens = {
        accessToken: 'token',
        refreshToken: 'refresh',
        expiresIn: -1,
        tokenType: 'Bearer' as const,
      };

      const result = AuthTokensSchema.safeParse(invalidTokens);
      expect(result.success).toBe(false);
    });

    it('should reject zero expiresIn', () => {
      const invalidTokens = {
        accessToken: 'token',
        refreshToken: 'refresh',
        expiresIn: 0,
        tokenType: 'Bearer' as const,
      };

      const result = AuthTokensSchema.safeParse(invalidTokens);
      expect(result.success).toBe(false);
    });

    it('should reject non-Bearer tokenType', () => {
      const invalidTokens = {
        accessToken: 'token',
        refreshToken: 'refresh',
        expiresIn: 3600,
        tokenType: 'Basic',
      };

      const result = AuthTokensSchema.safeParse(invalidTokens);
      expect(result.success).toBe(false);
    });
  });

  describe('LoginRequestSchema', () => {
    it('should validate valid login request', () => {
      const validRequest = {
        username: 'testuser',
        password: 'password123',
        rememberMe: true,
      };

      const result = LoginRequestSchema.safeParse(validRequest);
      expect(result.success).toBe(true);
    });

    it('should reject username shorter than 3 characters', () => {
      const invalidRequest = {
        username: 'ab',
        password: 'password123',
      };

      const result = LoginRequestSchema.safeParse(invalidRequest);
      expect(result.success).toBe(false);
    });

    it('should reject password shorter than 8 characters', () => {
      const invalidRequest = {
        username: 'testuser',
        password: 'short',
      };

      const result = LoginRequestSchema.safeParse(invalidRequest);
      expect(result.success).toBe(false);
    });

    it('should accept request without rememberMe', () => {
      const validRequest = {
        username: 'testuser',
        password: 'password123',
      };

      const result = LoginRequestSchema.safeParse(validRequest);
      expect(result.success).toBe(true);
    });
  });

  describe('AuthResponseSchema', () => {
    it('should validate complete auth response', () => {
      const validResponse = {
        user: {
          id: '123e4567-e89b-12d3-a456-426614174000',
          username: 'testuser',
          email: 'test@example.com',
          roles: [UserRole.USER],
          permissions: [Permission.VIEW_DASHBOARD],
          createdAt: new Date(),
        },
        tokens: {
          accessToken: 'access_token',
          refreshToken: 'refresh_token',
          expiresIn: 3600,
          tokenType: 'Bearer' as const,
        },
        permissions: [Permission.VIEW_DASHBOARD, Permission.VIEW_QUEUE],
      };

      const result = AuthResponseSchema.safeParse(validResponse);
      expect(result.success).toBe(true);
    });
  });

  describe('Result Pattern', () => {
    describe('Ok', () => {
      it('should create successful result', () => {
        const result = Ok('success');
        expect(result.ok).toBe(true);
        if (result.ok) {
          expect(result.value).toBe('success');
        }
      });

      it('should work with complex types', () => {
        const user: User = {
          id: '123e4567-e89b-12d3-a456-426614174000' as UserId,
          username: 'test',
          email: 'test@example.com',
          roles: [UserRole.USER],
          permissions: [Permission.VIEW_DASHBOARD],
          createdAt: new Date(),
        };
        const result = Ok(user);
        expect(result.ok).toBe(true);
        if (result.ok) {
          expect(result.value.username).toBe('test');
        }
      });
    });

    describe('Err', () => {
      it('should create error result', () => {
        const error = new Error('Something went wrong');
        const result = Err(error);
        expect(result.ok).toBe(false);
        if (!result.ok) {
          expect(result.error).toBe(error);
        }
      });

      it('should work with custom error types', () => {
        const authError = {
          code: AuthErrorCode.INVALID_CREDENTIALS,
          message: 'Invalid username or password',
        };
        const result = Err(authError);
        expect(result.ok).toBe(false);
        if (!result.ok) {
          expect(result.error.code).toBe(AuthErrorCode.INVALID_CREDENTIALS);
        }
      });
    });
  });

  describe('Enums', () => {
    describe('UserRole', () => {
      it('should have all expected roles', () => {
        expect(UserRole.ADMIN).toBe('ADMIN');
        expect(UserRole.USER).toBe('USER');
        expect(UserRole.READ_ONLY).toBe('READ_ONLY');
        expect(UserRole.OPERATOR).toBe('OPERATOR');
      });
    });

    describe('Permission', () => {
      it('should have dashboard permissions', () => {
        expect(Permission.VIEW_DASHBOARD).toBe('VIEW_DASHBOARD');
      });

      it('should have queue permissions', () => {
        expect(Permission.VIEW_QUEUE).toBe('VIEW_QUEUE');
        expect(Permission.MANAGE_QUEUE).toBe('MANAGE_QUEUE');
        expect(Permission.DELETE_QUEUE_ITEMS).toBe('DELETE_QUEUE_ITEMS');
      });

      it('should have security permissions', () => {
        expect(Permission.VIEW_SECURITY).toBe('VIEW_SECURITY');
        expect(Permission.MANAGE_SECURITY).toBe('MANAGE_SECURITY');
      });

      it('should have settings permissions', () => {
        expect(Permission.VIEW_SETTINGS).toBe('VIEW_SETTINGS');
        expect(Permission.MANAGE_SERVER_CONFIG).toBe('MANAGE_SERVER_CONFIG');
        expect(Permission.MANAGE_USERS).toBe('MANAGE_USERS');
      });
    });

    describe('AuthErrorCode', () => {
      it('should have all expected error codes', () => {
        expect(AuthErrorCode.INVALID_CREDENTIALS).toBe('INVALID_CREDENTIALS');
        expect(AuthErrorCode.TOKEN_EXPIRED).toBe('TOKEN_EXPIRED');
        expect(AuthErrorCode.NETWORK_ERROR).toBe('NETWORK_ERROR');
        expect(AuthErrorCode.UNAUTHORIZED).toBe('UNAUTHORIZED');
        expect(AuthErrorCode.FORBIDDEN).toBe('FORBIDDEN');
        expect(AuthErrorCode.SESSION_TIMEOUT).toBe('SESSION_TIMEOUT');
      });
    });
  });
});
