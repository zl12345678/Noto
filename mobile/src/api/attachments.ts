import { getAuthToken, request } from '../utils/http';
import { getApiBaseUrl } from '../utils/config';

export interface AttachmentLinkedNote {
  id: string;
  title: string;
}

export interface AttachmentVO {
  id: string;
  workspaceId?: string;
  noteId?: string | null;
  folderId?: string | null;
  folderName?: string | null;
  fileName: string;
  fileType: string;
  fileSize: number;
  fileUrl: string;
  storageKey: string;
  createdAt?: string;
  linkedNotes?: AttachmentLinkedNote[];
}

export function listNoteAttachments(noteId: string) {
  return request<AttachmentVO[]>({ url: `/notes/${noteId}/attachments` });
}

export function deleteAttachment(id: string) {
  return request<void>({ url: `/attachments/${id}`, method: 'DELETE' });
}

export function uploadNoteAttachmentPath(payload: {
  noteId: string;
  filePath: string;
  name?: string;
}) {
  return new Promise<AttachmentVO>((resolve, reject) => {
    (uni as any).uploadFile({
      url: `${getApiBaseUrl()}/attachments/upload`,
      filePath: payload.filePath,
      name: 'file',
      fileName: payload.name,
      formData: { noteId: payload.noteId },
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
          resolve(body.data as AttachmentVO);
          return;
        }
        resolve(body as AttachmentVO);
      },
      fail: () => reject(new Error('上传失败')),
    });
  });
}
