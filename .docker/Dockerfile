# Dockerfile for Robin UI Angular application
# Multi-stage build for production

# ============================================================================
# STAGE: build
# Build the Angular application
# ============================================================================
FROM node:20-alpine AS build

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies (use ci if lock file exists, otherwise install)
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi

# Copy source files
COPY . .

# Build for production
RUN npm run build -- --configuration=production

# ============================================================================
# STAGE: production
# Serve with nginx
# ============================================================================
FROM nginx:alpine AS production

# Environment variables for API endpoints (can be overridden at runtime)
ENV API_URL=http://localhost:8090
ENV SERVICE_API_URL=http://localhost:8080

# Copy nginx template (nginx will run envsubst automatically)
COPY .docker/nginx.conf.template /etc/nginx/templates/default.conf.template

# Copy built application and fix permissions
COPY --from=build /app/dist/robin-ui /usr/share/nginx/html
RUN chmod -R 755 /usr/share/nginx/html

# Expose port
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
