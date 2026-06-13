import http from './http';
import type { AttachmentVO } from './attachments';

export type { AttachmentVO };

export interface AttachmentLinkedNote {
  id: string;
  title: string;
}

export interface DriveFolder {
  id: string;
  workspaceId: string;
  parentId?: string | null;
  name: string;
  sortOrder?: number;
  fileCount?: number;
}

export interface DriveFile extends AttachmentVO {
  workspaceId?: string;
  folderId?: string | null;
  folderName?: string | null;
  createdAt?: string;
  linkedNotes?: AttachmentLinkedNote[];
}

export interface DriveFileQuery {
  workspaceId: string;
  folderId?: string;
  uncategorized?: boolean;
  noteId?: string;
  unlinkedOnly?: boolean;
}

export function listDriveFiles(params: DriveFileQuery) {
  return http.get<DriveFile[]>('/drive/files', { params });
}

export function uploadDriveFile(workspaceId: string, file: File, folderId?: string) {
  const formData = new FormData();
  formData.append('file', file);
  return http.post<DriveFile>('/drive/upload', formData, {
    params: { workspaceId, folderId },
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  });
}

export function moveDriveFileToFolder(attachmentId: string, folderId?: string | null) {
  return http.patch<DriveFile>(`/drive/files/${attachmentId}/folder`, null, {
    params: folderId ? { folderId } : {},
  });
}

export function listDriveFolders(workspaceId: string) {
  return http.get<DriveFolder[]>('/drive/folders', { params: { workspaceId } });
}

export function createDriveFolder(workspaceId: string, name: string, parentId?: string | null) {
  return http.post<DriveFolder>('/drive/folders', { workspaceId, name, parentId: parentId ?? null });
}

export function updateDriveFolder(folderId: string, name: string) {
  return http.put<DriveFolder>(`/drive/folders/${folderId}`, { name });
}

export function deleteDriveFolder(folderId: string) {
  return http.delete<void>(`/drive/folders/${folderId}`);
}

export function linkDriveFileToNote(attachmentId: string, noteId: string) {
  return http.post<DriveFile>(`/attachments/${attachmentId}/link`, null, {
    params: { noteId },
  });
}

export function unlinkDriveFileFromNote(attachmentId: string, noteId: string) {
  return http.delete<void>(`/attachments/${attachmentId}/link`, {
    params: { noteId },
  });
}

export async function batchDownloadDriveFiles(ids: string[]) {
  if (!ids.length) {
    throw new Error('请选择要下载的文件');
  }
  const prepare = await http.post<{ ticket: string; filename: string }>(
    '/drive/files/batch-download/prepare',
    { ids },
  );
  const url = `/api/v1/drive/files/batch-download/${encodeURIComponent(prepare.ticket)}`;

  // 浏览器原生下载：避免 fetch 读取 ZIP 流时 DevTools/代理中断导致 ERR_FAILED
  const iframe = document.createElement('iframe');
  iframe.style.display = 'none';
  iframe.src = url;
  document.body.appendChild(iframe);
  window.setTimeout(() => {
    iframe.remove();
  }, 120_000);
}
