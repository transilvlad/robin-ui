import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

/**
 * Centralized logging service for the application.
 * In production, errors can be sent to a logging service (e.g., Sentry, LogRocket).
 * In development, logs are output to the console.
 */
@Injectable({
  providedIn: 'root'
})
export class LoggingService {
  private readonly isProduction = environment.production;

  /**
   * Log an error message.
   * In production, this could send to an external logging service.
   */
  error(message: string, error?: unknown): void {
    if (this.isProduction) {
      // In production, send to external logging service
      // Example: Sentry.captureException(error);
      this.sendToExternalService('error', message, error);
    } else {
      // In development, log to console
      if (error) {
        console.error(`[ERROR] ${message}`, error);
      } else {
        console.error(`[ERROR] ${message}`);
      }
    }
  }

  /**
   * Log a warning message.
   */
  warn(message: string, data?: unknown): void {
    if (this.isProduction) {
      this.sendToExternalService('warn', message, data);
    } else {
      if (data) {
        console.warn(`[WARN] ${message}`, data);
      } else {
        console.warn(`[WARN] ${message}`);
      }
    }
  }

  /**
   * Log an info message.
   */
  info(message: string, data?: unknown): void {
    if (!this.isProduction) {
      // Only log info in development
      if (data) {
        console.info(`[INFO] ${message}`, data);
      } else {
        console.info(`[INFO] ${message}`);
      }
    }
  }

  /**
   * Log a debug message.
   * Only logs in development mode.
   */
  debug(message: string, data?: unknown): void {
    if (!this.isProduction) {
      if (data) {
        console.debug(`[DEBUG] ${message}`, data);
      } else {
        console.debug(`[DEBUG] ${message}`);
      }
    }
  }

  /**
   * Send logs to an external logging service (Sentry, LogRocket, etc.)
   * This is a placeholder - implement based on your logging service of choice.
   */
  private sendToExternalService(level: 'error' | 'warn' | 'info', message: string, data?: unknown): void {
    // TODO: Implement external logging service integration
    // Example with Sentry:
    // if (level === 'error' && data instanceof Error) {
    //   Sentry.captureException(data, { extra: { message } });
    // } else {
    //   Sentry.captureMessage(`${level.toUpperCase()}: ${message}`, level as SeverityLevel);
    // }

    // For now, still log to console in production (remove this in final implementation)
    console[level](`[${level.toUpperCase()}] ${message}`, data);
  }
}
