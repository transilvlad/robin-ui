import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChangeDetectorRef, DebugElement, ChangeDetectionStrategy } from '@angular/core';
import { By } from '@angular/platform-browser';
import { StatusBadgeComponent } from './status-badge.component';

describe('StatusBadgeComponent - OnPush Change Detection', () => {
  let component: StatusBadgeComponent;
  let fixture: ComponentFixture<StatusBadgeComponent>;
  let debugElement: DebugElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [StatusBadgeComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(StatusBadgeComponent);
    component = fixture.componentInstance;
    debugElement = fixture.debugElement;
  });

  describe('Change Detection Strategy', () => {
    it('should use OnPush change detection strategy', () => {
      // Access component metadata
      const componentMetadata = (component.constructor as any).__annotations__ ||
                                (component.constructor as any).decorators ||
                                [];

      // Find Component decorator
      const componentDecorator = componentMetadata.find((d: any) =>
        d.ngMetadataName === 'Component' || d.type?.prototype?.ngMetadataName === 'Component'
      );

      expect(componentDecorator).toBeDefined();
      expect(componentDecorator.changeDetection).toBe(ChangeDetectionStrategy.OnPush);
    });
  });

  describe('Input Change Detection', () => {
    it('should update view when status input changes', () => {
      // Initial state
      component.status = 'UP';
      fixture.detectChanges();

      let badge = debugElement.query(By.css('.badge'));
      expect(badge.nativeElement.classList.contains('bg-robin-green/15')).toBe(true);

      // Change status (simulates input change)
      component.status = 'DOWN';
      fixture.detectChanges();

      badge = debugElement.query(By.css('.badge'));
      expect(badge.nativeElement.classList.contains('bg-destructive/15')).toBe(true);
    });

    it('should update view when label input changes', () => {
      component.status = 'UP';
      component.label = 'Healthy';
      fixture.detectChanges();

      const badge = debugElement.query(By.css('.badge'));
      expect(badge.nativeElement.textContent?.trim()).toContain('Healthy');

      component.label = 'All Systems Go';
      fixture.detectChanges();

      expect(badge.nativeElement.textContent?.trim()).toContain('All Systems Go');
    });

    it('should update view when size input changes', () => {
      component.size = 'sm';
      fixture.detectChanges();

      let badge = debugElement.query(By.css('.badge'));
      expect(badge.nativeElement.classList.contains('text-xs')).toBe(true);

      component.size = 'lg';
      fixture.detectChanges();

      badge = debugElement.query(By.css('.badge'));
      expect(badge.nativeElement.classList.contains('text-sm')).toBe(true);
    });

    it('should update view when showDot input changes', () => {
      component.showDot = true;
      fixture.detectChanges();

      let dot = debugElement.query(By.css('.rounded-full'));
      expect(dot).toBeTruthy();

      component.showDot = false;
      fixture.detectChanges();

      dot = debugElement.query(By.css('.rounded-full'));
      expect(dot).toBeFalsy();
    });
  });

  describe('Computed Properties', () => {
    it('should compute badgeClass correctly for all status values', () => {
      const testCases: Array<{ status: any; expectedClass: string }> = [
        { status: 'UP', expectedClass: 'bg-robin-green/15 text-robin-green' },
        { status: 'active', expectedClass: 'bg-robin-green/15 text-robin-green' },
        { status: 'DOWN', expectedClass: 'bg-destructive/15 text-destructive' },
        { status: 'error', expectedClass: 'bg-destructive/15 text-destructive' },
        { status: 'warning', expectedClass: 'bg-warning/15 text-warning' },
        { status: 'UNKNOWN', expectedClass: 'bg-warning/15 text-warning' },
        { status: 'inactive', expectedClass: 'bg-muted text-muted-foreground' }
      ];

      testCases.forEach(({ status, expectedClass }) => {
        component.status = status;
        fixture.detectChanges();

        expect(component.badgeClass).toContain(expectedClass);
      });
    });

    it('should compute dotClass correctly for all status values', () => {
      const testCases: Array<{ status: any; expectedDot: string }> = [
        { status: 'UP', expectedDot: 'bg-robin-green' },
        { status: 'active', expectedDot: 'bg-robin-green' },
        { status: 'DOWN', expectedDot: 'bg-destructive' },
        { status: 'error', expectedDot: 'bg-destructive' },
        { status: 'warning', expectedDot: 'bg-warning' },
        { status: 'UNKNOWN', expectedDot: 'bg-warning' },
        { status: 'inactive', expectedDot: 'bg-muted-foreground' }
      ];

      testCases.forEach(({ status, expectedDot }) => {
        component.status = status;
        fixture.detectChanges();

        expect(component.dotClass).toBe(expectedDot);
      });
    });

    it('should compute displayLabel correctly', () => {
      component.status = 'UP';
      component.label = undefined;
      fixture.detectChanges();

      expect(component.displayLabel).toBe('UP');

      component.label = 'Custom Label';
      fixture.detectChanges();

      expect(component.displayLabel).toBe('Custom Label');
    });
  });

  describe('OnPush Behavior Verification', () => {
    it('should render correctly with default inputs', () => {
      fixture.detectChanges();

      const badge = debugElement.query(By.css('.badge'));
      expect(badge).toBeTruthy();
      expect(component.status).toBe('inactive');
      expect(component.size).toBe('md');
      expect(component.showDot).toBe(true);
    });

    it('should handle rapid input changes efficiently', () => {
      const statuses: Array<'UP' | 'DOWN' | 'UNKNOWN'> = ['UP', 'DOWN', 'UNKNOWN', 'UP', 'DOWN'];

      statuses.forEach((status) => {
        component.status = status;
        fixture.detectChanges();

        // Verify each change is reflected
        const expectedClass = status === 'UP' ? 'bg-robin-green/15' :
                             status === 'DOWN' ? 'bg-destructive/15' :
                             'bg-warning/15';

        const badge = debugElement.query(By.css('.badge'));
        expect(badge.nativeElement.classList.contains(expectedClass)).toBe(true);
      });
    });
  });

  describe('Template Rendering', () => {
    it('should render badge with correct structure', () => {
      component.status = 'UP';
      component.label = 'Test Label';
      fixture.detectChanges();

      const badge = debugElement.query(By.css('.badge'));
      expect(badge).toBeTruthy();

      const dot = debugElement.query(By.css('.rounded-full'));
      expect(dot).toBeTruthy();

      expect(badge.nativeElement.textContent).toContain('Test Label');
    });

    it('should not render dot when showDot is false', () => {
      component.showDot = false;
      fixture.detectChanges();

      const dot = debugElement.query(By.css('.rounded-full'));
      expect(dot).toBeFalsy();
    });
  });
});
