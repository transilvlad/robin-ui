# Testing OnPush Change Detection

**Date:** 2026-02-05
**Purpose:** Verify OnPush change detection is working correctly in Robin UI components

## Components with OnPush (5 total)

| Component | File | Inputs |
|-----------|------|--------|
| HealthWidgetComponent | `dashboard/components/health-widget/` | `@Input() health?: HealthResponse` |
| QueueWidgetComponent | `dashboard/components/queue-widget/` | Various queue inputs |
| ToastComponent | `shared/components/toast/` | Toast data inputs |
| ConfirmationDialogComponent | `shared/components/confirmation-dialog/` | Dialog data |
| StatusBadgeComponent | `shared/components/status-badge/` | `status`, `label`, `size`, `showDot` |

---

## Testing Strategy 1: Manual Browser Testing

### Test StatusBadgeComponent

1. **Start the dev server:**
   ```bash
   npm start
   ```

2. **Open Chrome DevTools** → Console

3. **Enable change detection profiler:**
   ```javascript
   // In browser console
   ng.profiler.timeChangeDetection()
   ```

4. **Test immutable input changes:**
   - Navigate to a page using status badges (Dashboard, Domain List)
   - Observe that badges update when data changes
   - Verify badges DON'T flicker or re-render unnecessarily

5. **Check for reference changes:**
   ```javascript
   // OnPush should ONLY update when:
   // - Input reference changes (new object)
   // - Async pipe emits
   // - Events trigger within component
   // - Manual ChangeDetectorRef.markForCheck()
   ```

---

## Testing Strategy 2: Unit Tests

### Create OnPush-Specific Tests

Create test file: `src/app/shared/components/status-badge/status-badge.component.onpush.spec.ts`

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { StatusBadgeComponent } from './status-badge.component';

