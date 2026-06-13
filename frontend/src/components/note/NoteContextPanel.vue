<template>
  <aside class="note-context-panel">
    <header class="panel-head">
      <div>
        <h3>文档上下文</h3>
      </div>
      <button type="button" class="icon-btn" aria-label="关闭" @click="emit('close')">
        <CloseOutlined />
      </button>
    </header>

    <div class="panel-body">
      <section class="context-section">
        <div class="section-head">
          <h4>关联待办</h4>
          <a-tag v-if="linkedTodos.length" color="blue">{{ linkedTodos.length }}</a-tag>
        </div>

        <a-spin :spinning="todosLoading">
          <ul v-if="linkedTodos.length" class="todo-list">
            <li v-for="item in linkedTodos" :key="item.id" class="todo-item">
              <div class="todo-main">
                <strong>{{ item.title }}</strong>
                <span v-if="item.dueAt" class="todo-meta">{{ formatDue(item) }}</span>
              </div>
              <a-space size="small" class="todo-actions">
                <a-button type="link" size="small" @click="emit('complete', item)">完成</a-button>
                <a-button
                  v-if="item.status === 0"
                  type="link"
                  size="small"
                  @click="emit('parallel', item)"
                >
                  开始
                </a-button>
              </a-space>
            </li>
          </ul>
          <EmptyState v-else title="暂无关联待办" preset="todo" compact />
        </a-spin>

        <a-space direction="vertical" style="width: 100%; margin-top: 10px">
          <a-button
            v-if="aiEnabled"
            block
            size="small"
            type="primary"
            ghost
            @click="emit('extract-todos')"
          >
            提取待办
          </a-button>
          <a-button block size="small" @click="emit('create-todo', 'action')">新建近期行动</a-button>
          <a-button v-if="linkedTodos.length" block size="small" type="link" @click="emit('view-all-todos')">
            在待办中心查看 →
          </a-button>
        </a-space>
      </section>

      <section class="context-section">
        <div class="section-head">
          <h4>相关文档</h4>
          <a-button type="link" size="small" :loading="relatedLoading" @click="emit('refresh-related')">
            刷新
          </a-button>
        </div>

        <a-spin :spinning="relatedLoading">
          <ul v-if="relatedNotes.length" class="related-list">
            <li
              v-for="item in relatedNotes"
              :key="item.id"
              class="related-item"
              @click="emit('open-note', item.id)"
            >
              <strong>{{ item.title || '未命名文档' }}</strong>
              <p>{{ preview(item) }}</p>
            </li>
          </ul>
          <EmptyState v-else title="暂无相关文档" preset="note" compact />
        </a-spin>
      </section>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { CloseOutlined } from '@ant-design/icons-vue';
import dayjs from 'dayjs';
import EmptyState from '../common/EmptyState.vue';
import type { Note } from '../../api/notes';
import { notePreviewText as getNotePreview } from '../../api/notes';
import type { TodoItem } from '../../api/todos';
import { isTodoOverdue } from '../../api/todos';

defineProps<{
  linkedTodos: TodoItem[];
  relatedNotes: Note[];
  aiEnabled?: boolean;
  todosLoading?: boolean;
  relatedLoading?: boolean;
}>();

const emit = defineEmits<{
  close: [];
  complete: [item: TodoItem];
  parallel: [item: TodoItem];
  'extract-todos': [];
  'create-todo': [horizon: 'action' | 'long_term'];
  'view-all-todos': [];
  'open-note': [id: string];
  'refresh-related': [];
}>();

const formatDue = (item: TodoItem) => {
  if (!item.dueAt) return '';
  const due = dayjs(item.dueAt);
  if (isTodoOverdue(item)) {
    return `逾期 ${due.format('MM-DD HH:mm')}`;
  }
  return `截止 ${due.format('MM-DD HH:mm')}`;
};

const preview = (note: Note) => {
  const text = getNotePreview(note);
  return text.length > 72 ? `${text.slice(0, 72)}…` : text;
};
</script>

<style scoped>
.note-context-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 14px 10px;
  border-bottom: 1px solid #eef2f7;
}

.panel-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #101828;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}

.icon-btn:hover {
  background: #f1f5f9;
  color: #334155;
}

.panel-body {
  flex: 1;
  overflow: auto;
  padding: 12px 14px 16px;
}

.context-section + .context-section {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid #eef2f7;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.section-head h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #344054;
}

.todo-list,
.related-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.todo-item {
  padding: 8px 10px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
}

.todo-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 4px;
}

.todo-main strong {
  font-size: 13px;
  color: #101828;
  line-height: 1.4;
}

.todo-meta {
  font-size: 11px;
  color: #667085;
}

.related-item {
  padding: 8px 10px;
  border-radius: 10px;
  background: #fafbff;
  border: 1px solid #eef2f7;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.related-item:hover {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.related-item strong {
  display: block;
  font-size: 13px;
  color: #101828;
  line-height: 1.4;
}

.related-item p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #667085;
  line-height: 1.45;
}
</style>
