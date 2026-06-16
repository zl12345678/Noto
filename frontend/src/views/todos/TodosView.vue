<template>
  <div class="todos-page noto-page" :class="{ 'todos-page--mobile': isMobile }">
    <a-card class="todos-hero" :bordered="false">
      <div class="hero-row">
        <div>
          <h2 class="noto-page-title">待办中心</h2>
        </div>
        <a-dropdown>
          <a-button type="primary" size="large">+ 新建待办</a-button>
          <template #overlay>
            <a-menu @click="onCreateMenu">
              <a-menu-item key="action">近期行动（入队）</a-menu-item>
              <a-menu-item key="long_term">长期目标</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
      <div class="noto-stat-row hero-meta">
        <span class="noto-stat-chip noto-stat-chip--blue">
          待排队 <strong>{{ board?.actionTodos?.length ?? 0 }}</strong>
        </span>
        <span class="noto-stat-chip noto-stat-chip--yellow">
          进行中 <strong>{{ board?.parallelActiveCount ?? 0 }}</strong>
        </span>
        <span v-if="(board?.longTermActiveCount ?? 0) > 0" class="noto-stat-chip noto-stat-chip--neutral">
          长期目标 <strong>{{ board?.longTermActiveCount ?? 0 }}</strong>
        </span>
        <span class="noto-stat-chip noto-stat-chip--green">
          今日完成 <strong>{{ board?.todayCompletedTodos ?? 0 }}</strong>
        </span>
      </div>
      <a-space wrap class="todo-filters">
        <a-select
          v-model:value="filters.workspaceId"
          allow-clear
          placeholder="全部知识库"
          style="min-width: 180px"
          :options="workspaceOptions"
          @change="reloadBoard"
        />
        <a-segmented v-model:value="viewMode" :options="viewOptions" @change="handleViewChange" />
        <a-segmented
          v-if="viewMode === 'board'"
          v-model:value="boardDensity"
          size="small"
          :options="densityOptions"
        />
        <a-button :loading="icsExporting" @click="handleExportIcs">导出 ICS</a-button>
      </a-space>
    </a-card>

    <TodoOverdueAdviceCard
      v-if="viewMode === 'board'"
      :overdue-count="overdueCount"
      :workspace-id="filters.workspaceId"
      @apply="applyOverdueAdvice"
    />

    <template v-if="viewMode === 'board'">
      <a-spin :spinning="boardLoading">
        <div :class="['board-shell', densityClass]">
          <a-card
            v-if="!boardLoading && isBoardEmpty"
            class="board-empty-card noto-surface-card"
            :bordered="false"
          >
            <EmptyState
              title="还没有待办"
              description="从文档提取待办，或在首页把文档拖到「待办队列」"
              preset="todo"
            >
              <a-space>
                <a-button type="primary" @click="router.push('/')">回首页写文档</a-button>
                <a-button @click="openCreateModal(TODO_HORIZON.ACTION)">直接新建</a-button>
              </a-space>
            </EmptyState>
          </a-card>

          <a-card class="board-section parallel-section noto-surface-card--accent" :bordered="false" :loading="boardLoading">
            <div class="section-head">
              <h3>进行中</h3>
            </div>
            <ParallelTodoCards
              :items="board?.parallelTodos || []"
              empty-text="从待办队列加入进行中"
              @complete="completeTodo"
              @pause="pauseTodo"
              @breakdown="runBreakdownForTodo"
              @open-note="openNote"
              @reminder="openReminderModal"
              @postpone="postponeTodo"
            />
          </a-card>

          <a-card class="board-section" :bordered="false" :loading="boardLoading">
            <div class="section-head">
              <h3>待办队列</h3>
            </div>
            <TodoActionList
              v-if="board?.actionTodos?.length"
              :items="board.actionTodos"
              @complete="completeTodo"
              @parallel="joinParallel"
              @breakdown="runBreakdownForTodo"
              @open-note="openNote"
              @reminder="openReminderModal"
              @postpone="postponeTodo"
            />
            <EmptyState
              v-else
              title="待办队列为空"
              description="从文档提取待办，或在此直接新建"
              preset="todo"
              compact
            >
              <a-space>
                <a-button type="primary" @click="router.push('/notes?action=new-doc')">先去写文档</a-button>
                <a-button @click="openCreateModal(TODO_HORIZON.ACTION)">直接新建</a-button>
              </a-space>
            </EmptyState>
          </a-card>
        </div>
      </a-spin>
    </template>

    <a-card v-else class="todos-table-card" :bordered="false">
      <a-space wrap class="table-filters">
        <a-segmented v-model:value="quickFilter" :options="quickFilterOptions" @change="onQuickFilterChange" />
        <a-input-search
          v-model:value="filters.keyword"
          placeholder="搜索"
          allow-clear
          style="width: 220px"
          @search="reloadTable"
        />
        <a-select
          v-model:value="filters.horizon"
          allow-clear
          placeholder="全部类型"
          style="min-width: 130px"
          :options="horizonOptions"
          @change="reloadTable"
        />
        <a-select
          v-model:value="filters.status"
          allow-clear
          placeholder="全部状态"
          style="min-width: 120px"
          :options="statusOptions"
          @change="reloadTable"
        />
      </a-space>
      <a-spin :spinning="loading">
        <ul v-if="isMobile" class="todo-all-cards">
          <li v-for="item in displayedTodos" :key="item.id" class="todo-all-card">
            <div class="todo-all-card-head">
              <a-tag :color="isLongTermTodo(item) ? 'purple' : 'blue'" class="todo-all-card-type">
                {{ TODO_HORIZON_LABEL[item.horizon] }}
              </a-tag>
              <a-tag :color="statusColor(item.status)">{{ TODO_STATUS_LABEL[item.status] }}</a-tag>
            </div>
            <p class="todo-all-card-title">{{ item.title }}</p>
            <p class="todo-all-card-meta">
              <span v-if="item.workspaceName">{{ item.workspaceName }}</span>
              <span v-if="item.dueAt" :class="{ overdue: isTodoOverdue(item) }">
                {{ formatTodoDue(item) }}
              </span>
              <span v-else class="muted">无截止</span>
            </p>
            <div class="todo-all-card-actions">
              <TodoQuickActions
                :item="item"
                size="mini"
                :show-parallel="item.status === TODO_STATUS.PENDING"
                :show-pause="item.status === TODO_STATUS.IN_PROGRESS"
                @complete="completeTodo"
                @parallel="joinParallel"
                @pause="pauseTodo"
                @open-note="openNote"
                @reminder="openReminderModal"
                @postpone="postponeTodo"
              />
              <a-button type="link" size="small" class="edit-link" @click="openEditModal(item)">编辑</a-button>
            </div>
          </li>
          <EmptyState
            v-if="!loading && !displayedTodos.length"
            title="没有匹配的待办"
            description="调整筛选条件或新建一条"
            preset="todo"
            compact
          />
        </ul>
        <a-table
          v-else
          :columns="columns"
          :data-source="displayedTodos"
          :loading="loading"
          :pagination="pagination"
          row-key="id"
          :scroll="{ x: 1000 }"
          class="all-table"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'title'">
              <a-tag :color="isLongTermTodo(record) ? 'purple' : 'blue'" style="margin-bottom: 4px">
                {{ TODO_HORIZON_LABEL[record.horizon] }}
              </a-tag>
              <strong>{{ record.title }}</strong>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ TODO_STATUS_LABEL[record.status] }}</a-tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <TodoQuickActions
                :item="record"
                size="mini"
                :show-parallel="record.status === TODO_STATUS.PENDING"
                :show-pause="record.status === TODO_STATUS.IN_PROGRESS"
                @complete="completeTodo"
                @parallel="joinParallel"
                @pause="pauseTodo"
                @open-note="openNote"
                @reminder="openReminderModal"
                @postpone="postponeTodo"
              />
              <a-button type="link" size="small" class="edit-link" @click="openEditModal(record)">编辑</a-button>
            </template>
          </template>
        </a-table>
        <div v-if="isMobile && quickFilter === 'all' && total > 0" class="todo-all-pagination">
          <a-pagination
            v-model:current="page"
            v-model:page-size="pageSize"
            :total="total"
            :show-size-changer="false"
            size="small"
            @change="onMobilePageChange"
          />
        </div>
      </a-spin>
    </a-card>

    <a-modal v-model:open="modalOpen" :title="modalTitle" :confirm-loading="saving" @ok="handleModalOk">
      <a-form layout="vertical">
        <a-form-item label="类型" required>
          <a-radio-group v-model:value="form.horizon" :disabled="modalMode === 'edit'">
            <a-radio :value="TODO_HORIZON.ACTION">近期行动</a-radio>
            <a-radio :value="TODO_HORIZON.LONG_TERM">长期目标</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="知识库" required>
          <a-select v-model:value="form.workspaceId" :options="workspaceOptions" :disabled="modalMode === 'edit'" />
        </a-form-item>
        <a-form-item label="标题" required>
          <a-input v-model:value="form.title" />
        </a-form-item>
        <a-form-item v-if="modalMode === 'create' || isVagueTodoTitle(form.title)" label=" ">
          <a-button type="dashed" :loading="breakdown.loading" @click="runAiBreakdown">
            AI 拆解子任务
          </a-button>
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="2" />
        </a-form-item>
        <a-form-item :label="form.horizon === TODO_HORIZON.LONG_TERM ? '目标日期（可选）' : '截止时间（可选）'">
          <a-date-picker v-model:value="form.dueAt" show-time format="YYYY-MM-DD HH:mm" style="width: 100%" />
        </a-form-item>
        <a-form-item label="关联文档">
          <NoteSelect v-model="form.noteId" :workspace-id="form.workspaceId" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="breakdown.open"
      title="AI 子任务建议"
      :confirm-loading="breakdown.confirming"
      ok-text="创建选中项"
      @ok="confirmBreakdown"
      @cancel="closeBreakdown"
    >
      <p v-if="breakdown.sourceTitle" class="breakdown-source">目标：{{ breakdown.sourceTitle }}</p>
      <a-checkbox-group v-model:value="breakdown.selected" style="width: 100%">
        <a-list :data-source="breakdown.items" item-layout="horizontal">
          <template #renderItem="{ item, index }">
            <a-list-item>
              <a-checkbox :value="index">
                <strong>{{ item.title }}</strong>
                <span class="breakdown-meta">
                  · {{ TODO_HORIZON_LABEL[item.horizon] || '近期行动' }}
                  · 优先级 {{ item.priority }}
                  <span v-if="item.dueAt"> · {{ item.dueAt }}</span>
                </span>
              </a-checkbox>
            </a-list-item>
          </template>
        </a-list>
      </a-checkbox-group>
    </a-modal>

    <TodoReminderModal v-model:open="reminderModal.open" :todo="reminderModal.todo" />
  </div>
