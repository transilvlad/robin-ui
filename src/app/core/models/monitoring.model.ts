import { z } from 'zod';

// ==========================================
// Metrics Models
// ==========================================

export enum MetricType {
  QUEUE_SIZE = 'QUEUE_SIZE',
  MESSAGES_SENT = 'MESSAGES_SENT',
  MESSAGES_RECEIVED = 'MESSAGES_RECEIVED',
  CONNECTIONS = 'CONNECTIONS',
  CPU_USAGE = 'CPU_USAGE',
  MEMORY_USAGE = 'MEMORY_USAGE',
  DISK_USAGE = 'DISK_USAGE',
}

export const MetricDataPointSchema = z.object({
  timestamp: z.string().datetime(),
  value: z.number(),
});

export type MetricDataPoint = z.infer<typeof MetricDataPointSchema>;

export const MetricSeriesSchema = z.object({
  metric: z.nativeEnum(MetricType),
  unit: z.string(),
  dataPoints: z.array(MetricDataPointSchema),
});

export type MetricSeries = z.infer<typeof MetricSeriesSchema>;

export const MetricsResponseSchema = z.object({
  series: z.array(MetricSeriesSchema),
  startTime: z.string().datetime(),
  endTime: z.string().datetime(),
  interval: z.number().optional(), // Interval in seconds
});

export type MetricsResponse = z.infer<typeof MetricsResponseSchema>;

// ==========================================
// Time Range
// ==========================================

export enum TimeRange {
  ONE_HOUR = '1h',
  SIX_HOURS = '6h',
  TWENTY_FOUR_HOURS = '24h',
  SEVEN_DAYS = '7d',
  THIRTY_DAYS = '30d',
}

export const TimeRangeLabels: Record<TimeRange, string> = {
  [TimeRange.ONE_HOUR]: 'Last Hour',
  [TimeRange.SIX_HOURS]: 'Last 6 Hours',
  [TimeRange.TWENTY_FOUR_HOURS]: 'Last 24 Hours',
  [TimeRange.SEVEN_DAYS]: 'Last 7 Days',
  [TimeRange.THIRTY_DAYS]: 'Last 30 Days',
};

// ==========================================
// Logs Models
// ==========================================

export enum LogLevel {
  ERROR = 'ERROR',
  WARN = 'WARN',
  INFO = 'INFO',
  DEBUG = 'DEBUG',
  TRACE = 'TRACE',
}

export const LogEntrySchema = z.object({
  id: z.string().optional(),
  timestamp: z.string().datetime(),
  level: z.nativeEnum(LogLevel),
  logger: z.string(),
  message: z.string(),
  thread: z.string().optional(),
  stackTrace: z.string().optional(),
  context: z.record(z.unknown()).optional(),
});

export type LogEntry = z.infer<typeof LogEntrySchema>;

export const LogsResponseSchema = z.object({
  entries: z.array(LogEntrySchema),
  total: z.number(),
  hasMore: z.boolean().optional(),
});

export type LogsResponse = z.infer<typeof LogsResponseSchema>;

export interface LogFilterParams {
  level?: LogLevel;
  search?: string;
  startTime?: string;
  endTime?: string;
  logger?: string;
  limit?: number;
  offset?: number;
}

// ==========================================
// System Stats
// ==========================================

export const SystemStatsSchema = z.object({
  timestamp: z.string().datetime(),
  cpu: z.object({
    usage: z.number().min(0).max(100).optional().default(0),
    cores: z.number().int().positive().optional(),
    processors: z.number().int().positive().optional(),
    loadAverage: z.array(z.number()).optional(),
  }),
  memory: z.object({
    total: z.number().positive(),
    used: z.number().nonnegative(),
    free: z.number().nonnegative(),
    max: z.number().optional(),
    usagePercent: z.number().min(0).max(100).optional().default(0),
  }),
  disk: z.object({
    total: z.number().positive(),
    used: z.number().nonnegative(),
    free: z.number().nonnegative(),
    usagePercent: z.number().min(0).max(100),
  }).optional(),
  network: z.object({
    bytesIn: z.number().nonnegative(),
    bytesOut: z.number().nonnegative(),
    packetsIn: z.number().nonnegative().optional(),
    packetsOut: z.number().nonnegative().optional(),
  }).optional(),
  uptime: z.number().nonnegative(), // Seconds
});

