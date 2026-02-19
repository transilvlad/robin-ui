# Robin Gateway - Troubleshooting Guide

Common issues and solutions when working with the compliance framework.

---

## 🔧 Build Issues

### Maven Not Found

**Error:**
```
mvn: command not found
```

**Solution:**
```bash
# macOS
brew install maven

# Verify installation
mvn --version

# Should output: Apache Maven 3.x.x
```

---

### Build Fails with "Cannot resolve dependencies"

**Error:**
```
[ERROR] Failed to execute goal on project gateway:
Could not resolve dependencies for project com.robin:gateway:jar:1.0.0-SNAPSHOT
```

**Solution:**
```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Rebuild
mvn clean install

# If still fails, check internet connection
# Maven needs to download dependencies
```

---

### Tests Fail with "TestContainers cannot start"

**Error:**
```
Could not start container
org.testcontainers.containers.ContainerLaunchException
```

**Solution:**
```bash
# Check Docker is running
docker ps

# If not running, start Docker Desktop

# Verify Docker works
docker run hello-world

# Retry tests
mvn test
```

---

### Out of Memory During Build

**Error:**
```
java.lang.OutOfMemoryError: Java heap space
```

**Solution:**
```bash
# Increase Maven memory
export MAVEN_OPTS="-Xmx2048m -XX:MaxPermSize=512m"

# Rebuild
mvn clean test

# Make permanent (add to ~/.zshrc or ~/.bash_profile)
echo 'export MAVEN_OPTS="-Xmx2048m -XX:MaxPermSize=512m"' >> ~/.zshrc
```

---

## 🧪 Test Issues

### Test Coverage Below Threshold

**Error:**
```
[ERROR] Rule violated for package com.robin.gateway.service:
lines covered ratio is 0.45, but expected minimum is 0.60
```

**Solution:**

**Option 1: Write More Tests** (Recommended)
```bash
# Identify uncovered code
mvn jacoco:report
open target/site/jacoco/index.html

# Focus on packages with low coverage
# Write unit tests for service layer first
```

**Option 2: Temporarily Lower Threshold** (Not Recommended)
```xml
<!-- In pom.xml, jacoco-maven-plugin configuration -->
<limit>
    <counter>LINE</counter>
    <value>COVEREDRATIO</value>
    <minimum>0.50</minimum>  <!-- Lowered from 0.60 -->
</limit>
```

**Note:** Only lower threshold temporarily during initial implementation. Always work towards 60%+.

---

### Reactive Tests Hanging

**Error:**
```
Test timeout after 60 seconds
```

**Solution:**
```java
// Common issue: Forgot to subscribe to Mono/Flux

// ❌ BAD - Test will hang
Mono<User> user = userService.getUser("john");
// Nothing happens - Mono is lazy

// ✅ GOOD - Use StepVerifier
StepVerifier.create(userService.getUser("john"))
    .expectNextMatches(u -> u.getUsername().equals("john"))
    .verifyComplete();

// ✅ ALSO GOOD - Block for tests
User user = userService.getUser("john").block();
assertThat(user.getUsername()).isEqualTo("john");
```

---

### MockMvc Not Working

**Error:**
```
java.lang.IllegalStateException: WebApplicationContext is required
```

**Solution:**
```java
// For WebFlux (Robin Gateway), use WebTestClient not MockMvc

// ❌ BAD - MockMvc is for Spring MVC
@Autowired
private MockMvc mockMvc;

// ✅ GOOD - WebTestClient is for WebFlux
@Autowired
private WebTestClient webTestClient;

@Test
void testEndpoint() {
    webTestClient.get()
        .uri("/api/v1/users")
        .exchange()
        .expectStatus().isOk();
}
```

---

## 📋 Code Style Issues

### Checkstyle: Line Too Long

**Error:**
```
[ERROR] Line is longer than 120 characters
```

**Solution:**
```java
// ❌ BAD
String message = "This is a very long string that exceeds the 120 character limit and violates the Checkstyle rule";

// ✅ GOOD - Split string
String message = "This is a very long string that exceeds the 120 character limit"
    + " and violates the Checkstyle rule";

// ✅ GOOD - Method chaining (builder pattern)
User user = User.builder()
    .username("john.doe")
    .email("john.doe@example.com")
    .roles(List.of("ROLE_USER", "ROLE_ADMIN"))
    .build();

// ✅ GOOD - Break parameters
public Mono<User> updateUser(
    String username,
    String email,
    List<String> roles
) {
```

---

### Checkstyle: Missing Javadoc

**Error:**
```
[ERROR] Missing a Javadoc comment at line 42
```

**Solution:**
```java
// ❌ BAD - No Javadoc for public method
public User createUser(User user) {
    return repository.save(user);
}

// ✅ GOOD - Complete Javadoc
/**
 * Creates a new user in the system.
 *
 * @param user the user to create (must not be null)
 * @return the created user with generated ID
 * @throws IllegalArgumentException if user is null
 * @throws DuplicateUserException if username already exists
 */
public User createUser(User user) {
    return repository.save(user);
}
```

