import { request } from '../utils/http';

export interface AiStatus {
  enabled: boolean;
  configured: boolean;
  model: string;
  provider?: string;
}

export interface AiReference {
  noteId: string;
  noteTitle: string;
  snippet: string;
}

export interface AiAskResult {
  answer: string;
  references: AiReference[];
  knowledgeGaps?: string[];
}

export interface AiRouteResult {
  intent: 'chat' | 'agent';
  reason?: string;
  source?: 'llm' | 'rule';
}

export interface NoteSummaryResult {
  summary: string;
  keyPoints: string[];
  riskPoints: string[];
}

export type NoteTransformMode =
  | 'polish'
  | 'bulletize'
  | 'structure'
  | 'template_weekly'
  | 'template_retro'
  | 'template_proposal';

export interface NoteTransformResult {
  mode: string;
  content: string;
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

export interface ConfirmExtractTodoItem {
  title: string;
  completed: boolean;
  priority?: number;
  horizon?: 'action' | 'long_term';
  dueAt?: string | null;
}

export interface NoteExtractTodosResult {
  createdCount: number;
  skippedCount: number;
  todos: Array<{ id: string; title: string }>;
}

export interface AiChatSession {
  id: string;
  workspaceId: string;
  scope?: 'workspace' | 'note';
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

export interface AiDailyReview {
  summary: string;
  highlights: string[];
  blockers: string[];
  tomorrowFocus: string[];
  completedCount: number;
}

export interface AiDigest {
  taskId?: string;
  digestDate?: string;
  generatedAt?: string;
  suggestions?: AiDailySuggestions;
  review?: AiDailyReview;
}

export interface AiWeeklyRetro {
  taskId?: string;
  weekStart?: string;
  noteId?: string;
  noteTitle?: string;
  generatedAt?: string;
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

export type TodoOverdueAction = 'reschedule' | 'breakdown' | 'archive' | 'complete';

export interface TodoOverdueAdviceItem {
  todoId: string | number;
  title: string;
  action: TodoOverdueAction;
  reason: string;
  suggestedDueAt?: string | null;
}

export interface TodoOverdueAdvice {
  summary: string;
  suggestions: TodoOverdueAdviceItem[];
}

export interface TodoCompletionRetro {
  line: string;
  noteId?: string | number | null;
  noteTitle?: string | null;
  todoTitle: string;
  appended: boolean;
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

export interface NoteRagReindexResult {
  indexedNotes: number;
  indexedChunks: number;
}

export function getAiStatus() {
  return request<AiStatus>({ url: '/ai/status' });
}

export function routeAiIntent(payload: {
  message: string;
  sessionId?: string;
  recentContext?: string;
}) {
  return request<AiRouteResult>({ url: '/ai/route', method: 'POST', data: payload });
}

export function askAi(payload: {
  workspaceId?: string;
  question: string;
  scope?: 'workspace' | 'note';
  targetId?: string;
  sessionId?: string;
  recentContext?: string;
}) {
  return request<AiAskResult>({ url: '/ai/ask', method: 'POST', data: payload });
}

export function summarizeNoteByAi(
  noteId: string,
  draft?: { title?: string; content?: string },
  persist = true,
) {
  return request<NoteSummaryResult>({
    url: `/ai/notes/${noteId}/summarize`,
    method: 'POST',
    params: { persist },
    data: draft || {},
  });
}

export function transformNoteByAi(
  noteId: string,
  payload: { mode: NoteTransformMode; title?: string; content?: string },
) {
  return request<NoteTransformResult>({ url: `/ai/notes/${noteId}/transform`, method: 'POST', data: payload });
}

export function previewExtractTodosByAi(noteId: string) {
  return request<NoteExtractTodosPreview>({ url: `/ai/notes/${noteId}/extract-todos`, method: 'POST' });
}

export function confirmExtractTodosByAi(noteId: string, items: ConfirmExtractTodoItem[]) {
  return request<NoteExtractTodosResult>({
    url: `/ai/notes/${noteId}/extract-todos/confirm`,
    method: 'POST',
    data: { items },
  });
}

export function listAiSessions(workspaceId: string) {
  return request<AiChatSession[]>({ url: '/ai/sessions', params: { workspaceId, limit: 20 } });
}

export function createAiSession(payload: {
  workspaceId: string;
  scope?: 'workspace' | 'note';
  targetId?: string;
  title?: string;
}) {
  return request<AiChatSession>({ url: '/ai/sessions', method: 'POST', data: payload });
}

export function listAiSessionMessages(sessionId: string) {
  return request<AiChatMessageRecord[]>({ url: `/ai/sessions/${sessionId}/messages` });
}

export function deleteAiSession(sessionId: string) {
  return request<void>({ url: `/ai/sessions/${sessionId}`, method: 'DELETE' });
}

export function getTodayAiDigest() {
  return request<AiDigest | null>({ url: '/ai/digest/today' });
}

export function generateAiDigest(workspaceId?: string) {
  return request<AiDigest>({
    url: '/ai/digest/generate',
    method: 'POST',
    params: workspaceId ? { workspaceId } : undefined,
  });
}

export function getCurrentWeeklyRetro() {
  return request<AiWeeklyRetro | null>({ url: '/ai/weekly-retro/current' });
}

export function generateWeeklyRetro(workspaceId?: string) {
  return request<AiWeeklyRetro>({
    url: '/ai/weekly-retro/generate',
    method: 'POST',
    params: workspaceId ? { workspaceId } : undefined,
  });
}

export function getAiDailyReview(workspaceId?: string) {
  return request<AiDailyReview>({
    url: '/ai/todos/daily-review',
    params: workspaceId ? { workspaceId } : undefined,
  });
}

export function breakdownTodoByAi(payload: {
  title: string;
  description?: string;
  horizon?: 'action' | 'long_term';
}) {
  return request<TodoBreakdownResult>({ url: '/ai/todos/breakdown', method: 'POST', data: payload });
}

export function getTodoOverdueAdvice(workspaceId?: string) {
  return request<TodoOverdueAdvice>({
    url: '/ai/todos/overdue-advice',
    params: workspaceId ? { workspaceId } : undefined,
  });
}

export function createTodoCompletionRetro(
  todoId: string,
  payload: { line?: string; append?: boolean; scenario?: 'reminder_due' | 'completed' } = {},
) {
  return request<TodoCompletionRetro>({
    url: `/ai/todos/${todoId}/completion-retro`,
    method: 'POST',
    data: payload,
  });
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
  return request<NoteSynthesizeResult>({ url: '/ai/notes/synthesize', method: 'POST', data: payload });
}

export function reindexWorkspaceRag(workspaceId?: string) {
  return request<NoteRagReindexResult>({
    url: '/ai/rag/reindex',
    method: 'POST',
    params: workspaceId ? { workspaceId } : undefined,
  });
}
