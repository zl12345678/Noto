<template>
  <div class="ai-page" :class="{ 'ai-page--mobile': isMobile }">
    <aside v-if="!isMobile" class="session-sidebar">
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
            <div class="session-item-body">
              <span class="session-title">{{ item.title || '新对话' }}</span>
              <span class="session-meta">
                {{ item.scope === 'note' ? '单篇' : '知识库' }}
                · {{ formatSessionTime(item.latestMessageAt || item.createdAt) }}
              </span>
            </div>
            <button
              type="button"
              class="session-delete-btn"
              aria-label="删除会话"
              title="删除"
              :disabled="deletingSessionId === String(item.id)"
              @click.stop="handleDeleteSession(item)"
            >
              ×
            </button>
          </li>
        </ul>
        <div v-else class="session-empty">暂无历史会话</div>
      </a-spin>
    </aside>

    <section class="chat-panel">
      <header v-if="isMobile" class="chat-topbar chat-topbar--mobile">
        <a-button type="text" class="mobile-ai-icon-btn" aria-label="历史会话" @click="sessionDrawerOpen = true">
          <UnorderedListOutlined />
        </a-button>
        <a-select
          v-model:value="aiChat.workspaceId"
          placeholder="知识库"
          size="small"
          class="mobile-ai-workspace"
          :options="workspaceOptions"
          :bordered="false"
          @change="onWorkspaceChange"
        />
        <span class="status-pill status-pill--compact" :class="{ 'status-pill--on': aiStatus?.enabled }">
          {{ aiStatus?.enabled ? '在线' : '关' }}
        </span>
        <a-dropdown trigger="click" placement="bottomRight">
          <a-button type="text" class="mobile-ai-icon-btn" aria-label="更多">
            <MoreOutlined />
          </a-button>
          <template #overlay>
            <a-menu @click="onMobileAiMenu">
              <a-menu-item key="new">新对话</a-menu-item>
              <a-menu-item key="scope">{{ aiChat.scope === 'note' ? '切换：知识库问答' : '切换：单篇文档' }}</a-menu-item>
              <a-menu-item key="synthesize">跨文档合成</a-menu-item>
              <a-menu-divider />
              <a-menu-item key="clear" danger :disabled="!aiChat.messages.length && !aiChat.sessionId">清空当前</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </header>

      <header v-else class="chat-topbar">
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

      <a-drawer
        v-if="isMobile"
        v-model:open="sessionDrawerOpen"
        title="历史会话"
        placement="left"
        :width="sessionDrawerWidth"
        class="ai-session-drawer"
      >
        <a-button type="primary" block class="drawer-new-chat" @click="onDrawerNewChat">新对话</a-button>
        <a-spin :spinning="loadingSessions">
          <ul v-if="sessions.length" class="session-list session-list--drawer">
            <li
              v-for="item in sessions"
              :key="item.id"
              class="session-item"
              :class="{ active: aiChat.sessionId === String(item.id) }"
              @click="onDrawerOpenSession(item)"
            >
              <div class="session-item-body">
                <span class="session-title">{{ item.title || '新对话' }}</span>
                <span class="session-meta">
                  {{ item.scope === 'note' ? '单篇' : '知识库' }}
                  · {{ formatSessionTime(item.latestMessageAt || item.createdAt) }}
                </span>
              </div>
              <button
                type="button"
                class="session-delete-btn session-delete-btn--visible"
                aria-label="删除会话"
                :disabled="deletingSessionId === String(item.id)"
                @click.stop="handleDeleteSession(item)"
              >
                ×
              </button>
            </li>
          </ul>
          <div v-else class="session-empty">暂无历史会话</div>
        </a-spin>
      </a-drawer>

      <NoteSynthesizeModal
        v-model:open="synthesizeOpen"
        :workspaces="workspaces"
        :default-workspace-id="aiChat.workspaceId"
      />

      <div class="chat-log-wrap">
        <div v-if="sessionLoading" class="chat-empty">
          <a-spin size="small" />
          <span class="loading-text">加载会话…</span>
        </div>
        <div v-else ref="chatLogRef" :key="chatLogKey" class="chat-log">
          <div v-if="aiChat.messages.length === 0 && !processing" class="chat-empty">
            <p class="empty-title">{{ isMobile ? '问文档、待办、网盘或让我帮你办事' : '说出你的需求，我来操控文档、待办、提醒、网盘与分享' }}</p>
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
              class="bubble-content"
              :content="item.content"
              :agent-steps="item.kind === 'agent' ? item.agentTask?.steps : undefined"
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
      </div>

      <footer class="chat-footer" :class="{ 'chat-footer--mobile': isMobile }">
        <div v-if="aiChat.messages.length === 0" class="quick-chips">
          <button
            v-for="(sample, idx) in isMobile ? mobileQuickSamples : quickSamples"
            :key="idx"
            type="button"
            class="sample-chip"
            @click="applyQuickSample(sample)"
          >
            {{ sample }}
          </button>
        </div>

        <div class="chat-input-toolbar">
          <a-segmented
            v-model:value="inputMode"
            size="small"
            block
            class="chat-mode-segmented"
            :options="inputModeOptions"
          />
          <span v-if="!isMobile" class="mode-hint">{{ inputModeHint }}</span>
        </div>

        <div class="chat-input" :class="{ 'chat-input--mobile': isMobile }">
          <a-textarea
            v-model:value="questionInput"
            :rows="isMobile ? 1 : 2"
            :auto-size="isMobile ? { minRows: 1, maxRows: 4 } : { minRows: 2, maxRows: 6 }"
            :placeholder="isMobile ? '输入问题…' : '例如：灵感有哪些文档 / 网盘有哪些文件 / 分享这篇文档 / 给文档打标签'"
            @pressEnter="handleSend"
          />
          <a-button type="primary" :loading="processing" @click="handleSend">发送</a-button>
        </div>

        <details v-if="!isMobile && agentTasks.length" class="task-history">
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
import { computed, nextTick, onDeactivated, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { message, Modal } from 'ant-design-vue';
import { MoreOutlined, UnorderedListOutlined } from '@ant-design/icons-vue';
import {
  askAiStream,
  createAiChatSession,
  deleteAiChatSession,
  getAiStatus,
  listAiChatMessages,
  listAiChatSessions,
  stripKnowledgeGaps,
  type AiChatMessageRecord,
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
import { useBreakpoint } from '../../composables/useBreakpoint';

defineOptions({ name: 'AiView' });

const { isMobile } = useBreakpoint();

const route = useRoute();
const router = useRouter();
const aiChat = useAiChatStore();
const todoSummary = useTodoSummaryStore();

const aiStatus = ref<AiStatus | null>(null);
const workspaces = ref<Workspace[]>([]);
const sessions = ref<AiChatSession[]>([]);
const agentTasks = ref<AiAgentTask[]>([]);
const loadingSessions = ref(false);
const deletingSessionId = ref<string | null>(null);
const sessionLoading = ref(false);
const processing = ref(false);
const processingHint = ref('思考中…');
const questionInput = ref('');
const synthesizeOpen = ref(false);
const sessionDrawerOpen = ref(false);
const chatLogRef = ref<HTMLElement | null>(null);

const sessionDrawerWidth = computed(() => {
  if (typeof window === 'undefined') return 300;
  return Math.min(320, Math.floor(window.innerWidth * 0.86));
});
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

const mobileQuickSamples = computed(() => QUICK_INSTRUCTIONS.slice(0, 3));

const applyQuickSample = (sample: string) => {
  questionInput.value = sample;
};

function onMobileAiMenu({ key }: { key: string }) {
  if (key === 'new') {
    handleNewChat();
    return;
  }
  if (key === 'scope') {
    aiChat.scope = aiChat.scope === 'note' ? 'workspace' : 'note';
    return;
  }
  if (key === 'synthesize') {
    synthesizeOpen.value = true;
    return;
  }
  if (key === 'clear') {
    handleClearChat();
  }
}

function onDrawerNewChat() {
  sessionDrawerOpen.value = false;
  handleNewChat();
}

async function onDrawerOpenSession(session: AiChatSession) {
  sessionDrawerOpen.value = false;
  await openSession(session);
}

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
    const page = await listAgentTasks({ workspaceId: aiChat.workspaceId, page: 1, size: 50 });
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
    const [records, taskPage] = await Promise.all([
      listAiChatMessages(session.id),
      listAgentTasks({ workspaceId: session.workspaceId, page: 1, size: 50 }).catch(() => ({ records: [] })),
    ]);
    agentTasks.value = taskPage.records || [];
    aiChat.messages = restoreAgentTasksForMessages(records, agentTasks.value);
    await nextTick();
    await scrollToBottom();
  } catch (error: any) {
    message.error(error?.message || '加载会话消息失败');
    aiChat.startNewSession();
  } finally {
    sessionLoading.value = false;
  }
};

