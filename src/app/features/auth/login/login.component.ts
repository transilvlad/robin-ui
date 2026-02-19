import { Component, OnInit, inject, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthStore } from '../../../core/state/auth.store';
import { LoginRequest } from '../../../core/models/auth.model';
import { FormErrorComponent } from '../../../shared/components/form-error/form-error.component';

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
    ReactiveFormsModule,
    FormErrorComponent
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
}