</template>

<script setup lang="ts">
import dayjs, { type Dayjs } from 'dayjs';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  TODO_HORIZON,
  TODO_HORIZON_LABEL,
  TODO_STATUS,
  TODO_STATUS_LABEL,
  createTodo,
  downloadTodosIcs,
  getTodo,
  getTodoBoard,
  isTodoOverdue,
  listTodos,
  patchTodoStatus,
  updateTodo,
  isLongTermTodo,
  type TodoBoard,
  type TodoHorizonType,
  type TodoItem,
} from '../../api/todos';
import { listWorkspaces, type Workspace } from '../../api/workspaces';
import { breakdownTodoByAi, type AiSubtaskSuggestion, type TodoOverdueAdviceItem } from '../../api/ai';
import TodoActionList from '../../components/todo/TodoActionList.vue';
import ParallelTodoCards from '../../components/todo/ParallelTodoCards.vue';
import TodoOverdueAdviceCard from '../../components/todo/TodoOverdueAdviceCard.vue';
import TodoQuickActions from '../../components/todo/TodoQuickActions.vue';
import TodoReminderModal from '../../components/todo/TodoReminderModal.vue';
import EmptyState from '../../components/common/EmptyState.vue';
import NoteSelect from '../../components/note/NoteSelect.vue';
import { isVagueTodoTitle, buildPostponedDueAt } from '../../utils/todoAssist';
import { useTodoSummaryStore } from '../../store/todoSummary';
import { useBoardDensity } from '../../composables/useBoardDensity';
import { useBreakpoint } from '../../composables/useBreakpoint';

