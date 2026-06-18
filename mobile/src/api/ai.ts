import { request } from '../utils/http';

export interface AiStatus {
  enabled: boolean;
  configured: boolean;
  model: string;
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

export interface AiChatSession {
  id: string;
  workspaceId: string;
  title: string;
  latestMessageAt?: string | null;
}

export interface AiChatMessageRecord {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  references?: AiReference[];
}

export function getAiStatus() {
  return request<AiStatus>({ url: '/ai/status' });
}

export function askAi(payload: {
  workspaceId?: string;
  question: string;
  scope?: 'workspace' | 'note';
  targetId?: string;
  sessionId?: string;
}) {
  return request<AiAskResult>({ url: '/ai/ask', method: 'POST', data: payload });
}

export function listAiSessions(workspaceId: string) {
  return request<AiChatSession[]>({ url: '/ai/sessions', params: { workspaceId } });
}

export function createAiSession(payload: { workspaceId: string; title?: string }) {
  return request<AiChatSession>({ url: '/ai/sessions', method: 'POST', data: payload });
}

export function listAiSessionMessages(sessionId: string) {
  return request<AiChatMessageRecord[]>({ url: `/ai/sessions/${sessionId}/messages` });
}

export function deleteAiSession(sessionId: string) {
  return request<void>({ url: `/ai/sessions/${sessionId}`, method: 'DELETE' });
}
