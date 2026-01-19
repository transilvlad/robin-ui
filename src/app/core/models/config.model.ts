export interface ServerConfig {
  listeners: ListenerConfig[];
  storage: StorageConfig;
  queue: QueueConfig;
  security: SecurityConfig;
  relay: RelayConfig;
}

export interface ListenerConfig {
  name: string;
  port: number;
  protocol: 'smtp' | 'smtps' | 'lmtp';
  enabled: boolean;
  maxConnections: number;
  timeout: number;
  tlsEnabled: boolean;
}

export interface StorageConfig {
  type: 'local' | 'dovecot-lda' | 'dovecot-lmtp';
  path?: string;
  host?: string;
  port?: number;
}

export interface QueueConfig {
  backend: 'mapdb' | 'mariadb' | 'postgresql';
  path?: string;
  connectionUrl?: string;
  maxRetries: number;
  retryDelay: number;
}

export interface SecurityConfig {
  clamav: ClamAVConfig;
  rspamd: RspamdConfig;
  blocklist: string[];
}

export interface ClamAVConfig {
  enabled: boolean;
  host: string;
  port: number;
  timeout: number;
}

export interface RspamdConfig {
  enabled: boolean;
  host: string;
  port: number;
  rejectScore: number;
}

export interface RelayConfig {
  enabled: boolean;
  host: string;
  port: number;
  auth: boolean;
  username?: string;
  tls: boolean;
}
