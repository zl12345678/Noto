<template>
  <div class="ai-page">
    <aside class="session-sidebar">
      <div class="sidebar-head">
        <div class="sidebar-head-text">
          <h3>历史会话</h3>
          <span v-if="sessions.length" class="session-count">{{ sessions.length }} 条</span>
        </div>
        <a-button type="link" size="small" class="new-chat-btn" @click="handleNewChat">新对话</a-button>
      </div>
      <a-spin :spinning="loadingSessions">
        <ul v-if="sessions.length" class="session-list">
          <li
            v-for="item in sessions"
            :key="item.id"
            class="session-item"
            :class="{ active: aiChat.sessionId === String(item.id) }"
            @click="openSession(item)"
          >
            <span class="session-title">{{ item.title || '新对话' }}</span>
            <span class="session-meta">
              {{ item.scope === 'note' ? '单篇' : '知识库' }}
              · {{ formatSessionTime(item.latestMessageAt || item.createdAt) }}
            </span>
          </li>
        </ul>
        <div v-else class="session-empty">暂无历史会话</div>
      </a-spin>
    </aside>

    <section class="chat-panel">
      <header class="chat-topbar">
        <div class="chat-topbar-filters">
          <a-select
            v-model:value="aiChat.workspaceId"
            placeholder="知识库"
            size="small"
            class="filter-select"
            :options="workspaceOptions"
            @change="onWorkspaceChange"
          />
          <a-select
            v-model:value="aiChat.scope"
            size="small"
            class="filter-select filter-select--scope"
            :options="scopeOptions"
          />
          <span class="status-pill" :class="{ 'status-pill--on': aiStatus?.enabled }">
            {{ aiStatus?.enabled ? '在线' : '未启用' }}
          </span>
        </div>
        <div class="chat-topbar-actions">
          <a-button type="link" size="small" @click="synthesizeOpen = true">跨文档合成</a-button>
          <a-button
            v-if="aiChat.messages.length || aiChat.sessionId"
            type="link"
            size="small"
            danger
            @click="handleClearChat"
          >
            清空
          </a-button>
        </div>
      </header>

      <NoteSynthesizeModal
        v-model:open="synthesizeOpen"
        :workspaces="workspaces"
        :default-workspace-id="aiChat.workspaceId"
      />

      <a-spin :spinning="sessionLoading" wrapper-class-name="chat-log-spin">
        <div ref="chatLogRef" class="chat-log" :key="chatLogKey">
          <div v-if="aiChat.messages.length === 0 && !processing" class="chat-empty">
          <p class="empty-title">说出你的需求，我来操控文档、待办、提醒、网盘与分享</p>
        </div>

        <div v-for="(item, index) in aiChat.messages" :key="messageKey(item, index)" class="chat-item" :class="item.role">
          <div class="bubble" :class="{ 'bubble-agent': item.kind === 'agent' }">
            <div class="bubble-head">
              <p class="bubble-role">{{ item.role === 'user' ? '我' : 'AI' }}</p>
              <a-tag v-if="item.role === 'assistant' && item.intent" size="small" :color="item.intent === 'agent' ? 'purple' : 'blue'">
                {{ intentLabel(item.intent) }}
              </a-tag>
            </div>

            <p v-if="item.role === 'user' && item.content" class="bubble-user-text">{{ item.content }}</p>

            <AiMessageContent
              v-if="shouldShowAssistantContent(item)"
              :key="messageKey(item, index)"
              class="bubble-content"
              :content="item.content"
              :agent-steps="item.kind === 'agent' ? item.agentTask?.steps : undefined"
              :streaming="processing && index === aiChat.messages.length - 1 && item.kind === 'text'"
              :workspace-id="aiChat.workspaceId"
            />

            <AiAgentTaskCard
              v-if="item.kind === 'agent' && item.agentTask && !isReadOnlyListTask(item.agentTask.steps)"
              :task="item.agentTask"
              hide-assistant-reply
              @task-updated="(task) => updateAgentTask(index, task)"
            />

            <div v-if="item.knowledgeGaps?.length" class="knowledge-gaps">
              <p class="refs-title">知识库缺口 · 建议补充</p>
              <ul>
                <li v-for="(gap, gapIndex) in item.knowledgeGaps" :key="`gap-${gapIndex}`" class="gap-item">
                  <span>{{ gap }}</span>
                  <a-button size="small" type="link" @click="createNoteFromGap(gap)">新建笔记</a-button>
                </li>
              </ul>
            </div>
            <div v-if="sortedReferences(item.references)?.length" class="refs">
              <p class="refs-title">引用文档</p>
              <a-tag
                v-for="ref in sortedReferences(item.references)"
                :key="`${ref.noteId}-${ref.noteTitle}-${ref.relevanceScore ?? 0}`"
                class="ref-tag"
                @click="openNote(ref)"
              >
                {{ ref.noteTitle || '文档' }}
                <span v-if="ref.relevanceScore" class="ref-score">{{ ref.relevanceScore }}</span>
              </a-tag>
            </div>
          </div>
        </div>

        <div v-if="processing" class="chat-item assistant">
          <div class="bubble bubble-loading">
            <p class="bubble-role">AI</p>
            <a-spin size="small" />
            <span class="loading-text">{{ processingHint }}</span>
          </div>
        </div>
        </div>
      </a-spin>

      <footer class="chat-footer">
        <div v-if="aiChat.messages.length === 0" class="quick-chips">
          <button
            v-for="(sample, idx) in quickSamples"
            :key="idx"
            type="button"
            class="sample-chip"
            @click="questionInput = sample"
          >
            {{ sample }}
          </button>
        </div>

        <div class="chat-input-toolbar">
          <a-segmented
            v-model:value="inputMode"
            size="small"
            :options="inputModeOptions"
          />
          <span class="mode-hint">{{ inputModeHint }}</span>
        </div>

        <div class="chat-input">
          <a-textarea
            v-model:value="questionInput"
            :rows="2"
            :auto-size="{ minRows: 2, maxRows: 6 }"
            placeholder="例如：灵感有哪些文档 / 网盘有哪些文件 / 分享这篇文档 / 给文档打标签"
            @pressEnter="handleSend"
          />
          <a-button type="primary" :loading="processing" @click="handleSend">发送</a-button>
        </div>

        <details v-if="agentTasks.length" class="task-history">
          <summary>最近办事记录（{{ agentTasks.length }}）</summary>
          <ul class="task-list">
            <li v-for="item in agentTasks" :key="item.id" class="task-item">
              <span>{{ item.instruction || '未命名' }}</span>
              <a-tag size="small">{{ agentStatusLabel(item.status) }}</a-tag>
            </li>
          </ul>
        </details>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { message, Modal } from 'ant-design-vue';
