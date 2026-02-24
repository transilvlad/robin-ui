import { z } from 'zod';

export const DomainSchema = z.object({
  id: z.number(),
  domain: z.string(),
  status: z.string().optional(),
  dnsProviderId: z.number().nullable().optional(),
  nsProviderId: z.number().nullable().optional(),
  lastHealthCheck: z.string().nullable().optional(),
  createdAt: z.string().optional(),
  updatedAt: z.string().nullable().optional(),
});

export const DnsProviderSchema = z.object({
  id: z.number(),
  name: z.string(),
  type: z.enum(['CLOUDFLARE', 'AWS_ROUTE53']),
  credentials: z.string(),
  createdAt: z.string().optional(),
  updatedAt: z.string().optional(),
});

export const DnsTemplateSchema = z.object({
  id: z.number(),
  name: z.string(),
  description: z.string().nullable().optional(),
  records: z.string(),
  createdAt: z.string().optional(),
});

export const DomainDnsRecordSchema = z.object({
  id: z.number(),
  domainId: z.number(),
  recordType: z.string(),
  name: z.string(),
  value: z.string(),
  ttl: z.number().optional(),
  priority: z.number().nullable().optional(),
  providerRecordId: z.string().nullable().optional(),
  managed: z.boolean().optional(),
  createdAt: z.string().optional(),
  updatedAt: z.string().optional(),
});

export const DkimKeySchema = z.object({
  id: z.number(),
  domainId: z.number(),
  selector: z.string(),
  algorithm: z.enum(['RSA_2048', 'ED25519']),
  privateKey: z.string(),
  publicKey: z.string(),
  cnameSelector: z.string().nullable().optional(),
  status: z.enum(['ACTIVE', 'ROTATING', 'RETIRED']),
  createdAt: z.string().optional(),
  retiredAt: z.string().nullable().optional(),
});

export const DomainHealthSchema = z.object({
  id: z.number(),
  domainId: z.number(),
  checkType: z.enum(['SPF', 'DKIM', 'DMARC', 'MTA_STS', 'MX', 'NS']),
  status: z.enum(['OK', 'WARN', 'ERROR', 'UNKNOWN']),
  message: z.string().nullable().optional(),
  lastChecked: z.string().optional(),
});

export const MtaStsWorkerSchema = z.object({
  id: z.number(),
  domainId: z.number(),
  workerName: z.string(),
  workerId: z.string().nullable().optional(),
  policyMode: z.enum(['testing', 'enforce', 'none']),
  policyVersion: z.string().nullable().optional(),
  deployedAt: z.string().nullable().optional(),
  status: z.enum(['PENDING', 'DEPLOYED', 'ERROR']),
});

export type Domain = z.infer<typeof DomainSchema>;
export type DnsProvider = z.infer<typeof DnsProviderSchema>;
export type DnsTemplate = z.infer<typeof DnsTemplateSchema>;
export type DomainDnsRecord = z.infer<typeof DomainDnsRecordSchema>;
export type DkimKey = z.infer<typeof DkimKeySchema>;
export type DomainHealth = z.infer<typeof DomainHealthSchema>;
export type MtaStsWorker = z.infer<typeof MtaStsWorkerSchema>;

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
