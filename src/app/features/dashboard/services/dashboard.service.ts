import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { HealthResponse } from '@core/models/health.model';

@Injectable()
export class DashboardService {
  constructor(private apiService: ApiService) {}

  getHealth(): Observable<HealthResponse> {
    return this.apiService.getHealth();
  }

  getMetrics(): Observable<any> {
    return this.apiService.getMetrics();
  }
}
