<template>
  <div class="dashboard-page noto-page">
    <QuickStartModal
      v-model:open="quickStartModalOpen"
      @write="startWriteDoc"
      @extract="goNotes('all')"
      @todos="router.push({ path: '/todos', query: { view: 'board' } })"
    />

    <div class="loop-shell">
      <a-card class="loop-card" :bordered="false" :loading="loading">
      <div class="loop-head">
        <div class="loop-head-text">
          <span class="loop-eyebrow">工作台</span>
          <h2>今日行动</h2>
        </div>
        <div class="loop-head-stats">
          <div class="stat-pill">
            <span class="stat-num">{{ stats?.actionTodos?.length ?? 0 }}</span>
            <span class="stat-label">待排队</span>
          </div>
          <div class="stat-pill">
            <span class="stat-num">{{ stats?.parallelActiveCount ?? 0 }}</span>
            <span class="stat-label">进行中</span>
          </div>
          <div class="stat-pill stat-pill--done">
            <span class="stat-num">{{ stats?.todayCompletedTodos ?? 0 }}</span>
            <span class="stat-label">今日完成</span>
          </div>
        </div>
        <div v-if="stats" class="loop-doc-stats">
          <button type="button" class="doc-stat-pill" @click="goNotes('all')">
            <span class="doc-stat-num">{{ stats.totalNotes }}</span>
            <span class="doc-stat-label">文档</span>
          </button>
          <button type="button" class="doc-stat-pill doc-stat-pill--favorite" @click="goNotes('favorite')">
            <span class="doc-stat-num">{{ stats.favoriteNotes }}</span>
            <span class="doc-stat-label">收藏</span>
          </button>
          <button type="button" class="doc-stat-pill doc-stat-pill--archived" @click="goNotes('archived')">
            <span class="doc-stat-num">{{ stats.archivedNotes }}</span>
            <span class="doc-stat-label">归档</span>
          </button>
        </div>
      </div>

      <div class="loop-workflow" :class="{ 'loop-workflow--mobile': isMobile }">
        <ActionWorkflowBoard
          variant="hero"
          :show-guide="!isMobile"
          :loading="loading"
          :action-todos="stats?.actionTodos || []"
          :parallel-todos="stats?.parallelTodos || []"
          :recent-notes="stats?.recentNotes || []"
          :today-completed="stats?.todayCompletedTodos ?? 0"
          @complete="completeTodo"
          @parallel="joinParallel"
          @pause="pauseTodo"
          @open-note="openNote"
          @go-notes="goNotes('all')"
          @go-board="router.push({ path: '/todos', query: { view: 'board' } })"
          @stage-click="onStageClick"
          @workflow-drop="handleWorkflowDrop"
        />
      </div>

      <div class="loop-foot">
        <a-button type="primary" @click="goNotes('all')">写文档</a-button>
        <a-button @click="router.push({ path: '/todos', query: { view: 'board' } })">待办中心</a-button>
        <a-button class="foot-guide-btn" @click="openQuickStartModal">使用引导</a-button>
        <a-button type="link" class="foot-link" :loading="dailySuggest.loading" @click="openDailySuggestions">
          AI 建议
        </a-button>
      </div>
      </a-card>
    </div>

    <a-card v-if="urgentTodos.length" :bordered="false" class="digest-card urgent-todos-card">
      <div class="section-head">
        <div>
          <h3>需要今天处理</h3>
        </div>
        <a-button type="link" @click="router.push({ path: '/todos', query: { view: 'all', focus: 'overdue' } })">
          查看全部 →
        </a-button>
      </div>
      <a-list :data-source="urgentTodos" item-layout="horizontal">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta
              :title="item.title"
              :description="formatUrgentMeta(item)"
            />
            <template #actions>
              <a-space size="small">
                <a-button type="link" size="small" @click="completeTodo(item)">完成</a-button>
                <a-button type="link" size="small" @click="postponeTodoItem(item)">推迟</a-button>
                <a-button type="link" size="small" @click="openReminderForTodo(item)">提醒</a-button>
              </a-space>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-card>

    <a-card v-if="todayDigest?.suggestions?.suggestions?.length" :bordered="false" class="digest-card">
      <div class="section-head">
        <div>
          <h3>今日 AI 建议</h3>
          <p v-if="todayDigest.suggestions?.summary" class="digest-summary">{{ todayDigest.suggestions.summary }}</p>
        </div>
        <a-button type="link" :loading="digestLoading" @click="refreshDigest">刷新</a-button>
      </div>
      <a-list :data-source="todayDigest.suggestions.suggestions.slice(0, 3)" item-layout="horizontal">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta :title="item.title" :description="item.reason" />
            <template #actions>
              <a-space size="small" class="digest-actions">
                <a-button
                  v-if="canStartDigestItem(item)"
                  type="link"
                  size="small"
                  @click="startDigestItem(item)"
                >
                  开始
                </a-button>
                <a-button
                  v-if="canCompleteDigestItem(item)"
                  type="link"
                  size="small"
                  @click="completeDigestItem(item)"
                >
                  完成
                </a-button>
                <a-button type="link" size="small" @click="postponeDigestItem(item)">推迟</a-button>
              </a-space>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-card>

    <a-card v-if="weeklyRetro?.noteId" :bordered="false" class="digest-card weekly-retro-card">
      <div class="section-head">
        <div>
          <h3>本周复盘已就绪</h3>
          <p class="digest-summary">{{ weeklyRetro.noteTitle || '每周复盘笔记' }}</p>
        </div>
        <a-space>
          <a-button type="link" @click="openWeeklyRetroNote">打开笔记</a-button>
          <a-button type="link" :loading="weeklyRetroMailLoading" @click="shareWeeklyRetroByEmail">邮件分享</a-button>
          <a-button type="link" :loading="weeklyRetroLoading" @click="refreshWeeklyRetro">重新生成</a-button>
        </a-space>
      </div>
    </a-card>

    <a-card v-if="(stats?.overdueTodos ?? 0) > 0" :bordered="false" class="alert-card">
      <a-alert type="error" show-icon :message="`有 ${stats?.overdueTodos} 项已逾期，请优先处理`" />
      <a-button
        type="primary"
        block
        style="margin-top: 12px"
        @click="router.push({ path: '/todos', query: { view: 'board' } })"
      >
        立即处理
      </a-button>
    </a-card>

    <a-card :bordered="false" :loading="loading" class="recent-card">
      <div class="section-head">
        <h3>最近文档</h3>
        <a-button type="link" @click="goNotes('recent')">全部</a-button>
      </div>
      <a-list v-if="stats?.recentNotes?.length" :data-source="stats.recentNotes" item-layout="horizontal">
        <template #renderItem="{ item }">
          <a-list-item class="recent-item" @click="openNote(item.id)">
            <a-list-item-meta :title="item.title" :description="item.summary || item.excerpt || '暂无摘要'" />
          </a-list-item>
        </template>
      </a-list>
      <EmptyState v-else title="还没有文档" description="写第一篇文档，开始记录与行动闭环" preset="note">
        <a-button type="primary" @click="startWriteDoc">新建第一篇</a-button>
      </EmptyState>
    </a-card>

    <TodoExtractReviewModal
      :open="extractReview.open"
      source="ai"
      :loading="extractReview.loading"
      :confirming="extractReview.confirming"
      :note-title="extractReview.noteTitle"
      :suggestions="extractReview.suggestions"
      :on-confirm="handleConfirmExtract"
      @cancel="closeExtractReview"
    />

    <a-modal
      v-model:open="dailySuggest.open"
      title="AI 今日行动建议"
      :footer="null"
      width="560px"
    >
      <a-spin :spinning="dailySuggest.loading">
        <p v-if="dailySuggest.summary" class="daily-summary">{{ dailySuggest.summary }}</p>
        <a-empty v-if="!dailySuggest.loading && !dailySuggest.suggestions.length" description="暂无待办可推荐" />
        <a-list v-else :data-source="dailySuggest.suggestions" item-layout="horizontal">
          <template #renderItem="{ item }">
            <a-list-item>
              <a-list-item-meta :title="item.title" :description="item.reason" />
              <template #actions>
                <a-space size="small" class="digest-actions">
                  <a-button
                    v-if="canStartDigestItem(item)"
                    type="link"
                    size="small"
                    @click="startDigestItem(item)"
                  >
                    开始
                  </a-button>
                  <a-button
                    v-if="canCompleteDigestItem(item)"
                    type="link"
                    size="small"
                    @click="completeDigestItem(item)"
                  >
                    完成
                  </a-button>
                  <a-button type="link" size="small" @click="postponeDigestItem(item)">推迟</a-button>
                </a-space>
              </template>
            </a-list-item>
          </template>
        </a-list>
      </a-spin>
    </a-modal>

    <a-modal
      v-model:open="dailyReview.open"
      title="AI 今日复盘"
      :footer="null"
      width="620px"
    >
      <a-spin :spinning="dailyReview.loading">
        <p v-if="dailyReview.summary" class="daily-summary">{{ dailyReview.summary }}</p>
        <p v-if="dailyReview.completedCount > 0" class="review-meta">今日已完成 {{ dailyReview.completedCount }} 项</p>
        <div v-if="dailyReview.highlights.length" class="review-block">
          <h4>亮点</h4>
          <ul><li v-for="(item, idx) in dailyReview.highlights" :key="`h-${idx}`">{{ item }}</li></ul>
        </div>
        <div v-if="dailyReview.blockers.length" class="review-block">
          <h4>卡点</h4>
          <ul><li v-for="(item, idx) in dailyReview.blockers" :key="`b-${idx}`">{{ item }}</li></ul>
        </div>
        <div v-if="dailyReview.tomorrowFocus.length" class="review-block">
          <h4>明日建议</h4>
          <ul><li v-for="(item, idx) in dailyReview.tomorrowFocus" :key="`t-${idx}`">{{ item }}</li></ul>
        </div>
        <a-empty v-if="!dailyReview.loading && !dailyReview.summary" description="暂无复盘内容" />
      </a-spin>
    </a-modal>

    <TodoReminderModal v-model:open="reminderModal.open" :todo="reminderModal.todo" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import dayjs from 'dayjs';
