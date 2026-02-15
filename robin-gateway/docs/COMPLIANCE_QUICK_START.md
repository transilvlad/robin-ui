# Robin Gateway Compliance - Quick Start Guide

**Target Audience**: Developers working on Robin Gateway
**Purpose**: Quick reference for running compliance checks and understanding standards

---

## TL;DR - Run All Checks

```bash
cd robin-gateway

# Install Maven (if not installed)
brew install maven

# Run full compliance suite
mvn clean test jacoco:report jacoco:check \
    checkstyle:check pmd:check spotbugs:check \
    org.owasp:dependency-check-maven:check

# View coverage report
open target/site/jacoco/index.html
```

---

## Daily Development Workflow

### Before Committing Code

```bash
# 1. Run tests with coverage
mvn test jacoco:report

# 2. Check coverage meets threshold (60%)
mvn jacoco:check

# 3. Run quick style check
mvn checkstyle:check

# 4. Fix any violations before committing
```

### Before Creating Pull Request

```bash
# Run full compliance suite
mvn clean test jacoco:report jacoco:check \
    checkstyle:check pmd:check spotbugs:check

# If all pass → Create PR
# If failures → Fix issues first
```

---

## Common Issues & Quick Fixes

### Issue 1: Test Coverage Below 60%

**Symptom:**
```
[ERROR] Rule violated for package com.robin.gateway.service:
lines covered ratio is 0.45, but expected minimum is 0.60
```

**Fix:**
Write more unit tests for the affected package. Focus on service layer tests first.

**Example Test Template:**
```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock
    private MyRepository repository;

    @InjectMocks
    private MyService service;

    @Test
    void testMethod_shouldReturnExpectedResult() {
        // Arrange
        when(repository.findById(any())).thenReturn(Mono.just(entity));

        // Act
        StepVerifier.create(service.getById("123"))
            .expectNextMatches(result -> result.getId().equals("123"))
            .verifyComplete();

        // Assert
        verify(repository).findById("123");
    }
}
```

---

### Issue 2: Checkstyle Violations

**Symptom:**
```
[ERROR] src/main/java/MyClass.java:42: Line is longer than 120 characters
[ERROR] src/main/java/MyClass.java:55: Missing Javadoc comment
```

**Fix:**

**Long Lines:**
```java
// BAD
String veryLongString = "This is a very long string that exceeds 120 characters and violates the Checkstyle rule for line length limits";

// GOOD
String veryLongString = "This is a very long string that exceeds 120 characters"
    + " and violates the Checkstyle rule for line length limits";
```

**Missing Javadoc:**
```java
// BAD
public User createUser(User user) {

// GOOD
/**
 * Creates a new user in the system.
 *
 * @param user the user to create
 * @return the created user
 */
public User createUser(User user) {
```

---

### Issue 3: Missing @Valid Annotation

**Symptom:**
Input validation not working, invalid data reaches service layer.

**Fix:**
```java
// BAD
@PostMapping
public Mono<User> createUser(@RequestBody User user) {

// GOOD
@PostMapping
public Mono<User> createUser(@Valid @RequestBody User user) {
```

Also ensure DTO has validation annotations:
```java
public class User {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}
```

---

### Issue 4: PMD Warnings About Unused Code

**Symptom:**
```
[WARNING] UnusedPrivateMethod: Private method 'helperMethod' is never used
```

