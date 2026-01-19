# Getting Started Checklist

Follow these steps to get Robin UI running.

## Prerequisites Checklist

- [ ] Node.js 18+ installed (`node --version`)
- [ ] npm 9+ installed (`npm --version`)
- [ ] Robin MTA Server available
- [ ] Git installed (optional, project already created)

## Installation Steps

### Step 1: Navigate to Project

```bash
cd /Users/cstan/development/workspace/open-source/transilvlad-robin/robin-ui
```

### Step 2: Install Dependencies

```bash
npm install
```

This will install ~50 packages including Angular, NgRx, Tailwind CSS, etc.

Expected time: 2-5 minutes

### Step 3: Verify Installation

```bash
npm list --depth=0
```

You should see all packages listed without errors.

## Configuration

### Step 4: Update Environment (Optional)

If Robin MTA is running on different ports, edit:

`src/environments/environment.ts`

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8090',      // Change if needed
  serviceUrl: 'http://localhost:8080',  // Change if needed
};
```

## Running the Application

### Step 5: Start Robin MTA Server

In one terminal:

```bash
cd /Users/cstan/development/workspace/open-source/transilvlad-robin
java -jar robin.jar --server cfg/
```

Wait until you see:
```
Server started on port 8080
API started on port 8090
```

### Step 6: Start Angular Dev Server

In another terminal:

```bash
cd /Users/cstan/development/workspace/open-source/transilvlad-robin/robin-ui
npm start
```

Wait until you see:
```
✔ Browser application bundle generation complete.
** Angular Live Development Server is listening on localhost:4200 **
```

### Step 7: Open Browser

Open your browser to:
```
http://localhost:4200/
```

You should see the Robin MTA Management UI.

## First-Time Verification

### Test Checklist

- [ ] Dashboard loads without errors
- [ ] Server status shows "UP" in header
- [ ] Health widget displays server information
- [ ] Queue widget shows queue size
- [ ] Sidebar navigation works
- [ ] Click "Email → Queue" route works
- [ ] Click "Email → Storage" route works
- [ ] All menu items are accessible

## Troubleshooting

### Issue: Port 4200 already in use

**Solution:**
```bash
ng serve --port 4300
```
Then open `http://localhost:4300/`

### Issue: Cannot connect to Robin API

**Symptom:** Error messages in browser console, "DOWN" status

**Solution:**
1. Verify Robin MTA is running: `curl http://localhost:8080/health`
2. Check ports 8080 and 8090 are accessible
3. Look at Robin MTA logs for errors

### Issue: CORS errors

**Symptom:** Console shows "CORS policy" errors

**Solution:** Create `proxy.conf.json`:

```json
{
  "/api": {
    "target": "http://localhost:8090",
    "secure": false,
    "changeOrigin": true,
    "pathRewrite": { "^/api": "" }
  },
  "/service": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true,
    "pathRewrite": { "^/service": "" }
  }
}
```

Update `environment.ts`:
```typescript
apiUrl: '/api',
serviceUrl: '/service',
```

Start with proxy:
```bash
ng serve --proxy-config proxy.conf.json
```

### Issue: Module not found errors

**Solution:**
```bash
rm -rf node_modules package-lock.json
npm install
```

### Issue: TypeScript errors

**Solution:**
Restart the dev server:
```bash
# Press Ctrl+C to stop
npm start
```

## What to Do Next

### Explore the Application

1. **Dashboard** - View server health and queue statistics
2. **Email Queue** - See queued emails, retry or delete them
3. **Email Storage** - Browse the email storage directory
4. **Other Modules** - Currently showing placeholder pages

### Start Development

1. **Read Documentation:**
   - `README.md` - Project overview
   - `SETUP.md` - Detailed setup guide
   - `PROJECT_STRUCTURE.md` - Architecture details
   - `PROJECT_SUMMARY.md` - Complete feature list

2. **Understand the Structure:**
   - `src/app/core/` - Services, guards, interceptors
   - `src/app/shared/` - Reusable components
   - `src/app/features/` - Feature modules

3. **Make Your First Change:**
   - Edit `src/app/features/dashboard/dashboard.component.html`
   - Save and watch it auto-reload
   - Explore other components

### Expand Placeholder Modules

The following modules have placeholder implementations:

1. **Security Module** (`src/app/features/security/`)
   - Implement ClamAV configuration form
   - Implement Rspamd configuration form
   - Implement blocklist management

2. **Monitoring Module** (`src/app/features/monitoring/`)
   - Add Chart.js visualizations for metrics
   - Implement real-time log viewer

3. **Settings Module** (`src/app/features/settings/`)
   - Create server configuration forms
   - Add user management CRUD

## Common Commands Reference

```bash
# Development
npm start                     # Start dev server
npm run build                 # Production build
npm test                      # Run tests

# Angular CLI
ng serve --open               # Start and open browser
ng serve --port 4300          # Use different port
ng generate component <name>  # Create new component
ng generate service <name>    # Create new service

# Development with proxy
ng serve --proxy-config proxy.conf.json
```

## Getting Help

### Documentation Files
1. `QUICKSTART.md` - Quick reference
2. `SETUP.md` - Detailed setup
3. `PROJECT_STRUCTURE.md` - Architecture
4. `FILES_CREATED.md` - Complete file list

### Check Logs
- **Browser Console:** F12 or Cmd+Option+I
- **Angular CLI:** Terminal output
- **Robin MTA:** Server terminal output

### Common Issues
- Port conflicts: Use different port
- CORS errors: Use proxy configuration
- Module errors: Reinstall dependencies
- TypeScript errors: Restart dev server

## Success Criteria

You're ready to develop when:

- ✅ `npm install` completed without errors
- ✅ `npm start` runs without errors
- ✅ Browser shows Robin UI
- ✅ Dashboard displays data from Robin API
- ✅ Navigation works between pages
- ✅ No console errors (except expected 404s for placeholder APIs)

## Next Steps After Setup

1. [ ] Read `PROJECT_STRUCTURE.md` to understand architecture
2. [ ] Explore existing components in `src/app/`
3. [ ] Try making a small change to test hot reload
4. [ ] Review Robin MTA API documentation
5. [ ] Plan which module to implement first
6. [ ] Start coding!

---

**Congratulations!** You now have a fully functional Angular application for managing Robin MTA Server.

For questions or issues, review the documentation files or check the inline code comments.
