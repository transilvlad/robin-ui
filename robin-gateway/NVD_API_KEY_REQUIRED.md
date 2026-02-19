# NVD API Key Registration Required

## Task: Register for NVD API Key

The OWASP Dependency Check plugin requires an NVD (National Vulnerability Database) API key for optimal performance.

## Registration Steps

1. **Visit**: https://nvd.nist.gov/developers/request-an-api-key
2. **Fill out the form**:
   - Email address (will receive the API key)
   - Organization (optional): "Personal" or your company name
   - Purpose: "Security scanning for open source project"
3. **Submit** and wait for email confirmation (usually arrives within minutes)
4. **Save the API key** from the email

## Configuration

Once you have the API key:

### Option 1: Environment Variable (Recommended)

```bash
# Add to ~/.zshrc or ~/.bashrc
export NVD_API_KEY="your-api-key-here"

# Or set for current session
export NVD_API_KEY="your-api-key-here"
```

### Option 2: Maven Settings

Add to `~/.m2/settings.xml`:

```xml
<settings>
  <profiles>
    <profile>
      <id>owasp</id>
      <properties>
        <nvd.api.key>your-api-key-here</nvd.api.key>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>owasp</activeProfile>
  </activeProfiles>
</settings>
```

## Running the OWASP Scan

After configuring the API key:

```bash
# Set JAVA_HOME to Java 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Run OWASP dependency check
mvn org.owasp:dependency-check-maven:check

# View report
open target/dependency-check-report.html
```

## Expected Results

- **Without API key**: Scan runs but may be slower and rate-limited
- **With API key**: Faster scan with full vulnerability data
- **Target**: 0 critical/high vulnerabilities (CVSS ≥7)

## Current Status

- OWASP plugin: ✅ Configured in pom.xml
- Suppression file: ✅ Created (dependency-check-suppressions.xml)
- API key: ⏳ **PENDING USER REGISTRATION**
- Last scan: Not yet run

## Next Steps

1. Register and get API key (5 minutes)
2. Configure API key as environment variable
3. Run scan: `mvn org.owasp:dependency-check-maven:check`
4. Review findings in report
5. Update dependencies or document suppressions
6. Mark GAP-002 as 100% complete

## Documentation

- Full guide: `docs/OWASP_SCAN_RESULTS.md`
- Suppression file: `dependency-check-suppressions.xml`
- Gap tracking: `docs/GAP_TRACKING.md` (GAP-002)
