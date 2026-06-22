import http from './http';
import { getApiBaseUrl } from '../utils/apiBase';

export interface AiStatus {
  enabled: boolean;
  configured: boolean;
  model: string;
  provider: string;
}

export interface AiObservabilityAction {
  actionType: string;
  resourceType?: string | null;
  success: boolean;
  latencyMs?: number | null;
  createdAt?: string;
}

export interface AiObservabilitySummary {
  totalCalls: number;
  successRate: number;
  averageLatencyMs?: number | null;
  recentActions: AiObservabilityAction[];
}

export interface NoteSummaryResult {
  summary: string;
  keyPoints: string[];
  riskPoints: string[];
}

export interface AiReference {
  noteId: string;
  noteTitle: string;
  snippet: string;
  offsetStart?: number | null;
  offsetEnd?: number | null;
  highlightKeyword?: string | null;
  relevanceScore?: number | null;
}

export interface AiAskResult {
  answer: string;
  references: AiReference[];
  knowledgeGaps?: string[];
}

export interface AiAskRequest {
  workspaceId?: string;
  question: string;
  scope?: 'workspace' | 'note';
  targetId?: string;
  sessionId?: string;
  recentContext?: string;
}

export interface AiChatSession {
  id: string;
  workspaceId: string;
  scope: 'workspace' | 'note';
  targetId?: string | null;
  title: string;
  latestMessageAt?: string | null;
  createdAt?: string;
}

export interface AiChatMessageRecord {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  references?: AiReference[];
  createdAt?: string;
}

export interface AiChatSessionCreateRequest {
  workspaceId: string;
  scope?: 'workspace' | 'note';
  targetId?: string;
  title?: string;
}

export interface ExtractedTodoSuggestion {
  title: string;
  completed: boolean;
  priority: number;
  horizon: 'action' | 'long_term';
  dueAt?: string | null;
  duplicate: boolean;
}

export interface NoteExtractTodosPreview {
  noteId: string;
  noteTitle: string;
  suggestions: ExtractedTodoSuggestion[];
}

export interface NoteExtractTodosResult {
  createdCount: number;
  skippedCount: number;
  todos: Array<{ id: string; title: string }>;
}

export type ExtractTodosSource = 'ai' | 'markdown';

export interface ConfirmExtractTodoItem {
  title: string;
  completed: boolean;
  priority?: number;
  horizon?: 'action' | 'long_term';
  dueAt?: string | null;
}

export interface AiStreamHandlers {
  onReferences?: (references: AiReference[]) => void;
  onToken: (token: string) => void;
  onKnowledgeGaps?: (gaps: string[]) => void;
  onDone?: () => void;
  onError?: (message: string) => void;
}

function authHeaders(): HeadersInit {
  const token = localStorage.getItem('noto-zhihui-token') || sessionStorage.getItem('noto-zhihui-token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function parseSseBlock(block: string, handlers: AiStreamHandlers) {
  const lines = block.split('\n');
  let eventName = 'message';
  const dataLines: string[] = [];

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart());
    }
  }

  if (dataLines.length === 0) return;
  const data = dataLines.join('\n');

  if (eventName === 'references') {
    handlers.onReferences?.(JSON.parse(data) as AiReference[]);
    return;
  }
  if (eventName === 'token') {
    handlers.onToken(data);
    return;
  }
  if (eventName === 'knowledge_gaps') {
    handlers.onKnowledgeGaps?.(JSON.parse(data) as string[]);
    return;
  }
  if (eventName === 'error') {
    handlers.onError?.(data);
    return;
  }
  if (eventName === 'done') {
    handlers.onDone?.();
  }
}

export function getAiStatus() {
  return http.get<AiStatus>('/ai/status');
}

export function getAiObservabilitySummary() {
  return http.get<AiObservabilitySummary>('/ai/observability/summary');
}

export interface AiRouteResult {
  intent: 'chat' | 'agent';
  reason?: string;
  source?: 'llm' | 'rule';
}

export function routeAiIntent(payload: {
  message: string;
  sessionId?: string;
  recentContext?: string;
}) {
  return http.post<AiRouteResult>('/ai/route', payload);
}

export function summarizeNoteByAi(
  noteId: string,
  draft?: { title?: string; content?: string },
  persist = true,
) {
  return http.post<NoteSummaryResult>(`/ai/notes/${noteId}/summarize`, draft || {}, {
    params: { persist },
  });
}

