import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DateFormatService, DATE_FORMAT_PRESETS } from '../../../core/services/date-format.service';
import { AppDatePipe } from '../../../shared/pipes/app-date.pipe';

@Component({
  selector: 'app-preferences-settings',
  standalone: true,
  imports: [CommonModule, AppDatePipe],
  templateUrl: './preferences-settings.component.html',
})
export class PreferencesSettingsComponent {
  private dateFormatService = inject(DateFormatService);

  readonly previewDate = new Date('2026-03-02T11:34:00');

  get presets() { return this.dateFormatService.presets; }
  get currentPresetId() { return this.dateFormatService.presetId(); }

  selectPreset(id: string): void {
    this.dateFormatService.setPreset(id);
  }
}
