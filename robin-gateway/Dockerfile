# Build stage
FROM amazoncorretto:21-alpine AS build
WORKDIR /app

# Install Maven (since we don't have the wrapper in the repo yet)
RUN apk add --no-cache maven

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (this layer will be cached unless pom.xml changes)
# Note: dependency:resolve-plugins is needed to download all plugins
RUN mvn dependency:go-offline dependency:resolve-plugins -B

# Copy source code
COPY src ./src

# Build the application (skip tests)
RUN mvn clean package -DskipTests

# Run stage
FROM amazoncorretto:21-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built jar
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM settings optimized for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