---

### Checkstyle: Wrong Indentation

**Error:**
```
[ERROR] 'method def' has incorrect indentation level 6, expected 4
```

**Solution:**
```java
// ❌ BAD - Using tabs or wrong spaces
public class MyClass {
      public void myMethod() {  // 6 spaces
          // ...
      }
}

// ✅ GOOD - 4 spaces
public class MyClass {
    public void myMethod() {  // 4 spaces
        // ...
    }
}

// Configure your IDE:
// IntelliJ: Settings > Editor > Code Style > Java > Tab size: 4, Indent: 4
// VS Code: Settings > Editor: Tab Size: 4, Insert Spaces: true
```

---

## 🔒 Security Issues

### OWASP: Critical Vulnerability Found

**Error:**
```
[ERROR] CVE-2023-12345 (CVSS 9.5) found in dependency foo-bar-1.2.3
```

**Solution:**

**Option 1: Update Dependency** (Best)
```xml
<!-- In pom.xml, update version -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>foo-bar</artifactId>
    <version>1.2.4</version>  <!-- Updated from 1.2.3 -->
</dependency>
```

**Option 2: Suppress with Justification** (If update not available)
```xml
<!-- In dependency-check-suppressions.xml -->
<suppress>
    <notes><![CDATA[
    CVE-2023-12345: SQL injection in foo-bar-1.2.3

    Justification: This vulnerability only affects the 'query' method,
    which we don't use in our codebase. We only use the 'execute' method.

    Risk: Low - Affected code path is not used.

    Mitigation: Monitoring upstream for patch release.
    Reviewed by: [Name]
    Date: 2026-02-06
    Next review: 2026-03-06
    ]]></notes>
    <cve>CVE-2023-12345</cve>
</suppress>
```

**Option 3: Exclude Transitive Dependency**
```xml
<!-- If vulnerability is in transitive dependency -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>parent-lib</artifactId>
    <version>2.0.0</version>
    <exclusions>
        <exclusion>
            <groupId>com.example</groupId>
            <artifactId>vulnerable-lib</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

---

### JWT Token Validation Fails

**Error:**
```
io.jsonwebtoken.SignatureException: JWT signature does not match
```

**Solution:**
```bash
# Check JWT secret is configured
echo $JWT_SECRET

# If empty, set it
export JWT_SECRET="your-secret-key-at-least-64-characters-long-for-hs512"

# Verify in application.yml
grep -A 2 "jwt:" src/main/resources/application.yml

# Expected:
# jwt:
#   secret: ${JWT_SECRET}
```

---

### CORS Error in Browser

**Error:**
```
Access to XMLHttpRequest has been blocked by CORS policy:
No 'Access-Control-Allow-Origin' header is present
```

**Solution:**
```bash
# Check CORS configuration
grep -A 5 "cors:" src/main/resources/application.yml

# For development, ensure localhost is allowed
cors:
  allowed-origins: http://localhost:4200,http://localhost:8080

# For production, set environment variable
export CORS_ALLOWED_ORIGINS=https://robin-ui.production.com

# Restart gateway
```

---

## 🏗️ Architecture Issues

### Field Injection Violation

**Error:**
```
[ArchUnit] Field <UserController.userService> should not be annotated with @Autowired
```

**Solution:**
```java
// ❌ BAD - Field injection
@RestController
public class UserController {
    @Autowired  // FORBIDDEN
    private UserService userService;
}

// ✅ GOOD - Constructor injection with Lombok
@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;  // Constructor injected by Lombok
}

// ✅ ALSO GOOD - Explicit constructor
@RestController
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
}
```

---

### Circular Dependency

**Error:**
```
The dependencies of some of the beans in the application context form a cycle:
   userService -> passwordService -> userService
```

**Solution:**
```java
// ❌ BAD - Circular dependency
@Service
public class UserService {
    private final PasswordService passwordService;  // Depends on PasswordService
}

@Service
public class PasswordService {
    private final UserService userService;  // Depends on UserService - CYCLE!
}

// ✅ GOOD - Use @Lazy or refactor
@Service
public class UserService {
    private final PasswordService passwordService;
}

@Service
public class PasswordService {
    @Lazy  // Break cycle by lazy-loading
    private final UserService userService;
}

// ✅ BETTER - Refactor to remove dependency
// Create a third service that both depend on
@Service
public class UserService {
    private final UserRepository userRepository;
}

@Service
public class PasswordService {
    private final PasswordEncoder passwordEncoder;
}

@Service
public class UserPasswordService {
    private final UserService userService;
    private final PasswordService passwordService;

    public void changePassword(String username, String newPassword) {
        User user = userService.findByUsername(username);
        String encoded = passwordService.encode(newPassword);
        user.setPassword(encoded);
        userService.save(user);
    }
}
```

---

## 🚀 Performance Issues

### Build Taking Too Long

**Issue:** Maven build takes >10 minutes

**Solution:**
```bash
# Skip tests during development
mvn clean install -DskipTests

