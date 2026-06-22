<template>
  <div class="ai-showcase-page">
    <section class="showcase-hero">
      <div>
        <p class="eyebrow">AI engineering showcase</p>
        <h1>Noto 把私人知识转化为可执行行动</h1>
        <p class="hero-copy">
          面向简历与面试演示：集中呈现 RAG 检索、可确认 Agent、AI 自动化闭环与生产化守护。
        </p>
      </div>
      <a-space>
        <a-button @click="loadShowcase">刷新</a-button>
        <a-button type="primary" @click="router.push('/ai')">打开 AI 助手</a-button>
      </a-space>
    </section>

    <a-alert
      v-if="loadError"
      type="warning"
      show-icon
      class="showcase-alert"
      :message="loadError"
    />

    <section class="metric-grid">
      <article class="metric-card">
        <span class="metric-label">AI Provider</span>
        <strong>{{ aiStatus?.provider || '未启用' }}</strong>
        <small>{{ aiStatus?.model || '模型未配置' }}</small>
      </article>
      <article class="metric-card">
        <span class="metric-label">RAG Chunks</span>
        <strong>{{ ragStatus?.indexedChunks ?? '-' }}</strong>
        <small>{{ ragStatus?.embeddingModel || 'embedding 未就绪' }}</small>
      </article>
      <article class="metric-card">
        <span class="metric-label">Indexed Notes</span>
        <strong>{{ ragStatus?.indexedNotes ?? '-' }}</strong>
        <small>{{ ragStatus?.ragAvailable ? '检索可用' : 'RAG 不可用' }}</small>
      </article>
      <article class="metric-card">
        <span class="metric-label">AI Calls</span>
        <strong>{{ observability?.totalCalls ?? '-' }}</strong>
        <small>{{ observability?.averageLatencyMs ? `${observability.averageLatencyMs}ms avg` : '调用审计样本' }}</small>
      </article>
    </section>

    <section class="showcase-section">
      <div class="section-copy">
        <p class="eyebrow">RAG retrieval</p>
        <h2>有引用的私有知识库问答</h2>
        <p>
          文档保存后分块、embedding、写入 pgvector，并在问答时结合关键词召回。演示重点不是“能聊天”，
          而是回答可以回链到原文和知识库。
        </p>
      </div>
      <div class="capability-list">
        <span>文档分块</span>
        <span>DashScope embedding</span>
        <span>pgvector/HNSW</span>
        <span>关键词混合召回</span>
        <span>引用跳转</span>
      </div>
    </section>

    <section class="showcase-section">
      <div class="section-copy">
        <p class="eyebrow">Confirmed agent</p>
        <h2>自然语言变成可审查工具调用</h2>
        <p>
          Agent 将「找文档、摘要、提取待办、创建提醒」拆成步骤；写操作进入确认链路，避免 AI
          静默修改业务数据。
        </p>
      </div>

      <div class="agent-list">
        <article v-for="task in agentTasks" :key="task.id" class="agent-card">
          <div class="agent-card-head">
            <div>
              <strong>{{ task.instruction || 'Agent workflow' }}</strong>
              <small>{{ formatDate(task.createdAt) }}</small>
            </div>
            <a-tag :color="taskStatusColor(task.status)">{{ taskStatusLabel(task.status) }}</a-tag>
          </div>
          <ol v-if="task.steps?.length" class="step-list">
            <li v-for="step in task.steps" :key="step.id">
              <span>{{ toolLabel(step.tool) }}</span>
              <small>{{ step.status }}{{ step.requiresConfirm ? ' · 需确认' : '' }}</small>
            </li>
          </ol>
          <p v-else class="empty-text">暂无步骤明细</p>
        </article>
        <div v-if="!agentTasks.length && !loading" class="empty-panel">
          在 AI 助手里执行一次办事指令后，这里会显示 Agent 规划步骤。
        </div>
        <a-skeleton v-if="loading" active :paragraph="{ rows: 4 }" />
      </div>
    </section>

    <section class="showcase-section">
      <div class="section-copy">
        <p class="eyebrow">Workflow automation</p>
        <h2>AI 输出回到笔记、待办和复盘</h2>
        <p>
          每日建议、今日复盘、周报草稿、待办拆解和完成复盘都进入业务闭环，能体现 AI
          与产品流程结合，而不是散装 prompt。
        </p>
      </div>
      <div class="automation-grid">
        <article>
          <span>今日 Digest</span>
          <strong>{{ digest?.generatedAt ? '已生成' : '今日未生成' }}</strong>
          <small>{{ digest?.generatedAt ? formatDate(digest.generatedAt) : '可在首页或 AI 设置中生成' }}</small>
        </article>
        <article>
          <span>每周复盘</span>
          <strong>{{ weeklyRetro?.noteTitle || '本周未生成' }}</strong>
          <small>{{ weeklyRetro?.generatedAt ? formatDate(weeklyRetro.generatedAt) : '生成后会保存为笔记' }}</small>
        </article>
      </div>
    </section>

    <section class="showcase-section observability-panel">
      <div class="section-copy">
        <p class="eyebrow">Production guardrails</p>
        <h2>AI 调用可观测、可复盘</h2>
        <p>
          基于审计日志汇总最近 AI 调用，展示成功率、平均延迟和动作轨迹。面试时可以说明：
          AI 功能不是孤立 demo，而是被记录、衡量和追踪的产品能力。
        </p>
      </div>
      <div class="observability-content">
        <div class="success-ring">
          <strong>{{ formatPercent(observability?.successRate) }}</strong>
          <span>成功率</span>
        </div>
        <div class="recent-actions">
          <article
            v-for="action in observability?.recentActions || []"
            :key="`${action.actionType}-${action.createdAt}`"
          >
            <div>
              <strong>{{ action.actionType }}</strong>
              <small>{{ action.resourceType || 'AI' }} · {{ formatDate(action.createdAt) }}</small>
            </div>
            <a-tag :color="action.success ? 'green' : 'red'">
              {{ action.success ? '成功' : '失败' }}
              <template v-if="action.latencyMs"> · {{ action.latencyMs }}ms</template>
            </a-tag>
          </article>
          <div v-if="!(observability?.recentActions || []).length && !loading" class="empty-panel">
            暂无 AI 审计记录；执行一次 AI 摘要、问答或 Agent 后会出现调用轨迹。
          </div>
        </div>
      </div>
    </section>

    <section class="resume-card">
      <p class="eyebrow">Resume bullets</p>
      <ul>
        <li>基于 LangChain4j + DashScope + pgvector 构建私有知识库 RAG，支持向量召回、引用跳转与 SSE 流式回答。</li>
        <li>实现可确认 AI Agent，将自然语言指令拆解为搜索、摘要、提取待办、创建提醒等工具调用。</li>
        <li>围绕知识到行动闭环落地摘要、待办提取、任务拆解、每日 digest 与周报复盘。</li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  getAiObservabilitySummary,
  getAiStatus,
  getCurrentWeeklyRetro,
  getNoteRagStatus,
  getTodayAiDigest,
  type AiDigest,
  type AiObservabilitySummary,
  type AiStatus,
  type AiWeeklyRetro,
  type NoteRagIndexStatus,
} from '../../api/ai';
import {
  AGENT_TOOL_LABEL,
  AI_TASK_STATUS_LABEL,
  listAgentTasks,
  type AiAgentTask,
} from '../../api/aiAgent';