import { getDashboardStats, type DashboardStats } from '../../api/dashboard';
import {
  confirmExtractTodosByAi,
  generateAiDigest,
  generateWeeklyRetro,
  getAiDailyReview,
  getAiDailySuggestions,
  getCurrentWeeklyRetro,
  getTodayAiDigest,
  previewExtractTodosByAi,
  type AiDailySuggestion,
  type AiDigest,
  type AiWeeklyRetro,
  type ConfirmExtractTodoItem,
  type ExtractedTodoSuggestion,
} from '../../api/ai';
import { getNote } from '../../api/notes';
import {
  createTodo,
  getTodo,
  patchTodoStatus,
  TODO_HORIZON,
  TODO_STATUS,
  updateTodo,
  isTodoOverdue,
  type TodoItem,
} from '../../api/todos';
import TodoReminderModal from '../../components/todo/TodoReminderModal.vue';
import { buildPostponedDueAt } from '../../utils/todoAssist';
import ActionWorkflowBoard, { type WorkflowStageKey } from '../../components/todo/ActionWorkflowBoard.vue';
import { useBreakpoint } from '../../composables/useBreakpoint';
import QuickStartModal from '../../components/onboarding/QuickStartModal.vue';
import EmptyState from '../../components/common/EmptyState.vue';
import TodoExtractReviewModal from '../../components/todo/TodoExtractReviewModal.vue';
import {
  inferOnboardingEligible,
  isOnboardingDismissed,
  resetOnboardingDismiss,
} from '../../utils/onboarding';
import { useAuthStore } from '../../store/auth';
import { useTodoSummaryStore } from '../../store/todoSummary';
import type { WorkflowDropEvent } from '../../utils/workflowDrag';

