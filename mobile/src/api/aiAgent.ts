import { request } from '../utils/http';

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
  createNote: '创建文档',
  updateNote: '更新文档',
  deleteNote: '删除文档',
  extractTodos: '提取待办',
  createTodo: '创建待办',
  updateTodo: '更新待办',
  completeTodo: '完成待办',
  deleteTodo: '删除待办',
  createReminder: '创建提醒',
  cancelReminder: '取消提醒',
  createNoteShare: '分享文档',
  createFileShare: '分享网盘文件',
  createNoteFolder: '创建文档分组',
  createDriveFolder: '创建网盘文件夹',
  moveNoteToFolder: '移动文档',
};

export function planAgentTask(payload: {
  workspaceId: string;
  instruction: string;
  sessionId?: string;
  recentContext?: string;
}) {
  return request<AiAgentTask>({ url: '/ai/agent/plan', method: 'POST', data: payload });
}

export function confirmAgentTask(taskId: string, stepIds: string[]) {
  return request<AiAgentTask>({
    url: `/ai/agent/tasks/${taskId}/confirm`,
    method: 'POST',
    data: { stepIds },
  });
}
