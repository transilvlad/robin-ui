# Robin Gateway - Developer Checklist

Quick reference checklist for developers to ensure code quality before committing and creating pull requests.

---

## 📝 Before You Start Coding

- [ ] Read `COMPLIANCE_README.md` for project overview
- [ ] Read `docs/COMPLIANCE_QUICK_START.md` for standards
- [ ] Read `docs/SECURITY.md` for security requirements
- [ ] Understand the architecture rules (see `ArchitectureTest.java`)
- [ ] Pull latest changes: `git pull origin main`
- [ ] Create feature branch: `git checkout -b feature/your-feature-name`

---

## 💻 While Coding

### Code Style Standards

- [ ] Use **constructor injection** (not field injection)
  ```java
  // ✅ GOOD
  @RequiredArgsConstructor
  public class MyService {
      private final MyRepository repository;
  }

  // ❌ BAD
  public class MyService {
      @Autowired private MyRepository repository;
  }
  ```

- [ ] Add **`@Valid`** to all `@RequestBody` parameters
  ```java
  // ✅ GOOD
  public Mono<User> createUser(@Valid @RequestBody User user) {

  // ❌ BAD
  public Mono<User> createUser(@RequestBody User user) {
  ```

- [ ] Use **proper logging** (not System.out)
  ```java
  // ✅ GOOD
  log.info("User created: {}", userId);

  // ❌ BAD
  System.out.println("User created: " + userId);
  ```

- [ ] Add **Javadoc** for all public methods
  ```java
  /**
   * Creates a new user in the system.
   *
   * @param user the user to create
   * @return the created user
   */
  public Mono<User> createUser(User user) {
  ```

- [ ] Keep **line length ≤120 characters**
- [ ] Use **4-space indentation** (not tabs)
- [ ] Follow **naming conventions** (camelCase for methods/variables, PascalCase for classes)

### Security Standards

- [ ] **Never hardcode secrets** (passwords, API keys, tokens)
  ```java
  // ✅ GOOD
  @Value("${api.key}")
  private String apiKey;

  // ❌ BAD
  private String apiKey = "sk_live_123456";
  ```

- [ ] **Never log passwords** (even in debug mode)
- [ ] **Validate all user input** (use Bean Validation annotations)
- [ ] **Use parameterized queries** (prevent SQL injection)
- [ ] **Handle errors gracefully** (no stack traces to users)

### Testing Standards

- [ ] **Write tests for new code**
  - Services: 70-80% coverage
  - Controllers: 80%+ coverage
  - Complex logic: 100% coverage

- [ ] **Test both success and error cases**
  ```java
  @Test
  void createUser_withValidData_shouldSucceed() { ... }

  @Test
  void createUser_withInvalidData_shouldFail() { ... }
  ```