const route = useRoute();
const router = useRouter();
const { isMobile } = useBreakpoint();
const todoSummary = useTodoSummaryStore();
const { density: boardDensity, densityClass } = useBoardDensity();

const densityOptions = [
  { label: '标准', value: 'comfortable' },
  { label: '紧凑', value: 'compact' },
];

const viewMode = ref<'board' | 'all'>(route.query.view === 'all' ? 'all' : 'board');
const viewOptions = [
  { label: '行动看板', value: 'board' },
  { label: '全部列表', value: 'all' },
];

const boardLoading = ref(false);
const loading = ref(false);
const saving = ref(false);
const modalOpen = ref(false);
const modalMode = ref<'create' | 'edit'>('create');
const editingId = ref('');
const board = ref<TodoBoard | null>(null);
const todos = ref<TodoItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const workspaces = ref<Workspace[]>([]);
const createHorizonDefault = ref<TodoHorizonType>(TODO_HORIZON.ACTION);
const icsExporting = ref(false);
const quickFilter = ref<'all' | 'overdue' | 'due_today' | 'no_due'>('all');

const quickFilterOptions = [
  { label: '全部', value: 'all' },
  { label: '逾期', value: 'overdue' },
  { label: '今日到期', value: 'due_today' },
  { label: '无截止', value: 'no_due' },
];

