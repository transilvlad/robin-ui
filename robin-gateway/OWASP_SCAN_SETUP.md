# OWASP Scan Setup - Quick Start

## ⏱️ Time Required: ~10 minutes + scan time

---

## Step 1: Register for NVD API Key (5 minutes)

1. **Open your browser** and go to:
   ```
   https://nvd.nist.gov/developers/request-an-api-key
   ```

2. **Fill out the form:**
   - Email address: [Your email]
   - Organization (optional): [Your organization or "Personal"]
   - Purpose: "Security scanning for open source project"

3. **Submit the form** and check your email

4. **Copy the API key** from the email (you'll receive it within a few minutes)

---

## Step 2: Configure the API Key (2 minutes)

### Option A: Permanent Configuration (Recommended)

**For Zsh (macOS default):**
```bash
echo 'export NVD_API_KEY="paste-your-key-here"' >> ~/.zshrc
source ~/.zshrc
```

**For Bash:**
```bash
echo 'export NVD_API_KEY="paste-your-key-here"' >> ~/.bashrc
source ~/.bashrc
```

### Option B: Temporary (Current Session Only)

```bash
export NVD_API_KEY="paste-your-key-here"
```

### Verify Configuration

```bash
echo $NVD_API_KEY
```

You should see your API key printed. ✅

---

## Step 3: Navigate to Robin Gateway (10 seconds)

```bash
cd ~/development/workspace/open-source/robin-ui/robin-gateway
```

---

## Step 4: Run Your First Scan (5-10 minutes first time, 1-2 min after)

```bash
./run-owasp-scan.sh
```

**What happens:**
1. ✓ Checks for NVD_API_KEY
2. ✓ Downloads NVD database (~500MB, first time only)
3. ✓ Analyzes all dependencies
4. ✓ Generates HTML and JSON reports
5. ⚠️ Fails build if CVSS >= 7 (High/Critical vulnerabilities)

**Expected output:**
```
════════════════════════════════════════════════════════════════
  OWASP Dependency Check - Robin Gateway
════════════════════════════════════════════════════════════════

✓ NVD API Key detected

Running OWASP Dependency Check (fails on CVSS >= 7)...

[INFO] Scanning Dependencies...
[INFO] Checking for updates and analyzing dependencies for known vulnerabilities...
[INFO] Analysis complete.

════════════════════════════════════════════════════════════════
  Scan Complete!
════════════════════════════════════════════════════════════════

Reports generated in: target/dependency-check-report.html
JSON report: target/dependency-check-report.json

To view the report:
  open target/dependency-check-report.html
```

---

## Step 5: Review Results (5 minutes)

### Open the HTML Report

```bash
open target/dependency-check-report.html
```

### What to Look For

| Severity | CVSS Score | Action Required |
|----------|------------|-----------------|
| 🔴 **Critical** | 9.0 - 10.0 | **Immediate upgrade required** |
| 🟠 **High** | 7.0 - 8.9 | **Upgrade within 7 days** |
| 🟡 **Medium** | 4.0 - 6.9 | Upgrade within 30 days |
| 🟢 **Low** | 0.0 - 3.9 | Monitor, plan upgrade |

### Understanding the Report

The report shows:
1. **Summary**: Total vulnerabilities by severity
2. **Dependencies**: Each library analyzed
3. **CVEs**: Specific vulnerabilities found
4. **Remediation**: Recommended version upgrades

---

## Expected Results (First Scan)

### Best Case Scenario: Clean Build ✅

```
[INFO] BUILD SUCCESS
[INFO] No vulnerabilities found with CVSS >= 7.0
```

**What this means:**
- No High or Critical vulnerabilities detected
- All dependencies are reasonably secure
- You can proceed with development

### Possible Scenario: Vulnerabilities Found ⚠️

```
[ERROR] BUILD FAILURE
[ERROR] Dependency-Check found 2 vulnerabilities with CVSS >= 7.0
[ERROR] - CVE-2024-12345 (CVSS 8.5) in spring-boot-starter-web:3.2.2
[ERROR] - CVE-2024-67890 (CVSS 7.2) in commons-io:2.15.1
```

**What to do:**
1. **Review each CVE** in the HTML report
2. **Check if upgrade is available** (usually listed in report)
3. **Upgrade the dependency** in `pom.xml`
4. **Test after upgrade**: `mvn clean test`
5. **Re-run scan**: `./run-owasp-scan.sh`

**Example fix:**
```xml
<!-- Before -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.15.1</version>
</dependency>

<!-- After -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.16.0</version> <!-- Upgraded to fix CVE-2024-67890 -->
</dependency>
```

---

## Troubleshooting

### "NVD_API_KEY not set"

```bash
# Set the key temporarily
export NVD_API_KEY="your-key-here"

# Or add permanently (recommended)
echo 'export NVD_API_KEY="your-key-here"' >> ~/.zshrc
source ~/.zshrc
```

### "Rate limit exceeded"

- Wait a few minutes
- Verify your API key is correct: `echo $NVD_API_KEY`
- API keys allow 50 requests/30 seconds (vs 5 without key)

### "OutOfMemoryError"

```bash
export MAVEN_OPTS="-Xmx2g"
./run-owasp-scan.sh
```

### Scan Taking Too Long

**First run (5-10 minutes)**: Normal - downloading ~500MB NVD database

**Subsequent runs (1-2 minutes)**: Much faster - database already cached

---

## What Happens Next?

### If Clean Build ✅

1. **Mark GAP-002 as Complete**: Security scanning is now operational
2. **Schedule Regular Scans**:
   - Before major releases
   - Monthly maintenance scans
   - After adding new dependencies
3. **Continue with Priority 2 Service Tests**: Return to test coverage work

### If Vulnerabilities Found ⚠️

1. **Triage Vulnerabilities**:
   - Critical/High: Fix immediately
   - Medium: Plan upgrade within 30 days
   - Low: Monitor for updates
2. **Upgrade Dependencies**: Update versions in `pom.xml`
3. **Test Changes**: `mvn clean test`
4. **Re-run Scan**: `./run-owasp-scan.sh`
5. **Document in CHANGELOG.md**

---

## Integration with Workflow

### Add to Pre-Release Checklist

```bash
# Before releasing v1.0.0
./run-owasp-scan.sh          # Security scan
mvn clean verify             # Full build with all checks
```

### Weekly Maintenance

```bash
# Monday morning routine
cd ~/development/workspace/open-source/robin-ui/robin-gateway
./run-owasp-scan.sh aggregate  # Non-blocking scan
open target/dependency-check-report.html
```

---

## Completion Criteria

You can consider GAP-002 (Security Scanning) **COMPLETE** when:

- ✅ NVD API key registered and configured
- ✅ First scan successfully executed
- ✅ All High/Critical vulnerabilities addressed (or suppressed with justification)
- ✅ Report reviewed and understood
- ✅ Process documented (already done - see docs/SECURITY_SCANNING.md)
- ✅ Team trained on running scans (optional, but recommended)

---

## Quick Reference

```bash
# Run scan (fail on High/Critical)
./run-owasp-scan.sh

# Generate report without failing
./run-owasp-scan.sh aggregate

# Update NVD database only
./run-owasp-scan.sh update-only

# View report
open target/dependency-check-report.html

# Check API key
echo $NVD_API_KEY
```

---

## Need Help?

- **Detailed Guide**: See `docs/SECURITY_SCANNING.md`
- **Handling CVEs**: See section "Handling Vulnerabilities" in `docs/SECURITY_SCANNING.md`
- **False Positives**: See section "Suppress False Positives" in `docs/SECURITY_SCANNING.md`

---

**Time to Complete**: ~10 minutes + scan time
**Difficulty**: Easy
**Estimated Scan Time**: 5-10 minutes (first run), 1-2 minutes (subsequent)

---

**Ready? Start with Step 1!** 🚀
