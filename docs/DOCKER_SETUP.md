# Robin MTA Complete Suite - Docker Setup

This guide explains how to run the complete Robin MTA suite with Docker Compose, including Robin MTA, Robin Gateway, and Robin UI with all dependencies.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Robin UI (Port 80)                       │
│                    Angular Frontend (Nginx)                      │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Robin Gateway (Port 8888)                     │
│          Spring Boot Gateway + Auth + Rate Limiting              │
└──────────────┬──────────────────────────────────┬───────────────┘
               │                                   │
               ↓                                   ↓
┌──────────────────────────┐         ┌──────────────────────────┐
│ Robin MTA Service API    │         │ Robin MTA Client API     │
│      (Port 8080)         │         │      (Port 8090)         │
└──────────────┬───────────┘         └──────────┬───────────────┘
               │                                 │
               └─────────────┬───────────────────┘
                             ↓
               ┌─────────────────────────┐
               │     Robin MTA Core      │
               │  (SMTP: 25, 587, 465)   │
               └─────────────┬───────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ↓                    ↓                    ↓
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  PostgreSQL  │    │    Redis     │    │   ClamAV     │
│  (Port 5432) │    │ (Port 6379)  │    │ (Port 3310)  │
└──────────────┘    └──────────────┘    └──────────────┘
        │                                        │
        └────────────────────┬───────────────────┘
                             ↓
                    ┌──────────────┐
                    │   Rspamd     │
                    │ (Port 11333) │
                    └──────────────┘
```

## Services Overview

| Service | Port | Purpose |
|---------|------|---------|
| **Robin UI** | 80 | Angular frontend (production) |
| **Robin Gateway** | 8888 | API Gateway with JWT authentication |
| **Robin MTA (SMTP)** | 25, 587, 465 | Mail server (SMTP, SUBMISSION, SMTPS) |
| **Robin MTA (Service API)** | 8080 | Control API (config, metrics, health) |
| **Robin MTA (Client API)** | 8090 | Client API (queue, storage, logs) |
| **PostgreSQL** | 5432 | Shared database |
| **Redis** | 6379 | Rate limiting and caching |
| **ClamAV** | 3310 | Virus scanning |
| **Rspamd** | 11333, 11334 | Spam detection (11334 for web UI) |

## Prerequisites

1. **Docker** (v20.10 or higher)
2. **Docker Compose** (v2.0 or higher)
3. **Robin MTA source code** at `../transilvlad-robin/` (relative to robin-ui)

## Quick Start

### 1. Clone Repositories

```bash
# Clone Robin MTA
cd ~/development/workspace/open-source/
git clone https://github.com/transilvlad/robin.git transilvlad-robin

# Clone Robin UI (if not already cloned)
git clone https://github.com/your-org/robin-ui.git
cd robin-ui
```

### 2. Configure Environment

```bash
# Copy environment template
cp .env.docker .env

# Edit .env and set secure passwords
nano .env
```

**Important:** Change the following in `.env`:
- `DB_PASSWORD`: Strong password for PostgreSQL
- `JWT_SECRET`: Generate with `openssl rand -base64 64`

### 3. Start the Suite

```bash
# Build and start all services
docker-compose -f docker-compose.full.yaml up -d

# Follow logs
docker-compose -f docker-compose.full.yaml logs -f
```

### 4. Verify Services

```bash
# Check all services are healthy
docker-compose -f docker-compose.full.yaml ps

# Should show all services as "healthy"
```

### 5. Access the UI

Open your browser to:
- **Robin UI**: http://localhost
- **Rspamd Web UI**: http://localhost:11334

### 6. Default Login

The default user is created by Robin Gateway on first run:
- **Username**: `admin@localhost`
- **Password**: `admin` (change immediately in production!)

## Service Health Checks

Each service has health checks configured:

```bash
# PostgreSQL
docker exec robin-postgres pg_isready -U robin -d robin

# Redis
docker exec robin-redis redis-cli ping

# Robin MTA
docker exec robin-mta sh -c "nc -z localhost 25 && nc -z localhost 8080"

# Robin Gateway
docker exec robin-gateway curl -f http://localhost:8080/actuator/health

# Robin UI
docker exec robin-ui wget --quiet --tries=1 --spider http://localhost:80
```

## Startup Order

Docker Compose handles service dependencies automatically:

1. **PostgreSQL** and **Redis** start first
2. **ClamAV** and **Rspamd** start (ClamAV takes ~3 minutes to download virus definitions)
3. **Robin MTA** waits for database, ClamAV, and Rspamd
4. **Robin Gateway** waits for database, Redis, and Robin MTA
5. **Robin UI** waits for Robin Gateway

## Configuration

### Robin MTA Configuration

Configuration files are mounted from `../transilvlad-robin/cfg/`:

```bash
# Edit Robin MTA config
cd ../transilvlad-robin/cfg
nano server.properties

