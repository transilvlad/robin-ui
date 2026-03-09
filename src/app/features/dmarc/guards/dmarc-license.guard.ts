import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { DmarcLicenseService } from '../services/dmarc-license.service';

/**
 * Guard that checks for a valid DMARC license before activating a route.
 * Redirects to /settings if no valid license is found.
 */
export const dmarcLicenseGuard: CanActivateFn = async () => {
  const licenseService = inject(DmarcLicenseService);
  const router = inject(Router);

  // If already checked and valid, allow immediately
  if (licenseService.hasLicense()) {
    return true;
  }

  const valid = await licenseService.check();
  if (valid) {
    return true;
  }

  return router.createUrlTree(['/settings'], {
    queryParams: { notice: 'dmarc-license-required' }
  });
};