export type SystemStats = z.infer<typeof SystemStatsSchema>;

// ==========================================
// Queue Stats
// ==========================================

export const QueueStatsSchema = z.object({
  timestamp: z.string().datetime(),
  queueSize: z.number().int().nonnegative(),
  processing: z.number().int().nonnegative(),
  failed: z.number().int().nonnegative(),
  completed: z.number().int().nonnegative(),
  retrying: z.number().int().nonnegative().optional(),
  averageProcessingTime: z.number().nonnegative().optional(), // Milliseconds
  throughput: z.number().nonnegative().optional(), // Messages per minute
});

export type QueueStats = z.infer<typeof QueueStatsSchema>;

// ==========================================
// Chart Configuration
// ==========================================

export interface ChartDataset {
  label: string;
  data: number[];
  borderColor?: string;
  backgroundColor?: string;
  fill?: boolean;
  tension?: number;
}

export interface ChartConfig {
  type: 'line' | 'bar' | 'doughnut' | 'pie';
  labels: string[];
  datasets: ChartDataset[];
  options?: any;
}

// ==========================================
// Utility Functions
// ==========================================

/**
 * Get time range in milliseconds
 */
export function getTimeRangeMs(range: TimeRange): number {
  switch (range) {
    case TimeRange.ONE_HOUR:
      return 60 * 60 * 1000;
    case TimeRange.SIX_HOURS:
      return 6 * 60 * 60 * 1000;
    case TimeRange.TWENTY_FOUR_HOURS:
      return 24 * 60 * 60 * 1000;
    case TimeRange.SEVEN_DAYS:
      return 7 * 24 * 60 * 60 * 1000;
    case TimeRange.THIRTY_DAYS:
      return 30 * 24 * 60 * 60 * 1000;
    default:
      return 60 * 60 * 1000;
  }
}

/**
 * Format bytes to human-readable format
 */
export function formatBytes(bytes: number, decimals = 2): string {
  if (bytes === 0) return '0 Bytes';

  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB'];

  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

/**
 * Format uptime to human-readable format
 */
export function formatUptime(seconds: number): string {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = Math.floor(seconds % 60);

  const parts: string[] = [];
  if (days > 0) parts.push(`${days}d`);
  if (hours > 0) parts.push(`${hours}h`);
  if (minutes > 0) parts.push(`${minutes}m`);
  if (secs > 0 || parts.length === 0) parts.push(`${secs}s`);

  return parts.join(' ');
}

/**
 * Get log level color
 */
export function getLogLevelColor(level: LogLevel): string {
  switch (level) {
    case LogLevel.ERROR:
      return 'text-red-600';
    case LogLevel.WARN:
      return 'text-yellow-600';
    case LogLevel.INFO:
      return 'text-blue-600';
    case LogLevel.DEBUG:
      return 'text-gray-600';
    case LogLevel.TRACE:
      return 'text-gray-400';
    default:
      return 'text-gray-600';
  }
}

/**
 * Get log level background color
 */
export function getLogLevelBgColor(level: LogLevel): string {
  switch (level) {
    case LogLevel.ERROR:
      return 'bg-red-100';
    case LogLevel.WARN:
      return 'bg-yellow-100';
    case LogLevel.INFO:
      return 'bg-blue-100';
    case LogLevel.DEBUG:
      return 'bg-gray-100';
    case LogLevel.TRACE:
      return 'bg-gray-50';
    default:
      return 'bg-gray-100';
  }
}
