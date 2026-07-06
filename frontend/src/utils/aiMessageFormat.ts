import type { AiAgentStep } from '../api/aiAgent';
import { AGENT_TOOL_LABEL } from '../api/aiAgent';

export type ResultItemKind =
  | 'note'
  | 'todo'
  | 'reminder'
  | 'drive-file'
  | 'drive-folder'
  | 'share'
  | 'tag'
  | 'folder'
  | 'generic';

export type ResultLinkType = 'note' | 'todo' | 'share' | 'reminder' | 'drive';

export interface ResultListLink {
  type: ResultLinkType;
  id?: string | number;
  url?: string;
  token?: string;
  resourceType?: string;
  passwordProtected?: boolean;
}

export interface ResultListItem {
  id?: string | number;
  title: string;
  meta?: string;
  badge?: string;
  badgeTone?: 'default' | 'success' | 'warning' | 'muted';
  link?: ResultListLink;
  actions?: Array<'preview' | 'copy' | 'open'>;
}

export interface CatalogGroup {
  name: string;
  countLabel?: string;
  items: ResultListItem[];
}

export interface AgentResultBlock {
  id: string;
  tool: string;
  title: string;
  kind: ResultItemKind | 'catalog';
  count?: number;
  items?: ResultListItem[];
  groups?: CatalogGroup[];
  text?: string;
}

const LIST_TOOLS = new Set([
  'listNotes',
  'listTodos',
  'searchTodos',
  'listReminders',
  'searchNotes',
  'listDriveFiles',
  'listDriveFolders',
  'listShares',
  'listTags',
  'listNoteFolders',
]);

function parseJson(raw?: string): Record<string, unknown> | null {
  if (!raw?.trim()) return null;
  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return null;
  }
}

/** 雪花 ID 必须保持字符串，避免 Number 精度丢失导致跳转 403 */
function asIdString(value: unknown): string | undefined {
  if (value == null || value === '') return undefined;
  return String(value).trim();
}

function firstNonBlank(...values: unknown[]): string {
  for (const value of values) {
    const text = value == null ? '' : String(value).trim();
    if (text) return text;
  }
  return '未命名';
}

function formatDateTime(raw?: unknown): string {
  if (!raw) return '';
  const text = String(raw).replace('T', ' ');
  return text.length >= 16 ? text.slice(0, 16) : text;
}

function shareTypeLabel(type?: unknown): string {
  if (type === 'NOTE') return '文档';
  if (type === 'BATCH') return '批量';
  if (type === 'ATTACHMENT') return '文件';
  return '分享';
}

function todoBadge(statusLabel?: string, status?: unknown): ResultListItem['badgeTone'] {
  const label = statusLabel || '';
  if (label.includes('完成')) return 'success';
  if (label.includes('进行')) return 'warning';
  if (status === 2) return 'success';
  if (status === 1) return 'warning';
  return 'default';
}

