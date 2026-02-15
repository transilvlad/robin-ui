# Security Scanning Guide

## Overview

This guide covers security scanning using OWASP Dependency-Check to identify known vulnerabilities in project dependencies.

## NVD API Key Setup

### Step 1: Request API Key

1. Visit: https://nvd.nist.gov/developers/request-an-api-key
2. Fill out the form with your email address
3. Submit the request
4. Check your email for the API key (usually arrives within minutes)

### Step 2: Configure API Key

**Temporary (current session only):**
```bash
export NVD_API_KEY='your-api-key-here'
```

**Permanent (recommended):**

For Bash:
```bash
echo 'export NVD_API_KEY="your-api-key-here"' >> ~/.bashrc
source ~/.bashrc
```

For Zsh:
```bash
echo 'export NVD_API_KEY="your-api-key-here"' >> ~/.zshrc
source ~/.zshrc
```

**Verify configuration:**
```bash
echo $NVD_API_KEY
```

## Running Security Scans

### Quick Start

```bash
# Run the helper script (recommended)
./run-owasp-scan.sh

# Or run Maven directly
mvn org.owasp:dependency-check-maven:check
```

### Scan Types

| Command | Purpose | Fails Build |
|---------|---------|-------------|
| `./run-owasp-scan.sh check` | Full scan with build failure on CVSS >= 7 | Yes |
| `./run-owasp-scan.sh aggregate` | Generate report without failing | No |
| `./run-owasp-scan.sh update-only` | Update NVD database only | No |

### First Run

The first scan will take longer (5-10 minutes) as it downloads the NVD database (~500MB). Subsequent scans are much faster (~1-2 minutes).

## Understanding Results

### Report Locations

After a scan:
- **HTML Report**: `target/dependency-check-report.html`
- **JSON Report**: `target/dependency-check-report.json`

### Opening the Report

```bash
# macOS
open target/dependency-check-report.html

# Linux
xdg-open target/dependency-check-report.html

# Windows
start target/dependency-check-report.html
```

### CVSS Severity Ratings

| CVSS Score | Severity | Action Required |
|------------|----------|-----------------|
| 0.0 - 3.9 | Low | Monitor, plan upgrade |
| 4.0 - 6.9 | Medium | Upgrade within 30 days |
| 7.0 - 8.9 | High | **Upgrade within 7 days** |
| 9.0 - 10.0 | Critical | **Immediate upgrade** |

**Build Configuration**: Builds fail automatically on CVSS >= 7.0 (High/Critical)

## Handling Vulnerabilities

### 1. Review the Vulnerability

Check the report for:
- **CVE ID**: The vulnerability identifier
- **Affected Component**: Which dependency is vulnerable
- **Description**: What the vulnerability does
- **CVSS Score**: Severity rating
- **Remediation**: Recommended fix (usually upgrade version)

### 2. Upgrade Dependencies

**Direct Dependencies** (in `pom.xml`):
```xml
<dependency>
    <groupId>org.example</groupId>
    <artifactId>vulnerable-lib</artifactId>
    <version>2.0.0</version> <!-- Upgrade to fixed version -->
</dependency>
```

**Transitive Dependencies** (pulled by other deps):
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.example</groupId>
            <artifactId>transitive-lib</artifactId>
            <version>3.0.0</version> <!-- Override transitive version -->
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 3. Test After Upgrade

```bash
# Run tests
mvn clean test

# Verify scan passes
./run-owasp-scan.sh check
```

### 4. Suppress False Positives

If a vulnerability is **confirmed false positive** or **not exploitable** in your context, you can suppress it.

Edit `dependency-check-suppressions.xml`:
```xml
<suppress>
    <notes><![CDATA[
    Justification: This CVE affects only Windows systems, we deploy on Linux
    Risk Assessment: Not exploitable in our configuration
    Reviewed By: [Your Name]
    Date: 2026-02-06
    Monitoring: Tracking upstream fix in [JIRA-123]
    ]]></notes>
    <cve>CVE-2024-12345</cve>
</suppress>
```

**Suppression Guidelines:**
- ✅ Use for false positives (wrong component identified)
- ✅ Use when vulnerability doesn't apply to your usage
- ✅ Use for test-scope dependencies with low risk
- ❌ Don't suppress to ignore real vulnerabilities
- ❌ Don't suppress High/Critical CVEs without security team review
- 📝 Always document justification thoroughly

### 5. Document in Change Log

Add to `CHANGELOG.md`:
```markdown
## [1.0.1] - 2026-02-06
### Security
- Upgraded spring-boot from 3.2.2 to 3.2.5 (CVE-2024-XXXXX)
- Upgraded commons-io from 2.15.1 to 2.16.0 (CVE-2024-YYYYY)
```

