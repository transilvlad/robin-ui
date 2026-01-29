# Build stage
FROM amazoncorretto:21-alpine AS build
WORKDIR /app

# Install Maven (since we don't have the wrapper in the repo yet)
RUN apk add --no-cache maven

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build the application (skip test compilation and execution)
RUN mvn clean package -Dmaven.test.skip=true

# Run stage
FROM amazoncorretto:21-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy the built jar
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
