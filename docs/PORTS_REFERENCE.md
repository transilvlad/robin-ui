# Robin MTA Suite - Port Reference

Quick reference for all ports used in the Robin MTA suite.

## Service Ports

### Frontend & Gateway

| Port | Service | Description | Access |
|------|---------|-------------|--------|
| **80** | Robin UI | Production web interface | http://localhost |
| **4200** | Robin UI Dev | Development server with hot reload | http://localhost:4200 |
| **8888** | Robin Gateway | API Gateway (prod uses 8080 internally) | http://localhost:8888 |

### Robin MTA

| Port | Service | Protocol | Description |
|------|---------|----------|-------------|
| **25** | Robin MTA | SMTP | Mail submission |
| **587** | Robin MTA | SUBMISSION | Mail submission with STARTTLS |
| **465** | Robin MTA | SMTPS | Mail submission with implicit TLS |
| **8080** | Robin MTA | HTTP | Service API (config, metrics, health) |
| **8090** | Robin MTA | HTTP | Client API (queue, storage, logs) |

### Backend Services

| Port | Service | Description | Web UI |
|------|---------|-------------|---------|
| **5432** | PostgreSQL | Database (shared) | N/A |
| **6379** | Redis | Cache & rate limiting | N/A |
| **3310** | ClamAV | Antivirus scanning | N/A |
| **11333** | Rspamd | Spam detection worker | N/A |
| **11334** | Rspamd | Web management interface | http://localhost:11334 |

### Debug Ports (Development Only)

| Port | Service | Description |
|------|---------|-------------|
| **5005** | Robin MTA | Java remote debugging |
| **5006** | Robin Gateway | Java remote debugging |

## Port Mapping by Environment

### Production (`docker-compose.full.yaml`)

```yaml
Robin UI:         80    → nginx:80 → gateway:8080
Robin Gateway:    8888  → gateway:8080 → robin-mta:8080/8090
Robin MTA:        25, 587, 465, 8080, 8090
PostgreSQL:       5432
Redis:            6379
ClamAV:           3310
Rspamd:           11333, 11334
```

### Development (`docker-compose.dev.full.yaml`)

```yaml
Robin UI:         4200  → node dev server
Robin Gateway:    8888  → gateway:8080 → robin-mta:8080/8090
Robin MTA:        25, 587, 465, 8080, 8090, 5005 (debug)
Robin Gateway:    8888, 5006 (debug)
PostgreSQL:       5432
Redis:            6379
ClamAV:           3310
Rspamd:           11333, 11334
```

## API Endpoints

### Robin UI → Robin Gateway

All UI requests go through Robin Gateway at `/api/v1`:

```
http://localhost/api/v1/auth/login       → Gateway auth endpoints
http://localhost/api/v1/queue/*          → Robin MTA Client API (8090)
http://localhost/api/v1/storage/*        → Robin MTA Client API (8090)
http://localhost/api/v1/logs/*           → Robin MTA Client API (8090)
http://localhost/api/v1/config/*         → Robin MTA Service API (8080)
http://localhost/api/v1/metrics/*        → Robin MTA Service API (8080)
```

### Robin Gateway → Robin MTA

Gateway proxies requests to Robin MTA:

```
/api/v1/queue/*    → http://robin-mta:8090/client/queue/*
/api/v1/storage/*  → http://robin-mta:8090/store/*
/api/v1/logs/*     → http://robin-mta:8090/logs/*
/api/v1/config/*   → http://robin-mta:8080/config/*
/api/v1/metrics/*  → http://robin-mta:8080/metrics/*
```

## Network Configuration

All services run on the same Docker network: `robin-network`

Internal service names (DNS):
```
postgres          → PostgreSQL database
redis             → Redis cache
clamav            → ClamAV antivirus
rspamd            → Rspamd spam detector
robin-mta         → Robin MTA server
robin-gateway     → Robin Gateway
robin-ui          → Robin UI (production only)
```

## Firewall Configuration

For production deployments, only expose necessary ports:

```bash
# Allow HTTP/HTTPS for web interface
ufw allow 80/tcp
ufw allow 443/tcp

# Allow SMTP ports for mail server
ufw allow 25/tcp
ufw allow 587/tcp
ufw allow 465/tcp

# Block direct access to backend services
ufw deny 5432/tcp   # PostgreSQL
ufw deny 6379/tcp   # Redis
ufw deny 3310/tcp   # ClamAV
ufw deny 8080/tcp   # Robin MTA APIs
ufw deny 8090/tcp   # Robin MTA APIs
ufw deny 8888/tcp   # Gateway (use reverse proxy instead)
```

## Testing Connectivity

```bash
# Test Robin UI
curl http://localhost

# Test Robin Gateway health
curl http://localhost:8888/actuator/health

# Test Robin MTA Service API
curl http://localhost:8080/health

# Test Robin MTA Client API
curl http://localhost:8090/health

# Test SMTP connectivity
telnet localhost 25

# Test PostgreSQL
psql -h localhost -p 5432 -U robin -d robin

# Test Redis
redis-cli -h localhost -p 6379 ping

# Test Rspamd Web UI
curl http://localhost:11334
```

## Port Conflicts

If you encounter port conflicts, you can override ports in `.env`:

```bash
# .env
POSTGRES_PORT=5433      # Instead of 5432
REDIS_PORT=6380         # Instead of 6379
GATEWAY_PORT=8889       # Instead of 8888
UI_PORT=8080            # Instead of 80
```

Then update the docker-compose file to use environment variables:

```yaml
ports:
  - "${UI_PORT:-80}:80"
```

## Quick Commands

```bash
# Show all listening ports
docker compose -f docker-compose.full.yaml ps

# Test all HTTP endpoints
curl -I http://localhost
curl -I http://localhost:8888/actuator/health
curl -I http://localhost:8080/health
curl -I http://localhost:8090/health
curl -I http://localhost:11334

# Monitor network traffic
docker compose -f docker-compose.full.yaml exec robin-mta netstat -tuln
```
