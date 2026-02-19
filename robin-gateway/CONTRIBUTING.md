# Contributing to Robin Gateway

Thank you for your interest in contributing to Robin Gateway! This document provides guidelines and instructions for contributing to the project.

---

## 📋 Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [Development Workflow](#development-workflow)
4. [Coding Standards](#coding-standards)
5. [Testing Requirements](#testing-requirements)
6. [Commit Guidelines](#commit-guidelines)
7. [Pull Request Process](#pull-request-process)
8. [Documentation](#documentation)
9. [Security](#security)
10. [Getting Help](#getting-help)

---

## Code of Conduct

### Our Pledge

We are committed to providing a welcoming and inclusive environment for all contributors.

### Expected Behavior

- Use welcoming and inclusive language
- Be respectful of differing viewpoints
- Accept constructive criticism gracefully
- Focus on what is best for the community
- Show empathy towards other community members

### Unacceptable Behavior

- Trolling, insulting/derogatory comments, personal or political attacks
- Public or private harassment
- Publishing others' private information without permission
- Other conduct which could reasonably be considered inappropriate

---

## Getting Started

### Prerequisites

```bash
# Required
- Java 21
- Maven 3.9+
- Git
- Docker (for TestContainers)

# Optional but recommended
- IntelliJ IDEA or VS Code
- Pre-commit hooks
```

### Initial Setup

1. **Fork the repository**
   ```bash
   # On GitHub: Click "Fork" button
   ```

2. **Clone your fork**
   ```bash
   git clone https://github.com/YOUR_USERNAME/robin-ui.git
   cd robin-ui/robin-gateway
   ```

3. **Add upstream remote**
   ```bash
   git remote add upstream https://github.com/ORIGINAL_OWNER/robin-ui.git
   ```

4. **Install dependencies**
   ```bash
   mvn clean install
   ```

5. **Install pre-commit hooks** (optional but recommended)
   ```bash
   pip install pre-commit
   pre-commit install
   ```

6. **Verify setup**
   ```bash
   ./verify-compliance.sh --quick
   ```

### Understanding the Codebase

Before making changes, familiarize yourself with:

1. **Documentation**: Read `COMPLIANCE_README.md` and `docs/INDEX.md`
2. **Architecture**: Review `ArchitectureTest.java` for structural rules
3. **Security**: Read `docs/SECURITY.md` for security guidelines
4. **Standards**: Review `docs/COMPLIANCE_QUICK_START.md` for code standards

---

## Development Workflow

### 1. Create a Feature Branch

```bash
# Always branch from main
git checkout main
git pull upstream main

# Create feature branch
git checkout -b feature/your-feature-name

# Or for bugfixes
git checkout -b fix/your-bugfix-name
```

**Branch Naming Convention**:
- `feature/` - New features
- `fix/` - Bug fixes
- `refactor/` - Code refactoring
- `test/` - Test additions/improvements
- `docs/` - Documentation updates
- `chore/` - Build/tooling changes

### 2. Make Your Changes

**Before coding**:
- [ ] Check `docs/GAP_TRACKING.md` for known issues
- [ ] Read relevant documentation
- [ ] Understand the existing code
- [ ] Plan your approach

**While coding**:
- [ ] Follow coding standards (see below)
- [ ] Write tests as you code
- [ ] Keep changes focused and small
- [ ] Update documentation as needed

### 3. Test Your Changes

```bash
# Run all tests
mvn test

# Check coverage
mvn jacoco:report jacoco:check

# Run style checks
mvn checkstyle:check

# Quick compliance check
./verify-compliance.sh --quick
```

### 4. Commit Your Changes

See [Commit Guidelines](#commit-guidelines) below.

### 5. Keep Your Branch Updated

```bash
# Fetch latest changes
git fetch upstream

# Rebase on main
git rebase upstream/main

# Resolve any conflicts
# Then continue
git rebase --continue
```

### 6. Push to Your Fork

```bash
git push origin feature/your-feature-name
```

### 7. Create Pull Request

See [Pull Request Process](#pull-request-process) below.

---

## Coding Standards

### Java Code Style

**We use Google Java Style with Spring Boot modifications.**

#### Key Rules

✅ **DO**:
```java
// Constructor injection with Lombok
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repository;
}

// @Valid on all @RequestBody
public Mono<User> createUser(@Valid @RequestBody User user)

// Proper logging
log.info("User created: {}", userId);

// Javadoc on public methods
/**
 * Creates a new user.
 * @param user the user to create
 * @return the created user
 */
public Mono<User> createUser(User user)

// Line length ≤120 characters
String message = "This is a long string that is split"
    + " into multiple lines to stay under 120 characters";

// 4-space indentation
public void method() {
    if (condition) {
        doSomething();
    }
}
```

❌ **DON'T**:
```java
// Field injection
@Autowired
private UserRepository repository;

// Missing @Valid
public Mono<User> createUser(@RequestBody User user)

// System.out
System.out.println("User created");

// Missing Javadoc
public Mono<User> createUser(User user)

// Line too long
String message = "This is a very long string that exceeds 120 characters and will fail the Checkstyle check so it should be split";

// Tabs or wrong indentation
public void method() {
      if (condition) {  // 6 spaces - WRONG
          doSomething();
      }
}
```

### Architecture Rules

**Enforced by ArchUnit**:

1. **Package Structure**: Classes in correct packages
   - `@Service` in `..service..`
   - `@RestController` in `..controller..`
   - `@Repository` in `..repository..`

2. **Dependency Rules**:
   - Services don't depend on controllers
   - Repositories don't depend on services

3. **Layered Architecture**:
   - Controller → Service → Repository
   - No skipping layers

4. **Constructor Injection Only**:
   - No `@Autowired` on fields
   - Use Lombok `@RequiredArgsConstructor`

### Security Requirements

**Always**:
- [ ] Never hardcode secrets (use environment variables)
- [ ] Validate all user input (`@Valid` annotations)
- [ ] Use parameterized queries (prevent SQL injection)
- [ ] Never log passwords or sensitive data
- [ ] Handle errors without leaking sensitive info

**Example**:
```java
// ✅ GOOD
@Value("${jwt.secret}")
private String jwtSecret;

public Mono<User> createUser(@Valid @RequestBody User user) {
    log.info("Creating user: {}", user.getUsername());
    // Don't log: user.getPassword()
}

// ❌ BAD
private String jwtSecret = "my-secret-key";  // Hardcoded!

public Mono<User> createUser(@RequestBody User user) {  // Missing @Valid!
    System.out.println("Password: " + user.getPassword());  // Logging password!
}
```

---

## Testing Requirements

### Coverage Targets

| Layer | Minimum | Target | Priority |
|-------|---------|--------|----------|
| Services | 60% | 70-80% | HIGH |
| Controllers | 60% | 80%+ | HIGH |
| Repositories | 40% | 50%+ | MEDIUM |
| Overall | **60%** | **70%+** | **REQUIRED** |

### Test Types

#### Unit Tests

**Required for**:
- All service classes
- All business logic
- Complex algorithms

**Example**:
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @InjectMocks
    private UserService service;

    @Test
    void createUser_withValidUser_shouldSucceed() {
        // Arrange
        User user = new User("john.doe", "john@example.com");
        when(repository.save(any())).thenReturn(Mono.just(user));

        // Act & Assert
        StepVerifier.create(service.createUser(user))
            .expectNextMatches(u -> u.getUsername().equals("john.doe"))
            .verifyComplete();

        verify(repository).save(any());
    }

    @Test
    void createUser_withDuplicateUsername_shouldFail() {
        // Test error case
        when(repository.save(any()))
            .thenReturn(Mono.error(new DuplicateUserException()));

        StepVerifier.create(service.createUser(new User()))
            .expectError(DuplicateUserException.class)
            .verify();
    }
}
```

#### Integration Tests

**Required for**:
- Controller endpoints
- Database operations
- External integrations

**Example**:
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class UserControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15");

    @Autowired
    private WebTestClient webClient;

    @Test
    void createUser_withValidRequest_returns201() {
        UserRequest request = new UserRequest("john", "john@example.com");

        webClient.post()
            .uri("/api/v1/users")
            .header("Authorization", "Bearer " + getAdminToken())
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.username").isEqualTo("john");
    }
}
```

### Test Naming

Use descriptive test names: `methodName_scenario_expectedResult`

```java
✅ GOOD:
createUser_withValidData_shouldSucceed()
createUser_withDuplicateUsername_shouldThrowException()
createUser_withInvalidEmail_shouldReturnValidationError()

❌ BAD:
testCreateUser()
test1()
userTest()
```

---

## Commit Guidelines

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code refactoring
- `test`: Adding/updating tests
- `docs`: Documentation changes
- `style`: Code style changes (formatting, no logic change)
- `perf`: Performance improvements
- `chore`: Build/tooling changes

### Examples

**Good commits**:
```bash
feat(user): add email validation to user creation

- Added @Email annotation to User DTO
- Added validation test cases
- Updated error messages

Closes #123

---

fix(security): resolve CORS configuration issue

Fixed hardcoded CORS origins by making them environment-configurable.

Before: Origins were hardcoded in SecurityConfig
After: Origins read from CORS_ALLOWED_ORIGINS env variable

Fixes #456

---

test(domain): increase coverage for DomainService

Added tests for:
- Domain creation with invalid data
- Domain update with missing fields
- Domain deletion with cascade

Coverage increased from 45% to 75%

Refs #789
```

**Bad commits**:
```bash
❌ fix stuff
❌ WIP
❌ asdfasdf
❌ Update UserController.java
```

### Commit Best Practices

- Keep commits small and focused
- One logical change per commit
- Write clear, descriptive messages
- Reference issues (Closes #123, Fixes #456, Refs #789)
- Use imperative mood ("add feature" not "added feature")

---

## Pull Request Process

### Before Creating PR

1. **✅ All tests pass**
   ```bash
   mvn test
   ```

2. **✅ Coverage meets threshold**
   ```bash
   mvn jacoco:check
   ```

3. **✅ Code style compliant**
   ```bash
   mvn checkstyle:check
   ```

4. **✅ No code quality issues**
   ```bash
   ./verify-compliance.sh
   ```

5. **✅ Branch rebased on latest main**
   ```bash
   git rebase upstream/main
   ```

6. **✅ Documentation updated**
   - README if API changes
   - SECURITY.md if security changes
   - Relevant docs/ files

### PR Title

Use same format as commit messages:
```
feat(user): add email validation
fix(security): resolve CORS issue
test(domain): increase coverage to 75%
```

### PR Description Template

```markdown
## Summary
Brief description of what this PR does.

## Changes
- Added feature X
- Fixed bug Y
- Refactored component Z

## Testing
- [x] Unit tests added/updated
- [x] Integration tests added/updated
- [x] Manual testing performed
- [x] Coverage ≥60%

## Checklist
- [x] Code follows style guidelines
- [x] Tests pass locally
- [x] Coverage meets threshold
- [x] Documentation updated
- [x] No security issues introduced
- [x] Commit messages follow guidelines

## Related Issues
Closes #123
Fixes #456
Refs #789

## Screenshots (if applicable)
[Add screenshots for UI changes]
```

### PR Review Process

1. **Automated Checks**: GitHub Actions runs compliance suite
2. **Code Review**: At least one approval required
3. **Address Feedback**: Make requested changes
4. **Re-review**: Request review again after changes
5. **Merge**: Squash and merge once approved

### Review Criteria

**Code Quality**:
- [ ] Follows coding standards
- [ ] No unnecessary complexity
- [ ] Well-structured and readable
- [ ] Proper error handling

**Testing**:
- [ ] Adequate test coverage
- [ ] Tests are meaningful
- [ ] Edge cases covered
- [ ] Tests pass consistently

**Security**:
- [ ] No vulnerabilities introduced
- [ ] Input validation present
- [ ] Secrets not hardcoded
- [ ] Error messages safe

**Documentation**:
- [ ] Code is self-documenting or commented
- [ ] Public APIs have Javadoc
- [ ] README updated if needed
- [ ] Security docs updated if needed

---

## Documentation

### When to Update Documentation

**Always update when**:
- Adding new features
- Changing existing APIs
- Modifying security behavior
- Changing configuration

**Files to consider**:
- `README.md` - For user-facing changes
- `docs/SECURITY.md` - For security changes
- `docs/COMPLIANCE_QUICK_START.md` - For development changes
- `docs/GAP_TRACKING.md` - For closing gaps
- `CHANGELOG.md` - For all changes

### Documentation Standards

- Use clear, concise language
- Include code examples
- Add comments for complex logic
- Keep examples up-to-date
- Use proper markdown formatting

---

## Security

### Reporting Security Issues

**DO NOT** create public issues for security vulnerabilities.

Instead:
1. Email: security@robin-mta.org
2. Include: Detailed description, steps to reproduce, potential impact
3. Wait for response before public disclosure

### Security Checklist

Before submitting code:
- [ ] No hardcoded secrets
- [ ] Input validation present
- [ ] SQL injection prevented
- [ ] XSS prevented
- [ ] Error messages don't leak sensitive info
- [ ] Passwords not logged
- [ ] HTTPS used for external calls

---

## Getting Help

### Resources

- **Documentation**: `docs/` directory
- **Quick Start**: `docs/COMPLIANCE_QUICK_START.md`
- **Troubleshooting**: `docs/TROUBLESHOOTING.md`
- **Security**: `docs/SECURITY.md`
- **Gap Tracking**: `docs/GAP_TRACKING.md`

### Asking Questions

**Before asking**:
1. Check documentation
2. Search existing issues
3. Review troubleshooting guide

**When asking**:
1. Provide context
2. Include error messages
3. Show what you've tried
4. Be specific and clear

**Where to ask**:
- GitHub Issues (for bugs/features)
- GitHub Discussions (for questions)
- Team Slack (for quick help)

---

## Recognition

Contributors will be acknowledged in:
- CHANGELOG.md
- GitHub contributors page
- Release notes

Thank you for contributing to Robin Gateway! 🎉

---

**Last Updated**: 2026-02-06
**Version**: 1.0
