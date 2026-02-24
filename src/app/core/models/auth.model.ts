import { z } from 'zod';

// Branded types for compile-time safety
export type AccessToken = string & { readonly __brand: 'AccessToken' };
export type RefreshToken = string & { readonly __brand: 'RefreshToken' };
export type UserId = string & { readonly __brand: 'UserId' };

// Role enum - matches gateway ROLE_* format
export enum UserRole {
  ADMIN = 'ROLE_ADMIN',
  USER = 'ROLE_USER',
  READONLY = 'ROLE_READONLY',
  OPERATOR = 'ROLE_OPERATOR'
}

// Permission enum (feature-based)
export enum Permission {
  // Dashboard
  VIEW_DASHBOARD = 'VIEW_DASHBOARD',

  // Email Queue
  VIEW_QUEUE = 'VIEW_QUEUE',
  MANAGE_QUEUE = 'MANAGE_QUEUE',
  DELETE_QUEUE_ITEMS = 'DELETE_QUEUE_ITEMS',

  // Storage
  VIEW_STORAGE = 'VIEW_STORAGE',
  MANAGE_STORAGE = 'MANAGE_STORAGE',

  // Security
  VIEW_SECURITY = 'VIEW_SECURITY',
  MANAGE_SECURITY = 'MANAGE_SECURITY',

  // Routing
  VIEW_ROUTING = 'VIEW_ROUTING',
  MANAGE_ROUTING = 'MANAGE_ROUTING',

  // Monitoring
  VIEW_METRICS = 'VIEW_METRICS',
  VIEW_LOGS = 'VIEW_LOGS',

  // Settings
  VIEW_SETTINGS = 'VIEW_SETTINGS',
  MANAGE_SERVER_CONFIG = 'MANAGE_SERVER_CONFIG',
  MANAGE_USERS = 'MANAGE_USERS',

  // Domains
  VIEW_DOMAINS = 'VIEW_DOMAINS',
  MANAGE_DOMAINS = 'MANAGE_DOMAINS',
  MANAGE_DNS_RECORDS = 'MANAGE_DNS_RECORDS',
  MANAGE_DNS_PROVIDERS = 'MANAGE_DNS_PROVIDERS',
  MANAGE_DKIM = 'MANAGE_DKIM',
}

// Zod schemas for runtime validation
export const UserSchema = z.object({
  id: z.union([z.string(), z.number()]).transform(id => String(id)),
  username: z.string().min(1),
  email: z.string().email(),
  firstName: z.string().optional(),
  lastName: z.string().optional(),
  roles: z.array(z.nativeEnum(UserRole)),
  permissions: z.array(z.nativeEnum(Permission)).optional().default([]),
  createdAt: z.string().optional(),
  updatedAt: z.string().optional(),
  lastLoginAt: z.string().optional(),
});

export const AuthTokensSchema = z.object({
  accessToken: z.string().transform(t => t as AccessToken),
  // Server sends refresh token in HttpOnly cookie, not in response
  refreshToken: z.string().nullable().optional().transform(t => t as RefreshToken | null),
  expiresIn: z.number().positive()
});

// Gateway AuthResponse structure (nested tokens format)
export const AuthResponseSchema = z.object({
  user: UserSchema,
  tokens: z.object({
    accessToken: z.string(),
    refreshToken: z.string().nullable().optional(),
    tokenType: z.string().optional(),
    expiresIn: z.number().positive(),
  }),
  permissions: z.array(z.nativeEnum(Permission)).optional().default([]),
}).transform(data => ({
  accessToken: data.tokens.accessToken as AccessToken,
  refreshToken: data.tokens.refreshToken as RefreshToken | null,
  expiresIn: data.tokens.expiresIn,
  user: {
    ...data.user,
    permissions: data.permissions || data.user.permissions || []
  }
}));

// Token refresh response
export const TokenResponseSchema = z.object({
  accessToken: z.string().transform(t => t as AccessToken),
  expiresIn: z.number().positive()
});

export const LoginRequestSchema = z.object({
  username: z.string().min(3),
  password: z.string().min(8),
  rememberMe: z.boolean().optional(),
});

// Type inference from Zod schemas
export type User = z.infer<typeof UserSchema>;
export type AuthTokens = z.infer<typeof AuthTokensSchema>;
export type AuthResponse = z.infer<typeof AuthResponseSchema>;
export type TokenResponse = z.infer<typeof TokenResponseSchema>;
export type LoginRequest = z.infer<typeof LoginRequestSchema>;

