import { Injectable } from '@angular/core';
import { User, UserSchema, AccessToken } from '../models/auth.model';

/**
 * Token Storage Service
 *
 * Manages secure storage of authentication tokens and user data.
 * Uses HttpOnly cookie strategy:
 * - Refresh token: Managed by backend as HttpOnly cookie (immune to XSS)
 * - Access token: Stored in sessionStorage for page refresh recovery
 * - User info: Stored in sessionStorage for UI rendering
 *
 * Note: sessionStorage is cleared on tab/browser close for enhanced security
 */
@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  private readonly TOKEN_KEY = 'robin_access_token';
  private readonly USER_KEY = 'robin_user';

  /**
   * Store access token in sessionStorage
   * @param token - JWT access token
   */
  setAccessToken(token: AccessToken): void {
    sessionStorage.setItem(this.TOKEN_KEY, token);
  }

  /**
   * Retrieve access token from sessionStorage
   * @returns Access token or null if not found
   */
  getAccessToken(): AccessToken | null {
    const token = sessionStorage.getItem(this.TOKEN_KEY);
    return token as AccessToken | null;
  }

  /**
   * Store user information in sessionStorage with runtime validation
   * @param user - User object to store
   */
  setUser(user: User): void {
    sessionStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  /**
   * Retrieve and validate user information from sessionStorage
   * Uses Zod validation to ensure data integrity
   * @returns Validated user object or null if not found/invalid
   */
  getUser(): User | null {
    const userJson = sessionStorage.getItem(this.USER_KEY);
    if (!userJson) return null;

    try {
      const parsed = JSON.parse(userJson);
      return UserSchema.parse(parsed); // Runtime validation with Zod
    } catch (error) {
      console.error('Corrupted user data in storage', error);
      this.clear();
      return null;
    }
  }

  /**
   * Clear all stored authentication data
   * Note: HttpOnly refresh token cookie is managed by backend
   */
  clear(): void {
    sessionStorage.removeItem(this.TOKEN_KEY);
    sessionStorage.removeItem(this.USER_KEY);
  }

  /**
   * Check if authentication data exists in storage
   * @returns True if both token and user exist
   */
  hasAuthData(): boolean {
    return !!this.getAccessToken() && !!this.getUser();
  }
}