const reminderModal = reactive({
  open: false,
  todo: null as TodoItem | null,
});

const filters = reactive({
  keyword: '',
  workspaceId: undefined as string | undefined,
  status: undefined as number | undefined,
  horizon: undefined as TodoHorizonType | undefined,
});

const form = reactive({
  workspaceId: undefined as string | undefined,
  title: '',
  description: '',
  priority: 2,
  horizon: TODO_HORIZON.ACTION as TodoHorizonType,
  dueAt: null as Dayjs | null,
  noteId: undefined as string | undefined,
});

const breakdown = reactive({
  open: false,
  loading: false,
  confirming: false,
  sourceTitle: '',
  items: [] as AiSubtaskSuggestion[],
  selected: [] as number[],
  parentTodo: null as TodoItem | null,
});

const overdueCount = computed(() => {
  const all = [
    ...(board.value?.actionTodos || []),
    ...(board.value?.parallelTodos || []),
  ];
  return all.filter((item) => isTodoOverdue(item)).length;
});

const isBoardEmpty = computed(() => {
  if (!board.value) return false;
  return (
    (board.value.actionTodos?.length ?? 0) === 0 &&
    (board.value.parallelTodos?.length ?? 0) === 0 &&
    (board.value.longTermTodos?.length ?? 0) === 0
  );
});

const displayedTodos = computed(() => {
  if (quickFilter.value === 'all') return todos.value;
  return todos.value.filter((item) => {
    if (item.status === TODO_STATUS.COMPLETED || item.status === TODO_STATUS.CANCELLED) {
      return false;
    }
    if (quickFilter.value === 'overdue') return isTodoOverdue(item);
    if (quickFilter.value === 'due_today') {
      return item.dueAt && dayjs(item.dueAt).isSame(dayjs(), 'day');
    }
    if (quickFilter.value === 'no_due') return !item.dueAt;
    return true;
  });
});

