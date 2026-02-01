# Quick Start - Docker Setup

Get the complete Robin MTA suite running in 5 minutes.

## Prerequisites

- Docker 20.10+
- Docker Compose 2.0+
- 4GB RAM minimum
- 10GB disk space

## 1. Clone Repositories

```bash
cd ~/development/workspace/open-source/

# Clone Robin MTA
git clone https://github.com/transilvlad/robin.git transilvlad-robin

# Clone Robin UI
git clone <robin-ui-repo-url>
cd robin-ui
```

## 2. Run Setup Script

```bash
chmod +x scripts/setup.sh
./scripts/setup.sh
```

The script will:
- ✅ Check prerequisites
- ✅ Generate secure passwords
- ✅ Create `.env` file
- ✅ Build Docker images
- ✅ Start all services

## 3. Access the Application

After setup completes (2-5 minutes):

- **Robin UI**: http://localhost (production) or http://localhost:4200 (development)
- **Default Login**: `admin@localhost` / `admin`

## 4. Verify Services

```bash
# Check all services are running
docker compose -f docker-compose.full.yaml ps

# Should show all services as "healthy"
```

## Manual Setup (Alternative)

If you prefer manual setup:

```bash
# 1. Copy environment template
cp .env.docker .env

# 2. Generate secure JWT secret
openssl rand -base64 64

# 3. Edit .env and set DB_PASSWORD and JWT_SECRET
nano .env

# 4. Build and start services
docker compose -f docker-compose.full.yaml build
docker compose -f docker-compose.full.yaml up -d

# 5. Watch logs
docker compose -f docker-compose.full.yaml logs -f
```

## Common Commands

```bash
# Stop services
docker compose -f docker-compose.full.yaml stop

# Restart a service
docker compose -f docker-compose.full.yaml restart robin-gateway

# View logs
docker compose -f docker-compose.full.yaml logs -f

# Remove everything (keeps volumes)
docker compose -f docker-compose.full.yaml down

# Remove everything including data (WARNING!)
docker compose -f docker-compose.full.yaml down -v
```

## Service URLs

| Service | URL | Description |
|---------|-----|-------------|
| Robin UI | http://localhost | Web interface |
| Robin Gateway | http://localhost:8888 | API Gateway |
| Rspamd UI | http://localhost:11334 | Spam detector admin |
| PostgreSQL | localhost:5432 | Database |

## Development Mode

For development with hot reload:

```bash
# Use development compose file
docker compose -f docker-compose.dev.full.yaml up -d

# UI with hot reload
open http://localhost:4200

# Debug ports
# - Robin MTA: localhost:5005
# - Robin Gateway: localhost:5006
```

## Troubleshooting

### ClamAV takes 3+ minutes to start
This is normal. ClamAV downloads virus definitions on first run.

```bash
# Monitor progress
docker compose -f docker-compose.full.yaml logs -f clamav
```

### Service not healthy
```bash
# Check specific service
docker compose -f docker-compose.full.yaml logs <service-name>

# Restart service
docker compose -f docker-compose.full.yaml restart <service-name>
```

### Port conflicts
Edit `.env` to change ports:

```bash
# .env
POSTGRES_PORT=5433
GATEWAY_PORT=8889
```

## Next Steps

1. **Change default password** in Robin UI
2. **Configure Robin MTA** in `../transilvlad-robin/cfg/`
3. **Set up TLS certificates** for production
4. **Read full documentation** in `DOCKER_SETUP.md`

## Support

- Full documentation: `DOCKER_SETUP.md`
- Port reference: `PORTS_REFERENCE.md`
- Issues: GitHub repository
