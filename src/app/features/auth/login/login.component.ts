import { Component, OnInit, inject, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthStore } from '../../../core/state/auth.store';
import { LoginRequest } from '../../../core/models/auth.model';

/**
 * Login Component
 *
 * Standalone component for user authentication.
 * Features:
 * - Reactive form with validation
 * - Loading states
 * - Error display
 * - Responsive design
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule
  ],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {
  private fb = inject(FormBuilder);
  private router = inject(Router);
  protected authStore = inject(AuthStore);

  loginForm!: FormGroup;

  constructor() {
    // Watch loading state and enable/disable form
    effect(() => {
      if (this.loginForm) {
        if (this.authStore.loading()) {
          this.loginForm.disable();
        } else {
          this.loginForm.enable();
        }
      }
    });
  }

  ngOnInit(): void {
    this.initializeForm();

    // If already authenticated, redirect to dashboard
    if (this.authStore.isAuthenticated()) {
      this.router.navigate(['/dashboard']);
    }
  }

  private initializeForm(): void {
    this.loginForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      password: ['', [Validators.required, Validators.minLength(4)]]
    });
  }

  async onSubmit(): Promise<void> {
    if (this.loginForm.invalid) {
      this.markFormGroupTouched(this.loginForm);
      return;
    }

    const formValue = this.loginForm.value;
    const credentials: LoginRequest = {
      username: formValue.username.trim(),
      password: formValue.password,
      rememberMe: false // Default to false as checkbox was removed
    };
    await this.authStore.login(credentials);
  }

  private markFormGroupTouched(formGroup: FormGroup): void {
    Object.keys(formGroup.controls).forEach(key => {
      const control = formGroup.get(key);
      control?.markAsTouched();

      if (control instanceof FormGroup) {
        this.markFormGroupTouched(control);
      }
    });
  }

  getErrorMessage(fieldName: string): string {
    const control = this.loginForm.get(fieldName);
    if (!control || !control.errors || !control.touched) {
      return '';
    }

    if (control.errors['required']) {
      return `${this.getFieldLabel(fieldName)} is required`;
    }

    if (control.errors['minlength']) {
      const minLength = control.errors['minlength'].requiredLength;
      return `${this.getFieldLabel(fieldName)} must be at least ${minLength} characters`;
    }

    return 'Invalid field value';
  }

  private getFieldLabel(fieldName: string): string {
    const labels: Record<string, string> = {
      username: 'Email Address',
      password: 'Password',
    };
    return labels[fieldName] || fieldName;
  }
}