import {
  askAiStream,
  createAiChatSession,
  deleteAiChatSession,
  getAiStatus,
  listAiChatMessages,
  listAiChatSessions,
  stripKnowledgeGaps,
  type AiChatSession,
  type AiReference,
  type AiStatus,
} from '../../api/ai';
import {
  AI_TASK_STATUS,
  AI_TASK_STATUS_LABEL,
  listAgentTasks,
  planAgentTask,
  type AiAgentTask,
} from '../../api/aiAgent';
import { getAiUserSettings } from '../../api/settings';
import { listWorkspaces, type Workspace } from '../../api/workspaces';
import { useAiChatStore } from '../../store/aiChat';
import { useTodoSummaryStore } from '../../store/todoSummary';
import AiAgentTaskCard from '../../components/ai/AiAgentTaskCard.vue';
import AiMessageContent from '../../components/ai/AiMessageContent.vue';
import NoteSynthesizeModal from '../../components/ai/NoteSynthesizeModal.vue';
import { intentLabel, resolveAiIntent } from '../../utils/aiIntent';
import {
  AI_INPUT_MODE_OPTIONS,
  buildRecentContextBlock,
  priorMessagesForContext,
  type AiInputMode,
} from '../../utils/aiContext';
import { createNoteFromGap as createGapNote, sortReferencesByRelevance } from '../../utils/knowledgeGap';
import { isReadOnlyListTask } from '../../utils/aiMessageFormat';
import { QUICK_INSTRUCTIONS } from '../../utils/agentStepPresentation';

