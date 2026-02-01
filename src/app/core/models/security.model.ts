import { z } from 'zod';

// ==========================================
// ClamAV Configuration
// ==========================================

export const ClamAVConfigSchema = z.object({
  enabled: z.boolean(),
  host: z.string().min(1, 'Host is required'),
  port: z.number().int().min(1).max(65535, 'Port must be between 1 and 65535'),
  timeout: z.number().int().min(1000).max(60000, 'Timeout must be between 1s and 60s'),
  maxFileSize: z.number().int().min(1).optional(),
  scanArchives: z.boolean().optional(),
});

export type ClamAVConfig = z.infer<typeof ClamAVConfigSchema>;

export const ClamAVStatusSchema = z.object({
  status: z.enum(['UP', 'DOWN', 'UNKNOWN']),
  version: z.string().optional(),
  lastCheck: z.string().datetime().optional(),
  error: z.string().optional(),
});

export type ClamAVStatus = z.infer<typeof ClamAVStatusSchema>;

// ==========================================
// Rspamd Configuration
// ==========================================

export const RspamdConfigSchema = z.object({
  enabled: z.boolean(),
  host: z.string().min(1, 'Host is required'),
  port: z.number().int().min(1).max(65535, 'Port must be between 1 and 65535'),
  apiKey: z.string().optional(),
  rejectScore: z.number().min(1).max(100, 'Reject score must be between 1 and 100'),
  addHeaderScore: z.number().min(1).max(100, 'Add header score must be between 1 and 100'),
  greylistScore: z.number().min(1).max(100, 'Greylist score must be between 1 and 100').optional(),
  timeout: z.number().int().min(1000).max(60000).optional(),
});

export type RspamdConfig = z.infer<typeof RspamdConfigSchema>;

export const RspamdStatusSchema = z.object({
  status: z.enum(['UP', 'DOWN', 'UNKNOWN']),
  version: z.string().optional(),
  uptime: z.number().optional(),
  scannedTotal: z.number().optional(),
  spamCount: z.number().optional(),
  hamCount: z.number().optional(),
  lastCheck: z.string().datetime().optional(),
  error: z.string().optional(),
});

export type RspamdStatus = z.infer<typeof RspamdStatusSchema>;

// ==========================================
// Blocklist Management
// ==========================================

export enum BlocklistEntryType {
  IP = 'IP',
  CIDR = 'CIDR',
  DOMAIN = 'DOMAIN',
}

export const BlocklistEntrySchema = z.object({
  id: z.string().uuid().optional(),
  type: z.nativeEnum(BlocklistEntryType),
  value: z.string().min(1, 'Value is required'),
  reason: z.string().min(1, 'Reason is required').max(500),
  createdAt: z.string().datetime().optional(),
  createdBy: z.string().optional(),
  expiresAt: z.string().datetime().optional().nullable(),
  active: z.boolean().optional(),
});

export type BlocklistEntry = z.infer<typeof BlocklistEntrySchema>;

export const CreateBlocklistEntrySchema = BlocklistEntrySchema.omit({
  id: true,
  createdAt: true,
  createdBy: true,
});

export type CreateBlocklistEntry = z.infer<typeof CreateBlocklistEntrySchema>;

// ==========================================
// Security Configuration (Combined)
// ==========================================

export const SecurityConfigSchema = z.object({
  clamav: ClamAVConfigSchema,
  rspamd: RspamdConfigSchema,
  blocklistEnabled: z.boolean(),
  blocklistEntries: z.array(BlocklistEntrySchema).optional(),
});

export type SecurityConfig = z.infer<typeof SecurityConfigSchema>;

// ==========================================
// Scanner Test Results
// ==========================================

export const ScannerTestResultSchema = z.object({
  success: z.boolean(),
  status: z.enum(['UP', 'DOWN', 'TIMEOUT', 'ERROR']),
  message: z.string(),
  responseTime: z.number().optional(),
  details: z.record(z.unknown()).optional(),
});

export type ScannerTestResult = z.infer<typeof ScannerTestResultSchema>;

// ==========================================
// Validation Helpers
// ==========================================

/**
 * Validates an IP address (IPv4 or IPv6)
 */
export function isValidIP(value: string): boolean {
  const ipv4Regex = /^(\d{1,3}\.){3}\d{1,3}$/;
  const ipv6Regex = /^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$/;

  if (ipv4Regex.test(value)) {
    const parts = value.split('.');
    return parts.every(part => {
      const num = parseInt(part, 10);
      return num >= 0 && num <= 255;
    });
  }

  return ipv6Regex.test(value);
}

/**
 * Validates a CIDR notation (e.g., 192.168.1.0/24)
 */
export function isValidCIDR(value: string): boolean {
  const cidrRegex = /^(\d{1,3}\.){3}\d{1,3}\/\d{1,2}$/;

  if (!cidrRegex.test(value)) {
    return false;
  }

  const [ip, prefix] = value.split('/');
  const prefixNum = parseInt(prefix, 10);

  return isValidIP(ip) && prefixNum >= 0 && prefixNum <= 32;
}

/**
 * Validates a domain name
 */
export function isValidDomain(value: string): boolean {
  const domainRegex = /^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/i;
  return domainRegex.test(value);
}

/**
 * Validates a blocklist entry value based on its type
 */
export function validateBlocklistValue(type: BlocklistEntryType, value: string): boolean {
  switch (type) {
    case BlocklistEntryType.IP:
      return isValidIP(value);
    case BlocklistEntryType.CIDR:
      return isValidCIDR(value);
    case BlocklistEntryType.DOMAIN:
      return isValidDomain(value);
    default:
      return false;
  }
}