# Restart Robin MTA to apply changes
docker-compose -f ../robin-ui/docker-compose.full.yaml restart robin-mta
```

### Robin Gateway Configuration

Gateway is configured via environment variables in `.env`:

```bash
# Database
DB_PASSWORD=robin123

# JWT tokens
JWT_SECRET=your-secret-here

# API endpoints (automatically configured)
ROBIN_SERVICE_URL=http://robin-mta:8080
ROBIN_CLIENT_URL=http://robin-mta:8090
```

### Robin UI Configuration

The UI is pre-configured to use the gateway at `/api/v1`. No additional configuration needed.

## Volume Management

All data is persisted in Docker volumes:

```bash
# List volumes
docker volume ls | grep robin

# Backup PostgreSQL database
docker exec robin-postgres pg_dump -U robin robin > backup.sql

# Restore PostgreSQL database
cat backup.sql | docker exec -i robin-postgres psql -U robin robin

# Clean up volumes (WARNING: deletes all data!)
docker-compose -f docker-compose.full.yaml down -v
```

## Logs

### View All Logs

```bash
docker-compose -f docker-compose.full.yaml logs -f
```

### View Specific Service Logs

```bash
docker-compose -f docker-compose.full.yaml logs -f robin-mta
docker-compose -f docker-compose.full.yaml logs -f robin-gateway
docker-compose -f docker-compose.full.yaml logs -f robin-ui
```

### Log Locations

Logs are stored in Docker volumes:
- Robin MTA: `robin-mta-logs`
- PostgreSQL: `robin-postgres-logs`
- ClamAV: `robin-clamav-logs`
- Rspamd: `robin-rspamd-logs`

## Troubleshooting

### Service Won't Start

```bash
# Check service status
docker-compose -f docker-compose.full.yaml ps

# Check specific service logs
docker-compose -f docker-compose.full.yaml logs robin-gateway

# Restart a specific service
docker-compose -f docker-compose.full.yaml restart robin-gateway
```

### ClamAV Takes Long to Start

ClamAV needs to download virus definitions on first run (~3 minutes):

```bash
# Monitor ClamAV startup
docker-compose -f docker-compose.full.yaml logs -f clamav
```

### Database Connection Issues

```bash
# Verify PostgreSQL is healthy
docker exec robin-postgres pg_isready -U robin -d robin

# Check if Flyway migrations ran
docker-compose -f docker-compose.full.yaml logs robin-gateway | grep Flyway
```

### Gateway Returns 502 Bad Gateway

```bash
# Verify Robin MTA is healthy
docker exec robin-mta sh -c "nc -z localhost 8080"

# Check gateway logs
docker-compose -f docker-compose.full.yaml logs robin-gateway
```

### UI Shows Connection Error

```bash
# Verify gateway is healthy
docker exec robin-gateway curl -f http://localhost:8080/actuator/health

# Check nginx logs
docker-compose -f docker-compose.full.yaml logs robin-ui
```

## Production Deployment

### Security Hardening

1. **Change default passwords**:
   ```bash
   # Generate strong JWT secret
   openssl rand -base64 64

   # Update .env with secure values
   nano .env
   ```

2. **Use TLS/SSL**:
   - Configure nginx with SSL certificates
   - Update Robin MTA for TLS on SMTP ports

3. **Restrict network access**:
   - Remove port mappings for internal services (Redis, PostgreSQL)
   - Use a reverse proxy (Traefik, nginx) for external access

4. **Enable firewall**:
   ```bash
   # Only allow necessary ports
   ufw allow 80/tcp   # HTTP
   ufw allow 443/tcp  # HTTPS
   ufw allow 25/tcp   # SMTP
   ufw allow 587/tcp  # SUBMISSION
   ```

### Resource Limits

Add resource limits to `docker-compose.full.yaml`:

```yaml
services:
  robin-mta:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 512M
```

### Monitoring

Set up monitoring with Prometheus and Grafana:

```bash
# Robin Gateway exposes metrics
curl http://localhost:8888/actuator/prometheus

# Robin MTA exposes metrics
curl http://localhost:8080/metrics
```

## Stopping the Suite

```bash
# Stop all services
docker-compose -f docker-compose.full.yaml stop

# Stop and remove containers (keeps volumes)
docker-compose -f docker-compose.full.yaml down

# Stop and remove everything including volumes (WARNING: data loss!)
docker-compose -f docker-compose.full.yaml down -v
```

## Development vs Production

For development with hot reload:

```bash
# Use the development compose file
docker-compose -f docker-compose.dev.yaml up -d
```

For production:

```bash
# Use the full suite
docker-compose -f docker-compose.full.yaml up -d
```

## Support

- Robin MTA: https://github.com/transilvlad/robin
- Robin UI: https://github.com/your-org/robin-ui
- Issues: Report to respective GitHub repositories

## License

See LICENSE files in respective projects.
