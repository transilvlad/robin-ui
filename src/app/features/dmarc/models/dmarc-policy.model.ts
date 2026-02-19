export interface DmarcPolicy {
  domain: string;
  p: string;
  sp: string;
  adkim: string;
  aspf: string;
  pct: number;
}
