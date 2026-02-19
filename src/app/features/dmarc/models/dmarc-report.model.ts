import { DmarcPolicy } from './dmarc-policy.model';
import { DmarcRecord } from './dmarc-record.model';

export interface DmarcMetadata {
  orgName: string;
  email: string;
  reportId: string;
  dateBegin: number;
  dateEnd: number;
}

export interface DmarcReport {
  id: string;
  metadata: DmarcMetadata;
  policy: DmarcPolicy;
  records: DmarcRecord[] | null;
  totalCount: number;
  ingestedAt: string;
}

export interface DmarcReportList {
  total: number;
  page: number;
  size: number;
  reports: DmarcReport[];
}

export interface DmarcValidationResult {
  domain: string;
  valid: boolean;
  record: Record<string, string>;
  errors: string[];
}

export interface DnsResult {
  domain: string;
  type: string;
  results: string[];
}
