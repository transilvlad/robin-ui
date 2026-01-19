# Quick Start Guide

Get Robin UI running in 5 minutes.

## Prerequisites

- Node.js 18+ installed
- Robin MTA Server running

## Installation

```bash
cd /Users/cstan/development/workspace/open-source/transilvlad-robin/robin-ui
npm install
```

## Configuration

Edit `src/environments/environment.ts`:

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8090',      // Robin API endpoint
  serviceUrl: 'http://localhost:8080',  // Robin service endpoint
};
```

## Start Development Server

```bash
npm start
```

Open browser to `http://localhost:4200/`

## Project Features

### ✅ Implemented

- **Dashboard**: Server health and queue overview
- **Email Queue**: View, retry, and delete queue items
- **Storage Browser**: Navigate email storage directories
- **Sidebar Navigation**: All feature modules accessible
- **Status Indicators**: Real-time server status
- **Responsive Layout**: Tailwind CSS styling

### 🔨 To Be Expanded

- **Security Module**: ClamAV, Rspamd, blocklist (placeholder)
- **Routing Module**: SMTP relay, webhooks (placeholder)
- **Monitoring Module**: Metrics, logs (placeholder)
- **Settings Module**: Server config, users (placeholder)

## Architecture Highlights

### NgModule-Based
- **CoreModule**: Singleton services (API, Auth, Notifications)
- **SharedModule**: Reusable components (Header, Sidebar, StatusBadge)
- **Feature Modules**: Lazy-loaded (Dashboard, Email, etc.)

### TypeScript Models
All API responses have TypeScript interfaces:
- `HealthResponse` - Server health
- `QueueItem` - Queue entries
- `StorageItem` - File browser items

### HTTP Interceptors
- **AuthInterceptor**: Adds auth headers
- **ErrorInterceptor**: Global error handling with notifications

### Custom Pipes
- `bytes` - Format file sizes (1024 → 1 KB)
- `relativeTime` - Format timestamps (60 → 1 minute ago)

## Common Commands

```bash
# Development
npm start                 # Start dev server
npm run build             # Production build
npm test                  # Run tests
npm run watch             # Build in watch mode

# Generate Components
ng generate component features/dashboard/components/new-widget
ng generate service features/dashboard/services/new-service
ng generate pipe shared/pipes/new-pipe
```

## Troubleshooting

### Port 4200 in use
```bash
ng serve --port 4300
```

### CORS errors
Create `proxy.conf.json`:
```json
{
  "/api": {
    "target": "http://localhost:8090",
    "secure": false,
    "changeOrigin": true,
    "pathRewrite": { "^/api": "" }
  }
}
```

Then start with proxy:
```bash
ng serve --proxy-config proxy.conf.json
```

### Module not found
```bash
rm -rf node_modules package-lock.json
npm install
```

## File Structure

```
robin-ui/
├── src/app/
│   ├── core/           # Singleton services, guards, interceptors
│   ├── shared/         # Reusable components, pipes
│   ├── features/       # Lazy-loaded feature modules
│   └── app.module.ts
├── src/environments/   # Environment configs
└── package.json
```

## API Endpoints

The app connects to Robin MTA:

### Service (port 8080)
- `GET /health` - Server health
- `GET /config` - Configuration
- `GET /metrics` - Metrics data

### API (port 8090)
- `GET /client/queue` - List queue items
- `POST /client/queue/:uid/retry` - Retry item
- `DELETE /client/queue/:uid` - Delete item
- `GET /store?path=/` - Browse storage

## Development Workflow

1. **Start Robin MTA Server**:
   ```bash
   java -jar robin.jar --server cfg/
   ```

2. **Start Angular UI** (separate terminal):
   ```bash
   cd robin-ui
   npm start
   ```

3. **Make Changes**: Edit files in `src/app/`

4. **Auto Reload**: Browser automatically reloads on save

## Next Steps

1. **Expand Dashboard**: Add more widgets and charts
2. **Implement Security**: Complete ClamAV/Rspamd forms
3. **Add Monitoring**: Metrics dashboard with Chart.js
4. **State Management**: Implement NgRx if needed
5. **Testing**: Add unit and E2E tests

## Resources

- **README.md** - Project overview
- **SETUP.md** - Detailed setup instructions
- **PROJECT_STRUCTURE.md** - Complete file listing
- **Angular Docs** - https://angular.io/docs

## Support

For issues:
1. Check SETUP.md for troubleshooting
2. Review PROJECT_STRUCTURE.md for architecture
3. Check Robin MTA Server logs
4. Verify API endpoints are accessible
