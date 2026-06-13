import type { Folder } from '../api/folders';
import type { Note } from '../api/notes';

export type TreeNodeKind = 'home' | 'folder' | 'note';

export type TreeDropNode = {
  nodeType: TreeNodeKind;
  rawId?: string;
};

export type NoteTreeMovePayload = {
  folderId: string | null;
  parentId: string | null;
  sortOrder: number;
};

export type FolderTreeMovePayload = {
  parentId: string | null;
  sortOrder: number;
};

/** 与 NotesView 树节点兼容的最小结构 */
export type TreeNodeSnapshot = {
  key: string;
  nodeType: TreeNodeKind;
  rawId?: string;
  sortOrder?: number;
  children?: TreeNodeSnapshot[];
};

export function cloneTreeSnapshot<T extends TreeNodeSnapshot>(nodes: T[]): T[] {
  return nodes.map((node) => ({
    ...node,
    children: node.children?.length
      ? cloneTreeSnapshot(node.children as T[])
      : undefined,
  }));
}

export type TreeDropGapIntent = 'before' | 'after';

export type TreeDropPlacementKind = 'inside' | 'before' | 'after' | 'root-before' | 'root-after';

export type TreeDropResolvedPlacement = {
  kind: TreeDropPlacementKind;
  gapIntent: TreeDropGapIntent | null;
};

export type TreeDropEventInfo = {
  dragKey: string;
  dropKey: string;
  dropToGap: boolean;
  dropPosition: number;
  nodePos: string;
  dropNodeChildCount: number;
  dropNodeExpanded: boolean;
  gapIntent?: TreeDropGapIntent | null;
  /** 由 resolveDropModeFromEvent 解析，优先于 dropToGap */
  placementKind?: TreeDropPlacementKind;
};

function parseKeyToDropNode(key: string): TreeDropNode | null {
  if (key === 'root-home') return { nodeType: 'home' };
  if (key.startsWith('folder-')) return { nodeType: 'folder', rawId: key.slice('folder-'.length) };
  if (key.startsWith('note-')) return { nodeType: 'note', rawId: key.slice('note-'.length) };
  return null;
}

/** 从 Tree 事件节点解析（兼容 dataRef 丢失） */
export function parseTreeDropNode(node: Record<string, unknown> | null | undefined): TreeDropNode | null {
  if (!node) return null;
  const key = String(node.key ?? (node.dataRef as Record<string, unknown> | undefined)?.key ?? '');
  const fromKey = parseKeyToDropNode(key);
  if (fromKey) return fromKey;

  const data = (node.dataRef ?? node) as Record<string, unknown>;
  if (data.nodeType === 'home') return { nodeType: 'home' };
  if (data.nodeType === 'folder' && data.rawId) return { nodeType: 'folder', rawId: String(data.rawId) };
  if (data.nodeType === 'note' && data.rawId) return { nodeType: 'note', rawId: String(data.rawId) };
  return null;
}

export function normalizeRelativeDropPosition(
  node: Record<string, unknown>,
  dropPosition: number,
): number {
  if (Math.abs(dropPosition) <= 1) {
    if (dropPosition === 0) return 0;
    return dropPosition > 0 ? 1 : -1;
  }

  const pos = String((node as { pos?: string }).pos ?? '');
  if (!pos) {
    return dropPosition > 0 ? 1 : -1;
  }
  const segments = pos.split('-');
  const index = Number(segments[segments.length - 1]);
  if (Number.isNaN(index)) return dropPosition > 0 ? 1 : -1;
  const relative = dropPosition - index;
  if (relative > 0) return 1;
  if (relative < 0) return -1;
  return 0;
}

