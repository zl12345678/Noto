<template>
  <a-modal
    :open="open"
    :title="modalTitle"
    width="720px"
    :confirm-loading="confirming"
    ok-text="确认加入待办"
    cancel-text="取消"
    :ok-button-props="{ disabled: !hasSelected }"
    @ok="handleConfirm"
    @cancel="emit('cancel')"
  >
    <a-alert
      type="info"
      show-icon
      message="提取结果不会直接入库。请核对标题、截止时间、类型后再确认；文档无明确期限时截止为空，可手动设置。"
      style="margin-bottom: 12px"
    />
    <p v-if="noteTitle" class="note-hint">来源文档：{{ noteTitle }}</p>
    <EmptyState v-if="!rows.length && !loading" :title="emptyHint" preset="todo" compact />
    <a-spin :spinning="loading">
      <div v-if="rows.length" class="review-toolbar">
        <a-space>
          <a-checkbox
            :indeterminate="indeterminate"
            :checked="allSelected"
            @change="toggleSelectAll"
          >
            全选
          </a-checkbox>
          <span class="hint">已选 {{ selectedCount }} / {{ rows.length }} 条</span>
        </a-space>
      </div>
      <div v-for="(row, index) in rows" :key="row.key" class="review-row">
        <a-checkbox v-model:checked="row.selected" :disabled="!row.title.trim()" />
        <div class="review-fields">
          <a-input
            v-model:value="row.title"
            placeholder="待办标题"
            :status="row.duplicate ? 'warning' : undefined"
            @change="onTitleChange(row)"
          />
          <a-space wrap class="row-meta">
            <a-date-picker
              v-model:value="row.dueAt"
              show-time
              format="YYYY-MM-DD HH:mm"
              size="small"
              placeholder="截止时间（可选）"
              style="width: 200px"
            />
            <a-select v-model:value="row.horizon" size="small" style="width: 110px" :options="horizonOptions" />
            <a-select v-model:value="row.priority" size="small" style="width: 88px" :options="priorityOptions" />
            <a-checkbox v-model:checked="row.completed" size="small">文档中已完成</a-checkbox>
            <a-tag v-if="row.duplicate" color="orange">同名已存在</a-tag>
          </a-space>
        </div>
        <a-button type="text" danger size="small" :disabled="rows.length <= 1" @click="removeRow(index)">
          移除
        </a-button>
      </div>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch, withDefaults } from 'vue';
import { message } from 'ant-design-vue';
import dayjs, { type Dayjs } from 'dayjs';
import EmptyState from '../common/EmptyState.vue';
import {
  TODO_HORIZON,
  TODO_HORIZON_LABEL,
  type TodoHorizonType,
} from '../../api/todos';
import type { ExtractedTodoSuggestion, ExtractTodosSource } from '../../api/ai';

export interface ExtractReviewRow {
  key: string;
  selected: boolean;
  title: string;
  completed: boolean;
  priority: number;
  horizon: TodoHorizonType;
  dueAt: Dayjs | null;
  duplicate: boolean;
}

export type ExtractConfirmItem = {
  title: string;
  completed: boolean;
  priority: number;
  horizon: TodoHorizonType;
  dueAt?: string | null;
};

const props = withDefaults(
  defineProps<{
    open: boolean;
    source?: ExtractTodosSource;
    loading?: boolean;
    confirming?: boolean;
    noteTitle?: string;
    suggestions?: ExtractedTodoSuggestion[];
    onConfirm?: (items: ExtractConfirmItem[]) => Promise<void>;
  }>(),
  { source: 'ai' },
);

const modalTitle = computed(() =>
  props.source === 'markdown' ? '审查规则提取的待办' : '审查 AI 提取的待办',
);

const emptyHint = computed(() =>
  props.source === 'markdown'
    ? '未在文档中发现待办（支持 - [ ] 复选框或「待办：」行）'
    : 'AI 未识别到可提取的待办',
);

const emit = defineEmits<{
  cancel: [];
}>();

const rows = ref<ExtractReviewRow[]>([]);

const horizonOptions = Object.entries(TODO_HORIZON_LABEL).map(([value, label]) => ({
  value,
  label,
}));

const priorityOptions = [
  { value: 1, label: '高' },
  { value: 2, label: '中' },
  { value: 3, label: '低' },
];

const buildRows = (suggestions: ExtractedTodoSuggestion[]) =>
  suggestions.map((item, index) => ({
    key: `s-${index}-${item.title}`,
    selected: !item.duplicate,
    title: item.title,
    completed: item.completed,
    priority: item.priority >= 1 && item.priority <= 3 ? item.priority : 2,
    horizon: item.horizon === TODO_HORIZON.LONG_TERM ? TODO_HORIZON.LONG_TERM : TODO_HORIZON.ACTION,
    dueAt: item.dueAt ? dayjs(item.dueAt) : null,
    duplicate: item.duplicate,
  }));

watch(
  () => props.suggestions,
  (list) => {
    rows.value = buildRows(list || []);
  },
  { immediate: true },
);

watch(
  () => props.open,
  (visible) => {
    if (visible && props.suggestions?.length) {
      rows.value = buildRows(props.suggestions);
    }
  },
);

const selectedCount = computed(() => rows.value.filter((r) => r.selected && r.title.trim()).length);
const hasSelected = computed(() => selectedCount.value > 0);
const allSelected = computed(
  () => rows.value.length > 0 && rows.value.every((r) => r.selected || !r.title.trim()),
);
const indeterminate = computed(
  () => selectedCount.value > 0 && selectedCount.value < rows.value.length,
);

const toggleSelectAll = (e: { target: { checked: boolean } }) => {
  const checked = e.target.checked;
  rows.value.forEach((row) => {
    if (row.title.trim()) row.selected = checked;
  });
};

const onTitleChange = (row: ExtractReviewRow) => {
  if (!row.title.trim()) {
    row.selected = false;
  }
};

const removeRow = (index: number) => {
  rows.value.splice(index, 1);
};

const handleConfirm = async () => {
  const items = rows.value
    .filter((row) => row.selected && row.title.trim())
    .map((row) => ({
      title: row.title.trim(),
      completed: row.completed,
      priority: row.priority,
      horizon: row.horizon,
      dueAt: row.dueAt ? row.dueAt.format('YYYY-MM-DDTHH:mm:ss') : null,
    }));
  if (!items.length) {
    message.warning('请至少选择一条待办');
    return Promise.reject();
  }
  if (!props.onConfirm) {
    return Promise.reject();
  }
  await props.onConfirm(items);
};
</script>

<style scoped>
.note-hint {
  margin: 0 0 12px;
  color: var(--text-secondary, #666);
  font-size: 13px;
}
.review-toolbar {
  margin-bottom: 10px;
}
.hint {
  font-size: 12px;
  color: #888;
}
.review-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.review-row:last-child {
  border-bottom: none;
}
.review-fields {
  flex: 1;
  min-width: 0;
}
.row-meta {
  margin-top: 8px;
}
</style>
