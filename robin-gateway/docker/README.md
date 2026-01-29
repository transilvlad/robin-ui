# Robin Gateway Docker Setup

This directory contains Docker Compose configuration for running Robin Gateway alongside Robin MTA.

## Architecture

```
┌─────────────────┐
│   Robin UI      │ (Angular SPA on port 4200)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Robin Gateway   │ (Spring Boot on port 8080)
│  - JWT Auth     │
│  - API Gateway  │
└────────┬────────┘
         │
         ├─────────────────┐
         ▼                 ▼
┌──────────────┐   ┌──────────────┐
│  Robin MTA   │   │  PostgreSQL  │
│  Suite       │   │  (suite-     │
│  :28080      │   │   postgres)  │
│  :28090      │   │  :5433       │
└──────────────┘   └──────────────┘
```

## Prerequisites

### 1. Start Robin MTA Suite

The gateway requires Robin MTA's PostgreSQL instance and services to be running.

```bash
# Navigate to Robin MTA suite directory
cd ~/development/workspace/open-source/transilvlad-robin/.suite

# Start the full Robin MTA suite (PostgreSQL, ClamAV, Rspamd, Robin, Dovecot, Roundcube)
docker-compose up -d

# Verify services are healthy
docker-compose ps

# Check PostgreSQL is running
docker exec suite-postgres pg_isready -U robin -d robin
```

The suite provides:
- **suite-postgres**: PostgreSQL database (port 5433:5432)
- **suite-robin**: Robin MTA service (ports 28080:8080, 28090:8090)
- **suite_suite**: Docker network (external)

### 2. Build Robin Gateway

```bash
# Navigate to gateway root
cd ~/development/workspace/open-source/robin-ui/robin-gateway

# Build the application
./mvnw clean package -DskipTests

# Or build with tests
./mvnw clean install
```

## Starting the Gateway

### Option 1: Docker Compose (Recommended)

```bash
# Navigate to docker directory
cd ~/development/workspace/open-source/robin-ui/robin-gateway/docker

# Start gateway and redis
docker-compose up -d

# Check logs
docker-compose logs -f gateway

# Verify gateway health
curl http://localhost:8080/actuator/health
```

### Option 2: Local Development (without Docker)

```bash
# Ensure Robin MTA suite is running (PostgreSQL on port 5433)

# Set environment variables
export DB_HOST=localhost
export DB_PORT=5433
export DB_NAME=robin
export DB_USER=robin
export DB_PASSWORD=robin
export REDIS_HOST=localhost
export REDIS_PORT=6379
export ROBIN_CLIENT_URL=http://localhost:28090
export ROBIN_SERVICE_URL=http://localhost:28080

# Run gateway
cd ~/development/workspace/open-source/robin-ui/robin-gateway
./mvnw spring-boot:run
```

## Configuration

### Environment Variables

The gateway docker-compose uses these environment variables:

| Variable | Value | Description |
|----------|-------|-------------|
| `DB_HOST` | `suite-postgres` | PostgreSQL container name |
| `DB_PORT` | `5432` | Internal PostgreSQL port |
| `DB_NAME` | `robin` | Database name (shared with Robin MTA) |
| `DB_USER` | `robin` | Database user |
| `DB_PASSWORD` | `robin` | Database password |
| `REDIS_HOST` | `redis` | Redis container name |
| `REDIS_PORT` | `6379` | Redis port |
| `ROBIN_CLIENT_URL` | `http://suite-robin:8090` | Robin MTA Client API |
| `ROBIN_SERVICE_URL` | `http://suite-robin:8080` | Robin MTA Service API |
| `JWT_SECRET` | `dev-secret-key...` | JWT signing secret (change in production!) |

### Network Configuration

The gateway connects to the external network `suite_suite` created by Robin MTA's docker-compose. This allows:
- Gateway to connect to `suite-postgres` container
- Gateway to proxy requests to `suite-robin` container
- All services to communicate on the same network

## Verifying the Setup

### 1. Check All Services

