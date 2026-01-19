export const environment = {
  production: false,
  apiUrl: 'http://localhost:8090',
  serviceUrl: 'http://localhost:8080',
  endpoints: {
    health: '/health',
    config: '/config',
    metrics: '/metrics',
    queue: '/client/queue',
    store: '/store',
    logs: '/logs',
  },
};