const router = useRouter();
const loading = ref(false);
const loadError = ref('');
const aiStatus = ref<AiStatus | null>(null);
const ragStatus = ref<NoteRagIndexStatus | null>(null);
const agentTasks = ref<AiAgentTask[]>([]);
const digest = ref<AiDigest | null>(null);
const weeklyRetro = ref<AiWeeklyRetro | null>(null);
const observability = ref<AiObservabilitySummary | null>(null);

function taskStatusLabel(status: number) {
  return AI_TASK_STATUS_LABEL[status] || '未知状态';
}

function taskStatusColor(status: number) {
  if (status === 2) return 'green';
  if (status === 3) return 'red';
  if (status === 4) return 'gold';
  if (status === 1) return 'blue';
  return 'default';
}

function toolLabel(tool: string) {
  return AGENT_TOOL_LABEL[tool] || tool;
}

function formatDate(value?: string | null) {
  if (!value) return '暂无时间';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatPercent(value?: number | null) {
  if (value === null || value === undefined) return '-';
  return `${value}%`;
}

async function loadShowcase() {
  loading.value = true;
  loadError.value = '';
  try {
    const [status, rag, tasks, todayDigest, retro, obs] = await Promise.all([
      getAiStatus(),
      getNoteRagStatus(),
      listAgentTasks({ page: 1, size: 5 }),
      getTodayAiDigest(),
      getCurrentWeeklyRetro(),
      getAiObservabilitySummary(),
    ]) as unknown as [
      AiStatus,
      NoteRagIndexStatus,
      { records?: AiAgentTask[] },
      AiDigest | null,
      AiWeeklyRetro | null,
      AiObservabilitySummary,
    ];
    aiStatus.value = status;
    ragStatus.value = rag;
    agentTasks.value = tasks.records || [];
    digest.value = todayDigest;
    weeklyRetro.value = retro;
    observability.value = obs;
  } catch (error: any) {
    loadError.value = error?.message || 'AI 展示数据加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(loadShowcase);
</script>

<style scoped>
.ai-showcase-page {
  display: grid;
  gap: 22px;
  padding: 26px;
  color: var(--noto-text, #1c1917);
}

.showcase-hero,
.showcase-section,
.resume-card {
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 12px 32px rgba(68, 64, 60, 0.06);
}

.showcase-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 34px;
}

.eyebrow {
  margin: 0 0 10px;
  color: #0e7490;
  font-size: 12px;
  font-weight: 760;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h1,
h2 {
  margin: 0;
  color: #1c1917;
  letter-spacing: 0;
}

h1 {
  max-width: 760px;
  font-size: 42px;
  line-height: 1.08;
}

h2 {
  font-size: 26px;
  line-height: 1.22;
}

.hero-copy,
.section-copy p {
  max-width: 720px;
  margin: 14px 0 0;
  color: #57534e;
  font-size: 15px;
  line-height: 1.7;
}

.showcase-alert {
  border-radius: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  min-height: 132px;
  padding: 22px;
  border-radius: 16px;
  background: #17201f;
  color: #fff;
}

.metric-label,
.metric-card small,
.agent-card small,
.automation-grid small {
  display: block;
  color: #78716c;
}

.metric-card .metric-label,
.metric-card small {
  color: rgba(255, 255, 255, 0.58);
}

.metric-card strong {
  display: block;
  margin: 14px 0 8px;
  overflow: hidden;
  font-size: 30px;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.showcase-section {
  display: grid;
  grid-template-columns: minmax(260px, 0.86fr) minmax(0, 1.14fr);
  gap: 28px;
  padding: 30px;
}

.capability-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.capability-list span,
.automation-grid article,
.agent-card,
.empty-panel {
  border-radius: 14px;
  background: #f7f6f3;
}

.capability-list span {
  padding: 18px;
  color: #292524;
  font-weight: 680;
}

.agent-list {
  display: grid;
  gap: 12px;
}

.agent-card {
  padding: 18px;
}

.agent-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.agent-card strong {
  display: block;
  color: #1c1917;
}

.step-list {
  margin: 14px 0 0;
  padding-left: 20px;
  color: #292524;
}

.step-list li + li {
  margin-top: 8px;
}

.step-list small {
  margin-top: 2px;
}

.empty-panel,
.empty-text {
  color: #78716c;
}

.empty-panel {
  padding: 22px;
}

.automation-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.automation-grid article {
  padding: 20px;
}

.automation-grid span {
  display: block;
  color: #78716c;
  font-size: 13px;
}

.automation-grid strong {
  display: block;
  margin: 10px 0 6px;
  color: #1c1917;
  font-size: 18px;
}

.observability-panel {
  grid-template-columns: minmax(260px, 0.72fr) minmax(0, 1.28fr);
}

.observability-content {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 18px;
  align-items: stretch;
}

.success-ring {
  display: grid;
  place-content: center;
  min-height: 180px;
  border: 1px solid rgba(14, 116, 144, 0.18);
  border-radius: 999px;
  background:
    radial-gradient(circle at center, #fff 58%, transparent 59%),
    conic-gradient(#0e7490 0 76%, #e7e5e4 76% 100%);
  text-align: center;
}

.success-ring strong {
  color: #1c1917;
  font-size: 34px;
  line-height: 1;
}

.success-ring span {
  margin-top: 8px;
  color: #78716c;
  font-size: 13px;
}

.recent-actions {
  display: grid;
  gap: 10px;
}

.recent-actions article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 14px;
  background: #f7f6f3;
}

.recent-actions strong {
  display: block;
  color: #1c1917;
}

.recent-actions small {
  display: block;
  margin-top: 2px;
  color: #78716c;
}

.resume-card {
  padding: 28px 30px;
}

.resume-card ul {
  margin: 0;
  padding-left: 20px;
  color: #292524;
  line-height: 1.8;
}

@media (max-width: 1100px) {
  .metric-grid,
  .showcase-section {
    grid-template-columns: 1fr;
  }

  .showcase-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .observability-content {
    grid-template-columns: 1fr;
  }

  .success-ring {
    min-height: 156px;
    border-radius: 18px;
  }
}

@media (max-width: 680px) {
  .ai-showcase-page {
    padding: 16px;
  }

  .showcase-hero,
  .showcase-section,
  .resume-card {
    padding: 22px;
    border-radius: 14px;
  }

  h1 {
    font-size: 30px;
  }

  h2 {
    font-size: 22px;
  }

  .metric-grid,
  .capability-list,
  .automation-grid {
    grid-template-columns: 1fr;
  }

  .recent-actions article {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