## CI/CD Integration

### GitHub Actions

Add to `.github/workflows/security-scan.yml`:
```yaml
name: Security Scan

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]
  schedule:
    - cron: '0 2 * * 1'  # Weekly on Mondays at 2 AM

jobs:
  owasp-scan:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'maven'

      - name: Run OWASP Dependency Check
        env:
          NVD_API_KEY: ${{ secrets.NVD_API_KEY }}
        run: |
          cd robin-gateway
          mvn org.owasp:dependency-check-maven:check

      - name: Upload Report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: owasp-report
          path: robin-gateway/target/dependency-check-report.html
```

**Configure GitHub Secret:**
1. Go to: Repository → Settings → Secrets and variables → Actions
2. Add secret: `NVD_API_KEY` = `your-api-key-here`

## Compliance Tracking

### GAP-002: Security Scanning

**Requirements:**
- ✅ OWASP Dependency-Check configured
- ✅ NVD API key integrated
- ✅ Automated scanning in build
- ✅ Fail on High/Critical vulnerabilities (CVSS >= 7)
- ✅ Suppression file with justifications
- ✅ Documentation provided

**Evidence:**
- `pom.xml` lines 339-366: Plugin configuration
- `dependency-check-suppressions.xml`: Suppression tracking
- `run-owasp-scan.sh`: Helper script
- This document: Process documentation

## Best Practices

### Scanning Frequency

| Environment | Frequency | Trigger |
|-------------|-----------|---------|
| Development | On-demand | Before commits with dependency changes |
| CI/CD | Automatic | Every push, nightly, weekly |
| Production | Monthly | Scheduled maintenance window |

### Dependency Management

1. **Keep Dependencies Updated**: Regular updates reduce vulnerability exposure
2. **Monitor Security Advisories**: Subscribe to Spring Security advisories
3. **Use Dependency Management**: Centralize version control in `<dependencyManagement>`
4. **Minimize Dependencies**: Fewer dependencies = smaller attack surface
5. **Prefer Well-Maintained Libraries**: Active projects patch faster

### Version Pinning

```xml
<!-- ❌ Bad: Range versions -->
<version>[1.0,2.0)</version>

<!-- ✅ Good: Explicit versions -->
<version>1.5.2</version>
```

### Transitive Dependency Analysis

```bash
# View dependency tree
mvn dependency:tree

# Find where a dependency comes from
mvn dependency:tree -Dincludes=org.example:problematic-lib

# Analyze conflicts
mvn dependency:analyze
```

## Troubleshooting

### "NVD_API_KEY not set"

**Problem**: Environment variable not configured

**Solution**:
```bash
export NVD_API_KEY='your-api-key-here'
./run-owasp-scan.sh check
```

### "Rate limit exceeded"

**Problem**: Too many API requests without key or with invalid key

**Solutions**:
- Verify API key is correct: `echo $NVD_API_KEY`
- API keys allow 50 requests/30 seconds (vs 5 without key)
- Wait a few minutes and retry

### "Database update failed"

**Problem**: Network issues downloading NVD database

**Solutions**:
```bash
# Retry update
mvn org.owasp:dependency-check-maven:update-only

# Clear cache and retry
rm -rf ~/.m2/repository/org/owasp/dependency-check-data/
mvn org.owasp:dependency-check-maven:update-only
```

### "OutOfMemoryError during scan"

**Problem**: Large dependency tree exhausts heap

**Solution**:
```bash
# Increase Maven memory
export MAVEN_OPTS="-Xmx2g"
mvn org.owasp:dependency-check-maven:check
```

### False Positives

**Problem**: CVE reported for wrong component (common with generic names)

**Solution**: Add suppression with justification in `dependency-check-suppressions.xml`

## Additional Resources

- **OWASP Dependency-Check**: https://jeremylong.github.io/DependencyCheck/
- **NVD API**: https://nvd.nist.gov/developers
- **CVE Database**: https://cve.mitre.org/
- **CVSS Calculator**: https://nvd.nist.gov/vuln-metrics/cvss/v3-calculator
- **Spring Security Advisories**: https://spring.io/security

## Quick Reference

```bash
# Run scan
./run-owasp-scan.sh

# Generate report without failing
./run-owasp-scan.sh aggregate

# Update database only
./run-owasp-scan.sh update-only

# View dependency tree
mvn dependency:tree

# Analyze dependencies
mvn dependency:analyze

# View report
open target/dependency-check-report.html
```

---

**Last Updated**: 2026-02-06
**Maintained By**: Security Team
**Review Frequency**: Quarterly
