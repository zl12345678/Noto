<template>
  <view class="ai-page">
    <view class="ai-nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="nav-row">
        <view>
          <text class="ai-title">AI 助手</text>
          <text class="ai-subtitle">{{ sessionId ? '继续上下文对话' : '知识库问答' }}</text>
        </view>
        <text class="status-pill" :class="{ online: aiStatus?.enabled }" @click="reindexCurrentWorkspace">
          {{ reindexing ? '索引中…' : aiStatus?.enabled ? '在线' : '未启用' }}
        </text>
      </view>
      <view class="control-row">
        <picker :range="workspaceNames" :value="workspaceIndex" @change="onWorkspaceChange">
          <view class="ws-picker">{{ currentWorkspaceName || '选择知识库' }} ▾</view>
        </picker>
        <picker :range="sessionNames" :value="sessionPickerIndex" @change="onSessionChange">
          <view class="session-picker">{{ currentSessionTitle || '新对话' }} ▾</view>
        </picker>
        <text class="new-chat" @click="openAiPageActions">更多</text>
      </view>
      <text class="mode-summary">{{ inputModeLabel }}模式</text>
    </view>

    <scroll-view scroll-y class="chat-log" :scroll-top="scrollTop">
      <view v-if="sessionLoading" class="empty-state">
        <text class="empty-title">加载会话…</text>
        <text class="muted">正在同步历史消息</text>
      </view>
      <view v-else-if="!messages.length && !loading" class="empty-state">
        <text class="empty-title">问文档、待办或网盘</text>
        <text class="muted">移动端会保留会话上下文</text>
      </view>
      <view v-for="(msg, idx) in messages" :key="idx" class="msg" :class="msg.role">
        <text class="bubble">{{ msg.content }}</text>
        <view v-if="msg.knowledgeGaps?.length" class="gaps">
          <text class="refs-title">知识库缺口</text>
          <text v-for="gap in msg.knowledgeGaps" :key="gap" class="gap-item">{{ gap }}</text>
        </view>
        <view v-if="msg.agentTask" class="agent-card">
          <view class="agent-head">
            <text class="agent-title">{{ taskStatusLabel(msg.agentTask.status) }}</text>
            <text v-if="msg.agentTask.autoExecuted" class="agent-auto">已自动执行</text>
          </view>
          <text v-if="msg.agentTask.errorMessage" class="agent-error">{{ msg.agentTask.errorMessage }}</text>
          <view v-for="step in msg.agentTask.steps || []" :key="step.id" class="agent-step">
            <text class="step-name">{{ toolLabel(step.tool) }}</text>
            <text class="step-status">{{ step.status }}</text>
          </view>
          <button
            v-if="msg.agentTask.status === AI_TASK_STATUS.AWAITING_CONFIRM"
            class="confirm-btn"
            :loading="confirmingTaskId === msg.agentTask.id"
            @click="confirmTask(idx)"
          >确认执行</button>
        </view>
        <view v-if="msg.references?.length" class="refs">
          <text class="refs-title">引用文档</text>
          <text
            v-for="ref in msg.references"
            :key="`${ref.noteId}-${ref.noteTitle}`"
            class="ref-link"
            @click="openNote(ref.noteId)"
          >{{ ref.noteTitle }}</text>
        </view>
        <view v-if="msg.synthesis" class="synthesis-card">
          <text class="refs-title">跨文档合成</text>
          <text v-if="msg.synthesis.sources?.length" class="source-line">
            综合 {{ msg.synthesis.sources.length }} 篇：{{ msg.synthesis.sources.map((item) => item.noteTitle).join('、') }}
          </text>
          <button
            v-if="msg.synthesis.createdNoteId"
            class="open-note-btn"
            @click="openNote(msg.synthesis.createdNoteId)"
          >打开新笔记</button>
        </view>
      </view>
      <view v-if="loading" class="typing">正在思考…</view>
    </scroll-view>

    <view v-if="synthesizeOpen" class="sheet-mask" @click="closeSynthesizePanel">
      <view class="synthesize-sheet" @click.stop>
        <view class="sheet-head">
          <view>
            <text class="sheet-title">跨文档合成</text>
            <text class="sheet-subtitle">按主题聚合多篇笔记生成草稿</text>
          </view>
          <text class="sheet-close" @click="closeSynthesizePanel">关闭</text>
        </view>
        <input v-model="synthesizeTopic" class="sheet-input" placeholder="主题 / 关键词，例如：产品迭代" />
        <picker :range="templateNames" :value="templateIndex" @change="onTemplateChange">
          <view class="sheet-picker">模板：{{ templateNames[templateIndex] }} ▾</view>
        </picker>
        <view class="date-row">
          <input v-model="dateFrom" class="date-input" placeholder="开始日期 YYYY-MM-DD" />
          <input v-model="dateTo" class="date-input" placeholder="结束日期 YYYY-MM-DD" />
        </view>
        <label class="save-row">
          <checkbox :checked="saveSynthesisAsNote" @click="saveSynthesisAsNote = !saveSynthesisAsNote" />
          <text>保存为新笔记</text>
        </label>
        <button class="synthesize-btn" :loading="synthesizing" @click="runSynthesize">生成</button>
      </view>
    </view>

    <view class="input-area" :style="{ paddingBottom: 'calc(16rpx + env(safe-area-inset-bottom))' }">
      <scroll-view scroll-x class="chips" :show-scrollbar="false">
        <text v-for="chip in quickQuestions" :key="chip" class="chip" @click="askQuick(chip)">{{ chip }}</text>
      </scroll-view>
      <view class="input-row">
        <input
          v-model="question"
          class="input"
          placeholder="输入问题…"
          confirm-type="send"
          :adjust-position="true"
          @confirm="send"
        />
        <button class="send-btn" :loading="loading" @click="send">发送</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import {
  askAi,
  createAiSession,
  getAiStatus,
  listAiSessionMessages,
  listAiSessions,
  reindexWorkspaceRag,
  routeAiIntent,
  synthesizeNotesByAi,
  type AiChatSession,
  type AiReference,
  type NoteSynthesizeResult,
  type NoteSynthesizeTemplate,
} from '../../api/ai';
import {
  AGENT_TOOL_LABEL,
  AI_TASK_STATUS,
  AI_TASK_STATUS_LABEL,
  confirmAgentTask,
  planAgentTask,
  type AiAgentTask,
} from '../../api/aiAgent';
import { ensureAuthPage } from '../../stores/auth';
import { refreshWorkspaces, workspaceState } from '../../stores/workspace';

