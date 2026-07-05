import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { AiReference } from '../api/ai';
import type { AiAgentTask } from '../api/aiAgent';

export type AiChatMessage = {
  id?: string;
  role: 'user' | 'assistant';
  content: string;
  kind?: 'text' | 'agent';
  intent?: 'chat' | 'agent' | 'clarify';
  references?: AiReference[];
  knowledgeGaps?: string[];
  agentTask?: AiAgentTask;
};

export const useAiChatStore = defineStore('aiChat', () => {
  const messages = ref<AiChatMessage[]>([]);
  const workspaceId = ref<string | undefined>();
  const scope = ref<'workspace' | 'note'>('workspace');
  const targetId = ref<string | undefined>();
  const sessionId = ref<string | undefined>();

  function setMessages(next: AiChatMessage[]) {
    messages.value = next;
  }

  function startNewSession() {
    sessionId.value = undefined;
    messages.value = [];
  }

  function applySession(
    id: string,
    nextMessages: AiChatMessage[],
    meta?: { scope?: 'workspace' | 'note'; targetId?: string },
  ) {
    sessionId.value = id;
    messages.value = nextMessages;
    if (meta?.scope) scope.value = meta.scope;
    if (meta?.targetId !== undefined) targetId.value = meta.targetId;
  }

  function clearSession() {
    sessionId.value = undefined;
    messages.value = [];
    scope.value = 'workspace';
    targetId.value = undefined;
  }

  return {
    messages,
    workspaceId,
    scope,
    targetId,
    sessionId,
    setMessages,
    startNewSession,
    applySession,
    clearSession,
  };
});