**Fix:**
Remove unused code (don't leave dead code in codebase):
```java
// Delete unused methods, fields, imports
```

If method is used reflectively or will be used soon, suppress with comment:
```java
@SuppressWarnings("PMD.UnusedPrivateMethod") // Used by reflection in Spring
private void configureSettings() {
```

---

### Issue 5: SpotBugs Security Warnings

**Symptom:**
```
[ERROR] Medium: Hard coded constant password [HARD_CODE_PASSWORD]
```

**Fix:**
Never hardcode secrets:
```java
// BAD
String password = "admin123";

// GOOD
@Value("${admin.password}")
private String password;
```

Or use environment variables:
```java
String password = System.getenv("ADMIN_PASSWORD");
```

---

### Issue 6: OWASP Dependency Vulnerabilities

**Symptom:**
```
[ERROR] CVE-2023-12345 (CVSS 8.5) found in dependency foo-bar-1.2.3
```

**Fix:**

**Option 1: Update Dependency**
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>foo-bar</artifactId>
    <version>1.2.4</version> <!-- Updated version -->
</dependency>
```

**Option 2: Suppress (if not exploitable in our context)**
Add to `dependency-check-suppressions.xml`:
```xml
<suppress>
    <notes><![CDATA[
    Justification: This vulnerability only affects Windows servers, we run Linux only.
    Risk: Low - not exploitable in our deployment.
    Mitigation: Monitoring upstream for fixes.
    ]]></notes>
    <cve>CVE-2023-12345</cve>
</suppress>
```

---

## Code Standards Cheat Sheet

### Controller Best Practices

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor // Constructor injection (Lombok)
public class UserController {

    private final UserService userService; // Constructor injected

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')") // Authorization
    public Mono<ResponseEntity<User>> createUser(
        @Valid @RequestBody User user // Validation
    ) {
        return userService.createUser(user)
            .map(ResponseEntity::ok)
            .onErrorResume(e -> {
                log.error("Error creating user", e); // Proper logging
                return Mono.just(ResponseEntity.internalServerError().build());
            });
    }
}
```

### Service Best Practices

```java
@Service
@RequiredArgsConstructor
@Slf4j // Lombok logging
public class UserService {

    private final UserRepository repository;

    /**
     * Creates a new user.
     *
     * @param user the user to create
     * @return the created user
     */
    public Mono<User> createUser(User user) {
        log.info("Creating user: {}", user.getUsername());

        return repository.save(user)
            .doOnSuccess(u -> log.info("User created: {}", u.getId()))
            .doOnError(e -> log.error("Failed to create user", e));
    }
}
```

### Test Best Practices

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @InjectMocks
    private UserService service;

    @Test
    void createUser_withValidData_shouldSaveUser() {
        // Arrange
        User user = new User();
        user.setUsername("john.doe");
        when(repository.save(any())).thenReturn(Mono.just(user));

        // Act
        StepVerifier.create(service.createUser(user))
            .expectNextMatches(u -> u.getUsername().equals("john.doe"))
            .verifyComplete();

        // Assert
        verify(repository).save(any());
    }

    @Test
    void createUser_withRepositoryError_shouldPropagateError() {
        // Arrange
        when(repository.save(any())).thenReturn(Mono.error(new RuntimeException()));

        // Act & Assert
        StepVerifier.create(service.createUser(new User()))
            .expectError(RuntimeException.class)
            .verify();
    }
}
```

---

## Coverage Report Interpretation

After running `mvn jacoco:report`, open `target/site/jacoco/index.html`:

### Understanding Metrics

| Metric | Good | Acceptable | Poor |
|--------|------|------------|------|
| Line Coverage | ≥80% | 60-79% | <60% |
| Branch Coverage | ≥70% | 50-69% | <50% |
| Complexity | Low | Medium | High |

### What to Test First

**Priority 1: Services** (business logic)
- Target: 70-80% coverage
- Focus: Happy path + error cases

**Priority 2: Controllers** (API endpoints)
- Target: 80%+ coverage
- Focus: Request validation, authorization, response codes

**Priority 3: Repositories** (data access)
- Target: 50%+ coverage
- Focus: Integration tests with TestContainers

**Skip:**
- DTOs (data classes, no logic)
- Config classes (Spring configuration)
- Entities (JPA entities)

---

## Architecture Rules (ArchUnit)

The following rules are enforced by `ArchitectureTest.java`:

### ✅ DO: Constructor Injection
```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final MyRepository repository; // Constructor injected by Lombok
}
```

### ❌ DON'T: Field Injection
```java
@Service
public class MyService {
    @Autowired // FORBIDDEN
    private MyRepository repository;
}
```

### ✅ DO: Package Structure
```
com.robin.gateway.
├── controller/     # @RestController classes
├── service/        # @Service classes
├── repository/     # @Repository classes
└── model/          # Entities, DTOs
```

### ❌ DON'T: Wrong Packages
```java
// BAD: Service in controller package
package com.robin.gateway.controller;

