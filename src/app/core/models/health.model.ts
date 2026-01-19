export interface HealthResponse {
  status: 'UP' | 'DOWN';
  uptime: string;
  listeners: ListenerStats[];
  queue: QueueStats;
  scheduler: SchedulerConfig;
  metricsCron: MetricsCronConfig;
  botPool: PoolStats;
  lmtpPool: PoolStats;
}

export interface ListenerStats {
  name: string;
  port: number;
  protocol: string;
  active: boolean;
  connections: number;
}

export interface QueueStats {
  size: number;
  retryHistogram: Record<number, number>;
}

export interface SchedulerConfig {
  threadPoolSize: number;
  activeThreads: number;
  queuedTasks: number;
}

export interface MetricsCronConfig {
  enabled: boolean;
  schedule: string;
  lastRun?: string;
}

export interface PoolStats {
  poolSize: number;
  activeCount: number;
  queueSize: number;
  completedTasks: number;
}
