# Java 21 Requirement

This project requires **Java 21** to build and run.

## Building with Java 21

### Option 1: Set JAVA_HOME (Recommended for local development)

```bash
# Set JAVA_HOME to Java 21 before running Maven
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Verify Maven is using Java 21
mvn -v

# Build the project
mvn clean install
```

### Option 2: Add to your shell profile

Add this to your `~/.zshrc` or `~/.bashrc`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### Option 3: Use Docker (No Java 21 installation required)

```bash
# Build using Docker
docker build -t robin-gateway .

# Or use docker-compose
docker-compose build gateway
```

## Why Java 21?

- The project uses Spring Boot 3.2.2 which requires Java 17+ (Java 21 is LTS)
- Lombok 1.18.32+ works best with Java 21
- All production deployments use Java 21 (Amazon Corretto 21)

## Troubleshooting

### Maven uses Java 25 instead of Java 21

**Problem**: `mvn -v` shows "Java version: 25.0.2"

**Solution**: Set JAVA_HOME before running Maven:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### Lombok compilation errors

**Problem**: Errors like `cannot find symbol: method getApiToken()`

**Cause**: Lombok annotation processing fails with Java 25

**Solution**: Use Java 21 as described above

## Verification

After setting JAVA_HOME, verify Maven uses Java 21:

```bash
mvn -v
# Should show: Java version: 21.0.x, vendor: Amazon.com Inc.
```

Then compile:

```bash
mvn clean compile
# Should show: BUILD SUCCESS
```