type ChatMsg = {
  role: 'user' | 'assistant';
  content: string;
  references?: AiReference[];
  knowledgeGaps?: string[];
  agentTask?: AiAgentTask;
  synthesis?: NoteSynthesizeResult;
};

type AiInputMode = 'auto' | 'chat' | 'agent';

const quickQuestions = [
  '今天先做哪几件？',
  '帮我创建一个明天上午的提醒',
  '新建一篇会议记录文档',
  '有哪些逾期待办？',
  '总结最近笔记',
];

const inputModeOptions = [
  { label: '自动', value: 'auto' as const },
  { label: '阅读', value: 'chat' as const },
  { label: '办事', value: 'agent' as const },
];

function loadInputMode(): AiInputMode {
  const cached = uni.getStorageSync('noto-ai-input-mode');
  return cached === 'chat' || cached === 'agent' || cached === 'auto' ? cached : 'auto';
}

const statusBarHeight = ref(0);
const workspaceIndex = ref(0);
const sessionPickerIndex = ref(0);
const sessions = ref<AiChatSession[]>([]);
const sessionId = ref('');
const messages = ref<ChatMsg[]>([]);
const question = ref('');
const loading = ref(false);
const inputMode = ref<AiInputMode>(loadInputMode());
const confirmingTaskId = ref('');
const sessionLoading = ref(false);
const reindexing = ref(false);
const synthesizeOpen = ref(false);
const synthesizeTopic = ref('');
const templateIndex = ref(0);
const dateFrom = ref('');
const dateTo = ref('');
const saveSynthesisAsNote = ref(true);
const synthesizing = ref(false);
const scrollTop = ref(0);
const aiStatus = ref<Awaited<ReturnType<typeof getAiStatus>> | null>(null);
const templates: Array<{ label: string; value: NoteSynthesizeTemplate }> = [
  { label: '周报', value: 'weekly' },
  { label: '项目现状', value: 'status' },
  { label: '决策记录', value: 'decision' },
  { label: '复盘', value: 'retro' },
];

