# Robin MTA Management UI

Angular 18+ management interface for Robin MTA Server.

## Project Structure

This project uses NgModule-based architecture with the following structure:

### Core Module (`src/app/core/`)
Singleton services, guards, interceptors, and models:
- `services/` - API, Auth, Notification services
- `interceptors/` - HTTP interceptors (Auth, Error)
- `guards/` - Route guards (Auth)
- `models/` - TypeScript interfaces for API responses

### Shared Module (`src/app/shared/`)
Reusable components and pipes:
- `components/` - Header, Sidebar, StatusBadge
- `pipes/` - BytesPipe, RelativeTimePipe

### Feature Modules (`src/app/features/`)
Lazy-loaded feature modules:
- **Dashboard** - Server health and queue overview
- **Email** - Queue management and storage browser
- **Security** - ClamAV, Rspamd, and blocklist configuration
- **Routing** - SMTP relay and webhook configuration
- **Monitoring** - Metrics dashboard and log viewer
- **Settings** - Server configuration and user management

## Development

### Prerequisites
- Node.js 18+
- npm or yarn

### Installation

```bash
npm install
```

### Development Server

```bash
npm start
```

Navigate to `http://localhost:4200/`. The application will automatically reload if you change any of the source files.

### Build

```bash
npm run build
```

Build artifacts will be stored in the `dist/` directory.

### Running Tests

```bash
npm test
```

## Robin API Configuration

The application connects to Robin's API endpoints:
- **Service Endpoint** (port 28080): `/health`, `/config`, `/metrics/*`
- **API Endpoint** (port 28090): `/client/queue/*`, `/store/*`, `/logs`

Update `src/environments/environment.ts` to configure API URLs.

## Features

### Dashboard
- Real-time server health monitoring
- Queue size and retry distribution
- Active listeners overview

### Email Management
- Queue item listing with pagination
- Retry and delete queue items
- Storage browser with file navigation

### Security
- ClamAV antivirus configuration
- Rspamd spam filtering settings
- IP/Domain blocklist management

### Routing
- SMTP relay configuration
- Webhook configuration for email events

### Monitoring
- Prometheus/Graphite metrics visualization
- Real-time log viewer with filtering

### Settings
- Server configuration (listeners, ports, TLS)
- User management

## Technology Stack

- **Angular 18+** - Frontend framework
- **NgRx** - State management
- **Tailwind CSS** - Utility-first CSS
- **Chart.js** - Data visualization
- **RxJS** - Reactive programming

## Module Architecture

- **CoreModule** - Imported once in AppModule
- **SharedModule** - Imported in each feature module
- **Feature Modules** - Lazy-loaded for performance

## License

Same as Robin MTA Server