const route = useRoute();
const router = useRouter();
const aiChat = useAiChatStore();
const todoSummary = useTodoSummaryStore();

const aiStatus = ref<AiStatus | null>(null);
const workspaces = ref<Workspace[]>([]);
const sessions = ref<AiChatSession[]>([]);
const agentTasks = ref<AiAgentTask[]>([]);
const loadingSessions = ref(false);
const sessionLoading = ref(false);
const processing = ref(false);
const processingHint = ref('思考中…');
const questionInput = ref('');
const synthesizeOpen = ref(false);
const chatLogRef = ref<HTMLElement | null>(null);
const inputMode = ref<AiInputMode>(
  (localStorage.getItem('noto-ai-input-mode') as AiInputMode) || 'agent',
);
const inputModeOptions = AI_INPUT_MODE_OPTIONS;

/** 切换会话时递增，作废进行中的流式回调，避免 patch 已卸载的 MdPreview */
let streamGeneration = 0;

const chatLogKey = computed(() => aiChat.sessionId ?? 'new');

function invalidateActiveStream() {
  streamGeneration += 1;
  processing.value = false;
}

function messageKey(item: (typeof aiChat.messages)[number], index: number) {
  return `${chatLogKey.value}-${item.id ?? index}`;
}

const inputModeHint = computed(() => {
  if (inputMode.value === 'chat') return '仅阅读解释文档内容（RAG 问答）';
  if (inputMode.value === 'agent') return '操控系统：文档、待办、提醒、网盘、分享、标签';
  return '自动识别：列表/操作用办事，阅读理解用问答';
});

watch(inputMode, (value) => {
  localStorage.setItem('noto-ai-input-mode', value);
});

const quickSamples = QUICK_INSTRUCTIONS.slice(0, 4);

const workspaceOptions = computed(() =>
  workspaces.value.map((item) => ({ label: item.name, value: item.id })),
);

const scopeOptions = [
  { label: '知识库问答', value: 'workspace' },
  { label: '单篇文档', value: 'note' },
];

const formatSessionTime = (value?: string | null) => {
  if (!value) return '刚刚';
  return dayjs(value).format('MM-DD HH:mm');
};

const agentStatusLabel = (status: number) => {
  if (status === AI_TASK_STATUS.AWAITING_CONFIRM) return '待确认';
  if (status === AI_TASK_STATUS.SUCCESS) return '已完成';
  return AI_TASK_STATUS_LABEL[status] || '未知';
};

function shouldShowAssistantContent(item: (typeof aiChat.messages)[number]) {
  if (item.role !== 'assistant') return false;
  if (item.kind === 'text') return true;
  if (item.kind === 'agent') {
    if (item.content?.trim()) return true;
    if (item.agentTask?.steps?.length) return true;
  }
  return false;
}

const scrollToBottom = async () => {
  await nextTick();
  const el = chatLogRef.value;
  if (el) {
    el.scrollTop = el.scrollHeight;
  }
};

let scrollRaf = 0;
const scheduleScrollToBottom = () => {
  if (scrollRaf) return;
  scrollRaf = window.requestAnimationFrame(() => {
    scrollRaf = 0;
    void scrollToBottom();
  });
};

watch(
  () => aiChat.messages.length,
  () => {
    scrollToBottom();
  },
);

const loadSessions = async () => {
  if (!aiChat.workspaceId) {
    sessions.value = [];
    return;
  }
  loadingSessions.value = true;
  try {
    sessions.value = await listAiChatSessions(aiChat.workspaceId);
  } catch (error: any) {
    message.error(error?.message || '加载会话失败');
  } finally {
    loadingSessions.value = false;
  }
};

const loadAgentTasks = async () => {
  if (!aiChat.workspaceId) {
    agentTasks.value = [];
    return;
  }
  try {
    const page = await listAgentTasks({ workspaceId: aiChat.workspaceId, page: 1, size: 10 });
    agentTasks.value = page.records || [];
  } catch {
    agentTasks.value = [];
  }
};