const router = useRouter();
const route = useRoute();
const { isMobile } = useBreakpoint();
const todoSummary = useTodoSummaryStore();
const loading = ref(false);
const digestLoading = ref(false);
const weeklyRetroLoading = ref(false);
const weeklyRetroMailLoading = ref(false);
const stats = ref<DashboardStats | null>(null);
const todayDigest = ref<AiDigest | null>(null);
const weeklyRetro = ref<AiWeeklyRetro | null>(null);
const quickStartModalOpen = ref(false);
const authStore = useAuthStore();

const isOnboardingEligible = computed(() => {
  if (!stats.value) return false;
  if (stats.value.onboardingEligible === true) return true;
  if (stats.value.onboardingEligible === false) return false;
  return inferOnboardingEligible(stats.value);
});

const reminderModal = reactive({
  open: false,
  todo: null as TodoItem | null,
});

const urgentTodos = computed(() => {
  if (!stats.value) return [];
  const all = [...(stats.value.actionTodos || []), ...(stats.value.parallelTodos || [])];
  return all
    .filter((item) => {
      if (item.status === TODO_STATUS.COMPLETED || item.status === TODO_STATUS.CANCELLED) {
        return false;
      }
      if (isTodoOverdue(item)) return true;
      return item.dueAt && dayjs(item.dueAt).isSame(dayjs(), 'day');
    })
    .slice(0, 12);
});

