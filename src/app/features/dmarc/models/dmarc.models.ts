// DMARC License

export interface DmarcLicense {
  valid: boolean;
  status: 'active' | 'expired' | 'missing' | 'invalid';
  license_id?: string;
  issued_to?: string;
  issued_at?: string;
  expires_at?: string;
  features?: {
    max_domains: number;
    forensic_reports: boolean;
    api_access: boolean;
    threat_detection: boolean;
  };
}

// Summary

export interface DmarcSummary {
  domain: string;
  from: string;
  to: string;
  totalMessages: number;
  dmarcPass: number;
  dmarcFail: number;
  compliancePercent: number;
  dkimAligned: number;
  spfAligned: number;
  uniqueSourceIps: number;
  uniqueReporters: number;
}

// Reports (paginated)

export interface DmarcReportItem {
  id: number;
  reportId: string;
  orgName: string;
  orgEmail: string;
  generator: string | null;
  dateBegin: string;
  dateEnd: string;
  receivedAt: string;
  domain: string;
  totalMessages: number;
}

export interface DmarcReportsPage {
  page: number;
  size: number;
  hasMore: boolean;
  items: DmarcReportItem[];
}

// Report detail

export interface DmarcRecordEntry {
  sourceIp: string;
  sourceCountry: string | null;
  sourceAsn: number | null;
  sourceKnownName: string | null;
  count: number;
  disposition: string;
  dkimResult: boolean;
  spfResult: boolean;
  dmarcPass: boolean;
  dateBegin: string;
  dateEnd: string;
}

export interface DmarcReportDetail {
  id: number;
  reportId: string;
  orgName: string;
  orgEmail: string;
  domain: string;
  dateBegin: string;
  dateEnd: string;
  records: DmarcRecordEntry[];
}

// Sources

export interface DmarcSourceItem {
  sourceIp: string;
  sourceCountry: string | null;
  sourceAsn: number | null;
  sourceAsnName: string | null;
  sourceRdns: string | null;
  sourceType: string | null;
  sourceKnownName: string | null;
  totalMessages: number;
  passedMessages: number;
  failedMessages: number;
  uniqueDomains: number;
}

export interface DmarcSourcesResponse {
  domain: string;
  from: string;
  to: string;
  items: DmarcSourceItem[];
}

// Compliance

export interface DmarcCompliance {
  domain: string;
  from: string;
  to: string;
  totalMessages: number;
  dmarcPass: number;
  dmarcFail: number;
  compliancePercent: number;
  dkimAligned: number;
  spfAligned: number;
}

// Daily analytics

export interface DmarcDailyPoint {
  date: string;
  totalMessages: number;
  dmarcPass: number;
  dmarcFail: number;
  compliancePercent: number;
  dkimAligned: number;
  spfAligned: number;
}

export interface DmarcDailyAnalytics {
  domain: string;
  from: string;
  to: string;
  points: DmarcDailyPoint[];
}

// Policy advice

export type DmarcRecommendation = 'advance' | 'stay' | 'caution';

export interface DmarcPolicyAdvice {
  domain: string;
  recommendation: DmarcRecommendation;
  reason: string;
  compliancePercent: number;
  totalMessages: number;
  evaluatedAt: string;
}

// Forensic reports (paginated)

export interface DmarcForensicItem {
  id: number;
  feedbackType: string;
  authFailure: string;
  identityAlignment: string;
  deliveryResult: string;
  originalMailFrom: string | null;
  sourceIp: string | null;
  sourceCountry: string | null;
  reportedDomain: string | null;
  sampleSubject: string | null;
  arrivalDate: string | null;
  receivedAt: string;
}

export interface DmarcForensicPage {
  page: number;
  size: number;
  hasMore: boolean;
  items: DmarcForensicItem[];
}

// Ingest response

export interface DmarcIngestResponse {
  accepted: boolean;
  type: 'aggregate' | 'forensic' | 'unknown';
  message: string;
}

// Query parameters

export interface DmarcDateRange {
  domain?: string;
  from?: string;
  to?: string;
  days?: number;
}

export interface DmarcPageParams extends DmarcDateRange {
  page?: number;
  size?: number;
}