const onWorkspaceChange = async () => {
  invalidateActiveStream();
  aiChat.startNewSession();
  await Promise.all([loadSessions(), loadAgentTasks()]);
};

const handleNewChat = () => {
  invalidateActiveStream();
  aiChat.startNewSession();
  questionInput.value = '';
};

const openSession = async (session: AiChatSession) => {
  const sid = String(session.id);
  if (sid === aiChat.sessionId && aiChat.messages.length > 0) return;

  invalidateActiveStream();
  aiChat.sessionId = sid;
  aiChat.messages = [];
  aiChat.scope = session.scope;
  aiChat.targetId = session.targetId || undefined;
  aiChat.workspaceId = session.workspaceId;

  sessionLoading.value = true;
  try {
    const records = await listAiChatMessages(session.id);
    aiChat.messages = records.map((item) => ({
      id: item.id,
      role: item.role === 'assistant' ? 'assistant' : 'user',
      content: item.content,
      references: item.references,
      kind: 'text' as const,
      intent: 'chat' as const,
    }));
    await nextTick();
    await scrollToBottom();
  } catch (error: any) {
    message.error(error?.message || '加载会话消息失败');
    aiChat.startNewSession();
  } finally {
    sessionLoading.value = false;
  }
};

const ensureSession = async (firstQuestion: string) => {
  if (aiChat.sessionId) return aiChat.sessionId;
  if (!aiChat.workspaceId) {
    throw new Error('请选择知识库');
  }
  const session = await createAiChatSession({
    workspaceId: aiChat.workspaceId,
    scope: aiChat.scope,
    targetId: aiChat.scope === 'note' ? aiChat.targetId : undefined,
    title: firstQuestion,
  });
  aiChat.sessionId = String(session.id);
  await loadSessions();
  return session.id;
};

const handleChat = async (question: string, recentContext: string) => {
  if (aiChat.scope === 'note' && !aiChat.targetId) {
    throw new Error('单篇文档模式需要指定文档 ID');
  }

  processingHint.value = '正在查资料…';
  const sessionId = await ensureSession(question);

  const assistantIndex = aiChat.messages.length;
  const streamGen = streamGeneration;
  const streamSessionId = aiChat.sessionId;
  aiChat.messages.push({
    role: 'assistant',
    content: '',
    kind: 'text',
    intent: 'chat',
    references: [],
  });
  await scrollToBottom();

  const isStreamActive = () =>
    streamGen === streamGeneration && aiChat.sessionId === streamSessionId;

  await askAiStream(
    {
      workspaceId: aiChat.workspaceId,
      question,
      scope: aiChat.scope,
      targetId: aiChat.targetId,
      sessionId,
      recentContext,
    },
    {
      onReferences: (references) => {
        if (!isStreamActive() || !aiChat.messages[assistantIndex]) return;
        aiChat.messages[assistantIndex].references = sortReferencesByRelevance(references);
      },
      onToken: (token) => {
        if (!isStreamActive() || !aiChat.messages[assistantIndex]) return;
        aiChat.messages[assistantIndex].content += token;
        scheduleScrollToBottom();
      },
      onKnowledgeGaps: (gaps) => {
        if (!isStreamActive() || !aiChat.messages[assistantIndex]) return;
        aiChat.messages[assistantIndex].knowledgeGaps = gaps;
        aiChat.messages[assistantIndex].content = stripKnowledgeGaps(
          aiChat.messages[assistantIndex].content,
        );
      },
      onError: (errorMessage) => {
        if (!isStreamActive()) return;
        message.error(errorMessage || 'AI 流式问答失败');
      },
    },
  );
  if (!isStreamActive() || !aiChat.messages[assistantIndex]) return;
  aiChat.messages[assistantIndex].content = stripKnowledgeGaps(
    aiChat.messages[assistantIndex].content,
  );
  if (!aiChat.messages[assistantIndex].content.trim()) {
    aiChat.messages[assistantIndex].content = '（无回答内容）';
  }
  await loadSessions();
};

