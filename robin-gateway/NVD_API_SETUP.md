# NVD API Key Setup Guide

## Why You Need an NVD API Key

The OWASP Dependency Check scans your project dependencies for known security vulnerabilities using the National Vulnerability Database (NVD). Without an API key, the scan will:
- Take **significantly longer** (hours instead of minutes)
- May fail with 403/404 errors due to rate limiting
- Use outdated vulnerability data

**Good news:** The NVD API key is **completely free** and takes less than 5 minutes to get!

## How to Get Your Free NVD API Key

### Step 1: Request the API Key

1. Visit: https://nvd.nist.gov/developers/request-an-api-key
2. Fill out the simple form:
   - Enter your email address
   - Agree to the terms of use
   - Click "Request API Key"

### Step 2: Check Your Email

You'll receive an email from `nvd@nist.gov` with your API key (usually arrives within 1-5 minutes).

**Subject:** "NVD API Key Request"

The email will contain your API key in this format:
```
xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

### Step 3: Configure the API Key

#### Option A: Environment Variable (Recommended for Development)

Add to your shell profile (`~/.bashrc`, `~/.zshrc`, or `~/.bash_profile`):

```bash
export NVD_API_KEY="your-api-key-here"
```

Then reload your shell:
```bash
source ~/.bashrc  # or ~/.zshrc
```

#### Option B: Project .env File (For Local Development)

Edit `/robin-gateway/.env` and add:
```bash
NVD_API_KEY=your-api-key-here
```

Then source it before running scans:
```bash
source .env
./run-owasp-scan.sh
```

#### Option C: Temporary (One-Time Use)

```bash
export NVD_API_KEY="your-api-key-here"
./run-owasp-scan.sh
```

### Step 4: Verify It Works

Run the OWASP scan:
```bash
cd robin-gateway
./run-owasp-scan.sh
```

You should see:
```
✓ NVD API Key detected

Running OWASP Dependency Check...
```

If the scan runs without 403/404 errors, you're all set!

## Troubleshooting

### "Parameter 'nvdApiKeyEnvironmentVariable' is unknown"

This error has been fixed in the latest version. Make sure you have:
1. Updated `pom.xml` with the correct configuration
2. Set the environment variable: `export NVD_API_KEY="your-key"`
3. Run the scan using `./run-owasp-scan.sh` (not direct Maven command)

### "NVD returned a 403 or 404 error"

This means:
- The API key is not being picked up by Maven
- The API key is invalid or expired

**Solution:**
1. Verify the key is set: `echo $NVD_API_KEY`
2. Make sure there are no extra quotes or spaces
3. Try exporting it again and running the scan

### "Build takes forever"

If the scan is very slow:
- First run downloads the entire NVD database (~2GB)
- Subsequent runs are much faster (updates only)
- Make sure your API key is configured correctly

### "No documents exist" error

This happens when the NVD database hasn't been downloaded yet. Solution:
```bash
# Download/update the database first
./run-owasp-scan.sh update-only

# Then run the scan
./run-owasp-scan.sh check
```

## API Key Limits

The free NVD API key has these rate limits:
- **50 requests in 30 seconds** (sliding window)
- **10,000 requests per day**

The OWASP Dependency Check respects these limits automatically with the configured 6-second delay between requests.

## Security Best Practices

### DO:
✅ Store in environment variables
✅ Add to `.gitignore` (already configured)
✅ Use different keys for different projects/teams
✅ Keep the key confidential

### DON'T:
❌ Commit the key to version control
❌ Share the key publicly
❌ Hardcode the key in source files
❌ Use the same key across production and development

## CI/CD Configuration

### GitHub Actions

```yaml
- name: OWASP Dependency Check
  env:
    NVD_API_KEY: ${{ secrets.NVD_API_KEY }}
  run: |
    cd robin-gateway
    ./run-owasp-scan.sh check
```

Add the `NVD_API_KEY` to your repository secrets:
1. Go to Settings → Secrets and variables → Actions
2. Click "New repository secret"
3. Name: `NVD_API_KEY`
4. Value: Your API key
5. Click "Add secret"

### GitLab CI

```yaml
owasp_scan:
  script:
    - cd robin-gateway
    - export NVD_API_KEY=$NVD_API_KEY
    - ./run-owasp-scan.sh check
  variables:
    NVD_API_KEY: ${NVD_API_KEY}
```

Add the variable in GitLab:
1. Go to Settings → CI/CD → Variables
2. Click "Add variable"
3. Key: `NVD_API_KEY`
4. Value: Your API key
5. Masked: ✓ (recommended)
6. Click "Add variable"

## Additional Resources

- **NVD API Documentation:** https://nvd.nist.gov/developers
- **OWASP Dependency-Check:** https://jeremylong.github.io/DependencyCheck/
- **Maven Plugin Docs:** https://jeremylong.github.io/DependencyCheck/dependency-check-maven/

## Quick Reference Commands

```bash
# Get API key status
echo $NVD_API_KEY

# Run full scan (fails on CVSS >= 7)
./run-owasp-scan.sh check

# Generate report without failing build
./run-owasp-scan.sh aggregate

# Update database only
./run-owasp-scan.sh update-only

# View generated report
open target/dependency-check-report.html
```

## Need Help?

If you're still having issues:
1. Check the logs in `target/dependency-check-report.html`
2. Review the detailed error messages
3. See `docs/SECURITY_SCANNING.md` for more information
4. Open an issue in the repository

---

**Remember:** Getting the NVD API key is **free**, **quick**, and **essential** for effective security scanning!