const modalTitle = computed(() => {
  if (modalMode.value === 'edit') return '编辑待办';
  return form.horizon === TODO_HORIZON.LONG_TERM ? '新建长期目标' : '新建近期行动';
});

const statusOptions = Object.entries(TODO_STATUS_LABEL).map(([value, label]) => ({
  value: Number(value),
  label,
}));

const horizonOptions = Object.entries(TODO_HORIZON_LABEL).map(([value, label]) => ({
  value,
  label,
}));

const workspaceOptions = computed(() =>
  workspaces.value.map((item) => ({ label: item.name, value: item.id })),
);

const columns = [
  { title: '标题', key: 'title', width: 280 },
  { title: '状态', key: 'status', width: 100 },
  { title: '知识库', dataIndex: 'workspaceName', width: 120 },
  { title: '操作', key: 'actions', width: 200, fixed: 'right' as const },
];

const pagination = computed(() => ({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  onChange: (next: number, size: number) => {
    page.value = next;
    pageSize.value = size;
    loadTodos();
  },
}));

const statusColor = (status: number) => {
  if (status === 2) return 'success';
  if (status === 1) return 'processing';
  return 'blue';
};

const formatTodoDue = (item: TodoItem) => {
  if (!item.dueAt) return '';
  const label = dayjs(item.dueAt).format('MM-DD HH:mm');
  return isTodoOverdue(item) ? `逾期 ${label}` : `截止 ${label}`;
};

const onMobilePageChange = (next: number) => {
  page.value = next;
  void loadTodos();
};

const reloadBoard = async () => {
  boardLoading.value = true;
  try {
    board.value = await getTodoBoard(filters.workspaceId);
    todoSummary.refresh();
  } catch (error: any) {
    message.error(error?.message || '加载看板失败');
  } finally {
    boardLoading.value = false;
  }
};

const handleExportIcs = async () => {
  icsExporting.value = true;
  try {
    await downloadTodosIcs(filters.workspaceId);
    message.success('ICS 文件已下载，可导入日历应用');
  } catch (error: any) {
    message.error(error?.message || '导出失败');
  } finally {
    icsExporting.value = false;
  }
};

const loadTodos = async () => {
  loading.value = true;
  try {
    const data = await listTodos({
      page: page.value,
      size: quickFilter.value === 'all' ? pageSize.value : 200,
      workspaceId: filters.workspaceId,
      status: filters.status,
      horizon: filters.horizon,
      keyword: filters.keyword || undefined,
      noteId: typeof route.query.noteId === 'string' ? route.query.noteId : undefined,
    });
    todos.value = data.records || [];
    total.value = data.total || 0;
  } catch (error: any) {
    message.error(error?.message || '加载失败');
  } finally {
    loading.value = false;
  }
};

const onQuickFilterChange = () => {
  page.value = 1;
  router.replace({
    path: '/todos',
    query: {
      ...route.query,
      view: 'all',
      focus: quickFilter.value === 'all' ? undefined : quickFilter.value,
    },
  });
  loadTodos();
};

const reloadTable = () => {
  page.value = 1;
  loadTodos();
};

const refresh = () => {
  if (viewMode.value === 'board') {
    reloadBoard();
  } else {
    loadTodos();
  }
};

const handleViewChange = () => {
  router.replace({ path: '/todos', query: { ...route.query, view: viewMode.value } });
  refresh();
};

const completeTodo = async (item: TodoItem) => {
  try {
    await patchTodoStatus(item.id, TODO_STATUS.COMPLETED);
    message.success('又完成一项，保持节奏！');
    refresh();
  } catch (error: any) {
    message.error(error?.message || '操作失败');
  }
};

const joinParallel = async (item: TodoItem) => {
  try {
    await patchTodoStatus(item.id, TODO_STATUS.IN_PROGRESS);
    message.success(`「${item.title}」已开始处理`);
    refresh();
  } catch (error: any) {
    message.error(error?.message || '操作失败');
  }
};

