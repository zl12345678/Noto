import type { AiAgentStep } from '../api/aiAgent';

export interface StepPresentation {
  title: string;
  summary: string;
  detail?: string;
}

function parseJson(raw?: string): Record<string, unknown> | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return null;
  }
}

function formatDateTime(raw?: unknown): string {
  if (!raw) return '';
  const text = String(raw).replace('T', ' ');
  if (text.length >= 16) return text.slice(0, 16);
  return text;
}

function formatListItems(output: Record<string, unknown> | null, itemKey: string, labelKey: string): string | undefined {
  const items = (output?.[itemKey] as Array<Record<string, unknown>>) || [];
  if (!items.length && output?.catalog) {
    return String(output.catalog).slice(0, 200);
  }
  if (!items.length) return undefined;
  return items
    .slice(0, 5)
    .map((item) => `· ${item[labelKey] ?? item.title ?? '未命名'}`)
    .join('\n');
}

export function presentStep(step: AiAgentStep): StepPresentation {
  const payload = step.actionPayload || {};
  const output = parseJson(step.output);
  const result = step.result || {};

  switch (step.tool) {
    case 'listNotes': {
      const catalog = output?.catalog ? String(output.catalog) : '';
      return {
        title: '查看文档目录',
        summary: catalog ? '已查询知识库文档列表' : '未找到文档',
        detail: catalog ? catalog.slice(0, 280) + (catalog.length > 280 ? '…' : '') : undefined,
      };
    }
    case 'listTodos':
    case 'searchTodos': {
      const count = output?.count ?? 0;
      return {
        title: '查看待办',
        summary: `共 ${count} 条待办`,
        detail: formatListItems(output, 'items', 'title'),
      };
    }
    case 'listReminders': {
      const count = output?.count ?? 0;
      return {
        title: '查看提醒',
        summary: `共 ${count} 条提醒`,
        detail: formatListItems(output, 'items', 'message'),
      };
    }
    case 'createNote': {
      const title = String(payload.title || '未命名文档');
      const content = String(payload.content || '').trim();
      return {
        title: '创建文档',
        summary: `新建笔记「${title}」`,
        detail: content ? content.slice(0, 120) + (content.length > 120 ? '…' : '') : undefined,
      };
    }
    case 'updateNote': {
      const title = String(payload.title || '文档');
      return { title: '更新文档', summary: `修改「${title}」` };
    }
    case 'deleteNote': {
      const title = String(payload.title || '文档');
      return { title: '删除文档', summary: `删除「${title}」` };
    }
    case 'searchNotes': {
      const count = output?.count ?? 0;
      const items = (output?.items as Array<{ title?: string }>) || [];
      const names = items.slice(0, 3).map((i) => i.title).filter(Boolean).join('、');
      return {
        title: '搜索文档',
        summary: count ? `找到 ${count} 篇相关文档` : '未找到匹配文档',
        detail: names || undefined,
      };
    }
    case 'summarize': {
      const summary = (output as { summary?: string })?.summary;
      return {
        title: '生成摘要',
        summary: summary ? summary.slice(0, 80) + (summary.length > 80 ? '…' : '') : '已生成文档摘要',
      };
    }
    case 'extractTodos': {
      const suggestions =
        (output?.suggestions as Array<{ title?: string; dueAt?: string }>)
        || (payload.suggestions as Array<{ title?: string; dueAt?: string }>)
        || [];
      if (!suggestions.length) {
        return { title: '提取待办', summary: '未识别到可执行待办' };
      }
      const lines = suggestions.slice(0, 5).map((s) => {
        const due = s.dueAt ? `（${formatDateTime(s.dueAt)}）` : '';
        return `· ${s.title}${due}`;
      });
      return {
        title: '提取待办',
        summary: `识别 ${suggestions.length} 条待办`,
        detail: lines.join('\n'),
      };
    }
    case 'createTodo': {
      const title = String(payload.title || '待办');
      const due = payload.dueAt ? `，截止 ${formatDateTime(payload.dueAt)}` : '';
      return {
        title: '创建待办',
        summary: `添加待办「${title}」${due}`,
      };
    }
    case 'updateTodo': {
      const title = String(payload.title || payload.newTitle || '待办');
      return { title: '更新待办', summary: `修改「${title}」` };
    }
    case 'completeTodo': {
      const title = String(payload.title || '待办');
      return { title: '完成待办', summary: `标记「${title}」为已完成` };
    }
    case 'deleteTodo': {
      const title = String(payload.title || '待办');
      return { title: '删除待办', summary: `删除「${title}」` };
    }
    case 'createReminder': {
      const when = formatDateTime(payload.triggerAt);
      const msg = String(payload.message || payload.todoTitle || '提醒');
      return {
        title: '设置提醒',
        summary: `${when || '指定时间'} 提醒：${msg}`,
      };
    }
    case 'cancelReminder': {
      return { title: '取消提醒', summary: `取消提醒 #${payload.reminderId ?? ''}` };
    }
    case 'listDriveFiles':
    case 'listDriveFolders': {
      const count = output?.count ?? 0;
      return {
        title: step.tool === 'listDriveFolders' ? '查看网盘文件夹' : '查看网盘文件',
        summary: `共 ${count} 项`,
        detail: formatListItems(output, 'items', 'fileName'),
      };
    }
    case 'listShares': {
      const count = output?.count ?? 0;
      return {
        title: '我的分享',
        summary: `共 ${count} 条分享`,
        detail: formatListItems(output, 'items', 'title'),
      };
    }
    case 'listTags': {
      const count = output?.count ?? 0;
      return {
        title: '查看标签',
        summary: `共 ${count} 个标签`,
        detail: formatListItems(output, 'items', 'tagName'),
      };
    }
    case 'listNoteFolders': {
      const count = output?.count ?? 0;
      return {
        title: '文档分组',
        summary: `共 ${count} 个分组`,
        detail: formatListItems(output, 'items', 'name'),
      };
    }
    case 'deleteDriveFile': {
      const name = String(payload.fileName || payload.title || '文件');
      return { title: '删除网盘文件', summary: `删除「${name}」` };
    }
    case 'moveDriveFile': {
      const name = String(payload.fileName || '文件');
      return { title: '移动网盘文件', summary: `移动「${name}」` };
    }
    case 'linkFileToNote': {
      return { title: '关联文件', summary: '将网盘文件关联到文档' };
    }
    case 'createNoteShare':
    case 'createFileShare': {
      const title = String(payload.title || payload.fileName || '内容');
      const pwd = payload.password ? '（含密码）' : '';
      return {
        title: step.tool === 'createFileShare' ? '分享网盘文件' : '分享文档',
        summary: `创建「${title}」的分享链接${pwd}`,
      };
    }
    case 'revokeShare': {
      return { title: '取消分享', summary: `撤销分享 #${payload.shareId ?? ''}` };
    }
    case 'createTag': {
      return { title: '创建标签', summary: `新建标签「${payload.tagName ?? ''}」` };
    }
    case 'deleteTag': {
      return { title: '删除标签', summary: `删除标签「${payload.tagName ?? ''}」` };
    }
    case 'tagNote': {
      const title = String(payload.title || '文档');
      const tags = String(payload.tagNames || payload.tagName || '');
      return { title: '文档打标签', summary: `为「${title}」添加标签：${tags}` };
    }
    case 'createNoteFolder': {
      return { title: '创建文档分组', summary: `新建分组「${payload.name ?? ''}」` };
    }
    case 'createDriveFolder': {
      return { title: '创建网盘文件夹', summary: `新建文件夹「${payload.name ?? ''}」` };
    }
    case 'moveNoteToFolder': {
      const title = String(payload.title || '文档');
      return { title: '移动文档', summary: `将「${title}」移入分组` };
    }
    default:
      return {
        title: step.tool,
        summary: step.output?.slice(0, 80) || '处理中',
      };
  }
}