const handleAgent = async (instruction: string, recentContext: string) => {
  if (!aiChat.workspaceId) {
    throw new Error('请选择知识库');
  }

  processingHint.value = '正在安排办事方案…';
  const sessionId = await ensureSession(instruction);
  const task = await planAgentTask({
    workspaceId: aiChat.workspaceId,
    instruction,
    sessionId,
    recentContext,
  });

  aiChat.messages.push({
    role: 'assistant',
    content: task.autoExecuted
      ? task.assistantReply || '已按信任模式自动执行。'
      : task.assistantReply || '已为你生成执行方案，请确认后执行。',
    kind: 'agent',
    intent: 'agent',
    agentTask: task,
  });
  if (task.autoExecuted || task.status === AI_TASK_STATUS.SUCCESS) {
    todoSummary.refresh();
  }
  await loadAgentTasks();
};

const handleSend = async (event?: KeyboardEvent) => {
  if (event?.shiftKey) return;
  event?.preventDefault();
  const text = questionInput.value.trim();
  if (!text) {
    message.warning('请输入内容');
    return;
  }
  questionInput.value = '';
  await submitInput(text);
};

const submitInput = async (text: string, options?: { forceIntent?: 'chat' | 'agent' }) => {
  if (!text.trim()) return;
  if (!aiChat.workspaceId) {
    message.warning('请选择知识库');
    return;
  }

  aiChat.messages.push({ role: 'user', content: text, kind: 'text' });
  processing.value = true;
  processingHint.value = '正在理解意图…';

  try {
    await ensureSession(text);
    const recentContext = buildRecentContextBlock(priorMessagesForContext(aiChat.messages));

    let intent: 'chat' | 'agent';
    if (options?.forceIntent) {
      intent = options.forceIntent;
    } else if (inputMode.value === 'chat' || inputMode.value === 'agent') {
      intent = inputMode.value;
    } else {
      const routed = await resolveAiIntent(text, {
        sessionId: aiChat.sessionId,
        recentContext,
      });
      intent = routed.intent;
    }

    const userMsg = aiChat.messages[aiChat.messages.length - 1];
    if (userMsg?.role === 'user') {
      userMsg.intent = intent;
    }

    if (intent === 'agent') {
      await handleAgent(text, recentContext);
    } else {
      await handleChat(text, recentContext);
    }
  } catch (error: any) {
    message.error(error?.message || '处理失败');
    const last = aiChat.messages[aiChat.messages.length - 1];
    if (last?.role === 'assistant' && !last.agentTask && !last.content?.trim()) {
      aiChat.messages.pop();
    }
  } finally {
    processing.value = false;
    scrollToBottom();
  }
};

const consumeRouteQuery = async () => {
  const nextQuery = { ...route.query };
  let changed = false;

  if (route.query.synthesize === '1') {
    synthesizeOpen.value = true;
    delete nextQuery.synthesize;
    changed = true;
  }

  const q = typeof route.query.q === 'string' ? route.query.q.trim() : '';
  if (q) {
    const forceIntent =
      route.query.intent === 'agent' ? 'agent' : route.query.intent === 'chat' ? 'chat' : undefined;
    delete nextQuery.q;
    delete nextQuery.intent;
    changed = true;
    if (changed) {
      router.replace({ path: route.path, query: nextQuery });
    }
    questionInput.value = q;
    await submitInput(q, forceIntent ? { forceIntent } : undefined);
    return;
  }

  if (changed) {
    router.replace({ path: route.path, query: nextQuery });
  }
};

const updateAgentTask = (index: number, task: AiAgentTask) => {
  const msg = aiChat.messages[index];
  if (!msg?.agentTask) return;
  msg.agentTask = task;
  if (task.assistantReply) {
    msg.content = task.assistantReply;
  }
  void loadAgentTasks();
};