const workspaceNames = computed(() => workspaceState.items.map((w) => w.name));
const currentWorkspace = computed(() => workspaceState.items[workspaceIndex.value]);
const currentWorkspaceName = computed(() => currentWorkspace.value?.name || '');
const sessionOptions = computed(() => [
  { id: '', title: '新对话' },
  ...sessions.value.map((item) => ({ id: String(item.id), title: item.title || '新对话' })),
]);
const sessionNames = computed(() => sessionOptions.value.map((item) => item.title));
const currentSessionTitle = computed(() => sessionOptions.value[sessionPickerIndex.value]?.title || '新对话');
const templateNames = computed(() => templates.map((item) => item.label));
const inputModeLabel = computed(() => inputModeOptions.find((item) => item.value === inputMode.value)?.label || '自动');

async function onWorkspaceChange(e: { detail: { value: string } }) {
  workspaceIndex.value = Number(e.detail.value);
  await loadSessions();
  startNewChat();
}

async function onSessionChange(e: { detail: { value: string } }) {
  sessionPickerIndex.value = Number(e.detail.value);
  const selected = sessionOptions.value[sessionPickerIndex.value];
  if (!selected?.id) {
    startNewChat();
    return;
  }
  await openSession(selected.id);
}

function openNote(id: string) {
  uni.navigateTo({ url: `/pages/notes/detail?id=${id}` });
}

function askQuick(text: string) {
  question.value = text;
  send();
}

function setInputMode(mode: AiInputMode) {
  inputMode.value = mode;
  uni.setStorageSync('noto-ai-input-mode', mode);
}

function openAiPageActions() {
  const items = ['新建对话', '跨文档合成', `切换模式：${inputModeLabel.value}`];
  if (aiStatus.value?.enabled && currentWorkspace.value) items.push('重建当前知识库索引');
  uni.showActionSheet({
    itemList: items,
    success: async (res) => {
      const label = items[res.tapIndex];
      if (label === '新建对话') startNewChat();
      if (label === '跨文档合成') openSynthesizePanel();
      if (label.startsWith('切换模式')) openInputModeActions();
      if (label === '重建当前知识库索引') await reindexCurrentWorkspace();
    },
  });
}

function openInputModeActions() {
  uni.showActionSheet({
    itemList: inputModeOptions.map((item) => item.label),
    success: (res) => {
      const option = inputModeOptions[res.tapIndex];
      if (option) setInputMode(option.value);
    },
  });
}

function openSynthesizePanel() {
  if (!currentWorkspace.value) {
    uni.showToast({ title: '请先选择知识库', icon: 'none' });
    return;
  }
  synthesizeOpen.value = true;
}

function closeSynthesizePanel() {
  if (synthesizing.value) return;
  synthesizeOpen.value = false;
}

function onTemplateChange(e: { detail: { value: string } }) {
  templateIndex.value = Number(e.detail.value);
}

function validDate(value: string) {
  return !value || /^\d{4}-\d{2}-\d{2}$/.test(value);
}

function isAgentIntent(input: string) {
  return /(?:创建|新建|添加|设置|安排|取消|删除|完成|标记|分享|移动|归档|提取).{0,12}(?:文档|笔记|待办|提醒|文件夹|网盘|文件|分享|标签)|提醒我/.test(input);
}

async function resolveIntent(input: string, sid: string, recentContext: string) {
  try {
    const routed = await routeAiIntent({ message: input, sessionId: sid, recentContext });
    return routed.intent;
  } catch {
    return isAgentIntent(input) ? 'agent' : 'chat';
  }
}

function toolLabel(tool: string) {
  return AGENT_TOOL_LABEL[tool] || tool;
}

function taskStatusLabel(status: number) {
  return AI_TASK_STATUS_LABEL[status] || '未知状态';
}

function startNewChat() {
  sessionId.value = '';
  sessionPickerIndex.value = 0;
  messages.value = [];
}

function buildRecentContext(maxTurns = 6, maxChars = 1200) {
  const recent = messages.value.slice(-maxTurns);
  const lines: string[] = [];
  recent.forEach((item) => {
    const text = item.content?.trim().replace(/\s+/g, ' ');
    if (!text) return;
    lines.push(`${item.role === 'user' ? '用户' : '助手'}：${text}`);
  });
  let block = lines.join('\n');
  if (block.length > maxChars) {
    block = block.slice(block.length - maxChars);
  }
  return block;
}