export function presentStepDone(step: AiAgentStep): string | undefined {
  if (step.status === 'skipped') return '已跳过';
  if (step.status !== 'done') return undefined;
  const result = step.result || {};
  if (step.tool === 'createNote') {
    const note = result.note as { title?: string } | undefined;
    return note?.title ? `已创建「${note.title}」` : '文档已创建';
  }
  if (step.tool === 'updateNote') {
    const note = result.note as { title?: string } | undefined;
    return note?.title ? `已更新「${note.title}」` : '文档已更新';
  }
  if (step.tool === 'deleteNote') {
    return '文档已删除';
  }
  if (step.tool === 'extractTodos') {
    const extract = result.extract as { createdCount?: number } | undefined;
    const n = extract?.createdCount ?? 0;
    return n > 0 ? `已添加 ${n} 条待办` : '待办已处理';
  }
  if (step.tool === 'createReminder' || step.tool === 'cancelReminder') {
    return step.tool === 'cancelReminder' ? '提醒已取消' : '提醒已设置';
  }
  if (step.tool === 'createTodo' || step.tool === 'updateTodo' || step.tool === 'completeTodo') {
    if (step.tool === 'completeTodo') return '待办已完成';
    if (step.tool === 'updateTodo') return '待办已更新';
    return '待办已创建';
  }
  if (step.tool === 'deleteTodo') {
    return '待办已删除';
  }
  if (['listNotes', 'listTodos', 'listReminders', 'searchNotes', 'searchTodos', 'summarize',
    'listDriveFiles', 'listDriveFolders', 'listShares', 'listTags', 'listNoteFolders'].includes(step.tool)) {
    return '已查询';
  }
  if (step.tool === 'createNoteShare' || step.tool === 'createFileShare') {
    const share = result.share as { sharePath?: string } | undefined;
    return share?.sharePath ? `分享已创建：${share.sharePath}` : '分享已创建';
  }
  if (step.tool === 'revokeShare') return '分享已取消';
  if (step.tool === 'deleteDriveFile') return '网盘文件已删除';
  if (step.tool === 'moveDriveFile') return '文件已移动';
  if (step.tool === 'linkFileToNote') return '文件已关联到文档';
  if (step.tool === 'createTag') return '标签已创建';
  if (step.tool === 'deleteTag') return '标签已删除';
  if (step.tool === 'tagNote') return '标签已更新';
  if (step.tool === 'createNoteFolder' || step.tool === 'createDriveFolder') return '分组已创建';
  if (step.tool === 'moveNoteToFolder') return '文档已移动';
  return '已完成';
}