/** 根据悬停预览 / drop 事件校正放置模式 */
export function resolveDropModeFromEvent(
  dropToGap: boolean,
  relativeDropPosition: number,
  preview: 'inside' | TreeDropGapIntent | null | undefined,
  dropKey?: string,
): TreeDropResolvedPlacement {
  if (preview === 'inside') {
    return { kind: 'inside', gapIntent: null };
  }
  if (preview === 'before' || preview === 'after') {
    return { kind: preview, gapIntent: preview };
  }

  if (dropKey === 'root-home') {
    if (!dropToGap) return { kind: 'inside', gapIntent: null };
    return relativeDropPosition < 0
      ? { kind: 'root-before', gapIntent: 'before' }
      : { kind: 'root-after', gapIntent: 'after' };
  }

  if (dropToGap) {
    if (relativeDropPosition < 0) return { kind: 'before', gapIntent: 'before' };
    if (relativeDropPosition > 0) return { kind: 'after', gapIntent: 'after' };
    return { kind: 'after', gapIntent: 'after' };
  }

  // Ant Design Tree 偶发 dropToGap=false 但 dropPosition 为 ±1
  if (relativeDropPosition < 0) {
    return dropKey === 'root-home'
      ? { kind: 'root-before', gapIntent: 'before' }
      : { kind: 'before', gapIntent: 'before' };
  }
  if (relativeDropPosition > 0) {
    return dropKey === 'root-home'
      ? { kind: 'root-after', gapIntent: 'after' }
      : { kind: 'after', gapIntent: 'after' };
  }

  return { kind: 'inside', gapIntent: null };
}

function computeSortOrder(
  siblings: ContainerItem[],
  insertIndex: number,
  movedId: string,
): number {
  const before = siblings.slice(0, insertIndex).filter((item) => item.id !== movedId);
  const after = siblings.slice(insertIndex).filter((item) => item.id !== movedId);

  if (before.length === 0 && after.length === 0) return 1000;
  // 降序排列：插到最前 → 高于当前最大 sortOrder
  if (before.length === 0) {
    const maxOrder = Math.max(...after.map((item) => item.sortOrder));
    return maxOrder + 1000;
  }
  // 插到最后 → 低于当前最小 sortOrder（避免 -1000 导致 0/负数）
  if (after.length === 0) {
    const minOrder = Math.min(...before.map((item) => item.sortOrder));
    if (minOrder > 1) {
      const mid = Math.floor(minOrder / 2);
      return mid > 0 ? mid : minOrder - 1;
    }
    return minOrder - 1000;
  }

  const above = before[before.length - 1].sortOrder;
  const below = after[0].sortOrder;
  if (above === below) return above + 1;
  if (above > below) {
    const mid = Math.floor((above + below) / 2);
    return mid === below ? below + 1 : mid;
  }
  return above + 1;
}

type ContainerItem = {
  kind: 'folder' | 'note';
  id: string;
  sortOrder: number;
};

type NodePlacement = {
  node: TreeNodeSnapshot;
  parent: TreeNodeSnapshot | null;
  siblings: TreeNodeSnapshot[];
  index: number;
};

function getSortOrderFromData(node: TreeNodeSnapshot, folders: Folder[], notes: Note[]): number {
  if (node.nodeType === 'folder' && node.rawId) {
    return folders.find((item) => item.id === node.rawId)?.sortOrder ?? node.sortOrder ?? 0;
  }
  if (node.nodeType === 'note' && node.rawId) {
    return notes.find((item) => item.id === node.rawId)?.sortOrder ?? node.sortOrder ?? 0;
  }
  return node.sortOrder ?? 0;
}

export function isDirectTreeChild(
  dragNode: TreeDropNode,
  dropNode: TreeDropNode,
  folders: Folder[],
  notes: Note[],
): boolean {
  if (!dragNode.rawId || !dropNode.rawId) return false;
  if (dragNode.nodeType === 'note') {
    const note = notes.find((item) => item.id === dragNode.rawId);
    if (!note) return false;
    if (dropNode.nodeType === 'note') return note.parentId === dropNode.rawId;
    if (dropNode.nodeType === 'folder') {
      return note.folderId === dropNode.rawId && !note.parentId;
    }
    return false;
  }
  if (dragNode.nodeType === 'folder' && dropNode.nodeType === 'folder') {
    const folder = folders.find((item) => item.id === dragNode.rawId);
    return folder?.parentId === dropNode.rawId;
  }
  return false;
}

