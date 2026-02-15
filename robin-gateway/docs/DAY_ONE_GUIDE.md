# Welcome to Robin Gateway! 🎉

**Your First Day Guide**

Welcome! This guide will get you productive on Day 1.

---

## ⏱️ Quick Timeline

```
☕ Morning (2 hours):    Setup & Environment
🍕 Lunch (1 hour):      Break
📚 Afternoon (3 hours): Learn & First Task
```

---

## ☕ **MORNING: Setup (2 hours)**

### Hour 1: Environment Setup

#### ✅ Checklist (tick as you complete)

**Prerequisites**:
- [ ] Java 21 installed (`java -version`)
- [ ] Maven 3.9+ installed (`mvn --version`)
- [ ] Docker running (`docker ps`)
- [ ] Git configured (`git config --list | grep user`)

**If missing, install now**:
```bash
# macOS
brew install openjdk@21 maven docker

# Verify
java -version
mvn --version
docker --version
```

**Project Setup**:
```bash
# 1. Clone repository
cd /Users/cstan/development/workspace/open-source/robin-ui/robin-gateway

# 2. Install dependencies (takes 5-10 minutes)
make install
# Or: mvn clean install -DskipTests

# 3. Start services
make docker-up

# 4. Run tests
make test

# 5. Check compliance
make quick
```

**Expected**: All commands succeed ✅

---

### Hour 2: Read Essential Docs

#### 📚 **Must Read** (45 minutes)

**Order matters!**

1. **`PROJECT_COMPLETE.md`** (10 min)
   - Overview of what was built
   - What files exist and why

2. **`COMPLIANCE_README.md`** (15 min)
   - Project structure
   - Compliance scorecard
   - Quick start commands

3. **`QUICK_REFERENCE.md`** (5 min)
   - Essential commands
   - Daily workflow
   - Bookmark this page!

4. **`docs/DEVELOPER_CHECKLIST.md`** (15 min)
   - Before committing
   - Before PRs
   - Code standards

#### 🔧 **Setup IDE** (15 minutes)

**IntelliJ IDEA**:
1. File → Open → Select robin-gateway folder
2. Settings → Plugins → Install "Lombok"
3. Settings → Build → Compiler → Annotation Processors → Enable
4. Import Code Style from `checkstyle.xml`
5. Run Configuration: `GatewayApplication` with `dev` profile

**VS Code**:
1. Install "Java Extension Pack"
2. Install "Spring Boot Extension Pack"
3. Install "Lombok Annotations Support"
4. Open folder: robin-gateway
5. Let Java extension load (wait for progress bar)

---

## 🍕 **LUNCH BREAK** (1 hour)

Take a break! You've earned it.

While on break, think about:
- What questions do you have?
- What's confusing?
- What needs clarification?

Write them down to ask your mentor after lunch.

---

## 📚 **AFTERNOON: Learn & Code (3 hours)**

### Hour 3: Deep Dive into Codebase

#### 🏗️ **Understand the Architecture** (30 min)

**Read this file**:
```bash
# Open in IDE
src/test/java/com/robin/gateway/architecture/ArchitectureTest.java
```

**This file teaches you**:
- Where services go (`..service..`)
- Where controllers go (`..controller..`)
- Constructor injection (no `@Autowired`)
- Layered architecture rules

**Explore package structure**:
```
src/main/java/com/robin/gateway/
├── controller/    # REST endpoints
├── service/       # Business logic
├── repository/    # Data access
├── model/         # Entities and DTOs
├── config/        # Configuration classes
├── exception/     # Exception handling
└── auth/          # Authentication
```

#### 🔍 **Read Real Code** (30 min)

**Start simple, read in order**:

1. **`UserController.java`** (5 min)
   - Simple REST controller
   - Note: `@Valid`, constructor injection, `@PreAuthorize`

2. **`UserService.java`** (5 min)
   - Business logic
   - Constructor injection with `@RequiredArgsConstructor`
   - Proper logging

