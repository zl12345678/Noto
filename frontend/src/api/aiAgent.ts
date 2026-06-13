import http from './http';

export interface AiAgentStep {
  id: string;
  tool: string;
  status: string;
  requiresConfirm: boolean;
  input?: Record<string, unknown>;
  output?: string;
  actionPayload?: Record<string, unknown>;
  result?: Record<string, unknown>;
}

export interface AiAgentTask {
  id: string;
  workspaceId: string;
  taskType: string;
  status: number;
  instruction?: string;
  assistantReply?: string;
  steps?: AiAgentStep[];
  autoExecuted?: boolean;
  errorMessage?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AiAgentTaskPage {
  records: AiAgentTask[];
  total: number;
  current: number;
  size: number;
}

export const AI_TASK_STATUS = {
  PENDING: 0,
  PROCESSING: 1,
  SUCCESS: 2,
  FAILED: 3,
  AWAITING_CONFIRM: 4,
} as const;

export const AI_TASK_STATUS_LABEL: Record<number, string> = {
  0: '待处理',
  1: '处理中',
  2: '已完成',
  3: '失败',
  4: '待确认',
};

export const AGENT_TOOL_LABEL: Record<string, string> = {
  searchNotes: '搜索文档',
  listNotes: '文档目录',
  createNote: '创建文档',
  updateNote: '更新文档',
  deleteNote: '删除文档',
  summarize: '生成摘要',
  extractTodos: '提取待办',
  listTodos: '查看待办',
  searchTodos: '搜索待办',
  createTodo: '创建待办',
  updateTodo: '更新待办',
  completeTodo: '完成待办',
  deleteTodo: '删除待办',
  listReminders: '查看提醒',
  createReminder: '创建提醒',
  cancelReminder: '取消提醒',
  listDriveFiles: '网盘文件',
  listDriveFolders: '网盘文件夹',
  deleteDriveFile: '删除网盘文件',
  moveDriveFile: '移动网盘文件',
  linkFileToNote: '关联文件到文档',
  listShares: '我的分享',
  createNoteShare: '分享文档',
  createFileShare: '分享网盘文件',
  revokeShare: '取消分享',
  listTags: '查看标签',
  createTag: '创建标签',
  deleteTag: '删除标签',
  tagNote: '文档打标签',
  listNoteFolders: '文档分组',
  createNoteFolder: '创建文档分组',
  createDriveFolder: '创建网盘文件夹',
  moveNoteToFolder: '移动文档分组',
};

export function planAgentTask(payload: {
  workspaceId: string;
  instruction: string;
  sessionId?: string;
  recentContext?: string;
}) {
  return http.post<AiAgentTask>('/ai/agent/plan', payload);
}

export function confirmAgentTask(taskId: string, stepIds: string[]) {
  return http.post<AiAgentTask>(`/ai/agent/tasks/${taskId}/confirm`, { stepIds });
}

export function getAgentTask(taskId: string) {
  return http.get<AiAgentTask>(`/ai/agent/tasks/${taskId}`);
}

export function listAgentTasks(params?: { workspaceId?: string; page?: number; size?: number }) {
  return http.get<AiAgentTaskPage>('/ai/agent/tasks', { params });
}