export function parseCatalogText(catalog: string): CatalogGroup[] {
  if (!catalog?.trim()) return [];
  const groups: CatalogGroup[] = [];
  let current: CatalogGroup | null = null;

  for (const line of catalog.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    const groupMatch = trimmed.match(/^【(.+?)】(.*)$/);
    if (groupMatch) {
      current = {
        name: groupMatch[1],
        countLabel: groupMatch[2]?.trim() || undefined,
        items: [],
      };
      groups.push(current);
      continue;
    }

    const itemMatch = trimmed.match(/^[-·•]\s*(.+?)(?:\s*\(#(\d+)\))?\s*$/);
    if (itemMatch) {
      if (!current) {
        current = { name: '文档', items: [] };
        groups.push(current);
      }
      const noteId = itemMatch[2]?.trim();
      current.items.push({
        title: itemMatch[1].trim(),
        id: noteId,
        link: noteId ? { type: 'note', id: noteId } : undefined,
      });
    }
  }

  return groups.filter((group) => group.items.length > 0);
}

function blockTitle(tool: string): string {
  return AGENT_TOOL_LABEL[tool] || tool;
}

function blockKind(tool: string): AgentResultBlock['kind'] {
  switch (tool) {
    case 'listNotes':
      return 'catalog';
    case 'listTodos':
    case 'searchTodos':
      return 'todo';
    case 'listReminders':
      return 'reminder';
    case 'searchNotes':
      return 'note';
    case 'listDriveFiles':
      return 'drive-file';
    case 'listDriveFolders':
      return 'drive-folder';
    case 'listShares':
      return 'share';
    case 'listTags':
      return 'tag';
    case 'listNoteFolders':
      return 'folder';
    default:
      return 'generic';
  }
}

function mapGenericItems(tool: string, items: Array<Record<string, unknown>>): ResultListItem[] {
  return items.map((item) => {
    const title = firstNonBlank(item.title, item.name, item.fileName, item.tagName, item.message);
    const id = asIdString(
      item.noteId ?? item.todoId ?? item.reminderId ?? item.attachmentId ?? item.folderId ?? item.tagId ?? item.id,
    );
    const metaParts: string[] = [];

    if (tool === 'listTodos' || tool === 'searchTodos') {
      if (item.dueAt) metaParts.push(`截止 ${formatDateTime(item.dueAt)}`);
    }
    if (tool === 'listReminders' && item.triggerAt) {
      metaParts.push(`触发 ${formatDateTime(item.triggerAt)}`);
    }
    if (tool === 'listShares') {
      if (item.viewCount != null) metaParts.push(`访问 ${item.viewCount} 次`);
      if (item.passwordProtected) metaParts.push('需密码');
    }
    if (item.folderName) metaParts.push(String(item.folderName));
    if (item.snippet) metaParts.push(String(item.snippet).slice(0, 60));

    const result: ResultListItem = {
      id: id as string | number | undefined,
      title,
      meta: metaParts.join(' · ') || undefined,
    };

    if (tool === 'listTodos' || tool === 'searchTodos') {
      result.badge = item.statusLabel ? String(item.statusLabel) : undefined;
      result.badgeTone = todoBadge(item.statusLabel as string | undefined, item.status);
      const todoId = asIdString(item.todoId);
      if (todoId) {
        result.link = { type: 'todo', id: todoId };
        result.actions = ['open'];
      }
    }
    const noteId = asIdString(item.noteId);
    if (tool === 'searchNotes' && noteId) {
      result.link = { type: 'note', id: noteId };
      result.actions = ['open'];
    }
    const reminderId = asIdString(item.reminderId);
    if (tool === 'listReminders' && reminderId) {
      result.link = { type: 'reminder', id: reminderId };
      result.actions = ['open'];
    }
    if (tool === 'listShares' && item.sharePath) {
      result.badge = shareTypeLabel(item.resourceType);
      result.badgeTone = 'muted';
      result.link = {
        type: 'share',
        url: String(item.sharePath),
        token: item.token ? String(item.token) : undefined,
        resourceType: item.resourceType ? String(item.resourceType) : undefined,
        passwordProtected: Boolean(item.passwordProtected),
      };
      result.actions = ['preview', 'copy'];
    }
    const attachmentId = asIdString(item.attachmentId);
    if (tool === 'listDriveFiles' && attachmentId) {
      result.link = { type: 'drive', id: attachmentId };
      result.actions = ['open'];
    }

    return result;
  });
}

export function buildAgentResultBlocks(steps?: AiAgentStep[]): AgentResultBlock[] {
  if (!steps?.length) return [];

  return steps
    .filter((step) => step.status === 'done' && LIST_TOOLS.has(step.tool))
    .map((step) => {
      const data = parseJson(step.output);
      const block: AgentResultBlock = {
        id: step.id,
        tool: step.tool,
        title: blockTitle(step.tool),
        kind: blockKind(step.tool),
      };

      if (step.tool === 'listNotes' && data?.catalog) {
        block.groups = parseCatalogText(String(data.catalog));
        block.count = block.groups.reduce((sum, group) => sum + group.items.length, 0);
        return block;
      }

      const items = (data?.items as Array<Record<string, unknown>>) || [];
      block.count = Number(data?.count ?? items.length);
      block.items = mapGenericItems(step.tool, items);
      return block;
    })
    .filter((block) => (block.groups?.length || 0) > 0 || (block.items?.length || 0) > 0 || block.text);
}

export function extractIntroText(content: string, hasStructuredBlocks: boolean): string {
  if (!content?.trim()) return '';
  const visibleContent = stripInternalContextBlocks(content);
  if (!visibleContent.trim()) return '';
  if (!hasStructuredBlocks) return visibleContent.trim();

  const lines = visibleContent.split('\n');
  const intro: string[] = [];

  for (const line of lines) {
    const trimmed = line.trim();
    if (/^共\s+\d+/.test(trimmed)) break;
    if (/^找到\s+\d+/.test(trimmed)) break;
    if (/^知识库共/.test(trimmed)) break;
    if (/^【.+】/.test(trimmed)) break;
    if (/^-\s+/.test(trimmed) && intro.length > 0) break;
    if (!trimmed && intro.length > 0) break;
    intro.push(line);
  }

  const text = intro.join('\n').trim();
  if (text && !/^[-·•\d]/.test(text)) return text;
  return '';
}

export function normalizeAiMarkdown(text: string): string {
  if (!text?.trim()) return '';

  let result = stripInternalContextBlocks(text).replace(/\r\n/g, '\n');

  result = result.replace(/^【(.+?)】(.*)$/gm, (_, name, suffix) => {
    const extra = String(suffix || '').trim();
    return extra ? `#### ${name}\n${extra}` : `#### ${name}`;
  });

  result = result.replace(/^(共\s+\d+\s*[^：:\n]*)([：:])/gm, '**$1**$2');
  result = result.replace(/^(找到\s+\d+\s*[^：:\n]*)([：:])/gm, '**$1**$2');
  result = result.replace(/^(知识库共[^：:\n]*)([：:])/gm, '**$1**$2');

  return result.trim();
}

function stripInternalContextBlocks(text: string): string {
  const visible: string[] = [];
  let skippingPendingPlan = false;

  for (const line of text.replace(/\r\n/g, '\n').split('\n')) {
    const trimmed = line.trim();
    if (trimmed.startsWith('PENDING_PLAN_JSON:')) {
      continue;
    }
    if (trimmed === '待确认方案：' || trimmed === '待确认方案:') {
      skippingPendingPlan = true;
      continue;
    }
    if (skippingPendingPlan) {
      if (!trimmed || /^-\s*(?:stepId=|.*\btool=)/.test(trimmed)) {
        continue;
      }
      skippingPendingPlan = false;
    }
    visible.push(line);
  }

  return visible.join('\n').trim();
}

export function isReadOnlyListTask(steps?: AiAgentStep[]): boolean {
  if (!steps?.length) return false;
  return steps.every((step) => LIST_TOOLS.has(step.tool) && step.status === 'done');
}