export function buildPlanOverview(steps: AiAgentStep[]): string {
  return steps
    .map((s) => presentStep(s).title)
    .filter(Boolean)
    .join(' → ');
}

export function stepCategoryLabel(tool: string): string | undefined {
  switch (tool) {
    case 'createNote':
    case 'updateNote':
    case 'deleteNote':
    case 'searchNotes':
    case 'listNotes':
    case 'summarize':
      return '文档';
    case 'extractTodos':
    case 'createTodo':
    case 'updateTodo':
    case 'completeTodo':
    case 'deleteTodo':
    case 'listTodos':
    case 'searchTodos':
      return '待办';
    case 'createReminder':
    case 'cancelReminder':
    case 'listReminders':
      return '提醒';
    case 'listDriveFiles':
    case 'listDriveFolders':
    case 'deleteDriveFile':
    case 'moveDriveFile':
    case 'linkFileToNote':
    case 'createDriveFolder':
      return '网盘';
    case 'listShares':
    case 'createNoteShare':
    case 'createFileShare':
    case 'revokeShare':
      return '分享';
    case 'listTags':
    case 'createTag':
    case 'deleteTag':
    case 'tagNote':
      return '标签';
    case 'listNoteFolders':
    case 'createNoteFolder':
    case 'moveNoteToFolder':
      return '分组';
    default:
      return undefined;
  }
}

export const QUICK_INSTRUCTIONS = [
  '灵感有哪些文档',
  '网盘里有哪些文件',
  '列出我的分享',
  '给「周报」打上产品标签',
  '分享这篇文档，密码 demo',
];
