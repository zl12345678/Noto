import { request } from '../utils/http';

export interface DriveFile {
  id: string;
  workspaceId?: string;
  folderId?: string | null;
  folderName?: string | null;
  fileName: string;
  fileType: string;
  fileSize: number;
  fileUrl: string;
  createdAt?: string;
}

export function listDriveFiles(params: { workspaceId: string; keyword?: string }) {
  return request<DriveFile[]>({ url: '/drive/files', params });
}