function restoreAgentTasksForMessages(records: AiChatMessageRecord[], tasks: AiAgentTask[]) {
  const usedTaskIds = new Set<string>();
  const taskCandidates = tasks
    .filter((task) => task.instruction && task.assistantReply)
    .sort((a, b) => taskTime(b) - taskTime(a));

  return records.map((item, index) => {
    const base = {
      id: item.id,
      role: item.role === 'assistant' ? 'assistant' as const : 'user' as const,
      content: item.content,
      references: item.references,
      kind: 'text' as const,
      intent: 'chat' as const,
    };
    if (base.role !== 'assistant') {
      return base;
    }
    const previousUser = findPreviousUserContent(records, index);
    const matched = matchAgentTaskForMessage(item, previousUser, taskCandidates, usedTaskIds);
    if (!matched) {
      return base;
    }
    usedTaskIds.add(String(matched.id));
    return {
      ...base,
      content: matched.assistantReply || item.content,
      kind: 'agent' as const,
      intent: 'agent' as const,
      agentTask: matched,
    };
  });
}

function findPreviousUserContent(records: AiChatMessageRecord[], index: number) {
  for (let i = index - 1; i >= 0; i -= 1) {
    if (records[i]?.role === 'user') {
      return records[i].content?.trim() || '';
    }
  }
  return '';
}

