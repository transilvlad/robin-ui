import { z } from 'zod';

// Branded types for compile-time safety
export type AccessToken = string & { readonly __brand: 'AccessToken' };
export type RefreshToken = string & { readonly __brand: 'RefreshToken' };
export type UserId = string & { readonly __brand: 'UserId' };

// Role enum
export enum UserRole {
  ADMIN = 'ADMIN',
  USER = 'USER',
  READ_ONLY = 'READ_ONLY',
  OPERATOR = 'OPERATOR'
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
}

// Zod schemas for runtime validation
export const UserSchema = z.object({
  // Server sends numeric ID, client uses string
  id: z.number().or(z.string()).transform(id => String(id) as UserId),
  username: z.string().min(3).max(50),
  email: z.string().email(),
  firstName: z.string().optional(),
  lastName: z.string().optional(),
  // Server sends "ROLE_ADMIN", client expects "ADMIN"
  roles: z.array(z.string()).transform(roles => 
    roles.map(r => r.replace('ROLE_', '') as UserRole)
  ),
  // Server might not send permissions in user object
  permissions: z.array(z.string()).optional().default([]).transform(perms => 
    perms.map(p => p as Permission)
  ),
  createdAt: z.coerce.date().optional(),
  lastLoginAt: z.coerce.date().optional(),
});

export const AuthTokensSchema = z.object({
  accessToken: z.string().transform(t => t as AccessToken),
  // Server sends null for refresh token (HttpOnly cookie)
  refreshToken: z.string().nullable().optional().transform(t => t as RefreshToken | null),
  expiresIn: z.number().positive(),
  tokenType: z.literal('Bearer'),
});

export const AuthResponseSchema = z.object({
  user: UserSchema,
  tokens: AuthTokensSchema,
  permissions: z.array(z.nativeEnum(Permission)),
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
