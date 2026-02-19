# Robin Gateway - Docker Deployment Guide

## Quick Start

### Prerequisites
- Docker 20.10+
- Docker Compose 2.0+

### 1. Configuration

Copy the example environment file and configure it:

```bash
cp .env.example .env
```

**Required Environment Variables:**
- `JWT_SECRET`: Generate with `openssl rand -base64 32`
- `POSTGRES_PASSWORD`: Strong database password

**Optional Variables:**
- `ENCRYPTION_SECRET_KEY`: For encrypting sensitive data (generate with `openssl rand -base64 32`)
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`: For Route53 integration

### 2. Build and Run

```bash
# Build and start all services
docker-compose up -d

# View logs
docker-compose logs -f gateway

# Check service health
docker-compose ps
```

### 3. Verify Deployment

```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# Check API documentation
open http://localhost:8080/swagger-ui.html
```

## Architecture

The Docker Compose setup includes:

- **robin-gateway**: Spring Boot API Gateway (port 8080)
- **postgres**: PostgreSQL 16 database (port 5432)
- **redis**: Redis 7 cache (port 6379)

### Network

All services run on the `robin-network` bridge network and can communicate using service names.

### Volumes

- `postgres_data`: Persistent PostgreSQL data

## Development

### Build Only

```bash
docker build -t robin-gateway:latest .
```

### Run with Custom Settings

```bash
docker run -d \
  -p 8080:8080 \
  -e JWT_SECRET=your_secret \
  -e POSTGRES_PASSWORD=your_password \
  -e SPRING_PROFILES_ACTIVE=prod \
  robin-gateway:latest
```

### Access Container Shell

```bash
docker-compose exec gateway sh
```

### View Application Logs

```bash
# Follow logs
docker-compose logs -f gateway

# Last 100 lines
docker-compose logs --tail=100 gateway
```

## Optimization Features

### Dockerfile Improvements

1. **Layer Caching**: Dependencies are downloaded separately from source code
2. **Multi-stage Build**: Build artifacts are not included in final image
3. **Non-root User**: Application runs as `spring:spring` user
4. **Health Check**: Automatic health monitoring via Spring Actuator
5. **Container-optimized JVM**: Uses `UseContainerSupport` and `MaxRAMPercentage`

### Performance Tuning

JVM settings are optimized for containers:
- `XX:+UseContainerSupport`: Detect container memory limits
- `XX:MaxRAMPercentage=75.0`: Use 75% of available memory
- `XX:+UseG1GC`: G1 garbage collector for better latency

## Troubleshooting

### Container won't start

Check logs:
```bash
docker-compose logs gateway
```

Common issues:
- Missing `JWT_SECRET` environment variable
- Database connection failure (check postgres service health)
- Port 8080 already in use

### Database Connection Issues

Verify PostgreSQL is healthy:
```bash
docker-compose ps postgres
docker-compose exec postgres pg_isready -U robin
```

### Redis Connection Issues

Verify Redis is healthy:
```bash
docker-compose ps redis
docker-compose exec redis redis-cli ping
```

### Performance Issues

Adjust JVM memory settings:
```yaml
environment:
  JAVA_OPTS: "-XX:MaxRAMPercentage=50.0 -XX:+UseG1GC"
```

## Production Deployment

### Security Considerations

1. **Change default passwords**: Update all passwords in `.env`
2. **Secure secrets**: Use Docker secrets or external secret management
3. **Enable TLS**: Use a reverse proxy (nginx, traefik) for HTTPS
4. **Restrict ports**: Only expose necessary ports
5. **Update images**: Regularly update base images and dependencies

### Example with Secrets

```yaml
services:
  gateway:
    environment:
      JWT_SECRET_FILE: /run/secrets/jwt_secret
    secrets:
      - jwt_secret

secrets:
  jwt_secret:
    external: true
```

### Resource Limits

Add resource limits to prevent excessive resource usage:

```yaml
services:
  gateway:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
```

## Monitoring

### Health Endpoints

- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Prometheus: `http://localhost:8080/actuator/prometheus`

### Integration with Monitoring Stack

Example Prometheus configuration:

```yaml
scrape_configs:
  - job_name: 'robin-gateway'
    static_configs:
      - targets: ['gateway:8080']
    metrics_path: '/actuator/prometheus'
```

## Backup and Restore

### Database Backup

```bash
docker-compose exec postgres pg_dump -U robin robin > backup.sql
```

### Database Restore

```bash
docker-compose exec -T postgres psql -U robin robin < backup.sql
```

## Cleanup

### Stop Services

```bash
docker-compose down
```

### Remove Volumes (WARNING: deletes data)

```bash
docker-compose down -v
```

### Remove Images

```bash
docker rmi robin-gateway:latest
```
