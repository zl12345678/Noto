import http from './http';

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

export function linkAttachmentToNote(attachmentId: string, noteId: string) {
  return http.post<AttachmentVO>(`/attachments/${attachmentId}/link`, null, {
    params: { noteId },
  });
}

export function unlinkAttachmentFromNote(attachmentId: string, noteId: string) {
  return http.delete<void>(`/attachments/${attachmentId}/link`, {
    params: { noteId },
  });
}

export function uploadAttachment(noteId: string, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('noteId', noteId);
  return http.post<AttachmentVO>('/attachments/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000,
  });
}

export function listNoteAttachments(noteId: string) {
  return http.get<AttachmentVO[]>(`/notes/${noteId}/attachments`);
}

export function deleteAttachment(id: string) {
  return http.delete<void>(`/attachments/${id}`);
}