async function ensureSession(firstQuestion: string) {
  if (sessionId.value) return sessionId.value;
  if (!currentWorkspace.value) throw new Error('请先选择知识库');
  const session = await createAiSession({
    workspaceId: String(currentWorkspace.value.id),
    scope: 'workspace',
    title: firstQuestion,
  });
  sessionId.value = String(session.id);
  await loadSessions(sessionId.value);
  return sessionId.value;
}

async function loadSessions(preferredSessionId = sessionId.value) {
  if (!currentWorkspace.value) {
    sessions.value = [];
    return;
  }
  try {
    sessions.value = await listAiSessions(String(currentWorkspace.value.id));
    const index = sessionOptions.value.findIndex((item) => item.id === preferredSessionId);
    sessionPickerIndex.value = index >= 0 ? index : 0;
  } catch {
    sessions.value = [];
    sessionPickerIndex.value = 0;
  }
}

async function openSession(id: string) {
  sessionLoading.value = true;
  sessionId.value = id;
  try {
    const records = await listAiSessionMessages(id);
    messages.value = records
      .filter((item) => item.role === 'user' || item.role === 'assistant')
      .map((item) => ({
        role: item.role === 'assistant' ? 'assistant' : 'user',
        content: item.content,
        references: item.references,
      }));
    scrollTop.value += 999;
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载会话失败', icon: 'none' });
    startNewChat();
  } finally {
    sessionLoading.value = false;
  }
}

async function send() {
  const q = question.value.trim();
  if (!q || loading.value) return;
  if (!currentWorkspace.value) {
    uni.showToast({ title: '请先选择知识库', icon: 'none' });
    return;
  }
  messages.value.push({ role: 'user', content: q });
  question.value = '';
  loading.value = true;
  scrollTop.value += 999;
  try {
    const recentContext = buildRecentContext();
    const sid = await ensureSession(q);
    const intent = inputMode.value === 'auto' ? await resolveIntent(q, sid, recentContext) : inputMode.value;
    if (intent === 'agent') {
      const task = await planAgentTask({
        workspaceId: String(currentWorkspace.value.id),
        instruction: q,
        sessionId: sid,
        recentContext,
      });
      messages.value.push({
        role: 'assistant',
        content: task.assistantReply || '我整理了可执行步骤，请确认后执行。',
        agentTask: task,
      });
      await loadSessions(sid);
      return;
    }
    const result = await askAi({
      workspaceId: String(currentWorkspace.value.id),
      question: q,
      scope: 'workspace',
      sessionId: sid,
      recentContext,
    });
    messages.value.push({
      role: 'assistant',
      content: result.answer || '（无回答内容）',
      references: result.references,
      knowledgeGaps: result.knowledgeGaps,
    });
    await loadSessions(sid);
  } catch (e: any) {
    uni.showToast({ title: e?.message || '问答失败', icon: 'none' });
  } finally {
    loading.value = false;
    scrollTop.value += 999;
  }
}

async function confirmTask(messageIndex: number) {
  const msg = messages.value[messageIndex];
  const task = msg?.agentTask;
  if (!task) return;
  const stepIds = (task.steps || [])
    .filter((step) => step.requiresConfirm && step.status !== 'done')
    .map((step) => step.id);
  if (!stepIds.length) {
    uni.showToast({ title: '没有需要确认的步骤', icon: 'none' });
    return;
  }
  confirmingTaskId.value = task.id;
  try {
    const updated = await confirmAgentTask(task.id, stepIds);
    messages.value[messageIndex] = {
      ...msg,
      content: updated.assistantReply || '已执行选中的步骤。',
      agentTask: updated,
    };
    uni.showToast({ title: updated.status === AI_TASK_STATUS.SUCCESS ? '已执行' : '已更新', icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '执行失败', icon: 'none' });
  } finally {
    confirmingTaskId.value = '';
    scrollTop.value += 999;
  }
}

async function reindexCurrentWorkspace() {
  if (!aiStatus.value?.enabled || !currentWorkspace.value || reindexing.value) return;
  reindexing.value = true;
  try {
    const result = await reindexWorkspaceRag(String(currentWorkspace.value.id));
    uni.showToast({ title: `已索引 ${result.indexedChunks} 个片段`, icon: 'none' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '重建索引失败', icon: 'none' });
  } finally {
    reindexing.value = false;
  }
}

