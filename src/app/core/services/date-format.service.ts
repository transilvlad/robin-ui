import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'robin-date-format';
const DEFAULT_PRESET = 'medium';

export interface DateFormatPreset {
  label: string;
  dateFormat: string;
  dateTimeFormat: string;
}

export const DATE_FORMAT_PRESETS: Record<string, DateFormatPreset> = {
  'medium':   { label: 'Mar 2, 2026',   dateFormat: 'MMM d, yyyy',  dateTimeFormat: 'MMM d, yyyy, h:mm a' },
  'short-us': { label: '03/02/2026',    dateFormat: 'MM/dd/yyyy',   dateTimeFormat: 'MM/dd/yyyy, h:mm a'  },
  'short-eu': { label: '02/03/2026',    dateFormat: 'dd/MM/yyyy',   dateTimeFormat: 'dd/MM/yyyy, HH:mm'   },
  'iso':      { label: '2026-03-02',    dateFormat: 'yyyy-MM-dd',   dateTimeFormat: 'yyyy-MM-dd HH:mm'    },
};

@Injectable({ providedIn: 'root' })
export class DateFormatService {
  readonly presetId = signal<string>(DEFAULT_PRESET);

  constructor() {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved && DATE_FORMAT_PRESETS[saved]) {
      this.presetId.set(saved);
    }
  }

  get dateFormat(): string {
    return DATE_FORMAT_PRESETS[this.presetId()]?.dateFormat ?? DATE_FORMAT_PRESETS[DEFAULT_PRESET].dateFormat;
  }

  get dateTimeFormat(): string {
    return DATE_FORMAT_PRESETS[this.presetId()]?.dateTimeFormat ?? DATE_FORMAT_PRESETS[DEFAULT_PRESET].dateTimeFormat;
  }

  setPreset(id: string): void {
    if (!DATE_FORMAT_PRESETS[id]) return;
    this.presetId.set(id);
    localStorage.setItem(STORAGE_KEY, id);
  }

  get presets(): { id: string; preset: DateFormatPreset }[] {
    return Object.entries(DATE_FORMAT_PRESETS).map(([id, preset]) => ({ id, preset }));
  }
}
