import { Pipe, PipeTransform } from '@angular/core';
import { DatePipe } from '@angular/common';
import { DateFormatService } from '../../core/services/date-format.service';

@Pipe({
  name: 'appDate',
  standalone: true,
  pure: false,
})
export class AppDatePipe implements PipeTransform {
  private datePipe = new DatePipe('en-US');

  constructor(private dateFormatService: DateFormatService) {}

  transform(value: string | Date | null | undefined, mode: 'date' | 'datetime' = 'datetime'): string {
    if (value == null) return '—';
    const format = mode === 'date'
      ? this.dateFormatService.dateFormat
      : this.dateFormatService.dateTimeFormat;
    return this.datePipe.transform(value, format) ?? '—';
  }
}