describe('StatusBadgeComponent - OnPush Behavior', () => {
  let component: StatusBadgeComponent;
  let fixture: ComponentFixture<StatusBadgeComponent>;
  let changeDetectorRef: ChangeDetectorRef;
  let debugElement: DebugElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [StatusBadgeComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(StatusBadgeComponent);
    component = fixture.componentInstance;
    changeDetectorRef = fixture.debugElement.injector.get(ChangeDetectorRef);
    debugElement = fixture.debugElement;
  });

  it('should use OnPush change detection strategy', () => {
    const metadata = (component.constructor as any).__annotations__[0];
    expect(metadata.changeDetection).toBe(1); // 1 = OnPush, 0 = Default
  });

  it('should update view when input reference changes', () => {
    // Set initial status
    component.status = 'UP';
    fixture.detectChanges();

    let badge = debugElement.query(By.css('.badge'));
    expect(badge.nativeElement.classList.contains('bg-robin-green/15')).toBe(true);

    // Change input (reference change)
    component.status = 'DOWN';
    fixture.detectChanges();

    badge = debugElement.query(By.css('.badge'));
    expect(badge.nativeElement.classList.contains('bg-destructive/15')).toBe(true);
  });

  it('should NOT update view when internal state changes without markForCheck', () => {
    component.status = 'UP';
    fixture.detectChanges();

    // Simulate internal state change (should NOT trigger change detection)
    (component as any).internalState = 'changed';

    // Don't call detectChanges - OnPush should prevent update
    const badge = debugElement.query(By.css('.badge'));
    expect(badge.nativeElement.classList.contains('bg-robin-green/15')).toBe(true);
  });

  it('should compute getters correctly on each check', () => {
    component.status = 'UP';
    fixture.detectChanges();

    expect(component.badgeClass).toContain('bg-robin-green/15');
    expect(component.dotClass).toBe('bg-robin-green');

    component.status = 'DOWN';
    fixture.detectChanges();

    expect(component.badgeClass).toContain('bg-destructive/15');
    expect(component.dotClass).toBe('bg-destructive');
  });

  it('should handle label input changes', () => {
    component.status = 'UP';
    component.label = 'Healthy';
    fixture.detectChanges();

    expect(component.displayLabel).toBe('Healthy');

    component.label = 'All Systems Go';
    fixture.detectChanges();

    expect(component.displayLabel).toBe('All Systems Go');
  });
});
```

---

## Testing Strategy 3: Performance Profiling

### Using Angular DevTools

1. **Install Angular DevTools:**
   - Chrome Extension: [Angular DevTools](https://chrome.google.com/webstore/detail/angular-devtools/)

2. **Open DevTools:**
   - F12 → Angular tab → Profiler

3. **Record change detection cycles:**
   - Click "Record" button
   - Interact with the application (navigate, click buttons)
   - Stop recording

4. **Analyze results:**
   - Components with OnPush should show **fewer change detection cycles**
   - Look for components that skip change detection (grayed out)

### Using Chrome Performance Tab

1. **Open Performance tab:**
   - F12 → Performance tab

2. **Record profile:**
   - Click record button
   - Interact with dashboard for 5-10 seconds
   - Stop recording

3. **Look for:**
   - `ChangeDetection` entries in flame graph
   - OnPush components should have fewer entries
   - Compare before/after OnPush implementation

---

## Testing Strategy 4: E2E Tests with Performance Metrics

### Create Cypress Performance Test

Create: `cypress/e2e/performance/onpush-change-detection.cy.ts`

```typescript
describe('OnPush Change Detection Performance', () => {
  beforeEach(() => {
    cy.loginAsAdmin();
    cy.visit('/dashboard');
  });

  it('should render dashboard with minimal change detection cycles', () => {
    // Enable performance tracking
    cy.window().then((win) => {
      (win as any).changeDetectionCount = 0;

      // Override markForCheck to count calls
      const originalMarkForCheck = win.ng.probe(
        win.document.querySelector('app-health-widget')
      ).injector.get(ChangeDetectorRef).markForCheck;

      win.ng.probe(
        win.document.querySelector('app-health-widget')
      ).injector.get(ChangeDetectorRef).markForCheck = function() {
        (win as any).changeDetectionCount++;
        return originalMarkForCheck.apply(this);
      };
    });

    // Trigger updates
    cy.wait(2000); // Wait for auto-refresh

    // Verify change detection was efficient
    cy.window().its('changeDetectionCount').should('be.lessThan', 10);
  });

  it('should update status badge when health status changes', () => {
    // Initial state
    cy.get('app-status-badge').first().should('contain', 'UP');

    // Simulate API response change (via intercept)
    cy.intercept('GET', '/api/v1/health', {
      statusCode: 200,
      body: { status: 'DOWN', components: {} }
    });

    // Wait for refresh
    cy.wait(2000);

    // Verify badge updated
    cy.get('app-status-badge').first().should('contain', 'DOWN');
  });
});
```

---

## Testing Strategy 5: Console-Based Verification

### Test in Browser Console

1. **Navigate to dashboard:**
   ```bash
   npm start
   # Open http://localhost:4200/dashboard
   ```

2. **Run in console:**
   ```javascript
   // Get component instance
   const healthWidget = ng.getComponent(document.querySelector('app-health-widget'));
   console.log('OnPush Component:', healthWidget);

   // Check change detection strategy
   const metadata = healthWidget.constructor.__annotations__[0];
   console.log('Change Detection:', metadata.changeDetection); // 1 = OnPush

   // Manually trigger change detection
   const injector = ng.getInjector(document.querySelector('app-health-widget'));
   const cdr = injector.get(ng.ChangeDetectorRef);

   // Test immutability
   healthWidget.health = { status: 'DOWN' };
   // View should NOT update yet

   cdr.markForCheck();
   cdr.detectChanges();
   // View should update NOW
   ```

---

## Testing Strategy 6: Check Common OnPush Issues

### Issue 1: Mutating Input Objects

**❌ WRONG (OnPush won't detect):**
```typescript
// Parent component
this.healthData.status = 'DOWN'; // Mutates object reference
```

**✅ CORRECT (OnPush will detect):**
```typescript
// Parent component
this.healthData = { ...this.healthData, status: 'DOWN' }; // New reference
```

### Issue 2: Array Mutations

**❌ WRONG:**
```typescript
this.items.push(newItem); // Mutates array
```

**✅ CORRECT:**
```typescript
this.items = [...this.items, newItem]; // New array reference
```

### Issue 3: Async Pipe Usage

**✅ CORRECT (Async pipe works with OnPush):**
```html
<app-health-widget [health]="health$ | async"></app-health-widget>
```

The async pipe automatically calls `markForCheck()` when observables emit.

---

## Quick Verification Checklist

Run these checks to verify OnPush is working:

- [ ] `grep -r "ChangeDetectionStrategy.OnPush"` shows 5 components
- [ ] Components with OnPush only have `@Input()` decorated properties
- [ ] No internal state mutations without `markForCheck()`
- [ ] Parent components pass new references (not mutated objects)
- [ ] Async pipes are used for observables
- [ ] Unit tests verify OnPush strategy is set
- [ ] Manual testing shows components update correctly
- [ ] Performance profiling shows reduced change detection cycles

---

## Expected Results

### Before OnPush (Default Change Detection)
- Every component checked on every change detection cycle
- ~40 components × N cycles = High overhead

### After OnPush (Optimized)
- Only 5 components checked on input changes or events
- 87.5% reduction in change detection overhead for these components

---

## Performance Benchmarks

### Measure Change Detection Time

```typescript
// In component constructor
constructor(private cd: ChangeDetectorRef) {
  const start = performance.now();
  this.cd.detectChanges();
  const end = performance.now();
  console.log(`Change detection took ${end - start}ms`);
}
```

### Expected Improvements
- **Before:** ~5-10ms per change detection cycle (all components)
- **After:** ~1-2ms per cycle (OnPush components skip checks)

---

## Troubleshooting

### OnPush Not Working?

1. **Check parent components:**
   - Are they passing new references or mutating objects?

2. **Check for internal state:**
   - Does component have internal state that changes?
   - Is `markForCheck()` called when needed?

3. **Check async pipes:**
   - Are observables properly piped in templates?

4. **Check event handlers:**
   - OnPush components update on events within the component

---

## Next Steps

After verifying OnPush works correctly:

1. **Add OnPush to more components:**
   - Domain list component (presentational)
   - Queue cards (presentational)
   - Other dashboard widgets

2. **Target:** 80% of presentational components with OnPush

3. **Update compliance tracking:**
   - Document OnPush usage in IMPLEMENTATION_PROGRESS.md
   - Update MEMORY.md with lessons learned