const pauseTodo = async (item: TodoItem) => {
  try {
    await patchTodoStatus(item.id, TODO_STATUS.PENDING);
    message.info(`「${item.title}」已暂停，回到队列`);
    refresh();
  } catch (error: any) {
    message.error(error?.message || '操作失败');
  }
};

const onCreateMenu = ({ key }: { key: string }) => {
  openCreateModal(key === 'long_term' ? TODO_HORIZON.LONG_TERM : TODO_HORIZON.ACTION);
};

const openCreateModal = (horizon: TodoHorizonType = TODO_HORIZON.ACTION) => {
  modalMode.value = 'create';
  createHorizonDefault.value = horizon;
  form.horizon = horizon;
  form.workspaceId = filters.workspaceId || workspaces.value[0]?.id;
  form.title = typeof route.query.title === 'string' ? route.query.title : '';
  form.description = '';
  form.dueAt = null;
  form.noteId = typeof route.query.noteId === 'string' ? route.query.noteId : undefined;
  modalOpen.value = true;
};

const openEditModal = (item: TodoItem) => {
  modalMode.value = 'edit';
  editingId.value = item.id;
  form.workspaceId = item.workspaceId;
  form.title = item.title;
  form.description = item.description || '';
  form.horizon = item.horizon || TODO_HORIZON.ACTION;
  form.dueAt = item.dueAt ? dayjs(item.dueAt) : null;
  form.noteId = item.noteId || undefined;
  modalOpen.value = true;
};

const handleModalOk = async () => {
  if (!form.workspaceId || !form.title.trim()) {
    message.warning('请填写必填项');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      workspaceId: form.workspaceId,
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      priority: 2,
      horizon: form.horizon,
      dueAt: form.dueAt ? form.dueAt.format('YYYY-MM-DDTHH:mm:ss') : null,
      noteId: form.noteId || null,
    };
    if (modalMode.value === 'create') {
      await createTodo(payload);
      message.success('已创建');
    } else {
      await updateTodo(editingId.value, payload);
      message.success('已保存');
    }
    modalOpen.value = false;
    refresh();
  } catch (error: any) {
    message.error(error?.message || '保存失败');
  } finally {
    saving.value = false;
  }
};

const closeBreakdown = () => {
  breakdown.open = false;
  breakdown.loading = false;
  breakdown.confirming = false;
  breakdown.sourceTitle = '';
  breakdown.items = [];
  breakdown.selected = [];
  breakdown.parentTodo = null;
};

const runBreakdownForTodo = async (item: TodoItem) => {
  breakdown.parentTodo = item;
  breakdown.loading = true;
  try {
    const result = await breakdownTodoByAi({
      title: item.title,
      description: item.description || undefined,
      horizon: item.horizon,
    });
    breakdown.sourceTitle = result.sourceTitle || item.title;
    breakdown.items = result.subtasks || [];
    breakdown.selected = breakdown.items.map((_, index) => index);
    if (!breakdown.items.length) {
      message.info('AI 未生成子任务建议');
      breakdown.parentTodo = null;
      return;
    }
    breakdown.open = true;
  } catch (error: any) {
    message.error(error?.message || 'AI 拆解失败');
    breakdown.parentTodo = null;
  } finally {
    breakdown.loading = false;
  }
};

const runAiBreakdown = async () => {
  if (!form.title.trim()) {
    message.warning('请先填写目标标题');
    return;
  }
  breakdown.parentTodo = null;
  breakdown.loading = true;
  try {
    const result = await breakdownTodoByAi({
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      horizon: form.horizon,
    });
    breakdown.sourceTitle = result.sourceTitle || form.title.trim();
    breakdown.items = result.subtasks || [];
    breakdown.selected = breakdown.items.map((_, index) => index);
    if (!breakdown.items.length) {
      message.info('AI 未生成子任务建议');
      return;
    }
    breakdown.open = true;
  } catch (error: any) {
    message.error(error?.message || 'AI 拆解失败');
  } finally {
    breakdown.loading = false;
  }
};