const extractReview = reactive({
  open: false,
  loading: false,
  confirming: false,
  noteId: '',
  noteTitle: '',
  suggestions: [] as ExtractedTodoSuggestion[],
});

const dailySuggest = reactive({
  open: false,
  loading: false,
  summary: '',
  suggestions: [] as AiDailySuggestion[],
});

const dailyReview = reactive({
  open: false,
  loading: false,
  summary: '',
  highlights: [] as string[],
  blockers: [] as string[],
  tomorrowFocus: [] as string[],
  completedCount: 0,
});

const closeExtractReview = () => {
  extractReview.open = false;
  extractReview.loading = false;
  extractReview.confirming = false;
  extractReview.noteId = '';
  extractReview.noteTitle = '';
  extractReview.suggestions = [];
};

const maybeOpenOnboardingGuide = () => {
  if (!isOnboardingEligible.value) return;
  const userId = authStore.currentUser?.id;
  const forceWelcome = route.query.welcome === '1';
  if (forceWelcome || !isOnboardingDismissed(userId)) {
    quickStartModalOpen.value = true;
  }
  if (forceWelcome) {
    resetOnboardingDismiss(userId);
    router.replace({ path: route.path, query: { ...route.query, welcome: undefined } });
  }
};

const loadStats = async () => {
  loading.value = true;
  try {
    stats.value = await getDashboardStats();
    maybeOpenOnboardingGuide();
    todoSummary.applyStats(stats.value.pendingTodos ?? 0, stats.value.overdueTodos ?? 0);
  } catch (error: any) {
    message.error(error?.message || '加载失败');
  } finally {
    loading.value = false;
  }
};

const loadTodayDigest = async () => {
  try {
    todayDigest.value = await getTodayAiDigest();
  } catch {
    todayDigest.value = null;
  }
};

const refreshDigest = async () => {
  digestLoading.value = true;
  try {
    todayDigest.value = await generateAiDigest();
    message.success('已更新今日建议');
  } catch (error: any) {
    message.error(error?.message || '生成失败');
  } finally {
    digestLoading.value = false;
  }
};

