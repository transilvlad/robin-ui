import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, ValidationErrors } from '@angular/forms';

/**
 * Reusable form validation error component with accessibility support.
 * Displays validation error messages with proper ARIA attributes.
 *
 * @example
 * <app-form-error
 *   [control]="form.get('email')"
 *   [fieldName]="'email'"
 *   [customMessages]="{required: 'Email is required'}">
 * </app-form-error>
 */
@Component({
  selector: 'app-form-error',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './form-error.component.html',
  styleUrls: ['./form-error.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FormErrorComponent {
  /** The form control to display errors for */
  @Input() control: AbstractControl | null = null;

  /** The field name for generating the aria-describedby ID */
  @Input() fieldName = '';

  /** Custom error messages that override defaults */
  @Input() customMessages: Record<string, string> = {};

  /** Whether to only show errors after the field is touched (default: true) */
  @Input() showOnTouched = true;

  /**
   * Default error messages for common validators.
   */
  private readonly defaultMessages: Record<string, string> = {
    required: 'This field is required',
    email: 'Please enter a valid email address',
    minlength: 'This field is too short',
    maxlength: 'This field is too long',
    min: 'Value is too low',
    max: 'Value is too high',
    pattern: 'Please enter a valid format',
    url: 'Please enter a valid URL',
    number: 'Please enter a valid number',
    integer: 'Please enter a whole number',
    domain: 'Please enter a valid domain name'
  };

  /**
   * Checks if the control has errors that should be displayed.
   */
  get shouldShowError(): boolean {
    if (!this.control) {
      return false;
    }

    const hasError = this.control.invalid;
    const isTouched = this.control.touched;

    return this.showOnTouched ? (hasError && isTouched) : hasError;
  }

  /**
   * Gets the error message to display based on the control's validation errors.
   */
  get errorMessage(): string {
    if (!this.control || !this.control.errors) {
      return '';
    }

    const errors: ValidationErrors = this.control.errors;
    const errorKey = Object.keys(errors)[0]; // Get first error

    // Check for custom message first
    if (this.customMessages[errorKey]) {
      return this.interpolateMessage(this.customMessages[errorKey], errors[errorKey]);
    }

    // Fall back to default message
    if (this.defaultMessages[errorKey]) {
      return this.interpolateMessage(this.defaultMessages[errorKey], errors[errorKey]);
    }

    // Generic fallback
    return 'Invalid input';
  }

  /**
   * Gets the ID for aria-describedby attribute.
   */
  get errorId(): string {
    return `${this.fieldName}-error`;
  }

  /**
   * Interpolates error message with validation error data.
   * For example: "Minimum length is {requiredLength}" becomes "Minimum length is 8"
   */
  private interpolateMessage(message: string, errorValue: unknown): string {
    if (typeof errorValue !== 'object' || errorValue === null) {
      return message;
    }

    let result = message;
    const errorObj = errorValue as Record<string, unknown>;

    Object.keys(errorObj).forEach(key => {
      const placeholder = `{${key}}`;
      if (result.includes(placeholder)) {
        result = result.replace(placeholder, String(errorObj[key]));
      }
    });

    return result;
  }
}
