# Form Validation Guide

**Last Updated:** 2026-02-06
**Component:** FormErrorComponent
**Status:** Production Ready

---

## Overview

The `FormErrorComponent` provides a standardized, accessible way to display form validation errors throughout the Robin UI application.

### Features

- ✅ **Consistent UI** - Unified error display across all forms
- ✅ **Accessibility** - WCAG 2.1 compliant with ARIA attributes
- ✅ **Customizable** - Override default messages per field
- ✅ **Type Safe** - TypeScript support with proper interfaces
- ✅ **OnPush Compatible** - Change detection strategy: OnPush
- ✅ **Standalone** - No module dependencies required

---

## Quick Start

### 1. Import the Component

```typescript
import { FormErrorComponent } from '@shared/components/form-error/form-error.component';

@Component({
  selector: 'app-my-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormErrorComponent  // Import here
  ]
})
```

### 2. Add to Your Template

```html
<div class="form-group">
  <label for="email">Email Address</label>
  <input
    type="email"
    id="email"
    formControlName="email"
    [attr.aria-describedby]="form.get('email')?.invalid && form.get('email')?.touched ? 'email-error' : null"
  >
  <app-form-error
    [control]="form.get('email')"
    [fieldName]="'email'">
  </app-form-error>
</div>
```

### 3. Add Validators in Component

```typescript
this.form = this.fb.group({
  email: ['', [Validators.required, Validators.email]],
  password: ['', [Validators.required, Validators.minLength(8)]]
});
```

---

## Component API

### Inputs

| Input | Type | Default | Description |
|-------|------|---------|-------------|
| `control` | `AbstractControl \| null` | `null` | The form control to display errors for |
| `fieldName` | `string` | `''` | Field name for generating aria-describedby ID |
| `customMessages` | `Record<string, string>` | `{}` | Custom error messages that override defaults |
| `showOnTouched` | `boolean` | `true` | Only show errors after field is touched |

### Default Error Messages

The component includes default messages for common validators:

```typescript
{
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
}
```

---

## Usage Examples

### Basic Usage

```html
<input
  type="text"
  formControlName="username"
  [attr.aria-describedby]="form.get('username')?.invalid && form.get('username')?.touched ? 'username-error' : null"
>
<app-form-error
  [control]="form.get('username')"
  [fieldName]="'username'">
</app-form-error>
```

### Custom Error Messages

```html
<input
  type="email"
  formControlName="email"
  [attr.aria-describedby]="form.get('email')?.invalid && form.get('email')?.touched ? 'email-error' : null"
>
<app-form-error
  [control]="form.get('email')"
  [fieldName]="'email'"
  [customMessages]="{
    required: 'Email address is mandatory',
    email: 'Please provide a valid email address'
  }">
</app-form-error>
```

### Show Errors Immediately (No Touch Required)

```html
<app-form-error
  [control]="form.get('field')"
  [fieldName]="'field'"
  [showOnTouched]="false">
</app-form-error>
```

### Message Interpolation

Custom messages can include placeholders that are replaced with error values:

```html
<input
  type="text"
  formControlName="username"
  [attr.aria-describedby]="form.get('username')?.invalid && form.get('username')?.touched ? 'username-error' : null"
>
<app-form-error
  [control]="form.get('username')"
  [fieldName]="'username'"
  [customMessages]="{
    minlength: 'Username must be at least {requiredLength} characters'
  }">
</app-form-error>
```

If the validator sets `minLength: 5`, the message becomes:
**"Username must be at least 5 characters"**

---

## Accessibility Features

### ARIA Attributes

The component automatically includes:

```html
<div
  id="fieldName-error"
  role="alert"
  aria-live="polite"
  class="form-error">
  <svg aria-hidden="true">...</svg>
  <span>Error message</span>
</div>
```

- **`role="alert"`** - Announces errors to screen readers
- **`aria-live="polite"`** - Non-intrusive announcements
- **`aria-hidden="true"`** on icon - Prevents duplicate announcements
- **`id="{fieldName}-error"`** - Links to input via aria-describedby

### Input Linking

Always link the input to the error message:

```html
<input
  id="email"
  formControlName="email"
  [attr.aria-describedby]="form.get('email')?.invalid && form.get('email')?.touched ? 'email-error' : null"
>
```

This ensures screen readers announce the error when the field is focused.

---

## Styling

### Default Styles

The component comes with pre-styled error messages:

- Red background with opacity
- Red left border
- Red error icon
- Smooth slide-in animation
- Dark mode support

### CSS Classes

```css
.form-error           /* Container */
.form-error-icon      /* Error icon */
.form-error-message   /* Error text */
```

### Customization

Override styles in your component:

```scss
::ng-deep app-form-error {
  .form-error {
    background-color: rgba(255, 152, 0, 0.1); // Orange
    border-left-color: rgb(255, 152, 0);
    color: rgb(255, 152, 0);
  }
}
```

---

## Complete Form Example

### TypeScript

```typescript
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { FormErrorComponent } from '@shared/components/form-error/form-error.component';

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormErrorComponent],
  templateUrl: './user-form.component.html'
})
export class UserFormComponent implements OnInit {
  form!: FormGroup;

  constructor(private fb: FormBuilder) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      age: ['', [Validators.required, Validators.min(18), Validators.max(120)]]
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.markFormGroupTouched(this.form);
      return;
    }
    // Process form...
  }

  private markFormGroupTouched(formGroup: FormGroup): void {
    Object.keys(formGroup.controls).forEach(key => {
      const control = formGroup.get(key);
      control?.markAsTouched();
    });
  }
}
```

### HTML Template

