<template>
  <div class="mobile-todo-board">
    <section class="mobile-todo-section">
      <div class="mobile-todo-section-head">
        <span>待办队列</span>
        <span class="mobile-todo-section-count">{{ actionTodos.length }} 项</span>
      </div>
      <TodoActionList
        v-if="actionTodos.length"
        :items="actionTodos"
        @complete="emit('complete', $event)"
        @parallel="emit('parallel', $event)"
        @breakdown="emit('breakdown', $event)"
        @open-note="emit('open-note', $event)"
        @reminder="emit('reminder', $event)"
        @postpone="emit('postpone', $event)"
      />
      <div v-else class="mobile-todo-card">
        <p class="mobile-todo-card-meta">队列为空，从文档提取或新建</p>
        <a-button type="primary" block @click="emit('create')">新建待办</a-button>
      </div>
    </section>

    <section class="mobile-todo-section">
      <div class="mobile-todo-section-head">
        <span>进行中</span>
        <span class="mobile-todo-section-count">{{ parallelTodos.length }} 项</span>
      </div>
      <ParallelTodoCards
        :items="parallelTodos"
        empty-text="从队列点「加入进行中」"
        @complete="emit('complete', $event)"
        @pause="emit('pause', $event)"
        @breakdown="emit('breakdown', $event)"
        @open-note="emit('open-note', $event)"
        @reminder="emit('reminder', $event)"
        @postpone="emit('postpone', $event)"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import type { TodoItem } from '../../api/todos';
import TodoActionList from '../todo/TodoActionList.vue';
import ParallelTodoCards from '../todo/ParallelTodoCards.vue';

defineProps<{
  actionTodos: TodoItem[];
  parallelTodos: TodoItem[];
}>();

const emit = defineEmits<{
  complete: [item: TodoItem];
  parallel: [item: TodoItem];
  pause: [item: TodoItem];
  breakdown: [item: TodoItem];
  'open-note': [item: TodoItem];
  reminder: [item: TodoItem];
  postpone: [item: TodoItem];
  create: [];
}>();
</script>

<style scoped>
.mobile-todo-board {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>