function isNoteDescendant(notes: Note[], ancestorId: string, candidateId: string): boolean {
  let current = notes.find((item) => item.id === candidateId);
  while (current?.parentId) {
    if (current.parentId === ancestorId) return true;
    current = notes.find((item) => item.id === current!.parentId);
  }
  return false;
}

function isFolderDescendant(folders: Folder[], ancestorId: string, candidateId: string): boolean {
  let current = folders.find((item) => item.id === candidateId);
  while (current?.parentId) {
    if (current.parentId === ancestorId) return true;
    current = folders.find((item) => item.id === current!.parentId);
  }
  return false;
}

function loopTree<T extends TreeNodeSnapshot>(
  data: T[],
  key: string,
  callback: (item: T, index: number, arr: T[]) => void,
): boolean {
  for (let i = 0; i < data.length; i += 1) {
    const item = data[i];
    if (item.key === key) {
      callback(item, i, data);
      return true;
    }
    if (Array.isArray(item.children)) {
      if (loopTree(item.children as T[], key, callback)) return true;
    }
  }
  return false;
}

function findNode(tree: TreeNodeSnapshot[], key: string): TreeNodeSnapshot | null {
  let found: TreeNodeSnapshot | null = null;
  loopTree(tree, key, (item) => {
    found = item;
  });
  return found;
}

function findSiblingsAndIndex(
  tree: TreeNodeSnapshot[],
  key: string,
): { siblings: TreeNodeSnapshot[]; index: number; parent: TreeNodeSnapshot | null } | null {
  let result: { siblings: TreeNodeSnapshot[]; index: number; parent: TreeNodeSnapshot | null } | null = null;
  const walk = (nodes: TreeNodeSnapshot[], parent: TreeNodeSnapshot | null): boolean => {
    const index = nodes.findIndex((item) => item.key === key);
    if (index >= 0) {
      result = { siblings: nodes, index, parent };
      return true;
    }
    for (const node of nodes) {
      if (node.children && walk(node.children, node)) return true;
    }
    return false;
  };
  walk(tree, null);
  return result;
}

function isDescendantKey(tree: TreeNodeSnapshot[], ancestorKey: string, candidateKey: string): boolean {
  if (ancestorKey === candidateKey) return true;
  let found = false;
  loopTree(tree, ancestorKey, (item) => {
    const walk = (nodes?: TreeNodeSnapshot[]): boolean => {
      if (!nodes) return false;
      for (const child of nodes) {
        if (child.key === candidateKey) return true;
        if (walk(child.children)) return true;
      }
      return false;
    };
    found = walk(item.children);
  });
  return found;
}

function resolvePlacementKind(info: TreeDropEventInfo): TreeDropPlacementKind {
  if (info.placementKind) return info.placementKind;
  if (info.dropToGap) {
    return info.gapIntent === 'before' ? 'before' : 'after';
  }
  return 'inside';
}

function insertAtRoot(data: TreeNodeSnapshot[], node: TreeNodeSnapshot, kind: TreeDropPlacementKind) {
  const homeIndex = data.findIndex((item) => item.key === 'root-home');
  if (kind === 'root-before') {
    data.splice(homeIndex >= 0 ? homeIndex : 0, 0, node);
    return;
  }
  if (homeIndex >= 0) {
    data.splice(homeIndex + 1, 0, node);
    return;
  }
  data.push(node);
}

/**
 * 按明确落点更新树（与 UI 绿线/蓝底一致）。
 */