```html
<form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-6">
  <!-- Email Field -->
  <div class="space-y-2">
    <label for="email" class="text-sm font-medium">Email Address</label>
    <input
      type="email"
      id="email"
      formControlName="email"
      class="input"
      placeholder="user@example.com"
      [attr.aria-describedby]="form.get('email')?.invalid && form.get('email')?.touched ? 'email-error' : null"
    >
    <app-form-error
      [control]="form.get('email')"
      [fieldName]="'email'"
      [customMessages]="{
        required: 'Email address is required'
      }">
    </app-form-error>
  </div>

  <!-- Password Field -->
  <div class="space-y-2">
    <label for="password" class="text-sm font-medium">Password</label>
    <input
      type="password"
      id="password"
      formControlName="password"
      class="input"
      [attr.aria-describedby]="form.get('password')?.invalid && form.get('password')?.touched ? 'password-error' : null"
    >
    <app-form-error
      [control]="form.get('password')"
      [fieldName]="'password'"
      [customMessages]="{
        required: 'Password is required',
        minlength: 'Password must be at least 8 characters'
      }">
    </app-form-error>
  </div>

  <!-- Age Field -->
  <div class="space-y-2">
    <label for="age" class="text-sm font-medium">Age</label>
    <input
      type="number"
      id="age"
      formControlName="age"
      class="input"
      [attr.aria-describedby]="form.get('age')?.invalid && form.get('age')?.touched ? 'age-error' : null"
    >
    <app-form-error
      [control]="form.get('age')"
      [fieldName]="'age'"
      [customMessages]="{
        required: 'Age is required',
        min: 'You must be at least 18 years old',
        max: 'Please enter a valid age'
      }">
    </app-form-error>
  </div>

  <!-- Submit Button -->
  <button type="submit" class="btn-primary">Submit</button>
</form>
```

---

## Best Practices

### 1. Always Use aria-describedby

Link the input to the error message for accessibility:

```html
<input
  [attr.aria-describedby]="control?.invalid && control?.touched ? 'field-error' : null"
>
```

### 2. Validate Before Submit

Mark all fields as touched on submit to show all errors:

```typescript
onSubmit(): void {
  if (this.form.invalid) {
    this.markFormGroupTouched(this.form);
    return;
  }
  // Process form
}
```

### 3. Provide Contextual Messages

Customize messages to match your domain:

```typescript
[customMessages]="{
  required: 'Domain name is required',
  pattern: 'Domain must be a valid DNS name (e.g., example.com)'
}"
```

### 4. Use Semantic Field Names

Choose descriptive field names for clear error IDs:

```typescript
[fieldName]="'dmarcReportingEmail'"  // Good
[fieldName]="'field1'"               // Bad
```

---

## Testing

### Unit Tests

```typescript
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

  it('should display error when control is invalid and touched', () => {
    const control = new FormControl('', Validators.required);
    control.markAsTouched();
    component.control = control;
    component.fieldName = 'test';
    fixture.detectChanges();

    const errorElement = fixture.nativeElement.querySelector('.form-error');
    expect(errorElement).toBeTruthy();
    expect(errorElement.textContent).toContain('This field is required');
  });

  it('should use custom error message', () => {
    const control = new FormControl('', Validators.required);
    control.markAsTouched();
    component.control = control;
    component.fieldName = 'email';
    component.customMessages = { required: 'Email is mandatory' };
    fixture.detectChanges();

    const messageElement = fixture.nativeElement.querySelector('.form-error-message');
    expect(messageElement.textContent.trim()).toBe('Email is mandatory');
  });
});
```

---

## Migration Guide

### From Inline Error Display

**Before:**

```html
<input type="email" formControlName="email">
@if (form.get('email')?.invalid && form.get('email')?.touched) {
  <div class="error-message">
    {{ getErrorMessage('email') }}
  </div>
}
```

```typescript
getErrorMessage(fieldName: string): string {
  const control = this.form.get(fieldName);
  if (control?.errors?.['required']) {
    return 'Email is required';
  }
  if (control?.errors?.['email']) {
    return 'Invalid email';
  }
  return '';
}
```

**After:**

```html
<input
  type="email"
  formControlName="email"
  [attr.aria-describedby]="form.get('email')?.invalid && form.get('email')?.touched ? 'email-error' : null"
>
<app-form-error
  [control]="form.get('email')"
  [fieldName]="'email'"
  [customMessages]="{
    required: 'Email is required'
  }">
</app-form-error>
```

```typescript
// Remove getErrorMessage() method
// Component is now cleaner!
```

---

## Troubleshooting

### Error Not Showing

**Problem:** Error message doesn't appear when field is invalid.

**Solution:** Ensure the field is marked as touched:

```typescript
this.form.get('email')?.markAsTouched();
```

### Wrong Error Message

**Problem:** Displaying "Invalid input" instead of specific error.

**Solution:** Add custom message for that validator:

```typescript
[customMessages]="{ customValidator: 'Your message here' }"
```

### ARIA Warnings in Console

**Problem:** "aria-describedby references non-existent element"

**Solution:** Ensure fieldName matches the input's aria-describedby:

```html
<input [attr.aria-describedby]="... ? 'email-error' : null">
<app-form-error [fieldName]="'email'">  <!-- Generates 'email-error' -->
```

---

## Future Enhancements

Potential improvements:

- [ ] Support for multiple simultaneous error messages
- [ ] Animated error transitions (slide/fade)
- [ ] Custom icon support
- [ ] Integration with Angular Material
- [ ] Server-side validation error display
- [ ] Async validator support with loading states

---

## References

- [Angular Forms Documentation](https://angular.io/guide/forms-overview)
- [WCAG 2.1 Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [ARIA: alert role](https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Roles/alert_role)
- [Robin UI Style Guide](./STYLE_GUIDE.md)

---

**Last Updated:** 2026-02-06
**Maintained by:** Robin UI Development Team
