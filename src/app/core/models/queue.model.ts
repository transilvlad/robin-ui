export interface QueueItem {
  uid: string;
  protocol: string;
  mailbox: string;
  retryCount: number;
  maxRetryCount: number;
  createTime: number;
  lastRetryTime: number;
  nextRetryTime?: number;
  error?: string;
}

export interface QueueListResponse {
  items: QueueItem[];
  totalCount: number;
  pageSize: number;
  pageNumber: number;
}

export interface QueueActionRequest {
  uid: string;
  action: 'retry' | 'delete' | 'pause' | 'resume';
}

export interface QueueActionResponse {
  success: boolean;
  message: string;
  uid: string;
}
