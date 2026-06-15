<template>
  <div v-if="items.length" class="parallel-grid">
    <div v-for="item in sortedItems" :key="item.id" class="parallel-card">
      <div class="card-top">
        <h4 :class="{ overdue: isTodoOverdue(item) }">{{ item.title }}</h4>
        <TodoQuickActions
          :item="item"
          size="mini"
          :show-pause="true"
          :show-breakdown="isVagueTodoTitle(item.title) || isTodoOverdue(item)"
          @complete="$emit('complete', $event)"
          @pause="$emit('pause', $event)"
          @breakdown="$emit('breakdown', $event)"
          @open-note="$emit('open-note', $event)"
          @reminder="$emit('reminder', $event)"
          @postpone="$emit('postpone', $event)"
        />
      </div>
      <p class="card-meta">{{ cardMeta(item) }}</p>
    </div>
  </div>
  <EmptyState
    v-else
    :title="emptyText"
    preset="todo"
    compact
  />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import dayjs from 'dayjs';
import EmptyState from '../common/EmptyState.vue';
import { isLongTermTodo, isTodoOverdue, TODO_HORIZON_LABEL, type TodoItem } from '../../api/todos';
import { isVagueTodoTitle } from '../../utils/todoAssist';
import { sortTodosByDueAt } from '../../utils/todoSort';
import TodoQuickActions from './TodoQuickActions.vue';

const props = defineProps<{
  items: TodoItem[];
  emptyText?: string;
}>();

const sortedItems = computed(() => sortTodosByDueAt(props.items));

defineEmits<{
  complete: [item: TodoItem];
  pause: [item: TodoItem];
  breakdown: [item: TodoItem];
  'open-note': [item: TodoItem];
  reminder: [item: TodoItem];
  postpone: [item: TodoItem];
}>();

const cardMeta = (item: TodoItem) => {
  const parts: string[] = [
    isLongTermTodo(item) ? TODO_HORIZON_LABEL.long_term : TODO_HORIZON_LABEL.action,
    '进行中',
  ];
  if (item.dueAt) {
    parts.push(isTodoOverdue(item) ? `逾期 ${dayjs(item.dueAt).format('MM-DD HH:mm')}` : `截止 ${dayjs(item.dueAt).format('MM-DD HH:mm')}`);
  }
  if (item.noteTitle) parts.push(`《${item.noteTitle}》`);
  return parts.join(' · ');
};
</script>

<style scoped>
.parallel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
}

.parallel-card {
  padding: 12px 14px;
  border-radius: 14px;
  background: var(--noto-parallel-card-bg);
  border: 1px solid var(--noto-parallel-card-border);
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.parallel-card h4 {
  margin: 0;
  font-size: 15px;
  color: var(--noto-text);
  line-height: 1.4;
  flex: 1;
  min-width: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.parallel-card h4.overdue {
  color: #cf1322;
}

.card-meta {
  margin: 6px 0 0;
  font-size: 12px;
  color: #667085;
  line-height: 1.4;
}
</style>