3. **`SecurityConfig.java`** (10 min)
   - Security configuration
   - JWT authentication
   - CORS setup
   - Note documented `@SuppressWarnings`

4. **`GlobalExceptionHandler.java`** (5 min)
   - Centralized error handling
   - Consistent error responses

5. **`ArchitectureTest.java`** (5 min)
   - Rules that enforce architecture
   - Run: `make arch`

---

### Hour 4: First Contribution (45 min)

#### ✍️ **Your First PR: Fix a Simple Issue**

**Option 1: Add Javadoc** (Easy)

Find a public method without Javadoc:
```bash
# Search for public methods without docs
grep -r "public " src/main/java/ | grep -v "@\|//"
```

Add Javadoc:
```java
// BEFORE
public User createUser(User user) {
    return repository.save(user);
}

// AFTER
/**
 * Creates a new user in the system.
 *
 * @param user the user to create (must not be null)
 * @return the created user with generated ID
 * @throws IllegalArgumentException if user is null
 */
public User createUser(User user) {
    return repository.save(user);
}
```

**Option 2: Add a Test** (Medium)

Pick a service class, add a test:
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
        User user = new User("john", "john@example.com");
        when(repository.save(any())).thenReturn(Mono.just(user));

        // Act & Assert
        StepVerifier.create(service.createUser(user))
            .expectNextMatches(u -> u.getUsername().equals("john"))
            .verifyComplete();
    }
}
```

**Option 3: Add @Valid** (Easy)

Check if any controllers missing `@Valid`:
```bash
grep -rn "@RequestBody" src/main/java/com/robin/gateway/controller/ | grep -v "@Valid"
```

If found, add `@Valid`:
```java
// BEFORE
public Mono<Domain> create(@RequestBody DomainRequest request)

// AFTER
public Mono<Domain> create(@Valid @RequestBody DomainRequest request)
```

---

### Hour 5: Submit Your First PR (30 min)

#### 📝 **Create Pull Request**

**1. Create branch**:
```bash
git checkout -b docs/add-javadoc-to-user-service
# Or: fix/add-validation-to-domain-controller
# Or: test/add-user-service-tests
```

**2. Make changes**:
- Add your code
- Save files

**3. Test locally**:
```bash
make quick
# All checks should pass ✅
```

**4. Commit**:
```bash
git add [your-files]
git commit -m "docs: add Javadoc to UserService.createUser

Added comprehensive Javadoc documentation to the createUser method
including parameter description and return value.

Fixes #123"
```

**5. Push**:
```bash
git push origin docs/add-javadoc-to-user-service
```

**6. Create PR on GitHub**:
- Click "Create Pull Request"
- Fill in description (see `docs/DEVELOPER_CHECKLIST.md` for template)
- Request review from mentor

---

### Hour 5.5: Review & Reflect (15 min)

#### ✅ **Day 1 Accomplishments**

Check off what you completed:

**Setup**:
- [ ] Environment installed and working
- [ ] Tests run successfully
- [ ] IDE configured

**Knowledge**:
- [ ] Read PROJECT_COMPLETE.md
- [ ] Read COMPLIANCE_README.md
- [ ] Read DEVELOPER_CHECKLIST.md
- [ ] Understand architecture rules
- [ ] Read example code

**Contribution**:
- [ ] Made first code change
- [ ] Ran compliance checks
- [ ] Submitted first PR

**Bookmarked**:
- [ ] QUICK_REFERENCE.md
- [ ] docs/TROUBLESHOOTING.md
- [ ] docs/COMPLIANCE_QUICK_START.md

---

## 🎯 **Your Daily Workflow (Going Forward)**

### Every Morning:
```bash
# 1. Pull latest changes
git checkout main && git pull origin main

# 2. Create feature branch
git checkout -b feature/your-feature

# 3. Start services
make docker-up

# 4. Run tests
make test
```

### While Coding:
```bash
# Run tests frequently
make test