async function runSynthesize() {
  const topic = synthesizeTopic.value.trim();
  if (!currentWorkspace.value) {
    uni.showToast({ title: '请先选择知识库', icon: 'none' });
    return;
  }
  if (!topic) {
    uni.showToast({ title: '请输入主题', icon: 'none' });
    return;
  }
  if (!validDate(dateFrom.value.trim()) || !validDate(dateTo.value.trim())) {
    uni.showToast({ title: '日期格式应为 YYYY-MM-DD', icon: 'none' });
    return;
  }
  synthesizing.value = true;
  try {
    const result = await synthesizeNotesByAi({
      workspaceId: String(currentWorkspace.value.id),
      topic,
      template: templates[templateIndex.value]?.value || 'weekly',
      dateFrom: dateFrom.value.trim() || undefined,
      dateTo: dateTo.value.trim() || undefined,
      saveAsNote: saveSynthesisAsNote.value,
    });
    messages.value.push({
      role: 'assistant',
      content: `${result.title}\n\n${result.content}`,
      synthesis: result,
    });
    synthesizeOpen.value = false;
    synthesizeTopic.value = '';
    dateFrom.value = '';
    dateTo.value = '';
    scrollTop.value += 999;
    if (result.createdNoteId) {
      uni.showModal({
        title: '已生成新笔记',
        content: result.title,
        confirmText: '打开',
        cancelText: '留在这里',
        success: (res) => {
          if (res.confirm && result.createdNoteId) openNote(result.createdNoteId);
        },
      });
    } else {
      uni.showToast({ title: '合成完成', icon: 'success' });
    }
  } catch (e: any) {
    uni.showToast({ title: e?.message || '合成失败', icon: 'none' });
  } finally {
    synthesizing.value = false;
  }
}

onShow(async () => {
  if (!ensureAuthPage()) return;
  const sys = uni.getSystemInfoSync();
  statusBarHeight.value = sys.statusBarHeight || 0;
  await refreshWorkspaces();
  if (!currentWorkspace.value && workspaceState.items[0]) {
    workspaceIndex.value = 0;
  }
  try {
    aiStatus.value = await getAiStatus();
  } catch {
    aiStatus.value = null;
  }
  await loadSessions();
});
</script>

<style scoped lang="scss">
.ai-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f6f3ee;
}

.ai-nav {
  padding: 16rpx 24rpx 20rpx;
  background: #17201f;
  color: #fff;
  border-bottom: 1rpx solid #e7e5e4;
}

.nav-row,
.control-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.control-row {
  margin-top: 18rpx;
  flex-wrap: wrap;
  justify-content: flex-start;
}

.ai-title {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
}

.ai-subtitle {
  display: block;
  margin-top: 6rpx;
  color: rgba(255, 255, 255, 0.58);
  font-size: 24rpx;
}

.ws-picker {
  max-width: 220rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 12rpx 16rpx;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.1);
  font-size: 26rpx;
  color: #fff;
}

.session-picker {
  max-width: 240rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 12rpx 16rpx;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  font-size: 26rpx;
}

.new-chat {
  color: #67e8f9;
  font-size: 26rpx;
  font-weight: 650;
}

.mode-summary {
  display: block;
  margin-top: 14rpx;
  color: rgba(255, 255, 255, 0.54);
  font-size: 23rpx;
}

.synthesis-card {
  max-width: 88%;
  margin-top: 12rpx;
  padding: 16rpx 18rpx;
  border: 1rpx solid #bae6fd;
  border-radius: 18rpx;
  background: #f0fdfa;
}

.source-line {
  display: block;
  color: #57534e;
  font-size: 24rpx;
  line-height: 1.55;
}

.open-note-btn {
  height: 62rpx;
  line-height: 62rpx;
  margin: 14rpx 0 0;
  padding: 0;
  border-radius: 14rpx;
  background: #0891b2;
  color: #fff;
  font-size: 25rpx;
}

.status-pill {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.68);
  font-size: 22rpx;
}

.status-pill.online {
  background: rgba(20, 184, 166, 0.18);
  color: #99f6e4;
}

.chat-log {
  flex: 1;
  padding: 24rpx;
  padding-bottom: 184rpx;
  box-sizing: border-box;
}

.empty-state {
  padding: 80rpx 40rpx;
  text-align: center;
}

.empty-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 12rpx;
}

.msg {
  margin-bottom: 24rpx;
  display: flex;
  flex-direction: column;
}

.msg.user {
  align-items: flex-end;
}

.bubble {
  max-width: 88%;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  background: #fff;
  line-height: 1.65;
  white-space: pre-wrap;
  font-size: 28rpx;
}

