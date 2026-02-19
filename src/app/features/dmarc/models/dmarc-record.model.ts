export type IpClass =
  | 'OWN'
  | 'AUTHORIZED'
  | 'DKIM_FORWARDER'
  | 'FORWARDER'
  | 'UNKNOWN';

export interface DmarcRecord {
  sourceIp: string;
  ptrHostname: string;
  disposition: string;
  dkimResult: string;
  spfResult: string;
  headerFrom: string;
  authDkimDomain: string;
  authSpfDomain: string;
  count: number;
  ipClass: IpClass;
}