```bash
# Check Robin MTA suite
cd ~/development/workspace/open-source/transilvlad-robin/.suite
docker-compose ps

# Check Gateway
cd ~/development/workspace/open-source/robin-ui/robin-gateway/docker
docker-compose ps

# Should see:
# - suite-postgres (healthy)
# - suite-robin (healthy)
# - robin-redis (healthy)
# - robin-gateway (healthy)
```

### 2. Test Gateway Endpoints

```bash
# Health check (aggregated)
curl http://localhost:8080/api/v1/health/aggregate

# Login (get JWT token)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Expected response:
# {
#   "accessToken": "eyJhbGc...",
#   "refreshToken": "eyJhbGc...",
#   "tokenType": "Bearer",
#   "expiresIn": 1800
# }

# Use the token
TOKEN="<access_token_from_above>"

# List domains
curl http://localhost:8080/api/v1/domains \
  -H "Authorization: Bearer $TOKEN"
```

### 3. Test Database Connection

```bash
# Connect to PostgreSQL
docker exec -it suite-postgres psql -U robin -d robin

# List tables (should include gateway tables)
\dt

# Check gateway users table
SELECT * FROM users;

# Exit
\q
```

## Troubleshooting

### Gateway Cannot Connect to PostgreSQL

```bash
# Check if suite-postgres is running
docker ps | grep suite-postgres

# Check if suite_suite network exists
docker network ls | grep suite_suite

# Check network connectivity
docker exec robin-gateway ping -c 3 suite-postgres

# Check PostgreSQL logs
docker logs suite-postgres
```

### Gateway Cannot Connect to Robin MTA

```bash
# Verify Robin MTA is running and healthy
curl http://localhost:28080/health
curl http://localhost:28090/health

# Check if Robin container is accessible from gateway
docker exec robin-gateway curl -f http://suite-robin:8080/health
```

### Redis Connection Issues

```bash
# Check if Redis is running
docker ps | grep robin-redis

# Test Redis connectivity
docker exec robin-redis redis-cli ping
# Expected: PONG

# Check gateway can reach Redis
docker exec robin-gateway nc -zv redis 6379
```

### Gateway Logs

```bash
# View gateway logs
docker logs robin-gateway -f

# View last 100 lines
docker logs robin-gateway --tail 100

# View errors only
docker logs robin-gateway 2>&1 | grep ERROR
```

## Stopping Services

```bash
# Stop gateway only
cd ~/development/workspace/open-source/robin-ui/robin-gateway/docker
docker-compose down

# Stop gateway and remove volumes
docker-compose down -v

# Stop entire Robin MTA suite (includes PostgreSQL)
cd ~/development/workspace/open-source/transilvlad-robin/.suite
docker-compose down

# Stop everything and remove all volumes
docker-compose down -v
```

## Production Considerations

### Security

1. **Change JWT Secret**: Replace the default JWT secret with a secure random value (at least 64 bytes for HS512).

```bash
# Generate a secure secret
openssl rand -base64 64
```

2. **Use Strong Database Passwords**: Change the default PostgreSQL password.

3. **Enable HTTPS**: Configure SSL/TLS certificates for the gateway.

4. **Network Isolation**: Use separate networks for production environments.

### Environment Files

Create a `.env` file for production:

```bash
SPRING_PROFILES_ACTIVE=prod
DB_HOST=suite-postgres
DB_PORT=5432
DB_NAME=robin
DB_USER=robin
DB_PASSWORD=your-secure-password-here
REDIS_HOST=redis
REDIS_PORT=6379
ROBIN_CLIENT_URL=http://suite-robin:8090
ROBIN_SERVICE_URL=http://suite-robin:8080
JWT_SECRET=your-secure-jwt-secret-at-least-64-bytes-long-here
```

Then update docker-compose.yml:

```yaml
gateway:
  env_file:
    - .env
```

## API Documentation

Once the gateway is running, access the Swagger UI:

```
http://localhost:8080/swagger-ui.html
```

## Next Steps

1. Start Robin UI Angular application (see robin-ui/README.md)
2. Configure UI to connect to gateway at `http://localhost:8080`
3. Login via UI and test the full stack