# Use parallel builds
mvn clean install -T 4  # Use 4 threads

# Skip OWASP check (slow) during development
mvn clean install -Dowasp.skip=true

# Combine for fastest build
mvn clean install -DskipTests -Dowasp.skip=true -T 4

# Note: Always run full build before committing!
```

---

### Tests Running Slowly

**Issue:** Test suite takes >5 minutes

**Solution:**
```bash
# Run only fast unit tests (not integration)
mvn test -Dgroups=unit

# Run single test
mvn test -Dtest=UserServiceTest

# Run tests matching pattern
mvn test -Dtest=*ServiceTest

# Profile tests to find slow ones
mvn test -Dsurefire.reportNameSuffix=_${timestamp}

# In code: Optimize slow tests
// ❌ SLOW - Unnecessary sleep
Thread.sleep(5000);

// ✅ FAST - Use virtual time
StepVerifier.withVirtualTime(() -> service.delayedOperation())
    .thenAwait(Duration.ofSeconds(5))
    .expectNext(result)
    .verifyComplete();
```

---

## 📊 Report Issues

### JaCoCo Report Not Generated

**Issue:** `target/site/jacoco/index.html` doesn't exist

**Solution:**
```bash
# Ensure tests run first
mvn clean test

# Then generate report
mvn jacoco:report

# Or combine
mvn clean test jacoco:report

# Check if report exists
ls -la target/site/jacoco/

# Open report
open target/site/jacoco/index.html
```

---

### Checkstyle Report Empty

**Issue:** `target/checkstyle-result.xml` is empty

**Solution:**
```bash
# Run checkstyle explicitly
mvn checkstyle:check

# If no violations, report may be empty (this is good!)
# Generate HTML report for better visualization
mvn checkstyle:checkstyle

# View HTML report
open target/site/checkstyle.html
```

---

## 🔄 Git Issues

### Pre-commit Hook Fails

**Error:**
```
[ERROR] Tests failed
[pre-commit] Commit aborted
```

**Solution:**
```bash
# Fix the failing tests
mvn test

# Once tests pass, try commit again
git commit -m "your message"

# Emergency only: Skip hooks (NOT RECOMMENDED)
git commit --no-verify -m "emergency fix"

# Note: CI will still run checks, so fix is still needed
```

---

### Large Files Blocked

**Error:**
```
[ERROR] File too large (>500KB)
```

**Solution:**
```bash
# Check file size
ls -lh [filename]

# Don't commit large files:
# - JAR files (build artifacts)
# - Large test data
# - Binary files
# - Database dumps

# If you must commit, add to .gitignore or use Git LFS
echo "large-file.zip" >> .gitignore

# Or use Git LFS
git lfs install
git lfs track "*.jar"
git add .gitattributes
```

---

## 💻 IDE Issues

### IntelliJ: Lombok Not Working

**Error:**
```
Cannot resolve symbol 'builder'
```

**Solution:**
1. Install Lombok plugin:
   - Settings → Plugins → Search "Lombok" → Install
2. Enable annotation processing:
   - Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   - Check "Enable annotation processing"
3. Rebuild project: Build → Rebuild Project

---

### VS Code: Java Extension Errors

**Error:**
```
Classpath is incomplete. Only syntax errors will be reported
```

**Solution:**
1. Install Java Extension Pack
2. Open Command Palette (Cmd+Shift+P)
3. Run: "Java: Clean Java Language Server Workspace"
4. Reload window
5. Run: `mvn clean install` to download dependencies

---

## 🆘 Getting More Help

### Logs to Check

```bash
# Maven debug logs
mvn clean test -X > debug.log 2>&1

# Application logs (when running)
tail -f logs/application.log

# Docker logs (if using TestContainers)
docker logs [container-id]

# System logs
# macOS
tail -f /var/log/system.log
```

---

### Information to Provide

When asking for help, include:

1. **What you're trying to do**
2. **Full error message** (copy-paste, not screenshot)
3. **What you've tried**
4. **Environment info**:
   ```bash
   mvn --version
   java -version
   echo $JAVA_HOME
   docker --version
   ```
5. **Relevant code snippets**

---

### Resources

- **Documentation**: `docs/` directory
- **Security Questions**: `docs/SECURITY.md`
- **Code Standards**: `docs/COMPLIANCE_QUICK_START.md`
- **Gap Tracking**: `docs/GAP_TRACKING.md`
- **Developer Checklist**: `docs/DEVELOPER_CHECKLIST.md`

---

### Common Patterns

**"It works locally but fails in CI":**
- Environment variables not set in CI
- Docker not available in CI
- Different Java version in CI

**"Tests pass individually but fail together":**
- Shared mutable state
- Test order dependency
- Port conflicts

**"Coverage drops after adding code":**
- New code not tested
- Test not covering all branches
- Need to write more tests

---

**Pro Tip:** Most issues can be solved by:
1. Reading the error message carefully
2. Checking the documentation
3. Running with `-X` flag for debug output
4. Googling the exact error message

---

**Last Updated**: 2026-02-06
**Version**: 1.0