export type NoteTransformMode =
  | 'polish'
  | 'bulletize'
  | 'structure'
  | 'template_weekly'
  | 'template_retro'
  | 'template_proposal';

export type SelectionTransformMode = 'concise' | 'expand' | 'formal';

export interface NoteTransformResult {
  mode: string;
  content: string;
}

export function transformNoteByAi(
  noteId: string,
  payload: { mode: NoteTransformMode; title?: string; content?: string },
) {
  return http.post<NoteTransformResult>(`/ai/notes/${noteId}/transform`, payload);
}

export function stripKnowledgeGaps(text: string) {
  return text.replace(/\n?KNOWLEDGE_GAPS:[^\n]*/g, '').trim();
}

async function readSseStream(response: Response, handlers: AiStreamHandlers) {
  if (!response.body) {
    throw new Error('流式连接失败');
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split('\n\n');
    buffer = blocks.pop() || '';

    for (const block of blocks) {
      if (block.trim()) {
        parseSseBlock(block, handlers);
      }
    }
  }

  if (buffer.trim()) {
    parseSseBlock(buffer, handlers);
  }

  handlers.onDone?.();
}

export async function transformSelectionStream(
  noteId: string,
  payload: {
    mode: SelectionTransformMode;
    selectedText: string;
    title?: string;
    content?: string;
  },
  handlers: AiStreamHandlers,
) {
  const response = await fetch(`${getApiBaseUrl()}/ai/notes/${noteId}/transform-selection/stream`, {
    method: 'POST',
    headers: {
      ...authHeaders(),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error('流式改写连接失败');
  }

  await readSseStream(response, handlers);
}

export function listAiChatSessions(workspaceId: string, limit = 20) {
  return http.get<AiChatSession[]>('/ai/sessions', { params: { workspaceId, limit } });
}

export function createAiChatSession(payload: AiChatSessionCreateRequest) {
  return http.post<AiChatSession>('/ai/sessions', payload);
}

export function listAiChatMessages(sessionId: string) {
  return http.get<AiChatMessageRecord[]>(`/ai/sessions/${sessionId}/messages`);
}

export function deleteAiChatSession(sessionId: string) {
  return http.delete<void>(`/ai/sessions/${sessionId}`);
}

/** AI 提取预览（不入库，需审查后 confirm） */
export function previewExtractTodosByAi(noteId: string) {
  return http.post<NoteExtractTodosPreview>(`/ai/notes/${noteId}/extract-todos`);
}

export function confirmExtractTodosByAi(noteId: string, items: ConfirmExtractTodoItem[]) {
  return http.post<NoteExtractTodosResult>(`/ai/notes/${noteId}/extract-todos/confirm`, { items });
}

export function askAi(payload: AiAskRequest) {
  return http.post<AiAskResult>('/ai/ask', payload);
}

export interface AiDailySuggestion {
  todoId: string;
  title: string;
  reason: string;
  action: 'start' | 'continue' | 'focus';
}

export interface AiDailySuggestions {
  summary: string;
  suggestions: AiDailySuggestion[];
}

export interface AiSubtaskSuggestion {
  title: string;
  priority: number;
  horizon: 'action' | 'long_term';
  dueAt?: string | null;
}

export interface TodoBreakdownResult {
  sourceTitle: string;
  subtasks: AiSubtaskSuggestion[];
}

export function getAiDailySuggestions(workspaceId?: string) {
  return http.get<AiDailySuggestions>('/ai/todos/daily-suggestions', {
    params: workspaceId ? { workspaceId } : undefined,
  });
}

export interface AiDailyReview {
  summary: string;
  highlights: string[];
  blockers: string[];
  tomorrowFocus: string[];
  completedCount: number;
}

export function getAiDailyReview(workspaceId?: string) {
  return http.get<AiDailyReview>('/ai/todos/daily-review', {
    params: workspaceId ? { workspaceId } : undefined,
  });
}

export interface AiDigest {
  taskId?: string;
  digestDate?: string;
  generatedAt?: string;
  suggestions?: AiDailySuggestions;
  review?: AiDailyReview;
}

export function getTodayAiDigest() {
  return http.get<AiDigest | null>('/ai/digest/today');
}

export function generateAiDigest(workspaceId?: string) {
  return http.post<AiDigest>('/ai/digest/generate', null, {
    params: workspaceId ? { workspaceId } : undefined,
  });
}

export interface AiWeeklyRetro {
  taskId?: string;
  weekStart?: string;
  noteId?: string;
  noteTitle?: string;
  generatedAt?: string;
}

export function getCurrentWeeklyRetro() {
  return http.get<AiWeeklyRetro | null>('/ai/weekly-retro/current');
}

export function generateWeeklyRetro(workspaceId?: string) {
  return http.post<AiWeeklyRetro>('/ai/weekly-retro/generate', null, {
    params: workspaceId ? { workspaceId } : undefined,
  });
}

export function breakdownTodoByAi(payload: {
  title: string;
  description?: string;
  horizon?: 'action' | 'long_term';
}) {
  return http.post<TodoBreakdownResult>('/ai/todos/breakdown', payload);
}

export type NoteSynthesizeTemplate = 'decision' | 'status' | 'weekly' | 'retro';

export interface NoteSynthesizeSource {
  noteId: string;
  noteTitle: string;
}

export interface NoteSynthesizeResult {
  template: NoteSynthesizeTemplate;
  title: string;
  content: string;
  sources: NoteSynthesizeSource[];
  createdNoteId?: string;
}

export function synthesizeNotesByAi(payload: {
  workspaceId: string;
  topic: string;
  template: NoteSynthesizeTemplate;
  dateFrom?: string;
  dateTo?: string;
  noteIds?: string[];
  saveAsNote?: boolean;
  folderId?: string;
}) {
  return http.post<NoteSynthesizeResult>('/ai/notes/synthesize', payload);
}

export type TodoOverdueAction = 'reschedule' | 'breakdown' | 'archive' | 'complete';

export interface TodoOverdueAdviceItem {
  todoId: string;
  title: string;
  action: TodoOverdueAction;
  reason: string;
  suggestedDueAt?: string | null;
}

export interface TodoOverdueAdvice {
  summary: string;
  suggestions: TodoOverdueAdviceItem[];
}

export function getOverdueTodoAdvice(workspaceId?: string) {
  return http.get<TodoOverdueAdvice>('/ai/todos/overdue-advice', {
    params: workspaceId ? { workspaceId } : undefined,
  });
}

export interface TodoCompletionRetroResult {
  line: string;
  noteId?: string | null;
  noteTitle?: string | null;
  todoTitle?: string | null;
  appended: boolean;
}

export function completionRetro(
  todoId: string,
  payload: { line?: string; append?: boolean; scenario?: 'reminder_due' | 'completed' } = {},
) {
  return http.post<TodoCompletionRetroResult>(`/ai/todos/${todoId}/completion-retro`, payload);
}

export interface NoteRagIndexStatus {
  indexedNotes: number;
  indexedChunks: number;
  embeddingModel?: string | null;
  ragAvailable: boolean;
}

export interface NoteRagReindexResult {
  indexedNotes: number;
  indexedChunks: number;
}

export function getNoteRagStatus(workspaceId?: string) {
  return http.get<NoteRagIndexStatus>('/ai/rag/status', {
    params: workspaceId ? { workspaceId } : undefined,
  });
}

export function reindexNoteRag(noteId: string) {
  return http.post<NoteRagReindexResult>(`/ai/rag/notes/${noteId}/reindex`);
}

export function reindexWorkspaceRag(workspaceId?: string) {
  return http.post<NoteRagReindexResult>('/ai/rag/reindex', undefined, {
    params: workspaceId ? { workspaceId } : undefined,
  });
}

export async function askAiStream(payload: AiAskRequest, handlers: AiStreamHandlers) {
  const params = new URLSearchParams();
  params.set('question', payload.question);
  if (payload.workspaceId) params.set('workspaceId', payload.workspaceId);
  if (payload.scope) params.set('scope', payload.scope);
  if (payload.targetId) params.set('targetId', payload.targetId);
  if (payload.sessionId) params.set('sessionId', payload.sessionId);
  if (payload.recentContext) params.set('recentContext', payload.recentContext);

  const response = await fetch(`${getApiBaseUrl()}/ai/ask/stream?${params.toString()}`, {
    method: 'GET',
    headers: authHeaders(),
  });

  if (!response.ok || !response.body) {
    throw new Error('流式问答连接失败');
  }

  await readSseStream(response, handlers);
}