const loadWeeklyRetro = async () => {
  try {
    weeklyRetro.value = await getCurrentWeeklyRetro();
  } catch {
    weeklyRetro.value = null;
  }
};

const refreshWeeklyRetro = async () => {
  weeklyRetroLoading.value = true;
  try {
    weeklyRetro.value = await generateWeeklyRetro();
    message.success('本周复盘已更新');
  } catch (error: any) {
    message.error(error?.message || '生成复盘失败');
  } finally {
    weeklyRetroLoading.value = false;
  }
};

const openWeeklyRetroNote = () => {
  if (!weeklyRetro.value?.noteId) return;
  router.push({ path: `/notes/${weeklyRetro.value.noteId}`, query: { from: 'dashboard' } });
};

const shareWeeklyRetroByEmail = async () => {
  if (!weeklyRetro.value?.noteId) return;
  weeklyRetroMailLoading.value = true;
  try {
    const note = await getNote(weeklyRetro.value.noteId);
    const title = note.title || weeklyRetro.value.noteTitle || '本周复盘';
    const body = [
      title,
      weeklyRetro.value.weekStart ? `周期：${weeklyRetro.value.weekStart} 起` : '',
      '',
      note.content || '（暂无正文）',
      '',
      '— 来自 Noto 知微',
    ]
      .filter(Boolean)
      .join('\n');
    const mailto = `mailto:?subject=${encodeURIComponent(`[复盘] ${title}`)}&body=${encodeURIComponent(body)}`;
    window.location.href = mailto;
  } catch (error: any) {
    message.error(error?.message || '无法打开邮件客户端');
  } finally {
    weeklyRetroMailLoading.value = false;
  }
};

const goNotes = (scope: 'all' | 'recent' | 'favorite' | 'archived' = 'all') => {
  const query = scope === 'all' ? {} : { scope };
  router.push({ path: '/notes', query });
};

const openQuickStartModal = () => {
  quickStartModalOpen.value = true;
};

const startWriteDoc = () => {
  router.push({ path: '/notes', query: { scope: 'all', action: 'new-doc' } });
};
const openNote = (id: string) => router.push({ path: `/notes/${id}`, query: { from: 'dashboard' } });

const onStageClick = (key: WorkflowStageKey) => {
  if (key === 'notes') {
    goNotes('all');
    return;
  }
  router.push({ path: '/todos', query: { view: 'board' } });
};

const completeTodo = async (item: TodoItem) => {
  try {
    await patchTodoStatus(item.id, TODO_STATUS.COMPLETED);
    message.success('又完成一项！');
    loadStats();
  } catch (error: any) {
    message.error(error?.message || '失败');
  }
};

const joinParallel = async (item: TodoItem) => {
  try {
    await patchTodoStatus(item.id, TODO_STATUS.IN_PROGRESS);
    message.success(`「${item.title}」已开始处理`);
    loadStats();
  } catch (error: any) {
    message.error(error?.message || '失败');
  }
};

const pauseTodo = async (item: TodoItem) => {
  try {
    await patchTodoStatus(item.id, TODO_STATUS.PENDING);
    message.info('已暂停，回到待办队列');
    loadStats();
  } catch (error: any) {
    message.error(error?.message || '失败');
  }
};

const handleNoteToQueue = async (noteId: string) => {
  extractReview.noteId = noteId;
  extractReview.open = true;
  extractReview.loading = true;
  extractReview.suggestions = [];
  try {
    const preview = await previewExtractTodosByAi(noteId);
    extractReview.noteTitle = preview.noteTitle || '';
    extractReview.suggestions = preview.suggestions || [];
    if (!extractReview.suggestions.length) {
      const note = stats.value?.recentNotes.find((item) => item.id === noteId);
      if (!note?.workspaceId) {
        message.warning('无法创建待办：文档缺少知识库');
        closeExtractReview();
        return;
      }
      closeExtractReview();
      message.info('AI 未识别到待办项，已为你创建跟进任务');
      await createTodo({
        workspaceId: note.workspaceId,
        noteId,
        title: `跟进：${note.title}`,
        horizon: TODO_HORIZON.ACTION,
      });
      message.success('已创建待办并加入队列');
      await loadStats();
    }
  } catch (error: any) {
    message.error(error?.message || 'AI 提取待办失败');
    closeExtractReview();
  } finally {
    extractReview.loading = false;
  }
};

