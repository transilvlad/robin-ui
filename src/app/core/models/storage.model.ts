export interface StorageItem {
  path: string;
  name: string;
  type: 'file' | 'directory';
  size: number;
  modified: number;
  permissions?: string;
}

export interface StorageListResponse {
  path: string;
  items: StorageItem[];
  breadcrumbs: BreadcrumbItem[];
}

export interface BreadcrumbItem {
  name: string;
  path: string;
}

export interface StorageFileContent {
  path: string;
  content: string;
  mimeType: string;
  size: number;
}
