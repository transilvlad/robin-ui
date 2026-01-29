export const environment = {
  production: false,
  apiUrl: '', // Proxy handles routing to localhost:28090 (Gateway)
  serviceUrl: '', // Proxy handles routing to localhost:28080 (Direct service for legacy routes)

  // Auth configuration (HttpOnly cookie strategy)
  auth: {
    tokenKey: 'robin_access_token',        // sessionStorage key for access token
    userKey: 'robin_user',                 // sessionStorage key for user info
    // Note: refreshToken NOT stored client-side - managed by HttpOnly cookie
    tokenExpirationBuffer: 60,             // seconds before expiry to refresh
    sessionTimeoutWarning: 300,            // 5 minutes before timeout warning
    sessionTimeout: 1800,                  // 30 minutes of inactivity before logout
  },

  endpoints: {
    health: '/health/aggregate',
    config: '/config',
    metrics: '/metrics',
    queue: '/client/queue',
    store: '/store',
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