const handleConfirmExtract = async (items: ConfirmExtractTodoItem[]) => {
  if (!extractReview.noteId) return;
  extractReview.confirming = true;
  const noteId = extractReview.noteId;
  try {
    const result = await confirmExtractTodosByAi(noteId, items);
    if (result.createdCount === 0) {
      closeExtractReview();
      message.info(result.skippedCount > 0 ? '所选待办均已存在，未新增' : '未创建待办');
      return;
    }
    const skippedHint = result.skippedCount > 0 ? `，跳过 ${result.skippedCount} 条重复` : '';
    closeExtractReview();
    message.success(`已创建 ${result.createdCount} 条待办${skippedHint}`);
    todoSummary.refresh();
    await loadStats();
  } catch (error: any) {
    message.error(error?.message || '创建待办失败');
    throw error;
  } finally {
    extractReview.confirming = false;
  }
};

const handleWorkflowDrop = async (event: WorkflowDropEvent) => {
  if (event.type === 'note' && event.note) {
    await handleNoteToQueue(event.note.id);
    return;
  }
  if (event.type !== 'todo' || !event.todo) return;

  const item = event.todo;
  if (event.fromStage === 'action' && event.toStage === 'parallel') {
    await joinParallel(item);
    return;
  }
  if (event.fromStage === 'parallel' && event.toStage === 'action') {
    await pauseTodo(item);
    return;
  }
  if (event.fromStage === 'parallel' && event.toStage === 'done') {
    await completeTodo(item);
  }
};

const findTodoById = (todoId: string): TodoItem | undefined => {
  const all = [
    ...(stats.value?.actionTodos || []),
    ...(stats.value?.parallelTodos || []),
  ];
  return all.find((item) => item.id === todoId);
};

const resolveDigestTodo = async (todoId: string): Promise<TodoItem | null> => {
  const local = findTodoById(todoId);
  if (local) return local;
  try {
    return await getTodo(todoId);
  } catch {
    return null;
  }
};

const canStartDigestItem = (item: AiDailySuggestion) => {
  const todo = findTodoById(item.todoId);
  if (todo) return todo.status === TODO_STATUS.PENDING;
  return item.action === 'start';
};

const canCompleteDigestItem = (item: AiDailySuggestion) => {
  const todo = findTodoById(item.todoId);
  if (todo) {
    return todo.status === TODO_STATUS.PENDING || todo.status === TODO_STATUS.IN_PROGRESS;
  }
  return item.action !== 'focus';
};

const startDigestItem = async (item: AiDailySuggestion) => {
  const todo = await resolveDigestTodo(item.todoId);
  if (!todo) {
    message.warning('该待办已不在当前看板');
    return;
  }
  if (todo.status === TODO_STATUS.PENDING) {
    await joinParallel(todo);
    dailySuggest.open = false;
    return;
  }
  message.info(`「${todo.title}」已在进行中`);
};

const completeDigestItem = async (item: AiDailySuggestion) => {
  const todo = await resolveDigestTodo(item.todoId);
  if (!todo) {
    message.warning('该待办已不在当前看板');
    return;
  }
  if (todo.status === TODO_STATUS.COMPLETED) {
    message.info('该项已完成');
    return;
  }
  await completeTodo(todo);
  dailySuggest.open = false;
};

const formatUrgentMeta = (item: TodoItem) => {
  if (isTodoOverdue(item) && item.dueAt) {
    return `逾期 · ${dayjs(item.dueAt).format('MM-DD HH:mm')}`;
  }
  if (item.dueAt) return `今日截止 · ${dayjs(item.dueAt).format('HH:mm')}`;
  return '今日需处理';
};

