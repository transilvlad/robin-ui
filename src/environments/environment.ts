export const environment = {
  production: false,
  apiUrl: '/api/v1', // All requests go through gateway at localhost:8080
  // serviceUrl removed - gateway handles all routing

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
    queue: '/queue',
    store: '/storage',
    logs: '/logs',

    domains: '/domains',
    dnsProviders: '/dns-providers',
    dnsTemplates: '/dns-templates',

    // Auth endpoints (Robin Gateway)
    auth: {
      login: '/auth/login',
      logout: '/auth/logout',
      refresh: '/auth/refresh',
      verify: '/auth/verify',
      me: '/auth/me',
    }
  },
};