const handleClearChat = () => {
  Modal.confirm({
    title: '清空对话？',
    content: aiChat.sessionId ? '将删除当前会话及全部消息。' : '将清除当前对话内容。',
    okText: '清空',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      if (aiChat.sessionId) {
        try {
          await deleteAiChatSession(aiChat.sessionId);
          await loadSessions();
        } catch (error: any) {
          message.error(error?.message || '删除会话失败');
          return;
        }
      }
      aiChat.clearSession();
      questionInput.value = '';
    },
  });
};

const pickHighlightKeyword = (ref: AiReference) => {
  if (ref.highlightKeyword?.trim()) return ref.highlightKeyword.trim();
  const text = (ref.snippet || '').replace(/\s+/g, ' ').trim();
  if (!text) return '';
  return text.length > 24 ? text.slice(0, 24) : text;
};

const openNote = (ref: AiReference) => {
  const highlight = pickHighlightKeyword(ref);
  router.push({
    path: `/notes/${ref.noteId}`,
    query: {
      from: 'ai',
      ...(aiChat.workspaceId ? { workspace: aiChat.workspaceId } : {}),
      ...(ref.offsetStart != null ? { start: String(ref.offsetStart) } : {}),
      ...(ref.offsetEnd != null ? { end: String(ref.offsetEnd) } : {}),
      ...(highlight ? { q: highlight } : {}),
    },
  });
};

const sortedReferences = (refs?: AiReference[]) => {
  if (!refs?.length) return [];
  return sortReferencesByRelevance(refs);
};

const createNoteFromGap = (gap: string) => {
  if (!aiChat.workspaceId) {
    message.warning('请先选择知识库');
    return;
  }
  void createGapNote(gap, aiChat.workspaceId, router);
};

onMounted(async () => {
  workspaces.value = await listWorkspaces();
  aiStatus.value = await getAiStatus();
  if (typeof route.query.workspace === 'string') {
    aiChat.workspaceId = route.query.workspace;
  } else if (!aiChat.workspaceId) {
    try {
      const settings = await getAiUserSettings();
      if (settings.primaryWorkspaceId) {
        aiChat.workspaceId = settings.primaryWorkspaceId;
      } else if (workspaces.value[0]) {
        aiChat.workspaceId = workspaces.value[0].id;
      }
    } catch {
      if (workspaces.value[0]) {
        aiChat.workspaceId = workspaces.value[0].id;
      }
    }
  }
  if (typeof route.query.noteId === 'string') {
    aiChat.scope = 'note';
    aiChat.targetId = route.query.noteId;
  } else {
    try {
      const settings = await getAiUserSettings();
      aiChat.scope = settings.defaultScope;
    } catch {
      // 使用默认 workspace
    }
  }
  await Promise.all([loadSessions(), loadAgentTasks()]);
  scrollToBottom();
  await consumeRouteQuery();
});
</script>

<style scoped>
.ai-page {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  background: var(--noto-surface-solid, #fff);
}

.session-sidebar {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 12px 12px 12px 16px;
  border-right: 1px solid var(--noto-border, #e2e8f0);
  background: #fafbfc;
}

.sidebar-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--noto-border, #e2e8f0);
  flex-shrink: 0;
}

.sidebar-head-text {
  min-width: 0;
}

.sidebar-head h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--noto-text-muted);
}

.session-count {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: #94a3b8;
}

.new-chat-btn {
  padding: 0;
  height: auto;
  font-size: 13px;
  flex-shrink: 0;
}

