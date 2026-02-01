import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, interval, throwError } from 'rxjs';
import { map, catchError, switchMap, startWith } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  MetricsResponse,
  MetricsResponseSchema,
  LogsResponse,
  LogsResponseSchema,
  LogFilterParams,
  SystemStats,
  SystemStatsSchema,
  QueueStats,
  QueueStatsSchema,
  TimeRange,
  getTimeRangeMs,
} from '../models/monitoring.model';

/**
 * Monitoring service for metrics and logs.
 */
@Injectable({
  providedIn: 'root'
})
export class MonitoringService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}`;

  // ==========================================
  // Metrics Operations
  // ==========================================

  /**
   * Get metrics data for a specific time range
   */
  getMetrics(timeRange: TimeRange): Observable<MetricsResponse> {
    const endTime = new Date();
    const startTime = new Date(endTime.getTime() - getTimeRangeMs(timeRange));

    const params = new HttpParams()
      .set('start', startTime.toISOString())
      .set('end', endTime.toISOString());

    return this.http.get(`${this.baseUrl}/metrics`, { params }).pipe(
      map(data => MetricsResponseSchema.parse(data)),
      catchError(err => {
        console.error('Failed to fetch metrics:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Get real-time metrics with auto-refresh
   */
  getMetricsStream(timeRange: TimeRange, refreshIntervalMs = 30000): Observable<MetricsResponse> {
    return interval(refreshIntervalMs).pipe(
      startWith(0), // Emit immediately
      switchMap(() => this.getMetrics(timeRange))
    );
  }

  /**
   * Get system statistics
   */
  getSystemStats(): Observable<SystemStats> {
    return this.http.get(`${this.baseUrl}/metrics/system`).pipe(
      map(data => {
        const stats = SystemStatsSchema.parse(data);
        // Fix: Upstream returns uptime in milliseconds, but UI expects seconds
        return {
          ...stats,
          uptime: Math.floor(stats.uptime / 1000)
        };
      }),
      catchError(err => {
        console.error('Failed to fetch system stats:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Get queue statistics
   */
  getQueueStats(): Observable<QueueStats> {
    return this.http.get(`${this.baseUrl}/metrics/queue`).pipe(
      map(data => QueueStatsSchema.parse(data)),
      catchError(err => {
        console.error('Failed to fetch queue stats:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Export metrics as CSV
   */
  exportMetrics(timeRange: TimeRange): Observable<Blob> {
    const endTime = new Date();
    const startTime = new Date(endTime.getTime() - getTimeRangeMs(timeRange));

    const params = new HttpParams()
      .set('start', startTime.toISOString())
      .set('end', endTime.toISOString())
      .set('format', 'csv');

    return this.http.get(`${this.baseUrl}/metrics/export`, {
      params,
      responseType: 'blob'
    }).pipe(
      catchError(err => {
        console.error('Failed to export metrics:', err);
        return throwError(() => err);
      })
    );
  }

  // ==========================================
  // Logs Operations
  // ==========================================

  /**
   * Get logs with filtering
   */
  getLogs(filters: LogFilterParams = {}): Observable<LogsResponse> {
    let params = new HttpParams();

    if (filters.level) {
      params = params.set('level', filters.level);
    }
    if (filters.search) {
      params = params.set('search', filters.search);
    }
    if (filters.startTime) {
      params = params.set('startTime', filters.startTime);
    }
    if (filters.endTime) {
      params = params.set('endTime', filters.endTime);
    }
    if (filters.logger) {
      params = params.set('logger', filters.logger);
    }
    if (filters.limit !== undefined) {
      params = params.set('limit', filters.limit.toString());
    }
    if (filters.offset !== undefined) {
      params = params.set('offset', filters.offset.toString());
    }

    return this.http.get(`${this.baseUrl}/logs`, { params }).pipe(
      map(data => LogsResponseSchema.parse(data)),
      catchError(err => {
        console.error('Failed to fetch logs:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Get real-time logs with auto-refresh
   */
  getLogsStream(filters: LogFilterParams = {}, refreshIntervalMs = 5000): Observable<LogsResponse> {
    return interval(refreshIntervalMs).pipe(
      startWith(0), // Emit immediately
      switchMap(() => this.getLogs(filters))
    );
  }

  /**
   * Download logs as file
   */
  downloadLogs(filters: LogFilterParams = {}, format: 'txt' | 'json' = 'txt'): Observable<Blob> {
    let params = new HttpParams().set('format', format);

    if (filters.level) {
      params = params.set('level', filters.level);
    }
    if (filters.search) {
      params = params.set('search', filters.search);
    }
    if (filters.startTime) {
      params = params.set('startTime', filters.startTime);
    }
    if (filters.endTime) {
      params = params.set('endTime', filters.endTime);
    }
    if (filters.logger) {
      params = params.set('logger', filters.logger);
    }

    return this.http.get(`${this.baseUrl}/logs/export`, {
      params,
      responseType: 'blob'
    }).pipe(
      catchError(err => {
        console.error('Failed to download logs:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Get available log sources/loggers
   */
  getLoggers(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/logs/loggers`).pipe(
      catchError(err => {
        console.error('Failed to fetch loggers:', err);
        return throwError(() => err);
      })
    );
  }

  /**
   * Clear old logs (admin only)
   */
  clearLogs(beforeDate: Date): Observable<{ deleted: number }> {
    const params = new HttpParams().set('before', beforeDate.toISOString());

    return this.http.delete<{ deleted: number }>(`${this.baseUrl}/logs`, { params }).pipe(
      catchError(err => {
        console.error('Failed to clear logs:', err);
        return throwError(() => err);
      })
    );
  }
}
