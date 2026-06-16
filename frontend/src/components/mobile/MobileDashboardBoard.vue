<template>
  <div class="mobile-dashboard-stack">
    <div class="mobile-stat-row">
      <div class="mobile-stat-pill">
        <span class="mobile-stat-num">{{ actionTodos.length }}</span>
        <span class="mobile-stat-label">待排队</span>
      </div>
      <div class="mobile-stat-pill">
        <span class="mobile-stat-num">{{ parallelTodos.length }}</span>
        <span class="mobile-stat-label">进行中</span>
      </div>
      <div class="mobile-stat-pill">
        <span class="mobile-stat-num">{{ todayCompleted }}</span>
        <span class="mobile-stat-label">今日完成</span>
      </div>
    </div>

    <section class="mobile-todo-section">
      <div class="mobile-todo-section-head">
        <span>待办队列</span>
        <span class="mobile-todo-section-count">{{ actionTodos.length }} 项</span>
      </div>
      <div v-if="!actionTodos.length" class="mobile-todo-card">
        <p class="mobile-todo-card-meta">从文档提取待办，或去待办页新建</p>
        <a-button type="primary" block @click="emit('go-notes')">写文档</a-button>
      </div>
      <div v-for="item in actionTodos" :key="item.id" class="mobile-todo-card">
        <div class="mobile-todo-card-title">{{ item.title }}</div>
        <div v-if="item.dueAt" class="mobile-todo-card-meta" :class="{ overdue: isTodoOverdue(item) }">
          {{ formatDue(item) }}
        </div>
        <div class="mobile-todo-card-actions">
          <a-button size="small" type="primary" @click="emit('parallel', item)">开始</a-button>
          <a-button size="small" @click="emit('complete', item)">完成</a-button>
        </div>
      </div>
    </section>

    <section class="mobile-todo-section">
      <div class="mobile-todo-section-head">
        <span>进行中</span>
        <span class="mobile-todo-section-count">{{ parallelTodos.length }} 项</span>
      </div>
      <div v-if="!parallelTodos.length" class="mobile-todo-card">
        <p class="mobile-todo-card-meta">从队列点「开始」加入进行中</p>
      </div>
      <div v-for="item in parallelTodos" :key="item.id" class="mobile-todo-card">
        <div class="mobile-todo-card-title">{{ item.title }}</div>
        <div v-if="item.dueAt" class="mobile-todo-card-meta" :class="{ overdue: isTodoOverdue(item) }">
          {{ formatDue(item) }}
        </div>
        <div class="mobile-todo-card-actions">
          <a-button size="small" @click="emit('pause', item)">退回队列</a-button>
          <a-button size="small" type="primary" @click="emit('complete', item)">完成</a-button>
        </div>
      </div>
    </section>

    <section v-if="recentNotes.length" class="mobile-todo-section">
      <div class="mobile-todo-section-head">
        <span>最近文档</span>
        <a-button type="link" size="small" @click="emit('go-notes')">全部</a-button>
      </div>
      <div
        v-for="note in recentNotes.slice(0, 5)"
        :key="note.id"
        class="mobile-todo-card mobile-todo-card--link"
        @click="emit('open-note', note.id)"
      >
        <div class="mobile-todo-card-title">{{ note.title }}</div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import type { Note } from '../../api/notes';
import { isTodoOverdue, type TodoItem } from '../../api/todos';

defineProps<{
  actionTodos?: TodoItem[];
  parallelTodos?: TodoItem[];
  recentNotes?: Note[];
  todayCompleted?: number;
}>();

const emit = defineEmits<{
  complete: [item: TodoItem];
  parallel: [item: TodoItem];
  pause: [item: TodoItem];
  'open-note': [noteId: string];
  'go-notes': [];
}>();

function formatDue(item: TodoItem) {
  if (!item.dueAt) return '';
  return isTodoOverdue(item)
    ? `已逾期 ${dayjs(item.dueAt).format('MM-DD HH:mm')}`
    : `截止 ${dayjs(item.dueAt).format('MM-DD HH:mm')}`;
}
</script>

<style scoped>
.mobile-todo-card--link {
  cursor: pointer;
}

.mobile-todo-card--link:active {
  background: var(--noto-canvas);
}
</style>