.session-sidebar :deep(.ant-spin-nested-loading),
.session-sidebar :deep(.ant-spin-container) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.session-list {
  list-style: none;
  margin: 0;
  padding: 4px 0 0;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.session-item {
  padding: 10px 8px 10px 10px;
  cursor: pointer;
  border-left: 2px solid transparent;
  transition:
    background-color 0.2s var(--noto-ease-premium),
    border-color 0.2s var(--noto-ease-premium);
}

.session-item + .session-item {
  border-top: 1px solid rgba(15, 23, 42, 0.05);
}

.session-item:hover {
  background: rgba(8, 145, 178, 0.04);
}

.session-item.active {
  border-left-color: var(--noto-accent-deep, #0e7490);
  background: rgba(8, 145, 178, 0.07);
}

.session-title {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--noto-text, #0f172a);
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item.active .session-title {
  font-weight: 600;
  color: var(--noto-accent-deep, #0e7490);
}

.session-meta {
  display: block;
  margin-top: 3px;
  font-size: 11px;
  color: var(--noto-text-muted, #64748b);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-empty {
  padding: 24px 8px;
  font-size: 13px;
  color: var(--noto-text-muted, #64748b);
  text-align: center;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

:deep(.chat-log-spin) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

:deep(.chat-log-spin .ant-spin-container) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
  padding: 10px 16px;
  border-bottom: 1px solid var(--noto-border, #e2e8f0);
  background: #fff;
}

.chat-topbar-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.filter-select {
  min-width: 140px;
}

.filter-select--scope {
  min-width: 120px;
}

.status-pill {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  color: #64748b;
  background: #f1f5f9;
}

.status-pill--on {
  color: #047857;
  background: #ecfdf5;
}

.chat-topbar-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.chat-log {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
}

.chat-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
}

.empty-title {
  margin: 0;
  font-size: 14px;
  color: var(--noto-text-muted, #64748b);
}

.chat-footer {
  flex-shrink: 0;
  padding: 0 16px 12px;
  border-top: 1px solid var(--noto-border, #e2e8f0);
  background: #fff;
}

.quick-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 0 8px;
}

.sample-chip {
  margin: 0;
  padding: 4px 12px;
  border: 1px solid var(--noto-border, #e2e8f0);
  border-radius: 999px;
  background: #f8fafc;
  color: var(--noto-text-muted, #64748b);
  font-size: 12px;
  line-height: 1.5;
  cursor: pointer;
  transition:
    border-color 0.2s,
    color 0.2s,
    background-color 0.2s;
}

.sample-chip:hover {
  border-color: rgba(8, 145, 178, 0.35);
  color: var(--noto-accent-deep, #0e7490);
  background: rgba(8, 145, 178, 0.06);
}

.chat-input-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 8px;
}

.mode-hint {
  font-size: 12px;
  color: #94a3b8;
}

.chat-input {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: end;
  padding-top: 8px;
}

.chat-item {
  display: flex;
  margin-bottom: 12px;
}

.chat-item.user {
  justify-content: flex-end;
}

.bubble {
  max-width: min(85%, 720px);
  padding: 12px 14px;
  border-radius: 14px;
  background: #f8fafc;
}

.bubble-agent {
  max-width: min(92%, 780px);
}

.chat-item.user .bubble {
  background: #e0f2fe;
}

.bubble-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.bubble-role {
  margin: 0;
  font-size: 12px;
  color: #667085;
}

.bubble-loading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.loading-text {
  font-size: 13px;
  color: #64748b;
}

.bubble-user-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.7;
  color: #101828;
}

.bubble-content {
  line-height: 1.7;
  color: #101828;
}

.refs {
  margin-top: 10px;
}

.refs-title {
  margin: 0 0 6px;
  font-size: 12px;
  color: #667085;
}

.knowledge-gaps {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff7e6;
}

.knowledge-gaps ul {
  margin: 0;
  padding-left: 0;
  list-style: none;
  color: #ad6800;
  font-size: 13px;
  line-height: 1.6;
}

.gap-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 0;
}

.ref-tag {
  cursor: pointer;
}

.ref-score {
  margin-left: 4px;
  font-size: 11px;
  opacity: 0.75;
}

.task-history {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
}

.task-history summary {
  cursor: pointer;
  padding: 4px 0;
}

.task-list {
  list-style: none;
  margin: 6px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 0;
  font-size: 12px;
  border-bottom: 1px solid #f1f5f9;
}

@media (max-width: 960px) {
  .ai-page {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
  }

  .session-sidebar {
    max-height: 180px;
    border-right: none;
    border-bottom: 1px solid var(--noto-border, #e2e8f0);
  }

  .chat-topbar {
    flex-direction: column;
    align-items: stretch;
  }

  .chat-topbar-actions {
    justify-content: flex-end;
  }
}
</style>