const openReminderForTodo = (item: TodoItem) => {
  reminderModal.todo = item;
  reminderModal.open = true;
};

const postponeTodoItem = async (todo: TodoItem) => {
  if (todo.status === TODO_STATUS.COMPLETED || todo.status === TODO_STATUS.CANCELLED) {
    message.info('已完成或已取消的待办无法推迟');
    return;
  }
  try {
    await updateTodo(todo.id, {
      title: todo.title,
      noteId: todo.noteId,
      description: todo.description || undefined,
      priority: todo.priority,
      horizon: todo.horizon,
      dueAt: buildPostponedDueAt(todo),
    });
    if (todo.status === TODO_STATUS.IN_PROGRESS) {
      await patchTodoStatus(todo.id, TODO_STATUS.PENDING);
    }
    message.success(`「${todo.title}」已推迟到明天`);
    await loadStats();
  } catch (error: any) {
    message.error(error?.message || '推迟失败');
  }
};

const postponeDigestItem = async (item: AiDailySuggestion) => {
  const todo = await resolveDigestTodo(item.todoId);
  if (!todo) {
    message.warning('该待办已不在当前看板');
    return;
  }
  await postponeTodoItem(todo);
  dailySuggest.open = false;
};

const openDailySuggestions = async () => {
  dailySuggest.open = true;
  dailySuggest.loading = true;
  dailySuggest.summary = '';
  dailySuggest.suggestions = [];
  try {
    const result = await getAiDailySuggestions();
    dailySuggest.summary = result.summary || '';
    dailySuggest.suggestions = result.suggestions || [];
    if (!dailySuggest.suggestions.length) {
      message.info('当前没有可推荐的待办');
    }
  } catch (error: any) {
    message.error(error?.message || 'AI 今日建议失败');
    dailySuggest.open = false;
  } finally {
    dailySuggest.loading = false;
  }
};

const openDailyReview = async () => {
  dailyReview.open = true;
  dailyReview.loading = true;
  dailyReview.summary = '';
  dailyReview.highlights = [];
  dailyReview.blockers = [];
  dailyReview.tomorrowFocus = [];
  dailyReview.completedCount = 0;
  try {
    const result = await getAiDailyReview();
    dailyReview.summary = result.summary || '';
    dailyReview.highlights = result.highlights || [];
    dailyReview.blockers = result.blockers || [];
    dailyReview.tomorrowFocus = result.tomorrowFocus || [];
    dailyReview.completedCount = result.completedCount ?? 0;
  } catch (error: any) {
    message.error(error?.message || 'AI 今日复盘失败');
    dailyReview.open = false;
  } finally {
    dailyReview.loading = false;
  }
};

onMounted(() => {
  void loadStats();
  void loadTodayDigest();
  void loadWeeklyRetro();
});
</script>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 24px;
}

.loop-shell {
  padding: 4px;
  border-radius: 24px;
  background: rgba(8, 145, 178, 0.06);
  border: 1px solid rgba(8, 145, 178, 0.12);
  box-shadow: var(--noto-shadow-ambient);
}

.loop-card {
  position: relative;
  border-radius: calc(24px - 4px);
  overflow: hidden;
  background:
    radial-gradient(circle at 88% 12%, rgba(8, 145, 178, 0.08), transparent 42%),
    radial-gradient(circle at 8% 88%, rgba(99, 102, 241, 0.06), transparent 38%),
    var(--noto-loop-card-bg);
  color: var(--noto-text);
  border: 1px solid var(--noto-border);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.loop-card :deep(.ant-card-body) {
  padding: 24px 20px 20px;
  overflow: visible;
  position: relative;
  z-index: 1;
}

.loop-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}