export function applyTreeDropMutation<T extends TreeNodeSnapshot>(
  tree: T[],
  info: TreeDropEventInfo,
): T[] | null {
  if (info.dragKey === 'root-home') return null;

  const data = JSON.parse(JSON.stringify(tree)) as T[];
  const dragInfo = findSiblingsAndIndex(data, info.dragKey);
  if (!dragInfo) return null;

  const dragObj = dragInfo.siblings[dragInfo.index] as T | undefined;
  if (!dragObj || dragObj.nodeType === 'home') return null;
  dragInfo.siblings.splice(dragInfo.index, 1);

  if (info.dropKey === info.dragKey) {
    dragInfo.siblings.splice(dragInfo.index, 0, dragObj);
    return data;
  }

  const placementKind = resolvePlacementKind(info);

  const insertInto = (targetKey: string): boolean => {
    const target = findNode(data, targetKey);
    if (!target || target.nodeType === 'home') return false;
    if (!target.children) target.children = [];
    target.children.unshift(dragObj);
    return true;
  };

  const insertBeforeAfter = (targetKey: string, before: boolean): boolean => {
    const targetInfo = findSiblingsAndIndex(data, targetKey);
    if (!targetInfo) return false;
    const insertIndex = before ? targetInfo.index : targetInfo.index + 1;
    targetInfo.siblings.splice(insertIndex, 0, dragObj);
    return true;
  };

  if (info.dropKey === 'root-home') {
    if (placementKind === 'inside') return null;
    insertAtRoot(data, dragObj, placementKind);
    return data;
  }

  switch (placementKind) {
    case 'inside':
      if (!insertInto(info.dropKey)) return null;
      return data;
    case 'before':
      if (!insertBeforeAfter(info.dropKey, true)) return null;
      return data;
    case 'after':
      if (!insertBeforeAfter(info.dropKey, false)) return null;
      return data;
    case 'root-before':
    case 'root-after':
      insertAtRoot(data, dragObj, placementKind);
      return data;
    default:
      return null;
  }
}

/** 新父节点是否落在被拖拽节点的子树内（循环嵌套） */
function isTreeMoveCyclic(
  tree: TreeNodeSnapshot[],
  dragKey: string,
  parent: TreeNodeSnapshot | null,
): boolean {
  if (!parent) return false;
  return isDescendantKey(tree, dragKey, parent.key);
}

function siblingsToContainerItems(
  siblings: TreeNodeSnapshot[],
  folders: Folder[],
  notes: Note[],
): ContainerItem[] {
  return siblings
    .filter((item) => (item.nodeType === 'folder' || item.nodeType === 'note') && item.rawId)
    .map((item) => ({
      kind: item.nodeType as 'folder' | 'note',
      id: item.rawId!,
      sortOrder: getSortOrderFromData(item, folders, notes),
    }));
}

function findNodePlacement(tree: TreeNodeSnapshot[], key: string): NodePlacement | null {
  const walk = (nodes: TreeNodeSnapshot[], parent: TreeNodeSnapshot | null): NodePlacement | null => {
    for (const node of nodes) {
      if (node.key === key) {
        const sortableSiblings = nodes.filter((item) => item.nodeType === 'folder' || item.nodeType === 'note');
        const index = sortableSiblings.findIndex((item) => item.key === key);
        return {
          node,
          parent,
          siblings: sortableSiblings,
          index: index < 0 ? 0 : index,
        };
      }
      if (Array.isArray(node.children)) {
        const found = walk(node.children, node);
        if (found) return found;
      }
    }
    return null;
  };
  return walk(tree, null);
}