const confirmBreakdown = async () => {
  const workspaceId = breakdown.parentTodo?.workspaceId || form.workspaceId;
  if (!workspaceId) {
    message.warning('请选择知识库');
    return;
  }
  const picked = breakdown.selected
    .map((index) => breakdown.items[index])
    .filter(Boolean);
  if (!picked.length) {
    message.warning('请至少选择一项子任务');
    return;
  }
  breakdown.confirming = true;
  try {
    const noteId = breakdown.parentTodo?.noteId || form.noteId || null;
    for (const item of picked) {
      await createTodo({
        workspaceId,
        title: item.title,
        priority: item.priority,
        horizon: item.horizon || TODO_HORIZON.ACTION,
        dueAt: item.dueAt || null,
        noteId,
      });
    }
    if (breakdown.parentTodo) {
      await patchTodoStatus(breakdown.parentTodo.id, TODO_STATUS.CANCELLED);
      message.success(`已拆解为 ${picked.length} 条子任务，原待办已归档`);
    } else {
      message.success(`已创建 ${picked.length} 条子任务`);
      modalOpen.value = false;
    }
    closeBreakdown();
    refresh();
  } catch (error: any) {
    message.error(error?.message || '创建子任务失败');
  } finally {
    breakdown.confirming = false;
  }
};

const findBoardTodo = (todoId: string): TodoItem | undefined => {
  const all = [
    ...(board.value?.actionTodos || []),
    ...(board.value?.parallelTodos || []),
  ];
  return all.find((item) => item.id === todoId);
};

const applyOverdueAdvice = async (advice: TodoOverdueAdviceItem) => {
  const todo = findBoardTodo(advice.todoId);
  if (!todo) {
    message.warning('该待办已不在当前看板');
    return;
  }
  if (advice.action === 'complete') {
    await completeTodo(todo);
    return;
  }
  if (advice.action === 'archive') {
    try {
      await patchTodoStatus(todo.id, TODO_STATUS.CANCELLED);
      message.success(`「${todo.title}」已归档`);
      refresh();
    } catch (error: any) {
      message.error(error?.message || '归档失败');
    }
    return;
  }
  if (advice.action === 'breakdown') {
    await runBreakdownForTodo(todo);
    return;
  }
  if (advice.action === 'reschedule') {
    try {
      const dueAt =
        advice.suggestedDueAt ||
        dayjs().add(1, 'day').hour(9).minute(0).second(0).millisecond(0).toISOString();
      await updateTodo(todo.id, {
        title: todo.title,
        noteId: todo.noteId,
        description: todo.description || undefined,
        priority: todo.priority,
        horizon: todo.horizon,
        dueAt,
      });
      message.success(`「${todo.title}」已改期`);
      refresh();
    } catch (error: any) {
      message.error(error?.message || '改期失败');
    }
  }
};

const openNote = (item: TodoItem) => {
  if (!item.noteId) return;
  const query: Record<string, string> = { from: 'todos' };
  if (item.workspaceId) query.workspace = item.workspaceId;
  router.push({ path: `/notes/${item.noteId}`, query });
};

const openReminderModal = (item: TodoItem) => {
  reminderModal.todo = item;
  reminderModal.open = true;
};

const postponeTodo = async (item: TodoItem) => {
  if (item.status === TODO_STATUS.COMPLETED || item.status === TODO_STATUS.CANCELLED) {
    message.info('已完成或已取消的待办无法推迟');
    return;
  }
  try {
    await updateTodo(item.id, {
      title: item.title,
      noteId: item.noteId,
      description: item.description || undefined,
      priority: item.priority,
      horizon: item.horizon,
      dueAt: buildPostponedDueAt(item),
    });
    if (item.status === TODO_STATUS.IN_PROGRESS) {
      await patchTodoStatus(item.id, TODO_STATUS.PENDING);
    }
    message.success(`「${item.title}」已推迟到明天`);
    refresh();
  } catch (error: any) {
    message.error(error?.message || '推迟失败');
  }
};

