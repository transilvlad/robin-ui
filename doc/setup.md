# Robin UI Setup Guide

Complete guide for setting up and running the Robin MTA Management UI.

## Prerequisites

- Node.js 18.x or later
- npm 9.x or later (comes with Node.js)
- Git

## Installation Steps

### 1. Navigate to Project Directory

```bash
cd /Users/cstan/development/workspace/open-source/transilvlad-robin/robin-ui
```

### 2. Install Dependencies

```bash
npm install
```

This will install:
- Angular 18.2.x
- NgRx (Store, Effects, Entity)
- Tailwind CSS
- Chart.js and ng2-charts
- Angular CDK
- All development dependencies

### 3. Verify Installation

```bash
npm list --depth=0
```

You should see all dependencies listed without errors.

## Development

### Start Development Server

```bash
npm start
```

Or with specific options:

```bash
ng serve --open --port 4200
```

The application will be available at `http://localhost:4200/`

### Watch Mode

The development server automatically reloads when you save changes to source files.

## Building for Production

### Production Build

```bash
npm run build
```

Build artifacts will be in `dist/robin-ui/`

### Build with Specific Configuration

```bash
ng build --configuration production
```

## Project Structure

```
robin-ui/
├── src/
│   ├── app/
│   │   ├── core/                 # CoreModule (singleton)
│   │   │   ├── guards/           # Route guards
│   │   │   ├── interceptors/     # HTTP interceptors
│   │   │   ├── models/           # TypeScript interfaces
│   │   │   └── services/         # Core services
│   │   ├── shared/               # SharedModule (reusable)
│   │   │   ├── components/       # Shared components
│   │   │   └── pipes/            # Custom pipes
│   │   ├── features/             # Feature modules (lazy-loaded)
│   │   │   ├── dashboard/
│   │   │   ├── email/
│   │   │   ├── security/
│   │   │   ├── routing/
│   │   │   ├── monitoring/
│   │   │   └── settings/
│   │   ├── app-routing.module.ts
│   │   ├── app.component.ts
│   │   └── app.module.ts
│   ├── environments/
│   ├── assets/
│   ├── styles.scss
│   └── index.html
├── angular.json
├── package.json
├── tailwind.config.js
└── tsconfig.json
```

## Configuration

### Environment Files

Update API endpoints in `src/environments/environment.ts`:

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8090',      // Robin API endpoint
  serviceUrl: 'http://localhost:8080',  // Robin service endpoint
};
```

For production, update `src/environments/environment.prod.ts`:

```typescript
export const environment = {
  production: true,
  apiUrl: '/api',
  serviceUrl: '/service',
};
```

### Tailwind CSS

Tailwind is pre-configured in `tailwind.config.js`. To customize:

```javascript
module.exports = {
  theme: {
    extend: {
      colors: {
        primary: { /* your custom colors */ },
      },
    },
  },
};
```

## Running with Robin MTA Server

### 1. Start Robin MTA Server

```bash
cd /Users/cstan/development/workspace/open-source/transilvlad-robin
java -jar robin.jar --server cfg/
```

This starts Robin on ports:
- 8080 (service endpoint)
- 8090 (API endpoint)

### 2. Start Angular Dev Server

In a separate terminal:

```bash
cd robin-ui
npm start
```

### 3. Access UI

Open browser to `http://localhost:4200/`

## Troubleshooting

### Port Already in Use

If port 4200 is in use:

```bash
ng serve --port 4300
```

### CORS Issues

If you encounter CORS errors, you may need to configure Robin MTA to allow requests from `http://localhost:4200`.

Create `robin-ui/proxy.conf.json`:

```json
{
  "/api": {
    "target": "http://localhost:8090",
    "secure": false,
    "changeOrigin": true,
    "pathRewrite": {
      "^/api": ""
    }
  },
  "/service": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true,
    "pathRewrite": {
      "^/service": ""
    }
  }
}
```

Then start dev server with proxy:

```bash
ng serve --proxy-config proxy.conf.json
```

And update `environment.ts`:

```typescript
apiUrl: '/api',
serviceUrl: '/service',
```

### Module Not Found Errors

If you get "Cannot find module" errors:

```bash
rm -rf node_modules package-lock.json
npm install
```

### TypeScript Path Mapping Issues

The project uses path aliases defined in `tsconfig.json`:
- `@core/*` → `src/app/core/*`
- `@shared/*` → `src/app/shared/*`
- `@features/*` → `src/app/features/*`
- `@environments/*` → `src/environments/*`

If imports fail, restart the dev server.

## Available Scripts

- `npm start` - Start development server
- `npm run build` - Build for production
- `npm test` - Run unit tests
- `npm run watch` - Build in watch mode
- `npm run lint` - Run linter (if configured)

## Testing

### Unit Tests

```bash
npm test
```

This uses Karma and Jasmine.

### End-to-End Tests

E2E tests can be added using Playwright or Cypress.

## Next Steps

1. **Implement Real API Integration**: Currently using placeholder implementations
2. **Add NgRx State Management**: State management structure is ready
3. **Implement Charts**: Add Chart.js visualizations for metrics
4. **Add Authentication**: Complete auth service implementation
5. **Expand Feature Modules**: Build out placeholder components

## Module Development Guidelines

### Adding a New Component to a Feature Module

```bash
ng generate component features/dashboard/components/new-widget
```

### Adding a New Service

```bash
ng generate service features/dashboard/services/new-service
```

### Adding a New Feature Module

```bash
ng generate module features/new-feature --routing
ng generate component features/new-feature/new-feature
```

Then add lazy route in `app-routing.module.ts`:

```typescript
{
  path: 'new-feature',
  loadChildren: () => import('./features/new-feature/new-feature.module')
    .then(m => m.NewFeatureModule)
}
```

## Important Notes

- **CoreModule** should only be imported in AppModule
- **SharedModule** should be imported in each feature module that needs it
- All feature modules are lazy-loaded for performance
- Use the path aliases (`@core/`, `@shared/`, etc.) for imports

## Support

For issues related to:
- **Robin MTA Server**: See main project README
- **Angular UI**: Check this SETUP.md and README.md