@Service // Should be in service package
public class UserService {
```

### ✅ DO: Respect Layers
```
Controller → Service → Repository
```

### ❌ DON'T: Break Layers
```java
// BAD: Service depends on Controller
@Service
public class UserService {
    private final UserController controller; // FORBIDDEN
}
```

---

## Security Checklist

### Before Deployment

- [ ] No hardcoded passwords/secrets in code
- [ ] All `@RequestBody` have `@Valid`
- [ ] CORS origins configured for production (no `http://localhost`)
- [ ] JWT secret is ≥64 characters random string
- [ ] Encryption key is 256-bit AES key
- [ ] Rate limiting enabled and tested
- [ ] HTTPS enforced in production
- [ ] Security headers configured
- [ ] OWASP dependency check passes (no critical CVEs)

### Code Review Checklist

- [ ] No `System.out.println()` or `printStackTrace()`
- [ ] Proper logging with SLF4J
- [ ] No SQL injection (use parameterized queries)
- [ ] Input validation on all endpoints
- [ ] Error messages don't leak sensitive info
- [ ] Passwords never logged (even in debug)

---

## Useful Commands

### View Reports

```bash
# Coverage report
open target/site/jacoco/index.html

# Checkstyle report
cat target/checkstyle-result.xml | less

# PMD report
cat target/pmd.xml | less

# SpotBugs report
cat target/spotbugsXml.xml | less
```

### Find Specific Issues

```bash
# Find all TODOs
grep -rn "TODO" src/main/java/

# Find System.out usage
grep -rn "System\.out\." src/main/java/

# Find missing @Valid
grep -rn "@RequestBody" src/main/java/ | grep -v "@Valid"

# Find @SuppressWarnings (should be rare)
grep -rn "@SuppressWarnings" src/main/java/

# Count test files vs production files
find src/main/java -name "*.java" | wc -l
find src/test/java -name "*Test.java" | wc -l
```

### Maven Tips

```bash
# Skip tests (for quick builds)
mvn clean install -DskipTests

# Run single test
mvn test -Dtest=UserServiceTest

# Run tests matching pattern
mvn test -Dtest=*IntegrationTest

# Clean everything
mvn clean

# Update dependencies
mvn versions:display-dependency-updates
```

---

## Getting Help

### Documentation

- **Security**: `docs/SECURITY.md`
- **Implementation Summary**: `docs/COMPLIANCE_IMPLEMENTATION_SUMMARY.md`
- **Full Plan**: Root directory `ROBIN_GATEWAY_COMPLIANCE_PLAN.md`

### Common Questions

**Q: Why do I need 60% test coverage?**
A: Industry standard for production code. Ensures critical paths are tested.

**Q: Can I suppress Checkstyle/PMD warnings?**
A: Only if justified. Add comment explaining why.

**Q: Do I need to test DTOs/entities?**
A: No. Focus on services and controllers (business logic).

**Q: Tests are failing in CI but pass locally. Why?**
A: Likely TestContainers issue. Ensure Docker is available in CI.

**Q: How do I add a new dependency?**
A: Add to `pom.xml`, then run `mvn org.owasp:dependency-check-maven:check` to verify no CVEs.

---

## CI/CD Integration

### GitHub Actions

On every push to `main`/`develop` or PR to `main`, the following runs:

1. Build and test
2. Generate coverage report
3. Check coverage threshold (fails if <60%)
4. Run Checkstyle (fails on violations)
5. Run PMD (fails on errors)
6. Run SpotBugs (fails on high-priority bugs)
7. Run OWASP check (fails if CVSS ≥7)
8. Upload reports to Codecov
9. Archive reports as artifacts

**View results**: GitHub Actions tab in repository

**Download reports**: Click on workflow run → Artifacts

---

## Summary: What You Need to Know

1. **Always run tests before committing**: `mvn test`
2. **Check coverage**: `mvn jacoco:report` → target 60%+
3. **Run style checks**: `mvn checkstyle:check`
4. **No System.out or printStackTrace**: Use `log.info()`, `log.error()`
5. **Use `@Valid` on all `@RequestBody`**: Input validation is critical
6. **Constructor injection only**: No `@Autowired` on fields
7. **Write Javadoc for public methods**: Checkstyle enforces this
8. **Run full suite before PR**: `mvn clean test jacoco:check checkstyle:check pmd:check spotbugs:check`

---

**Questions?** Check `docs/SECURITY.md` or `docs/COMPLIANCE_IMPLEMENTATION_SUMMARY.md`

**Last Updated**: 2026-02-06
