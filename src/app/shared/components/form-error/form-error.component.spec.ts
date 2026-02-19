import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, Validators } from '@angular/forms';
import { FormErrorComponent } from './form-error.component';

describe('FormErrorComponent', () => {
  let component: FormErrorComponent;
  let fixture: ComponentFixture<FormErrorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FormErrorComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(FormErrorComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('shouldShowError', () => {
    it('should return false when control is null', () => {
      component.control = null;
      expect(component.shouldShowError).toBe(false);
    });

    it('should return false when control is valid', () => {
      const control = new FormControl('value');
      component.control = control;
      expect(component.shouldShowError).toBe(false);
    });

    it('should return false when control is invalid but not touched (with showOnTouched=true)', () => {
      const control = new FormControl('', Validators.required);
      component.control = control;
      component.showOnTouched = true;
      expect(component.shouldShowError).toBe(false);
    });

    it('should return true when control is invalid and touched', () => {
      const control = new FormControl('', Validators.required);
      control.markAsTouched();
      component.control = control;
      component.showOnTouched = true;
      expect(component.shouldShowError).toBe(true);
    });

    it('should return true when control is invalid and showOnTouched=false', () => {
      const control = new FormControl('', Validators.required);
      component.control = control;
      component.showOnTouched = false;
      expect(component.shouldShowError).toBe(true);
    });
  });

  describe('errorMessage', () => {
    it('should return empty string when control is null', () => {
      component.control = null;
      expect(component.errorMessage).toBe('');
    });

    it('should return empty string when control has no errors', () => {
      const control = new FormControl('value');
      component.control = control;
      expect(component.errorMessage).toBe('');
    });

    it('should return default message for required error', () => {
      const control = new FormControl('', Validators.required);
      component.control = control;
      expect(component.errorMessage).toBe('This field is required');
    });

    it('should return default message for email error', () => {
      const control = new FormControl('invalid', Validators.email);
      component.control = control;
      expect(component.errorMessage).toBe('Please enter a valid email address');
    });

    it('should return custom message when provided', () => {
      const control = new FormControl('', Validators.required);
      component.control = control;
      component.customMessages = { required: 'Email is mandatory' };
      expect(component.errorMessage).toBe('Email is mandatory');
    });

    it('should return generic fallback for unknown error', () => {
      const control = new FormControl('');
      control.setErrors({ customError: true });
      component.control = control;
      expect(component.errorMessage).toBe('Invalid input');
    });

    it('should interpolate error values in message', () => {
      const control = new FormControl('ab', Validators.minLength(5));
      component.control = control;
      component.customMessages = { minlength: 'Minimum length is {requiredLength} characters' };
      expect(component.errorMessage).toBe('Minimum length is 5 characters');
    });
  });

  describe('errorId', () => {
    it('should generate correct ID from fieldName', () => {
      component.fieldName = 'email';
      expect(component.errorId).toBe('email-error');
    });

    it('should generate correct ID for nested field names', () => {
      component.fieldName = 'user.email';
      expect(component.errorId).toBe('user.email-error');
    });
  });

  describe('template rendering', () => {
    it('should not render error when shouldShowError is false', () => {
      component.control = new FormControl('value');
      fixture.detectChanges();
      const errorElement = fixture.nativeElement.querySelector('.form-error');
      expect(errorElement).toBeNull();
    });

    it('should render error when shouldShowError is true', () => {
      const control = new FormControl('', Validators.required);
      control.markAsTouched();
      component.control = control;
      component.fieldName = 'test';
      fixture.detectChanges();
      const errorElement = fixture.nativeElement.querySelector('.form-error');
      expect(errorElement).toBeTruthy();
    });

    it('should have correct ARIA attributes', () => {
      const control = new FormControl('', Validators.required);
      control.markAsTouched();
      component.control = control;
      component.fieldName = 'email';
      fixture.detectChanges();
      const errorElement = fixture.nativeElement.querySelector('.form-error');
      expect(errorElement.getAttribute('role')).toBe('alert');
      expect(errorElement.getAttribute('aria-live')).toBe('polite');
      expect(errorElement.getAttribute('id')).toBe('email-error');
    });

    it('should display error message in template', () => {
      const control = new FormControl('', Validators.required);
      control.markAsTouched();
      component.control = control;
      fixture.detectChanges();
      const messageElement = fixture.nativeElement.querySelector('.form-error-message');
      expect(messageElement.textContent.trim()).toBe('This field is required');
    });

    it('should hide icon from screen readers', () => {
      const control = new FormControl('', Validators.required);
      control.markAsTouched();
      component.control = control;
      fixture.detectChanges();
      const iconElement = fixture.nativeElement.querySelector('.form-error-icon');
      expect(iconElement.getAttribute('aria-hidden')).toBe('true');
    });
  });
});
