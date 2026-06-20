import { getAuthToken, request } from '../utils/http';
import { getApiBaseUrl } from '../utils/config';

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
  linkedNotes?: Array<{ id: string; title: string }>;
}

export interface DriveFolder {
  id: string;
  workspaceId: string;
  parentId?: string | null;
  name: string;
  sortOrder?: number;
  fileCount?: number;
}

export interface DriveBatchDownloadPayload {
  ids?: string[];
  folderIds?: string[];
  uncategorized?: boolean;
  unlinkedOnly?: boolean;
  workspaceId?: string;
}

export function listDriveFiles(params: {
  workspaceId: string;
  folderId?: string;
  uncategorized?: boolean;
  unlinkedOnly?: boolean;
  keyword?: string;
}) {
  return request<DriveFile[]>({ url: '/drive/files', params });
}

export function listDriveFolders(workspaceId: string, keyword?: string) {
  return request<DriveFolder[]>({
    url: '/drive/folders',
    params: keyword ? { workspaceId, keyword } : { workspaceId },
  });
}

export function createDriveFolder(workspaceId: string, name: string, parentId?: string | null) {
  return request<DriveFolder>({
    url: '/drive/folders',
    method: 'POST',
    data: { workspaceId, name, parentId: parentId ?? null },
  });
}

export function updateDriveFolder(folderId: string, name: string) {
  return request<DriveFolder>({ url: `/drive/folders/${folderId}`, method: 'PUT', data: { name } });
}

export function deleteDriveFolder(folderId: string) {
  return request<void>({ url: `/drive/folders/${folderId}`, method: 'DELETE' });
}

export function moveDriveFileToFolder(attachmentId: string, folderId?: string | null) {
  return request<DriveFile>({
    url: `/drive/files/${attachmentId}/folder`,
    method: 'PATCH',
    params: folderId ? { folderId } : {},
  });
}

export function linkDriveFileToNote(attachmentId: string, noteId: string) {
  return request<DriveFile>({
    url: `/attachments/${attachmentId}/link`,
    method: 'POST',
    params: { noteId },
  });
}

export function unlinkDriveFileFromNote(attachmentId: string, noteId: string) {
  return request<void>({
    url: `/attachments/${attachmentId}/link`,
    method: 'DELETE',
    params: { noteId },
  });
}

export function deleteDriveFile(id: string) {
  return request<void>({ url: `/attachments/${id}`, method: 'DELETE' });
}

export function prepareDriveBatchDownload(payload: DriveBatchDownloadPayload) {
  const hasFiles = payload.ids?.length;
  const hasFolders = payload.folderIds?.length;
  const hasVirtual = payload.uncategorized || payload.unlinkedOnly;
  if (!hasFiles && !hasFolders && !hasVirtual) {
    throw new Error('请选择要下载的项目');
  }

  return request<{ ticket: string; filename: string }>({
    url: '/drive/files/batch-download/prepare',
    method: 'POST',
    data: payload,
  });
}

export async function batchDownloadDriveSelection(payload: DriveBatchDownloadPayload) {
  const prepare = await prepareDriveBatchDownload(payload);
  const url = `${getApiBaseUrl()}/drive/files/batch-download/${encodeURIComponent(prepare.ticket)}`;
  const filename = prepare.filename || 'noto-drive.zip';
  const token = getAuthToken();

  // #ifdef H5
  const response = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) throw new Error('批量下载失败');
  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(objectUrl);
  return;
  // #endif

  // #ifndef H5
  return new Promise<void>((resolve, reject) => {
    uni.downloadFile({
      url,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        if ((res.statusCode || 0) < 200 || (res.statusCode || 0) >= 300) {
          reject(new Error('批量下载失败'));
          return;
        }
        uni.openDocument({
          filePath: res.tempFilePath,
          showMenu: true,
          success: () => resolve(),
          fail: () => reject(new Error('ZIP 已下载，但当前设备无法直接打开，请在文件管理器中查看')),
        });
      },
      fail: () => reject(new Error('批量下载失败')),
    });
  });
  // #endif
}

export function uploadDriveFilePath(payload: {
  workspaceId: string;
  filePath: string;
  name?: string;
  folderId?: string | null;
}) {
  const params = new URLSearchParams({ workspaceId: payload.workspaceId });
  if (payload.folderId) params.set('folderId', payload.folderId);
  const url = `${getApiBaseUrl()}/drive/upload?${params.toString()}`;

  return new Promise<DriveFile>((resolve, reject) => {
    (uni as any).uploadFile({
      url,
      filePath: payload.filePath,
      name: 'file',
      fileName: payload.name,
      header: getAuthToken() ? { Authorization: `Bearer ${getAuthToken()}` } : {},
      success: (res: any) => {
        const status = res.statusCode || 0;
        let body: any = res.data;
        if (typeof body === 'string') {
          try {
            body = JSON.parse(body);
          } catch {
            body = { message: body };
          }
        }
        if (status < 200 || status >= 300) {
          reject(new Error(body?.message || `上传失败 (${status})`));
          return;
        }
        if (body && typeof body === 'object' && 'code' in body) {
          if (body.code !== 0) {
            reject(new Error(body.message || '上传失败'));
            return;
          }
          resolve(body.data as DriveFile);
          return;
        }
        resolve(body as DriveFile);
      },
      fail: () => reject(new Error('上传失败')),
    });
  });
}