// Token payload (decoded JWT)
export interface TokenPayload {
  sub: UserId;
  username: string;
  roles: UserRole[];
  permissions: Permission[];
  exp: number; // expiration timestamp
  iat: number; // issued at timestamp
}

// Error handling with Result<T, E> pattern
export type Result<T, E = Error> =
  | { ok: true; value: T }
  | { ok: false; error: E };

export const Ok = <T>(value: T): Result<T, never> => ({ ok: true, value });
export const Err = <E>(error: E): Result<never, E> => ({ ok: false, error });

// Auth-specific error types
export enum AuthErrorCode {
  INVALID_CREDENTIALS = 'INVALID_CREDENTIALS',
  TOKEN_EXPIRED = 'TOKEN_EXPIRED',
  NETWORK_ERROR = 'NETWORK_ERROR',
  UNAUTHORIZED = 'UNAUTHORIZED',
  FORBIDDEN = 'FORBIDDEN',
  SESSION_TIMEOUT = 'SESSION_TIMEOUT',
  INVALID_TOKEN = 'INVALID_TOKEN',
  REFRESH_FAILED = 'REFRESH_FAILED',
}

export interface AuthError {
  code: AuthErrorCode;
  message: string;
  details?: unknown;
}

// Helper function to create auth errors
export function createAuthError(code: AuthErrorCode, message: string, details?: unknown): AuthError {
  return { code, message, details };
}

// ===== Helper Functions =====

/**
 * Validate and parse data using a Zod schema.
 * Returns a Result type for explicit error handling.
 */
export function validateWithSchema<T>(
  schema: z.ZodSchema<T>,
  data: unknown
): Result<T, AuthError> {
  try {
    const parsed = schema.parse(data);
    return Ok(parsed);
  } catch (error) {
    if (error instanceof z.ZodError) {
      return Err(createAuthError(
        AuthErrorCode.INVALID_TOKEN,
        'Validation failed',
        error.errors
      ));
    }
    return Err(createAuthError(
      AuthErrorCode.NETWORK_ERROR,
      'Unknown validation error',
      error
    ));
  }
}

/**
 * Check if user has a specific role.
 */
export function hasRole(user: User | null, role: UserRole): boolean {
  return user?.roles?.includes(role) ?? false;
}

/**
 * Check if user has a specific permission.
 */
export function hasPermission(user: User | null, permission: Permission): boolean {
  return user?.permissions?.includes(permission) ?? false;
}

/**
 * Check if user has any of the specified roles.
 */
export function hasAnyRole(user: User | null, roles: UserRole[]): boolean {
  return roles.some(r => hasRole(user, r));
}

/**
 * Check if user has all of the specified permissions.
 */
export function hasAllPermissions(user: User | null, permissions: Permission[]): boolean {
  return permissions.every(p => hasPermission(user, p));
}

/**
 * Check if user has any of the specified permissions.
 */
export function hasAnyPermission(user: User | null, permissions: Permission[]): boolean {
  return permissions.some(p => hasPermission(user, p));
}

/**
 * Check if access token is expired based on expiration timestamp.
 */
export function isTokenExpired(expiresAt: number): boolean {
  return Date.now() >= expiresAt;
}

/**
 * Calculate token expiration timestamp from expiresIn (milliseconds).
 */
export function calculateExpiresAt(expiresIn: number): number {
  return Date.now() + expiresIn;
}

/**
 * Get user display name (email or username).
 */
export function getUserDisplayName(user: User): string {
  if (user.firstName && user.lastName) {
    return `${user.firstName} ${user.lastName}`;
  }
  return user.email || user.username;
}

/**
 * Check if user is admin.
 */
export function isAdmin(user: User | null): boolean {
  return hasRole(user, UserRole.ADMIN);
}

/**
 * Get human-readable role name.
 */
export function getRoleName(role: UserRole): string {
  const roleNames: Record<UserRole, string> = {
    [UserRole.ADMIN]: 'Administrator',
    [UserRole.USER]: 'User',
    [UserRole.READONLY]: 'Read Only',
    [UserRole.OPERATOR]: 'Operator'
  };
  return roleNames[role] || role;
}
