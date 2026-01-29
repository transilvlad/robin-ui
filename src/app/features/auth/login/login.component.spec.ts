import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { LoginComponent } from './login.component';
import { AuthStore } from '../../../core/state/auth.store';
import { AuthService } from '../../../core/services/auth.service';
import { TokenStorageService } from '../../../core/services/token-storage.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Router } from '@angular/router';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authStoreMock: jasmine.SpyObj<typeof AuthStore.prototype>;
  let compiled: HTMLElement;

  beforeEach(async () => {
    authStoreMock = jasmine.createSpyObj(
      'AuthStore',
      ['login'],
      {
        loading: jasmine.createSpy().and.returnValue(false),
        error: jasmine.createSpy().and.returnValue(null),
      }
    );

    await TestBed.configureTestingModule({
      imports: [
        LoginComponent,
        ReactiveFormsModule,
        NoopAnimationsModule,
        MatFormFieldModule,
        MatInputModule,
        MatButtonModule,
        MatCheckboxModule,
        MatIconModule,
        MatProgressSpinnerModule,
      ],
      providers: [
        { provide: AuthStore, useValue: authStoreMock },
        {
          provide: AuthService,
          useValue: jasmine.createSpyObj('AuthService', ['login']),
        },
        {
          provide: TokenStorageService,
          useValue: jasmine.createSpyObj('TokenStorageService', ['clear']),
        },
        {
          provide: NotificationService,
          useValue: jasmine.createSpyObj('NotificationService', ['info']),
        },
        {
          provide: Router,
          useValue: jasmine.createSpyObj('Router', ['navigate']),
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    compiled = fixture.nativeElement;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Form Initialization', () => {
    it('should initialize login form with empty values', () => {
      expect(component.loginForm.get('username')?.value).toBe('');
      expect(component.loginForm.get('password')?.value).toBe('');
      expect(component.loginForm.get('rememberMe')?.value).toBe(false);
    });

    it('should have form controls', () => {
      expect(component.loginForm.get('username')).toBeTruthy();
      expect(component.loginForm.get('password')).toBeTruthy();
      expect(component.loginForm.get('rememberMe')).toBeTruthy();
    });

    it('should initialize with hidePassword as true', () => {
      expect(component.hidePassword()).toBe(true);
    });
  });

  describe('Form Validation', () => {
    it('should be invalid when empty', () => {
      expect(component.loginForm.valid).toBe(false);
    });

    it('should require username', () => {
      const usernameControl = component.loginForm.get('username');
      expect(usernameControl?.hasError('required')).toBe(true);

      usernameControl?.setValue('test');
      expect(usernameControl?.hasError('required')).toBe(false);
    });

    it('should require username minimum 3 characters', () => {
      const usernameControl = component.loginForm.get('username');
      usernameControl?.setValue('ab');
      expect(usernameControl?.hasError('minlength')).toBe(true);

      usernameControl?.setValue('abc');
      expect(usernameControl?.hasError('minlength')).toBe(false);
    });

    it('should require username maximum 50 characters', () => {
      const usernameControl = component.loginForm.get('username');
      usernameControl?.setValue('a'.repeat(51));
      expect(usernameControl?.hasError('maxlength')).toBe(true);

      usernameControl?.setValue('a'.repeat(50));
      expect(usernameControl?.hasError('maxlength')).toBe(false);
    });

    it('should require password', () => {
      const passwordControl = component.loginForm.get('password');
      expect(passwordControl?.hasError('required')).toBe(true);

      passwordControl?.setValue('password');
      expect(passwordControl?.hasError('required')).toBe(false);
    });

    it('should require password minimum 8 characters', () => {
      const passwordControl = component.loginForm.get('password');
      passwordControl?.setValue('short');
      expect(passwordControl?.hasError('minlength')).toBe(true);

      passwordControl?.setValue('password');
      expect(passwordControl?.hasError('minlength')).toBe(false);
    });

    it('should be valid with correct inputs', () => {
      component.loginForm.patchValue({
        username: 'testuser',
        password: 'password123',
        rememberMe: true,
      });

      expect(component.loginForm.valid).toBe(true);
    });
  });

  describe('Form Submission', () => {
    beforeEach(() => {
      component.loginForm.patchValue({
        username: 'testuser',
        password: 'password123',
        rememberMe: false,
      });
    });

    it('should call authStore.login on valid form submission', async () => {
      authStoreMock.login.and.returnValue(Promise.resolve());

      await component.onSubmit();

      expect(authStoreMock.login).toHaveBeenCalledWith({
        username: 'testuser',
        password: 'password123',
        rememberMe: false,
      });
    });

    it('should not submit when form is invalid', async () => {
      component.loginForm.patchValue({
        username: 'ab', // Too short
        password: 'short', // Too short
      });

      await component.onSubmit();

      expect(authStoreMock.login).not.toHaveBeenCalled();
    });

    it('should not submit when form is already submitting', async () => {
      Object.defineProperty(authStoreMock, 'loading', {
        get: () => true,
      });

      await component.onSubmit();

      expect(authStoreMock.login).not.toHaveBeenCalled();
    });

    it('should pass rememberMe value correctly', async () => {
      authStoreMock.login.and.returnValue(Promise.resolve());

      component.loginForm.patchValue({ rememberMe: true });
      await component.onSubmit();

      expect(authStoreMock.login).toHaveBeenCalledWith(
        jasmine.objectContaining({ rememberMe: true })
      );
    });
  });

  describe('Password Visibility Toggle', () => {
    it('should toggle password visibility', () => {
      expect(component.hidePassword()).toBe(true);

      component.togglePasswordVisibility();
      expect(component.hidePassword()).toBe(false);

      component.togglePasswordVisibility();
      expect(component.hidePassword()).toBe(true);
    });

    it('should update password input type on toggle', () => {
      fixture.detectChanges();

      const passwordInput = compiled.querySelector(
        'input[formControlName="password"]'
      ) as HTMLInputElement;

      expect(passwordInput?.type).toBe('password');

      component.togglePasswordVisibility();
      fixture.detectChanges();

      expect(passwordInput?.type).toBe('text');
    });
  });

  describe('Template Rendering', () => {
    it('should render username input', () => {
      const usernameInput = compiled.querySelector(
        'input[formControlName="username"]'
      );
      expect(usernameInput).toBeTruthy();
    });

    it('should render password input', () => {
      const passwordInput = compiled.querySelector(
        'input[formControlName="password"]'
      );
      expect(passwordInput).toBeTruthy();
    });

    it('should render remember me checkbox', () => {
      const checkbox = compiled.querySelector(
        'input[formControlName="rememberMe"]'
      );
      expect(checkbox).toBeTruthy();
    });

    it('should render submit button', () => {
      const submitButton = compiled.querySelector('button[type="submit"]');
      expect(submitButton).toBeTruthy();
      expect(submitButton?.textContent?.trim()).toContain('Sign In');
    });

    it('should disable submit button when form is invalid', () => {
      component.loginForm.patchValue({
        username: '',
        password: '',
      });
      fixture.detectChanges();

      const submitButton = compiled.querySelector(
        'button[type="submit"]'
      ) as HTMLButtonElement;
      expect(submitButton?.disabled).toBe(true);
    });

    it('should enable submit button when form is valid', () => {
      component.loginForm.patchValue({
        username: 'testuser',
        password: 'password123',
      });
      fixture.detectChanges();

      const submitButton = compiled.querySelector(
        'button[type="submit"]'
      ) as HTMLButtonElement;
      expect(submitButton?.disabled).toBe(false);
    });

    it('should show loading spinner when loading', () => {
      Object.defineProperty(authStoreMock, 'loading', {
        get: () => true,
      });
      fixture.detectChanges();

      const spinner = compiled.querySelector('mat-spinner');
      expect(spinner).toBeTruthy();
    });

    it('should display error message when present', () => {
      Object.defineProperty(authStoreMock, 'error', {
        get: () => 'Invalid credentials',
      });
      fixture.detectChanges();

      const errorMessage = compiled.querySelector('.error-message');
      expect(errorMessage).toBeTruthy();
      expect(errorMessage?.textContent).toContain('Invalid credentials');
    });
  });

  describe('Validation Error Messages', () => {
    it('should show username required error', () => {
      const usernameControl = component.loginForm.get('username');
      usernameControl?.markAsTouched();
      fixture.detectChanges();

      const errorElement = compiled.querySelector(
        'mat-error[data-error="username-required"]'
      );
      expect(errorElement?.textContent).toContain('required');
    });

    it('should show username minlength error', () => {
      const usernameControl = component.loginForm.get('username');
      usernameControl?.setValue('ab');
      usernameControl?.markAsTouched();
      fixture.detectChanges();

      const errorElement = compiled.querySelector(
        'mat-error[data-error="username-minlength"]'
      );
      expect(errorElement?.textContent).toContain('3 characters');
    });

    it('should show password required error', () => {
      const passwordControl = component.loginForm.get('password');
      passwordControl?.markAsTouched();
      fixture.detectChanges();

      const errorElement = compiled.querySelector(
        'mat-error[data-error="password-required"]'
      );
      expect(errorElement?.textContent).toContain('required');
    });

    it('should show password minlength error', () => {
      const passwordControl = component.loginForm.get('password');
      passwordControl?.setValue('short');
      passwordControl?.markAsTouched();
      fixture.detectChanges();

      const errorElement = compiled.querySelector(
        'mat-error[data-error="password-minlength"]'
      );
      expect(errorElement?.textContent).toContain('8 characters');
    });
  });

  describe('User Interactions', () => {
    it('should update form value when user types in username', () => {
      const usernameInput = compiled.querySelector(
        'input[formControlName="username"]'
      ) as HTMLInputElement;

      usernameInput.value = 'newuser';
      usernameInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      expect(component.loginForm.get('username')?.value).toBe('newuser');
    });

    it('should update form value when user types in password', () => {
      const passwordInput = compiled.querySelector(
        'input[formControlName="password"]'
      ) as HTMLInputElement;

      passwordInput.value = 'newpassword';
      passwordInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      expect(component.loginForm.get('password')?.value).toBe('newpassword');
    });

    it('should update rememberMe when checkbox is clicked', () => {
      const checkbox = compiled.querySelector(
        'input[formControlName="rememberMe"]'
      ) as HTMLInputElement;

      checkbox.click();
      fixture.detectChanges();

      expect(component.loginForm.get('rememberMe')?.value).toBe(true);
    });

    it('should call onSubmit when form is submitted', async () => {
      spyOn(component, 'onSubmit');
      component.loginForm.patchValue({
        username: 'testuser',
        password: 'password123',
      });

      const form = compiled.querySelector('form') as HTMLFormElement;
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      expect(component.onSubmit).toHaveBeenCalled();
    });
  });

  describe('Accessibility', () => {
    it('should have proper labels for form fields', () => {
      const usernameLabel = compiled.querySelector('label[for="username"]');
      const passwordLabel = compiled.querySelector('label[for="password"]');

      expect(usernameLabel).toBeTruthy();
      expect(passwordLabel).toBeTruthy();
    });

    it('should have aria-label for password visibility toggle', () => {
      const toggleButton = compiled.querySelector(
        'button[aria-label*="password"]'
      );
      expect(toggleButton).toBeTruthy();
    });

    it('should mark form fields as touched on blur', () => {
      const usernameInput = compiled.querySelector(
        'input[formControlName="username"]'
      ) as HTMLInputElement;

      usernameInput.dispatchEvent(new Event('blur'));
      fixture.detectChanges();

      expect(component.loginForm.get('username')?.touched).toBe(true);
    });
  });

  describe('Edge Cases', () => {
    it('should handle rapid form submissions', async () => {
      authStoreMock.login.and.returnValue(
        new Promise((resolve) => setTimeout(resolve, 1000))
      );

      component.loginForm.patchValue({
        username: 'testuser',
        password: 'password123',
      });

      // Attempt multiple rapid submissions
      const promise1 = component.onSubmit();
      const promise2 = component.onSubmit();
      const promise3 = component.onSubmit();

      await Promise.all([promise1, promise2, promise3]);

      // Should only call login once due to loading state check
      expect(authStoreMock.login).toHaveBeenCalledTimes(1);
    });

    it('should trim whitespace from username', () => {
      component.loginForm.patchValue({
        username: '  testuser  ',
        password: 'password123',
      });

      const trimmedUsername =
        component.loginForm.get('username')?.value.trim();
      expect(trimmedUsername).toBe('testuser');
    });

    it('should handle very long inputs', () => {
      const longUsername = 'a'.repeat(100);
      const longPassword = 'p'.repeat(100);

      component.loginForm.patchValue({
        username: longUsername,
        password: longPassword,
      });

      expect(component.loginForm.get('username')?.hasError('maxlength')).toBe(
        true
      );
    });

    it('should handle special characters in inputs', () => {
      component.loginForm.patchValue({
        username: 'user@example.com',
        password: 'P@ssw0rd!#$%',
      });

      expect(component.loginForm.valid).toBe(true);
    });
  });
});