watch(() => route.query.view, (v) => {
  viewMode.value = v === 'all' ? 'all' : 'board';
});

const focusTodoFromQuery = async () => {
  const todoId = typeof route.query.todoId === 'string' ? route.query.todoId : '';
  if (!todoId) return;
  viewMode.value = 'all';
  try {
    const todo = await getTodo(todoId);
    if (todo.workspaceId) filters.workspaceId = todo.workspaceId;
    await loadTodos();
    openEditModal(todo);
    const nextQuery = { ...route.query };
    delete nextQuery.todoId;
    router.replace({ path: '/todos', query: nextQuery });
  } catch (error: any) {
    message.warning(error?.message || '未找到该待办');
  }
};

watch(
  () => route.query.todoId,
  (todoId) => {
    if (typeof todoId === 'string' && todoId) {
      void focusTodoFromQuery();
    }
  },
);

onMounted(async () => {
  workspaces.value = await listWorkspaces();
  if (typeof route.query.workspace === 'string') filters.workspaceId = route.query.workspace;
  if (typeof route.query.horizon === 'string' && route.query.horizon === TODO_HORIZON.LONG_TERM) {
    createHorizonDefault.value = TODO_HORIZON.LONG_TERM;
  }
  const focus = route.query.focus;
  if (focus === 'overdue' || focus === 'due_today' || focus === 'no_due') {
    quickFilter.value = focus;
    viewMode.value = 'all';
  }
  if (route.query.create === '1') {
    openCreateModal(createHorizonDefault.value);
  } else if (typeof route.query.todoId === 'string' && route.query.todoId) {
    await focusTodoFromQuery();
  } else {
    refresh();
  }
});
</script>

<style scoped>
.todos-hero,
.board-section,
.todos-table-card {
  border-radius: var(--noto-radius-lg);
}

.hero-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.hero-meta {
  margin-bottom: 4px;
}

.board-empty {
  text-align: center;
  padding: 32px 16px;
}

.board-empty h3 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
}

.board-empty p {
  margin: 0 0 16px;
  color: var(--noto-text-muted);
  font-size: 14px;
}

.section-head h3 {
  margin: 0 0 4px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.section-head p {
  margin: 0;
  color: var(--noto-text-muted);
  font-size: 13px;
}

.table-filters {
  margin-bottom: 12px;
}

.all-table {
  margin-top: 8px;
}

.todo-all-cards {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.todo-all-card {
  padding: 12px 14px;
  border-radius: var(--noto-radius-md, 12px);
  border: 1px solid var(--noto-border-soft, #eef2f7);
  background: var(--noto-surface-solid, #fff);
}

.todo-all-card-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.todo-all-card-type {
  margin: 0;
}

.todo-all-card-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--noto-text);
  word-break: break-word;
}

.todo-all-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--noto-text-muted);
}

.todo-all-card-meta .overdue {
  color: #cf1322;
  font-weight: 500;
}

.todo-all-card-meta .muted {
  color: #94a3b8;
}

.todo-all-card-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 4px 8px;
  padding-top: 8px;
  border-top: 1px solid var(--noto-border-soft, #eef2f7);
}

.todo-all-pagination {
  display: flex;
  justify-content: center;
  padding: 16px 0 8px;
}

.edit-link {
  padding: 0 4px;
  margin-left: 4px;
  vertical-align: middle;
}

.breakdown-source {
  margin: 0 0 12px;
  color: var(--noto-text-muted);
}

.breakdown-meta {
  margin-left: 6px;
  color: #98a2b3;
  font-size: 12px;
}

.board-shell.board-density--compact :deep(.parallel-card) {
  padding: 8px 10px;
}

.board-shell.board-density--compact :deep(.parallel-card h4) {
  font-size: 13px;
}

.board-shell.board-density--compact :deep(.action-row) {
  padding: 6px 10px;
}

.board-shell.board-density--compact :deep(.action-title) {
  font-size: 13px;
}
</style>