function buildMovePayload(
  placement: NodePlacement,
  folders: Folder[],
  notes: Note[],
): { type: 'folder'; payload: FolderTreeMovePayload } | { type: 'note'; payload: NoteTreeMovePayload } | null {
  const { node, parent, index, siblings } = placement;
  if (node.nodeType === 'home') return null;

  // 以拖拽后的树同级顺序为准，支持子级拖到与父级同级等跨容器移动
  const containerItems = siblingsToContainerItems(siblings, folders, notes);
  let insertIndex = containerItems.findIndex((item) => item.id === node.rawId);
  if (insertIndex < 0) {
    insertIndex = Math.max(0, Math.min(index, containerItems.length));
  }

  const sortOrder = computeSortOrder(containerItems, insertIndex, node.rawId!);

  if (node.nodeType === 'folder') {
    if (!node.rawId) return null;
    if (parent?.nodeType === 'note') return null;
    const parentId = parent?.nodeType === 'folder' ? parent.rawId! : null;
    if (parentId && isFolderDescendant(folders, node.rawId, parentId)) return null;
    return { type: 'folder', payload: { parentId, sortOrder } };
  }

  if (node.nodeType === 'note') {
    if (!node.rawId) return null;
    if (!parent) return { type: 'note', payload: { folderId: null, parentId: null, sortOrder } };
    if (parent.nodeType === 'folder') {
      return { type: 'note', payload: { folderId: parent.rawId!, parentId: null, sortOrder } };
    }
    if (parent.nodeType === 'note') {
      if (isNoteDescendant(notes, node.rawId, parent.rawId!)) return null;
      const parentNote = notes.find((item) => item.id === parent.rawId);
      return { type: 'note', payload: { folderId: parentNote?.folderId ?? null, parentId: parent.rawId!, sortOrder } };
    }
    return { type: 'note', payload: { folderId: null, parentId: null, sortOrder } };
  }

  return null;
}

/** he-tree 拖放完成时的落点信息（来自 dragContext.targetInfo） */
export type HeTreeDropTargetInfo = {
  parent: { data: TreeNodeSnapshot } | null;
  siblings: Array<{ data: TreeNodeSnapshot }>;
  indexBeforeDrop: number;
};

function isMoveParentCyclic(
  dragNode: TreeDropNode,
  parent: TreeNodeSnapshot | null,
  folders: Folder[],
  notes: Note[],
): boolean {
  if (!parent?.rawId || !dragNode.rawId) return false;
  if (dragNode.nodeType === 'note' && parent.nodeType === 'note') {
    return isNoteDescendant(notes, dragNode.rawId, parent.rawId);
  }
  if (dragNode.nodeType === 'folder' && parent.nodeType === 'folder') {
    return isFolderDescendant(folders, dragNode.rawId, parent.rawId);
  }
  return false;
}

/** 根据 he-tree 拖放落点推导移动参数（子级提到父级同级等场景更可靠） */
export function resolveTreeMoveFromTargetInfo(
  targetInfo: HeTreeDropTargetInfo | null | undefined,
  dragKey: string,
  folders: Folder[],
  notes: Note[],
): { type: 'folder'; payload: FolderTreeMovePayload } | { type: 'note'; payload: NoteTreeMovePayload } | null {
  if (!targetInfo) return null;

  const dragNode = parseKeyToDropNode(dragKey);
  if (!dragNode?.rawId || dragNode.nodeType === 'home') return null;

  const parent = targetInfo.parent?.data ?? null;
  if (parent?.nodeType === 'home') return null;
  if (dragNode.nodeType === 'folder' && parent?.nodeType === 'note') return null;
  if (isMoveParentCyclic(dragNode, parent, folders, notes)) return null;

  const siblings = targetInfo.siblings
    .map((item) => item.data)
    .filter((item) => item.nodeType === 'folder' || item.nodeType === 'note');

  const placement: NodePlacement = {
    node: {
      key: dragKey,
      nodeType: dragNode.nodeType,
      rawId: dragNode.rawId,
      title: '',
    },
    parent,
    siblings,
    index: targetInfo.indexBeforeDrop,
  };

  return buildMovePayload(placement, folders, notes);
}