# Check coverage
make coverage-html
```

### Before Committing:
```bash
# Run quick checks
make quick

# If all pass, commit
git add [files]
git commit -m "feat: your message"
```

### Before PR:
```bash
# Run full checks
make verify

# If all pass, create PR
git push origin feature/your-feature
```

---

## 📞 **Getting Help**

### When Stuck

**1. Check Documentation** (5 min)
```bash
# Troubleshooting guide
open docs/TROUBLESHOOTING.md

# Quick reference
open QUICK_REFERENCE.md

# Search all docs
grep -r "your error" docs/
```

**2. Search Issues** (5 min)
- GitHub Issues tab
- Search for similar problems

**3. Ask Your Mentor** (when ready)
- Slack message
- Email
- In person

**When asking for help, include**:
- What you're trying to do
- What error you're seeing
- What you've already tried

---

## 💡 **Pro Tips for Success**

### Do's ✅

1. **Ask questions early** - Don't struggle in silence
2. **Read error messages** - They usually tell you what's wrong
3. **Run tests often** - Catch issues early
4. **Use make commands** - They're shortcuts for common tasks
5. **Keep PRs small** - Easier to review (<300 lines)
6. **Write tests** - As you code, not after
7. **Follow checklist** - `docs/DEVELOPER_CHECKLIST.md`
8. **Use Quick Reference** - `QUICK_REFERENCE.md`

### Don'ts ❌

1. **Don't skip tests** - They're there for a reason
2. **Don't hardcode secrets** - Use environment variables
3. **Don't commit directly to main** - Always use branches
4. **Don't use `git add .`** - Add specific files
5. **Don't commit without checking** - Run `make quick` first
6. **Don't copy-paste without understanding** - Ask if unclear
7. **Don't work late fixing build** - Ask for help
8. **Don't skip documentation** - It saves time later

---

## 🎓 **Week 1 Learning Plan**

### Monday (Today):
- ✅ Setup complete
- ✅ First PR submitted
- ✅ Understand basics

### Tuesday:
- [ ] Read `docs/SECURITY.md` (30 min)
- [ ] Add 2-3 tests (1 hour)
- [ ] Review teammate's PR (30 min)

### Wednesday:
- [ ] Read `docs/COMPLIANCE_QUICK_START.md` (30 min)
- [ ] Fix a small bug (1-2 hours)
- [ ] Pair program with mentor (1 hour)

### Thursday:
- [ ] Review `docs/GAP_TRACKING.md` (15 min)
- [ ] Pick a gap to work on (2-3 hours)
- [ ] Submit PR

### Friday:
- [ ] Help new developer (pay it forward)
- [ ] Reflect on week
- [ ] Plan next week

---

## 🎉 **Welcome Aboard!**

You're now part of the Robin Gateway team!

**Remember**:
- It's okay to ask questions
- Everyone was new once
- Mistakes are learning opportunities
- Code quality matters more than speed
- We're here to help

**Your mentor**: [Name]
**Team Slack**: #robin-gateway
**Daily Standup**: [Time]

---

## 📋 **Day 1 Checklist**

Before you leave today, make sure:

- [ ] All software installed and working
- [ ] Tests run successfully
- [ ] IDE configured
- [ ] Essential docs read
- [ ] First PR submitted (or drafted)
- [ ] QUICK_REFERENCE.md bookmarked
- [ ] Know how to get help
- [ ] Understand daily workflow
- [ ] Met your mentor
- [ ] Joined team Slack

**If all checked**: You had a great Day 1! 🎉

**If some missing**: That's okay! Ask your mentor tomorrow.

---

## 🚀 **Tomorrow's Preview**

On Day 2, you'll:
- Review PR feedback
- Learn about security best practices
- Write more tests
- Understand reactive programming (WebFlux)
- Contribute to a real feature

**Get good rest. See you tomorrow!** 😊

---

**Welcome to Robin Gateway!** 🎊

**Last Updated**: 2026-02-06
**Version**: 1.0
