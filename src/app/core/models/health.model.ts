export interface HealthResponse {
  status: 'UP' | 'DOWN';
  uptime?: string;
  listeners?: ListenerStats[];
  queue?: QueueStats;
  scheduler?: SchedulerInfo;
  metricsCron?: MetricsCronConfig;
  botPool?: PoolStats;
  lmtpPool?: PoolStats;
}

export interface ListenerStats {
  port: number;
  threadPool: ThreadPoolStats;
}

export interface ThreadPoolStats {
  core: number;
  max: number;
  size: number;
  largest: number;
  active: number;
  queue: number;
  taskCount: number;
  completed: number;
  keepAliveSeconds: number;
}

export interface QueueStats {
  size: number;
  retryHistogram: Record<string, number>;
}

export interface SchedulerInfo {
  config: SchedulerConfig;
  cron: CronInfo;
}

export interface SchedulerConfig {
  totalRetries: number;
  firstWaitMinutes: number;
  growthFactor: number;
}

export interface CronInfo {
  initialDelaySeconds: number;
  periodSeconds: number;
  lastExecutionEpochSeconds: number;
  nextExecutionEpochSeconds: number;
}

export interface MetricsCronConfig {
  intervalSeconds: number;
  lastExecutionEpochSeconds: number;
  nextExecutionEpochSeconds: number;
}

export interface PoolStats {
  enabled: boolean;
  type?: string;
  maxSize?: number;
  total?: number;
  poolSize?: number;
  idle?: number;
  borrowed?: number;
  activeThreads?: number;
  queueSize?: number;
  taskCount?: number;
  completedTaskCount?: number;
}
