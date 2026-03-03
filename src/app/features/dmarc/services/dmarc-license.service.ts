import { Injectable, signal, computed } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { DmarcApiService } from './dmarc-api.service';
import { DmarcLicense } from '../models/dmarc.models';

@Injectable({ providedIn: 'root' })
export class DmarcLicenseService {
  private readonly _license = signal<DmarcLicense | null>(null);
  private readonly _loading = signal(false);

  readonly license = this._license.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly hasLicense = computed(() => this._license()?.valid === true);
  readonly licenseStatus = computed(() => this._license()?.status ?? 'missing');

  constructor(private api: DmarcApiService) {}

  async check(): Promise<boolean> {
    this._loading.set(true);
    const result = await lastValueFrom(this.api.getLicense());
    this._loading.set(false);

    if (result.ok) {
      this._license.set(result.value);
      return result.value.valid;
    }

    // 402/403 responses are expected when no license — treat as missing
    this._license.set({ valid: false, status: 'missing' });
    return false;
  }
}
