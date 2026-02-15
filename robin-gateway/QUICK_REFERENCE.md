# Robin Gateway - Quick Reference Card

**Print this page and keep it handy!**

---

## 🚀 Essential Commands

### Before Committing
```bash
./verify-compliance.sh --quick
```

### Before Pull Request
```bash
./verify-compliance.sh
```

### Run Tests
```bash
mvn test
```

### Check Coverage
```bash
mvn jacoco:report && open target/site/jacoco/index.html
```

---

## 📋 Daily Checklist

- [ ] Pull latest: `git pull origin main`
- [ ] Run tests: `mvn test`
- [ ] Check coverage: `mvn jacoco:check`
- [ ] Check style: `mvn checkstyle:check`
- [ ] Commit often with good messages
- [ ] Push daily: `git push`

---

## ✅ Code Standards (Must-Know)

### DO ✅
```java
// Constructor injection
@RequiredArgsConstructor
public class MyService {
    private final MyRepository repository;
}

// @Valid on @RequestBody
public Mono<User> create(@Valid @RequestBody User user)

// Proper logging
log.info("User created: {}", userId);

// Javadoc on public methods
/**
 * Creates user.
 * @param user the user
 * @return created user
 */
public User create(User user)
```

### DON'T ❌
```java
// Field injection
@Autowired private MyRepository repository;

// Missing @Valid
public Mono<User> create(@RequestBody User user)

// System.out
System.out.println("User created");

// Missing Javadoc
public User create(User user)
```

---

## 🔧 Troubleshooting

### Tests Failing?
```bash
mvn test -Dtest=FailingTest  # Run single test
# Fix issue, re-run
```

### Coverage Low?
```bash
mvn jacoco:report && open target/site/jacoco/index.html
# Identify uncovered code, write tests
```

### Style Violations?
```bash
mvn checkstyle:check
# See docs/TROUBLESHOOTING.md for fixes
```

### Build Slow?
```bash
mvn install -DskipTests -Dowasp.skip=true -T 4
# Note: Run full build before committing!
```

---

## 📚 Documentation Quick Links

| Document | Purpose |
|----------|---------|
| `COMPLIANCE_README.md` | Project overview |
| `docs/COMPLIANCE_QUICK_START.md` | Developer guide |
| `docs/SECURITY.md` | Security standards |
| `docs/TROUBLESHOOTING.md` | Common issues |
| `docs/DEVELOPER_CHECKLIST.md` | Commit/PR checklist |
| `docs/GAP_TRACKING.md` | Known issues |

---

## 🎯 Coverage Targets

| Layer | Target | Priority |
|-------|--------|----------|
| Services | 70-80% | High |
| Controllers | 80%+ | High |
| Repositories | 50%+ | Medium |
| Config | 40%+ | Low |

---

## 📊 Compliance Score

```
Current: ~70%
Target:  ≥95%
Gap:     -25%
```

**Critical Priorities:**
1. Test coverage to 60% (currently 15%)
2. Security audit (OWASP scan)
3. Performance benchmarks (not done)

---

## 🚨 Emergency Commands

### Skip Tests (Emergency Only!)
```bash
git commit --no-verify -m "emergency fix"
```

### Reset Local Changes
```bash
git reset --hard origin/main  # CAUTION: Loses all local changes!
```

### Force Push (DANGEROUS!)
```bash
# NEVER force push to main!
# Only use on your feature branch if needed
git push --force-with-lease
```

---

## 💡 Pro Tips

1. Run `./verify-compliance.sh --quick` before every commit
2. Write tests as you code (don't save for later)
3. Commit small, focused changes
4. Use meaningful branch names: `feature/add-validation`
5. Keep PRs <500 lines
6. Read error messages carefully
7. Check documentation first

---

## 📞 Help

**Docs**: `docs/` directory
**Issues**: GitHub Issues
**Questions**: Team Slack

---

**Version**: 1.0 | **Date**: 2026-02-06
