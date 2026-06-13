export type NoteListFilter = 'all' | 'favorite' | 'recent' | 'archived';

export const NOTE_LIST_FILTER_OPTIONS: { label: string; value: NoteListFilter }[] = [
  { label: '全部', value: 'all' },
  { label: '收藏', value: 'favorite' },
  { label: '最近', value: 'recent' },
  { label: '归档', value: 'archived' },
];

export const NOTE_FILTER_EMPTY_COPY: Record<NoteListFilter, { title: string; description: string; preset: 'note' | 'favorite' | 'archive' | 'default' }> = {
  all: {
    title: '还没有分组或文档',
    description: '从第一篇文档开始，或使用下方按钮创建',
    preset: 'note',
  },
  favorite: {
    title: '还没有收藏的文档',
    description: '在文档右键菜单中点击「收藏」，或打开文档后标记收藏',
    preset: 'favorite',
  },
  recent: {
    title: '暂无最近编辑',
    description: '最近 20 篇编辑过的文档会出现在这里',
    preset: 'note',
  },
  archived: {
    title: '还没有归档的文档',
    description: '归档后的文档会集中显示在这里，可随时取消归档',
    preset: 'archive',
  },
};

export function noteListQueryParams(
  filter: NoteListFilter,
  base: { page?: number; size?: number; workspaceId?: string | null; keyword?: string },
) {
  const params = {
    page: base.page ?? 1,
    size: base.size ?? 500,
    workspaceId: base.workspaceId ?? undefined,
    keyword: base.keyword || undefined,
  };

  switch (filter) {
    case 'favorite':
      return { ...params, isFavorite: true, status: 0 };
    case 'archived':
      return { ...params, status: 1 };
    case 'recent':
    case 'all':
    default:
      return { ...params, status: 0 };
  }
}

export function parseNoteListFilter(scope: unknown): NoteListFilter {
  if (scope === 'favorite' || scope === 'recent' || scope === 'archived') {
    return scope;
  }
  return 'all';
}