.msg.user .bubble {
  background: #0891b2;
  color: #fff;
}

.refs,
.gaps,
.agent-card {
  max-width: 88%;
  margin-top: 12rpx;
  padding: 16rpx 18rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.72);
}

.agent-card {
  border: 1rpx solid #bae6fd;
  background: #f0fdfa;
}

.agent-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.agent-title {
  color: #0e7490;
  font-size: 26rpx;
  font-weight: 700;
}

.agent-auto {
  color: #0f766e;
  font-size: 22rpx;
}

.agent-error {
  display: block;
  margin-bottom: 12rpx;
  color: #b91c1c;
  font-size: 24rpx;
}

.agent-step {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 12rpx 0;
  border-top: 1rpx solid rgba(14, 116, 144, 0.12);
}

.step-name {
  color: #164e63;
  font-size: 25rpx;
}

.step-status {
  flex: 0 0 auto;
  color: #78716c;
  font-size: 22rpx;
}

.confirm-btn {
  height: 70rpx;
  line-height: 70rpx;
  margin: 16rpx 0 0;
  padding: 0;
  border-radius: 16rpx;
  background: #0891b2;
  color: #fff;
  font-size: 26rpx;
}

.refs-title {
  display: block;
  margin-bottom: 10rpx;
  color: #78716c;
  font-size: 22rpx;
  font-weight: 650;
}

.ref-link {
  display: block;
  color: #0891b2;
  font-size: 26rpx;
  margin-top: 10rpx;
}

.gap-item {
  display: block;
  color: #57534e;
  font-size: 25rpx;
  line-height: 1.55;
  margin-top: 8rpx;
}

.typing {
  text-align: center;
  color: #78716c;
  font-size: 26rpx;
}

.sheet-mask {
  position: fixed;
  z-index: 30;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(23, 32, 31, 0.42);
  display: flex;
  align-items: flex-end;
}

.synthesize-sheet {
  width: 100%;
  padding: 28rpx 28rpx calc(32rpx + env(safe-area-inset-bottom));
  border-radius: 28rpx 28rpx 0 0;
  background: #fff;
  box-sizing: border-box;
}

.sheet-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
  margin-bottom: 22rpx;
}

.sheet-title {
  display: block;
  color: #1c1917;
  font-size: 34rpx;
  font-weight: 720;
}

.sheet-subtitle {
  display: block;
  margin-top: 6rpx;
  color: #78716c;
  font-size: 24rpx;
}

.sheet-close {
  color: #0891b2;
  font-size: 26rpx;
  font-weight: 650;
}

.sheet-input,
.sheet-picker,
.date-input {
  min-height: 78rpx;
  padding: 0 22rpx;
  border-radius: 16rpx;
  background: #f5f5f4;
  color: #1c1917;
  font-size: 27rpx;
  box-sizing: border-box;
}

.sheet-input,
.sheet-picker {
  margin-bottom: 16rpx;
}

.sheet-picker {
  line-height: 78rpx;
}

.date-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14rpx;
  margin-bottom: 18rpx;
}

.save-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 18rpx;
  color: #57534e;
  font-size: 26rpx;
}

.synthesize-btn {
  height: 82rpx;
  line-height: 82rpx;
  margin: 0;
  padding: 0;
  border-radius: 18rpx;
  background: #0891b2;
  color: #fff;
  font-size: 28rpx;
  font-weight: 650;
}

.input-area {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  border-top: 1rpx solid #e7e5e4;
  padding: 12rpx 24rpx 16rpx;
  box-sizing: border-box;
  z-index: 20;
}

.chips {
  white-space: nowrap;
  margin-bottom: 12rpx;
}

.chip {
  display: inline-block;
  margin-right: 12rpx;
  padding: 12rpx 20rpx;
  background: #ecfeff;
  color: #0891b2;
  border-radius: 999rpx;
  font-size: 24rpx;
}

.input-row {
  display: flex;
  gap: 12rpx;
  align-items: center;
}

.input {
  flex: 1;
  height: 80rpx;
  padding: 0 24rpx;
  background: #f5f5f4;
  border-radius: 999rpx;
  font-size: 28rpx;
}

.send-btn {
  min-width: 120rpx;
  height: 80rpx;
  line-height: 80rpx;
  margin: 0;
  padding: 0 24rpx;
  background: #0891b2;
  color: #fff;
  border-radius: 999rpx;
  font-size: 28rpx;
}
</style>
