import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import {
  StorageListResponse,
  StorageFileContent,
} from '@core/models/storage.model';

@Injectable()
export class StorageService {
  constructor(private apiService: ApiService) {}

  getItems(path = '/'): Observable<StorageListResponse> {
    return this.apiService.getStorageItems(path);
  }

  getFile(path: string): Observable<StorageFileContent> {
    return this.apiService.getStorageFile(path);
  }
}