- [ ] **Use descriptive test names** (`methodName_scenario_expectedResult`)
- [ ] **Mock external dependencies** (don't call real APIs in tests)

---

## ✅ Before Committing (LOCAL CHECKS)

### 1. Run Tests
```bash
mvn test
```
**Expected**: All tests pass ✅

### 2. Check Coverage
```bash
mvn jacoco:report jacoco:check
```
**Expected**: Coverage ≥60% ✅

### 3. Check Code Style
```bash
mvn checkstyle:check
```
**Expected**: No violations ✅

### 4. Quick Verification (Fast)
```bash
# This runs tests + coverage + style checks
./verify-compliance.sh --quick
```
**Expected**: Compliance score ≥80% ✅

### 5. Review Your Changes
```bash
git diff
```
**Check for**:
- [ ] No `System.out.println()`
- [ ] No `printStackTrace()`
- [ ] No commented-out code (remove it)
- [ ] No debugging statements (remove them)
- [ ] No TODO comments (create issues instead)

### 6. Commit with Descriptive Message
```bash
git add [specific-files]  # Don't use 'git add .'
git commit -m "feat: add user validation to UserController

- Added @Valid annotations to createUser and updateUser
- Added validation constraints to User DTO
- Added tests for validation error handling

Refs: #123"
```

**Commit Message Format**:
- **Type**: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`
- **Scope**: Short description (50 chars max)
- **Body**: Detailed description (optional)
- **Footer**: Issue references (e.g., `Refs: #123`, `Closes: #456`)

---

## 🚀 Before Creating Pull Request

### 1. Rebase on Latest Main
```bash
git fetch origin
git rebase origin/main
```
**Resolve any conflicts**

### 2. Run Full Compliance Suite
```bash
./verify-compliance.sh
```
**Expected**: All checks pass ✅

Or run individual checks:
```bash
# All checks
mvn clean test jacoco:report jacoco:check \
    checkstyle:check pmd:check spotbugs:check

# Security scan (takes 5-10 minutes)
mvn org.owasp:dependency-check-maven:check

# Architecture tests
mvn test -Dtest=ArchitectureTest
```

### 3. Review Reports

**Coverage Report**:
```bash
open target/site/jacoco/index.html
```
- [ ] Overall coverage ≥60%
- [ ] New code coverage ≥80%
- [ ] No uncovered critical paths

**Checkstyle Report**:
```bash
cat target/checkstyle-result.xml | less
```
- [ ] Zero violations

**PMD Report**:
```bash
cat target/pmd.xml | less
```
- [ ] No Priority 1 (High) issues

### 4. Self-Review Checklist

**Code Quality**:
- [ ] Code is clean and readable
- [ ] No duplicated code
- [ ] Functions are small and focused (<50 lines)
- [ ] Complex logic has comments
- [ ] No magic numbers (use constants)

**Testing**:
- [ ] All tests pass
- [ ] New code has tests
- [ ] Tests are meaningful (not just for coverage)
- [ ] Edge cases covered

**Security**:
- [ ] No secrets committed
- [ ] Input validation present
- [ ] Error messages don't leak sensitive info
- [ ] SQL injection prevented

**Documentation**:
- [ ] Public APIs have Javadoc
- [ ] README updated (if needed)
- [ ] API docs updated (if endpoints changed)
- [ ] Migration guide added (if breaking changes)

### 5. Create Pull Request

**PR Title**: Same format as commit message
```
feat: add user validation to UserController
```

**PR Description Template**:
```markdown
## Summary
Brief description of what this PR does.

## Changes
- Added @Valid annotations to UserController
- Added validation constraints to User DTO
- Added tests for validation error handling

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

## Checklist
- [ ] Code follows style guidelines
- [ ] Tests pass locally
- [ ] Coverage meets threshold (≥60%)
- [ ] Documentation updated
- [ ] No security issues introduced

## Screenshots (if applicable)
[Add screenshots for UI changes]

## Related Issues
Closes #123
```

---

## 🔍 During Code Review

### For PR Author

- [ ] Respond to all comments
- [ ] Make requested changes
- [ ] Run tests after each change
- [ ] Re-request review when ready

### For Reviewers

**Code Quality**:
- [ ] Code is readable and maintainable
- [ ] Follows project conventions
- [ ] No unnecessary complexity
- [ ] Error handling is appropriate

**Testing**:
- [ ] Tests are present and meaningful
- [ ] Edge cases are covered
- [ ] Tests are maintainable

**Security**:
- [ ] No security vulnerabilities
- [ ] Input validation is present
- [ ] Secrets are not hardcoded

**Documentation**:
- [ ] Code is self-documenting or has comments
- [ ] Public APIs have Javadoc
- [ ] README is updated if needed

---

## 🎯 After PR is Merged

- [ ] Delete your feature branch:
  ```bash
  git checkout main
  git pull origin main
  git branch -d feature/your-feature-name
  ```

- [ ] Monitor CI/CD pipeline for any issues

- [ ] Update any related documentation

- [ ] Close related issues

---

## 🚨 Common Mistakes to Avoid

### ❌ Don't Do This:

1. **Committing without running tests**
   ```bash
   git commit -m "quick fix"  # ❌ NO TESTS RUN
   ```

2. **Using `git add .`** (commits everything, including unintended files)
   ```bash
   git add .  # ❌ COMMITS EVERYTHING
   ```

3. **Committing to main directly**
   ```bash
   git checkout main
   git commit  # ❌ SHOULD BE ON FEATURE BRANCH
   ```

4. **Large PRs** (>500 lines changed)
   - Split into smaller, focused PRs

5. **Vague commit messages**
   ```bash
   git commit -m "fix"  # ❌ WHAT WAS FIXED?
   ```

6. **Skipping pre-commit hooks**
   ```bash
   git commit --no-verify  # ❌ BYPASSES ALL CHECKS
   ```

### ✅ Do This Instead:

1. **Always run tests**
   ```bash
   mvn test
   git commit -m "feat: add user validation"
   ```

2. **Stage specific files**
   ```bash
   git add src/main/java/MyClass.java
   git add src/test/java/MyClassTest.java
   ```

3. **Use feature branches**
   ```bash
   git checkout -b feature/add-validation
   git commit
   ```

4. **Keep PRs small and focused**
   - One feature per PR
   - <500 lines changed

5. **Write descriptive commit messages**
   ```bash
   git commit -m "feat: add validation to UserController

   - Added @Valid annotations
   - Added DTO constraints
   - Added tests

   Closes #123"
   ```

6. **Trust the pre-commit hooks**
   - They catch issues early
   - Only skip in emergencies

---

## 📚 Quick References

### Commands
```bash
# Run all compliance checks
./verify-compliance.sh

# Run tests only
mvn test

# Check coverage
mvn jacoco:report && open target/site/jacoco/index.html

# Run style checks
mvn checkstyle:check

# Run architecture tests
mvn test -Dtest=ArchitectureTest
```

### Documentation
- **Quick Start**: `docs/COMPLIANCE_QUICK_START.md`
- **Security**: `docs/SECURITY.md`
- **Project Overview**: `COMPLIANCE_README.md`
- **Gap Tracking**: `docs/GAP_TRACKING.md`

### CI/CD
- **GitHub Actions**: `.github/workflows/gateway-compliance.yml`
- **Pre-commit Hooks**: `.pre-commit-config.yaml`

---

## 🆘 Need Help?

### Issues During Development

**Tests failing?**
```bash
mvn test -Dtest=FailingTest  # Run single test
# Check error message, fix issue, re-run
```

**Coverage below threshold?**
```bash
mvn jacoco:report && open target/site/jacoco/index.html
# Identify uncovered code, write tests
```

**Checkstyle violations?**
- See `docs/COMPLIANCE_QUICK_START.md` for common fixes
- Line too long? Split into multiple lines
- Missing Javadoc? Add doc comment

**Merge conflicts?**
```bash
git status  # See conflicted files
# Edit files, resolve conflicts
git add [resolved-files]
git rebase --continue
```

### Getting Unstuck

1. **Read the docs** (they're comprehensive!)
2. **Check examples** (existing code in the repo)
3. **Ask the team** (Slack, email, standup)
4. **Create an issue** (for bugs/questions)

---

## ✨ Pro Tips

1. **Run `./verify-compliance.sh` before every commit** - catches issues early
2. **Write tests as you code** - don't save them for later
3. **Commit often** - small, focused commits are easier to review
4. **Use meaningful branch names** - `feature/add-user-validation` not `fix-stuff`
5. **Keep PRs small** - easier to review, faster to merge
6. **Update documentation** - code and docs should always match
7. **Ask for help early** - don't struggle in silence

---

**Remember**: Quality > Speed. Take time to do it right the first time.

---

**Last Updated**: 2026-02-06
**Version**: 1.0
