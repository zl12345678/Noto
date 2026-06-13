import type { Note, NoteUpdateRequest } from '../api/notes';

export function buildNoteUpdatePayload(
  note: Note,
  form: { title: string; content: string; summary: string; folderId?: string; tagIds?: string[] },
  extras?: Partial<NoteUpdateRequest>,
): NoteUpdateRequest {
  return {
    title: form.title,
    content: form.content,
    contentType: note.contentType,
    summary: form.summary,
    status: note.status,
    isFavorite: note.isFavorite,
    folderId: form.folderId ?? note.folderId,
    tagIds: form.tagIds ?? note.tags?.map((tag) => tag.id),
    ...extras,
  };
}
