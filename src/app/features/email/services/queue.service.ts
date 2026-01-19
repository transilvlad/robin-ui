import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import {
  QueueListResponse,
  QueueActionRequest,
  QueueActionResponse,
} from '@core/models/queue.model';

@Injectable()
export class QueueService {
  constructor(private apiService: ApiService) {}

  getQueue(page = 0, size = 50): Observable<QueueListResponse> {
    return this.apiService.getQueue(page, size);
  }

  retryItem(uid: string): Observable<QueueActionResponse> {
    return this.apiService.performQueueAction(uid, 'retry');
  }

  deleteItem(uid: string): Observable<any> {
    return this.apiService.deleteQueueItem(uid);
  }

  pauseItem(uid: string): Observable<QueueActionResponse> {
    return this.apiService.performQueueAction(uid, 'pause');
  }

  resumeItem(uid: string): Observable<QueueActionResponse> {
    return this.apiService.performQueueAction(uid, 'resume');
  }
}
