import { z } from 'zod';

export enum DomainStatus {
  PENDING = 'PENDING',
  ACTIVE = 'ACTIVE',
  ERROR = 'ERROR',
}

export enum DnsProviderType {
  CLOUDFLARE = 'CLOUDFLARE',
  AWS_ROUTE53 = 'AWS_ROUTE53',
}

export enum DkimAlgorithm {
  RSA_2048 = 'RSA_2048',
  ED25519 = 'ED25519',
}

export enum DkimKeyStatus {
  ACTIVE = 'ACTIVE',
  ROTATING = 'ROTATING',
  RETIRED = 'RETIRED',
}

export enum DomainHealthCheckType {
  SPF = 'SPF',
  DKIM = 'DKIM',
  DMARC = 'DMARC',
  MTA_STS = 'MTA_STS',
  MTA_STS_TXT = 'MTA_STS_TXT',
  MX = 'MX',
  NS = 'NS',
}

export enum DomainHealthStatus {
  OK = 'OK',
  WARN = 'WARN',
  ERROR = 'ERROR',
  UNKNOWN = 'UNKNOWN',
}

export enum MtaStsPolicyMode {
  TESTING = 'testing',
  ENFORCE = 'enforce',
  NONE = 'none',
}

export enum MtaStsWorkerStatus {
  PENDING = 'PENDING',
  DEPLOYED = 'DEPLOYED',
  ERROR = 'ERROR',
}

const TimestampStringSchema = z.string().min(1);

export const DomainSchema = z.object({
  id: z.number(),
  domain: z.string().min(1),
  status: z.nativeEnum(DomainStatus).optional(),
  dnsProviderId: z.number().nullable().optional(),
  nsProviderId: z.number().nullable().optional(),
  lastHealthCheck: TimestampStringSchema.nullable().optional(),
  createdAt: TimestampStringSchema.optional(),
  updatedAt: TimestampStringSchema.nullable().optional(),
});

export const DnsProviderSchema = z.object({
  id: z.number(),
  name: z.string().min(1),
  type: z.nativeEnum(DnsProviderType),
  credentials: z.string().optional(),
  createdAt: TimestampStringSchema.optional(),
  updatedAt: TimestampStringSchema.optional(),
});

export const DnsTemplateRecordSchema = z.object({
  type: z.string().min(1),
  name: z.string().min(1),
  value: z.string().min(1),
  ttl: z.number().int().positive().optional(),
  priority: z.number().int().nonnegative().optional(),
});

export const DnsTemplateSchema = z.object({
  id: z.number(),
  name: z.string().min(1),
  description: z.string().nullable().optional(),
  records: z.string(),
  createdAt: TimestampStringSchema.optional(),
});

export const DomainDnsRecordSchema = z.object({
  id: z.number(),
  domainId: z.number(),
  recordType: z.string().min(1),
  name: z.string().min(1),
  value: z.string().min(1),
  ttl: z.number().int().positive().optional(),
  priority: z.number().nullable().optional(),
  providerRecordId: z.string().nullable().optional(),
  managed: z.boolean().optional(),
  createdAt: TimestampStringSchema.optional(),
  updatedAt: TimestampStringSchema.optional(),
});

export const DkimKeySchema = z.object({
  id: z.number(),
  domainId: z.number(),
  selector: z.string().min(1),
  algorithm: z.nativeEnum(DkimAlgorithm),
  privateKey: z.string(),
  publicKey: z.string(),
  cnameSelector: z.string().nullable().optional(),
  status: z.nativeEnum(DkimKeyStatus),
  createdAt: TimestampStringSchema.optional(),
  retiredAt: TimestampStringSchema.nullable().optional(),
});

export const DomainHealthSchema = z.object({
  id: z.number(),
  domainId: z.number(),
  checkType: z.nativeEnum(DomainHealthCheckType),
  status: z.nativeEnum(DomainHealthStatus),
  message: z.string().nullable().optional(),
  lastChecked: TimestampStringSchema.optional(),
});

export const MtaStsWorkerSchema = z.object({
  id: z.number(),
  domainId: z.number(),
  workerName: z.string().min(1),
  workerId: z.string().nullable().optional(),
  policyMode: z.nativeEnum(MtaStsPolicyMode),
  policyVersion: z.string().nullable().optional(),
  deployedAt: TimestampStringSchema.nullable().optional(),
  status: z.nativeEnum(MtaStsWorkerStatus),
});

export type Domain = z.infer<typeof DomainSchema>;
export type DnsProvider = z.infer<typeof DnsProviderSchema>;
export type DnsTemplateRecord = z.infer<typeof DnsTemplateRecordSchema>;
export type DnsTemplate = z.infer<typeof DnsTemplateSchema>;
export type DomainDnsRecord = z.infer<typeof DomainDnsRecordSchema>;
export type DkimKey = z.infer<typeof DkimKeySchema>;
export type DomainHealth = z.infer<typeof DomainHealthSchema>;
export type MtaStsWorker = z.infer<typeof MtaStsWorkerSchema>;

export const buildPageResponseSchema = <T extends z.ZodTypeAny>(itemSchema: T) => z.object({
  content: z.array(itemSchema),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  number: z.number().int().nonnegative(),
  size: z.number().int().positive(),
});

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const DomainListSchema = z.array(DomainSchema);
export const DnsProviderListSchema = z.array(DnsProviderSchema);
export const DnsTemplateListSchema = z.array(DnsTemplateSchema);
export const DomainDnsRecordListSchema = z.array(DomainDnsRecordSchema);
export const DkimKeyListSchema = z.array(DkimKeySchema);
export const DomainHealthListSchema = z.array(DomainHealthSchema);