function matchAgentTaskForMessage(
  messageRecord: AiChatMessageRecord,
  previousUser: string,
  tasks: AiAgentTask[],
  usedTaskIds: Set<string>,
) {
  const messageTime = messageRecord.createdAt ? dayjs(messageRecord.createdAt) : null;
  return tasks
    .filter((task) => !usedTaskIds.has(String(task.id)))
    .filter((task) => task.instruction?.trim() === previousUser)
    .map((task) => {
      const sameReply = task.assistantReply?.trim() === messageRecord.content?.trim();
      const distance = timeDistance(task.createdAt, messageTime);
      return { task, score: (sameReply ? 0 : 1_000_000) + distance };
    })
    .sort((a, b) => a.score - b.score)[0]?.task;
}

function timeDistance(value?: string | null, target?: ReturnType<typeof dayjs> | null) {
  if (!value || !target?.isValid()) return Number.MAX_SAFE_INTEGER / 2;
  const parsed = dayjs(value);
  if (!parsed.isValid()) return Number.MAX_SAFE_INTEGER / 2;
  return Math.abs(parsed.valueOf() - target.valueOf());
}

function taskTime(task: AiAgentTask) {
  const parsed = dayjs(task.createdAt);
  return parsed.isValid() ? parsed.valueOf() : 0;
}

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

    let intent: 'chat' | 'agent' | 'clarify';
    let routeReason = '';
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
      routeReason = routed.reason || '';
    }

    const userMsg = aiChat.messages[aiChat.messages.length - 1];
    if (userMsg?.role === 'user') {
      userMsg.intent = intent;
    }

    if (intent === 'clarify') {
      aiChat.messages.push({
        role: 'assistant',
        content: routeReason || '我不太确定你想改哪一部分。你是要修改刚才的待办/提醒方案，还是重新创建一个？如果是改方案，可以直接说“改成下周三上午10点”或“标题改成交周报”。',
        kind: 'text',
        intent: 'clarify',
      });
      return;
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

const handleDeleteSession = (session: AiChatSession) => {
  const sid = String(session.id);
  const title = session.title?.trim() || '新对话';
  Modal.confirm({
    title: '删除会话？',
    content: `将删除「${title}」及全部消息，此操作不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      deletingSessionId.value = sid;
      try {
        await deleteAiChatSession(sid);
        if (aiChat.sessionId === sid) {
          invalidateActiveStream();
          aiChat.clearSession();
          questionInput.value = '';
        }
        await loadSessions();
        message.success('已删除会话');
      } catch (error: any) {
        message.error(error?.message || '删除会话失败');
      } finally {
        deletingSessionId.value = null;
      }
    },
  });
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
      invalidateActiveStream();
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

onDeactivated(() => {
  invalidateActiveStream();
});

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
  background: var(--noto-sidebar-muted-bg);
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
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 6px 10px 10px;
  cursor: pointer;
  border-left: 2px solid transparent;
  transition:
    background-color 0.2s var(--noto-ease-premium),
    border-color 0.2s var(--noto-ease-premium);
}

.session-item-body {
  flex: 1;
  min-width: 0;
}

.session-delete-btn {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--noto-text-muted, #64748b);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition:
    opacity 0.15s var(--noto-ease-premium),
    background-color 0.15s var(--noto-ease-premium),
    color 0.15s var(--noto-ease-premium);
}

.session-item:hover .session-delete-btn,
.session-item:focus-within .session-delete-btn {
  opacity: 1;
}

.session-delete-btn:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}

.session-delete-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.session-item + .session-item {
  border-top: 1px solid var(--noto-border-soft);
}

.session-item:hover {
  background: var(--noto-hover-bg);
}

.session-item.active {
  border-left-color: var(--noto-accent-deep, #0e7490);
  background: var(--noto-pastel-blue);
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

.chat-log-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
  padding: 10px 16px;
  border-bottom: 1px solid var(--noto-border, #e2e8f0);
  background: var(--noto-surface);
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
  color: var(--noto-text-muted);
  background: var(--noto-status-pill-bg);
}

.status-pill--on {
  color: var(--noto-accent-deep);
  background: var(--noto-pastel-green);
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
  gap: 8px;
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
  background: var(--noto-surface);
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
  background: var(--noto-chip-bg);
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
  background: var(--noto-bubble-bg);
}

.bubble-agent {
  max-width: min(92%, 780px);
}

.chat-item.user .bubble {
  background: var(--noto-bubble-user-bg);
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
  color: var(--noto-text);
}

.bubble-content {
  line-height: 1.7;
  color: var(--noto-text);
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
  border-bottom: 1px solid var(--noto-border-soft);
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

  .chat-topbar:not(.chat-topbar--mobile) {
    flex-direction: column;
    align-items: stretch;
  }

  .chat-topbar:not(.chat-topbar--mobile) .chat-topbar-actions {
    justify-content: flex-end;
  }
}

/* 移动 Web：单列聊天，会话进抽屉 */
.ai-page--mobile {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  height: 100%;
  max-height: 100%;
  grid-template-columns: unset;
  grid-template-rows: unset;
  background: var(--noto-canvas, #f7f6f3);
}

.ai-page--mobile .chat-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.chat-topbar--mobile {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  min-height: 44px;
  border-bottom: 1px solid var(--noto-border, #e2e8f0);
  background: var(--noto-surface-solid, #fff);
}

.mobile-ai-icon-btn {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.mobile-ai-workspace {
  flex: 1;
  min-width: 0;
}

.mobile-ai-workspace :deep(.ant-select-selector) {
  padding-left: 4px !important;
}

.status-pill--compact {
  flex-shrink: 0;
  font-size: 10px;
  padding: 2px 6px;
}

.drawer-new-chat {
  margin-bottom: 12px;
}

.session-list--drawer {
  max-height: none;
  padding-top: 4px;
}

.session-delete-btn--visible {
  opacity: 1;
}

.ai-page--mobile .chat-log-wrap {
  flex: 1;
  min-height: 0;
}

.ai-page--mobile .chat-log {
  padding: 12px 12px 16px;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
}

.ai-page--mobile .chat-item {
  margin-bottom: 10px;
}

.ai-page--mobile .bubble {
  max-width: 92%;
  padding: 10px 12px;
  border-radius: 12px;
}

.ai-page--mobile .bubble-agent {
  max-width: 96%;
}

.ai-page--mobile .bubble-head {
  margin-bottom: 4px;
}

.ai-page--mobile .bubble-role {
  font-size: 11px;
}

.ai-page--mobile .empty-title {
  font-size: 13px;
  text-align: center;
  padding: 0 12px;
  line-height: 1.55;
}

.ai-page--mobile .chat-footer--mobile {
  padding: 8px 10px calc(10px + env(safe-area-inset-bottom, 0));
  background: var(--noto-surface-solid, #fff);
  border-top: 1px solid var(--noto-border, #e2e8f0);
  box-shadow: 0 -4px 16px rgba(15, 23, 42, 0.04);
}

.ai-page--mobile .quick-chips {
  flex-wrap: nowrap;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  padding: 0 0 8px;
  gap: 6px;
  overscroll-behavior-x: contain;
}

.ai-page--mobile .sample-chip {
  flex-shrink: 0;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  padding: 4px 10px;
}

.ai-page--mobile .chat-input-toolbar {
  padding-top: 0;
  margin-bottom: 6px;
}

.ai-page--mobile .chat-mode-segmented {
  width: 100%;
}

.ai-page--mobile .chat-mode-segmented :deep(.ant-segmented) {
  width: 100%;
}

.ai-page--mobile .chat-input--mobile {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding-top: 0;
}

.ai-page--mobile .chat-input--mobile .ant-input-textarea {
  flex: 1;
  min-width: 0;
}

.ai-page--mobile .chat-input--mobile .ant-btn {
  flex-shrink: 0;
  height: 40px;
  padding-inline: 16px;
  margin-top: 0;
  width: auto;
}

.ai-page--mobile .refs,
.ai-page--mobile .knowledge-gaps {
  margin-top: 8px;
}

.ai-page--mobile .refs-title {
  font-size: 11px;
}

.ai-page--mobile .ref-tag {
  margin-bottom: 4px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
