export const environment = {
  production: true,
  apiUrl: '', // Empty in production - uses relative paths (served by same origin)

  // Auth configuration (HttpOnly cookie strategy)
  auth: {
    tokenKey: 'robin_access_token',
    userKey: 'robin_user',
    tokenExpirationBuffer: 60,
    sessionTimeoutWarning: 300,
    sessionTimeout: 1800,
  },

  endpoints: {
    health: '/health/aggregate',
    config: '/config',
    metrics: '/metrics',
    queue: '/queue',
    store: '/storage',
    logs: '/logs',

    // Auth endpoints (Robin Gateway)
    auth: {
      login: '/api/v1/auth/login',
      logout: '/api/v1/auth/logout',
      refresh: '/api/v1/auth/refresh',
      verify: '/api/v1/auth/verify',
      me: '/api/v1/auth/me',
    }
  },
};
