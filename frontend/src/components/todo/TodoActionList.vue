<template>
  <div class="todo-action-list">
    <div v-for="item in sortedItems" :key="item.id" class="action-row">
      <div class="action-main">
        <div class="action-title-line">
          <strong :class="{ overdue: isTodoOverdue(item) }">{{ item.title }}</strong>
          <a-tag v-if="isTodoOverdue(item)" color="red" class="status-tag">逾期</a-tag>
          <a-tag v-else-if="isVagueTodoTitle(item.title)" color="purple" class="status-tag">可拆解</a-tag>
        </div>
        <p class="action-meta">{{ formatMeta(item) }}</p>
      </div>
      <TodoQuickActions
        :item="item"
        :show-parallel="item.status === TODO_STATUS.PENDING"
        :show-breakdown="isVagueTodoTitle(item.title)"
        :show-reminder="showReminder"
        @complete="$emit('complete', $event)"
        @parallel="$emit('parallel', $event)"
        @breakdown="$emit('breakdown', $event)"
        @open-note="$emit('open-note', $event)"
        @reminder="$emit('reminder', $event)"
        @postpone="$emit('postpone', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import dayjs from 'dayjs';
import {
  isLongTermTodo,
  isTodoOverdue,
  TODO_HORIZON_LABEL,
  TODO_STATUS,
  type TodoItem,
} from '../../api/todos';
import { sortTodosByDueAt } from '../../utils/todoSort';
import { isVagueTodoTitle } from '../../utils/todoAssist';
import TodoQuickActions from './TodoQuickActions.vue';

const props = withDefaults(
  defineProps<{
    items: TodoItem[];
    showReminder?: boolean;
  }>(),
  { showReminder: true },
);

const sortedItems = computed(() => sortTodosByDueAt(props.items));

defineEmits<{
  complete: [item: TodoItem];
  parallel: [item: TodoItem];
  breakdown: [item: TodoItem];
  'open-note': [item: TodoItem];
  reminder: [item: TodoItem];
  postpone: [item: TodoItem];
}>();

const formatMeta = (item: TodoItem) => {
  const parts: string[] = [TODO_HORIZON_LABEL[item.horizon] || '短期'];
  if (item.dueAt) {
    parts.push(
      isTodoOverdue(item)
        ? `逾期 ${dayjs(item.dueAt).format('MM-DD HH:mm')}`
        : `截止 ${dayjs(item.dueAt).format('MM-DD HH:mm')}`,
    );
  }
  if (item.noteTitle) {
    parts.push(`《${item.noteTitle}》`);
  } else if (item.workspaceName) {
    parts.push(item.workspaceName);
  }
  if (item.status === TODO_STATUS.PENDING) {
    parts.push('待开始');
  } else if (item.status === TODO_STATUS.IN_PROGRESS) {
    parts.push('进行中');
  }
  if (isLongTermTodo(item) && item.status === TODO_STATUS.PENDING) {
    parts.push('可加入进行中');
  }
  return parts.join(' · ');
};
</script>

<style scoped>
.todo-action-list {
  display: grid;
  gap: 8px;
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--noto-list-row-bg);
  border: 1px solid var(--noto-list-row-border);
}

.action-main {
  min-width: 0;
  flex: 1;
}

.action-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-main strong {
  color: var(--noto-text);
  line-height: 1.45;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-main strong.overdue {
  color: #cf1322;
}

.status-tag {
  margin: 0;
  flex-shrink: 0;
  font-size: 11px;
  line-height: 18px;
}

.action-meta {
  margin: 4px 0 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.4;
}
</style>