.loop-eyebrow {
  display: inline-flex;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
  font-family: var(--noto-font-mono);
  color: var(--noto-accent-deep);
  background: var(--noto-pastel-blue);
  border: 1px solid rgba(8, 145, 178, 0.15);
}

.loop-head-text h2 {
  margin: 10px 0 0;
  color: var(--noto-text);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.15;
}

.loop-desc {
  margin: 0;
  max-width: 52ch;
  font-size: 14px;
  line-height: 1.65;
  color: rgba(255, 255, 255, 0.88);
}

.eyebrow {
  display: inline-flex;
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.04em;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}

.loop-head-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.stat-pill {
  padding: 12px 16px;
  border-radius: 12px;
  background: var(--noto-pastel-blue);
  text-align: center;
  min-width: 80px;
  border: 1px solid rgba(8, 145, 178, 0.12);
  transition:
    transform 0.3s var(--noto-ease-premium),
    box-shadow 0.3s var(--noto-ease-premium);
}

.stat-pill:nth-child(2) {
  background: var(--noto-pastel-yellow);
  border-color: rgba(245, 158, 11, 0.15);
}

.stat-pill--done {
  background: var(--noto-pastel-green);
  border-color: rgba(16, 185, 129, 0.15);
}

.loop-doc-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.doc-stat-pill {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-width: 72px;
  padding: 8px 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 10px;
  background: var(--noto-doc-stat-bg);
  cursor: pointer;
  transition:
    transform 0.2s var(--noto-ease-premium),
    box-shadow 0.2s var(--noto-ease-premium);
}

.doc-stat-pill:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.doc-stat-pill--favorite {
  background: var(--noto-pastel-yellow);
  border-color: rgba(245, 158, 11, 0.15);
}

.doc-stat-pill--archived {
  background: var(--noto-status-pill-bg);
  border-color: var(--noto-border-soft);
}

.doc-stat-num {
  font-size: 18px;
  font-weight: 700;
  color: var(--noto-text, #0f172a);
  line-height: 1.1;
}

.doc-stat-label {
  font-size: 11px;
  color: var(--noto-text-muted, #64748b);
}

.stat-pill:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(8, 145, 178, 0.12);
}

.stat-num {
  display: block;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.1;
  font-family: var(--noto-font-mono);
  font-variant-numeric: tabular-nums;
  color: var(--noto-text);
}

.stat-label {
  font-size: 11px;
  color: var(--noto-text-muted);
  font-weight: 500;
}

.loop-workflow {
  margin-bottom: 16px;
  overflow: visible;
}

.loop-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--noto-border);
  margin-top: 4px;
}

.foot-link {
  color: var(--noto-accent-deep) !important;
}

.foot-guide-btn {
  font-weight: 500;
}

.alert-card,
.digest-card,
.recent-card {
  border-radius: var(--noto-radius-lg, 20px);
  box-shadow: var(--noto-shadow-soft);
  border: 1px solid var(--noto-border, #eaeaea);
}

.digest-summary {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.digest-actions {
  flex-shrink: 0;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-head h3 {
  margin: 0;
}

.recent-item {
  cursor: pointer;
  border-radius: 10px;
}

.recent-item:hover {
  background: var(--noto-hover-bg);
}

.daily-summary {
  margin: 0 0 16px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--noto-info-bg);
  color: var(--noto-info-text);
  line-height: 1.6;
}

.review-meta {
  margin: 0 0 12px;
  color: #667085;
  font-size: 13px;
}

.review-block {
  margin-bottom: 14px;
}

.review-block h4 {
  margin: 0 0 6px;
  font-size: 14px;
}

.review-block ul {
  margin: 0;
  padding-left: 18px;
  color: #344054;
  line-height: 1.7;
}

@media (max-width: 640px) {
  .loop-head-text h2 {
    font-size: 22px;
  }

  .loop-head-stats {
    width: 100%;
    justify-content: space-between;
  }

  .stat-pill {
    flex: 1;
    min-width: 0;
  }
}
</style>