/** 拖拽时是否允许以 target 为落点（仅拦截首页与循环嵌套） */
export function canTreeDropOnTarget(
  dragNode: TreeDropNode,
  targetNode: TreeDropNode,
  tree: TreeNodeSnapshot[],
): boolean {
  if (targetNode.nodeType === 'home') return false;
  if (!dragNode.rawId) return true;

  const dragKey = dragNode.nodeType === 'folder'
    ? `folder-${dragNode.rawId}`
    : dragNode.nodeType === 'note'
      ? `note-${dragNode.rawId}`
      : '';
  const targetKey = targetNode.nodeType === 'folder'
    ? `folder-${targetNode.rawId}`
    : targetNode.nodeType === 'note'
      ? `note-${targetNode.rawId}`
      : '';
  if (!dragKey || !targetKey) return true;

  // 不能放入自身子树（循环）；同级/父级/祖先节点仍允许
  return !isDescendantKey(tree, dragKey, targetKey);
}

/** 从 he-tree 拖拽后的最终树结构推导 API 移动参数 */
export function resolveTreeMoveFromFinalTree(
  tree: TreeNodeSnapshot[],
  dragKey: string,
  folders: Folder[],
  notes: Note[],
  homeNoteId: string | null,
): { type: 'folder'; payload: FolderTreeMovePayload } | { type: 'note'; payload: NoteTreeMovePayload } | null {
  const dragNode = parseKeyToDropNode(dragKey);
  if (!dragNode?.rawId || dragNode.nodeType === 'home') return null;

  const placement = findNodePlacement(tree, dragKey);
  if (!placement) return null;

  const { node, parent } = placement;
  if (node.nodeType === 'home') return null;
  if (parent?.nodeType === 'home') return null;
  if (node.nodeType === 'folder' && parent?.nodeType === 'note') return null;
  if (isTreeMoveCyclic(tree, dragKey, parent)) return null;
  if (isMoveParentCyclic(dragNode, parent, folders, notes)) return null;

  return buildMovePayload(placement, folders, notes);
}

/** 先按 UI 规则变换树，再从最终位置推导 API 移动参数 */
export function resolveTreeDropFromMutation(
  tree: TreeNodeSnapshot[],
  info: TreeDropEventInfo,
  folders: Folder[],
  notes: Note[],
  homeNoteId: string | null,
): { type: 'folder'; payload: FolderTreeMovePayload } | { type: 'note'; payload: NoteTreeMovePayload } | null {
  const dragNode = parseKeyToDropNode(info.dragKey);
  const dropNode = parseKeyToDropNode(info.dropKey);
  if (!dragNode?.rawId || dragNode.nodeType === 'home') return null;
  if (!dropNode) return null;

  const placementKind = resolvePlacementKind(info);
  if (dropNode.nodeType === 'home' && placementKind === 'inside') return null;
  if (dragNode.rawId && dropNode.rawId && dragNode.nodeType === 'folder' && dropNode.nodeType === 'note') {
    if (placementKind === 'inside') return null;
  }

  if (isDescendantKey(tree, info.dragKey, info.dropKey) && placementKind === 'inside') {
    return null;
  }

  const nextTree = applyTreeDropMutation(tree, info);
  if (!nextTree) return null;

  const placement = findNodePlacement(nextTree, info.dragKey);
  if (!placement) return null;

  return buildMovePayload(placement, folders, notes);
}

export function sortTreeNodes<T extends { sortOrder?: number }>(nodes: T[]): T[] {
  return [...nodes].sort((a, b) => (b.sortOrder ?? 0) - (a.sortOrder ?? 0));
}

/** 供 allowDrop 使用：是否允许放入目标内部（非 gap） */
export function canDropInsideTreeNode(
  dragNode: TreeDropNode,
  dropNode: TreeDropNode,
  isDropTargetDescendant: boolean,
): boolean {
  if (dropNode.nodeType === 'home') return false;
  if (isDropTargetDescendant) return false;
  if (dragNode.nodeType === 'folder' && dropNode.nodeType === 'note') return false;
  return true;
}

/** gap 放置（-1/1）时是否允许；含拖到父级同级 */
export function canDropGapOnTreeNode(
  _dragNode: TreeDropNode,
  dropNode: TreeDropNode,
  _isDropTargetDescendant: boolean,
): boolean {
  if (dropNode.nodeType === 'home') return true;
  return true;
}
