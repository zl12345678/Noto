<template>
  <div class="workflow-board" :class="[`variant-${variant}`, { 'is-loading': loading }]">
    <p v-if="showGuide" class="workflow-guide">
      按流程推进：<strong>写文档</strong> → <strong>提取待办</strong> → <strong>待办队列</strong> →
      <strong>进行中</strong> → <strong>完成</strong>；也可拖拽卡片跨列改变状态
    </p>

    <div class="workflow-track">
      <template v-for="(stage, index) in stages" :key="stage.key">
        <div
          class="workflow-stage"
          :class="[
            `stage-${stage.key}`,
            {
              'stage-empty': !stage.hasContent,
              'stage-active': stage.hasContent,
              'stage-drop-target': isDropHighlight(stage.key),
              'stage-drop-invalid': isDropInvalid(stage.key),
            },
          ]"
          :style="{ '--stage-accent': stage.accent }"
        >
          <button type="button" class="stage-header" @click="emit('stage-click', stage.key)">
            <span class="stage-icon" aria-hidden="true">{{ stage.icon }}</span>
            <div class="stage-titles">
              <h4>{{ stage.title }}</h4>
            </div>
            <span class="stage-count" :style="{ background: stage.accent }">{{ stage.count }}</span>
          </button>

          <div
            class="stage-body"
            :class="{ 'is-drop-zone': isStageDropTarget(stage.key) }"
            @dragover="onStageDragOver($event, stage.key)"
            @dragenter="onStageDragEnter($event, stage.key)"
            @dragleave="onStageDragLeave($event, stage.key)"
            @drop="onStageDrop($event, stage.key)"
          >
            <template v-if="stage.key === 'done'">
              <div class="done-ring" :class="{ 'done-ring--droppable': isDropHighlight('done') }">
                <span class="done-num">{{ todayCompleted }}</span>
                <span class="done-label">{{ isDropHighlight('done') ? '松手完成' : '今日完成' }}</span>
              </div>
            </template>

            <template v-else-if="stage.key === 'notes'">
              <ul v-if="recentNotes.length" class="mini-list mini-list--scrollable">
                <li
                  v-for="note in recentNotes"
                  :key="note.id"
                  class="mini-item clickable draggable-item"
                  :class="{ 'is-dragging': isDraggingNote(note.id) }"
                  draggable="true"
                  @click.stop="emit('open-note', note.id)"
                  @dragstart="onDragStartNote($event, note)"
                  @dragend="clearDragState"
                >
                  <span class="drag-grip" aria-hidden="true">⠿</span>
                  <span class="drag-label">{{ note.title }}</span>
                </li>
              </ul>
              <p v-else class="stage-hint">{{ stage.emptyHint }}</p>
              <a v-if="stage.key === 'notes'" class="stage-cta" @click.stop="emit('go-notes')">去写文档 →</a>
            </template>

            <template v-else>
              <ul v-if="stage.todos.length" class="mini-list mini-list--scrollable">
                <li
                  v-for="item in stage.todos"
                  :key="item.id"
                  class="mini-item todo-item draggable-item"
                  :class="{ 'is-dragging': isDraggingTodo(item.id) }"
                  draggable="true"
                  @dragstart="onDragStartTodo($event, item, stage.key)"
                  @dragend="clearDragState"
                  @click.stop
                >
                  <div class="mini-row">
                    <div class="mini-content">
                      <span class="drag-grip" aria-hidden="true">⠿</span>
                      <span class="mini-title" :title="item.title">{{ item.title }}</span>
                      <span v-if="item.dueAt" class="mini-due" :class="{ overdue: isTodoOverdue(item) }">
                        {{ formatDue(item) }}
                      </span>
                    </div>
                    <div class="mini-actions" @mousedown.stop @click.stop>
                      <TodoQuickActions
                        :item="item"
                        size="mini"
                        :show-parallel="stage.key === 'action'"
                        :show-pause="stage.key === 'parallel'"
                        :show-open-note="false"
                        :show-reminder="false"
                        @complete="emit('complete', $event)"
                        @parallel="emit('parallel', $event)"
                        @pause="emit('pause', $event)"
                      />
                    </div>
                  </div>
                </li>
              </ul>
              <p v-else class="stage-hint">{{ stage.emptyHint }}</p>
              <button
                v-if="stage.todos.length >= listCapHint"
                type="button"
                class="stage-more"
                @click.stop="emit('go-board')"
              >
                查看全部待办 →
              </button>
              <a v-if="stage.cta === '去写文档'" class="stage-cta" @click.stop="emit('go-notes')">去写文档 →</a>
              <a v-else-if="stage.cta === '加入队列'" class="stage-cta" @click.stop="emit('go-board')">加入队列 →</a>
            </template>
          </div>
        </div>

        <div
          v-if="index < stages.length - 1"
          class="workflow-connector"
          :class="{ 'workflow-connector--hero': variant === 'hero' }"
          aria-hidden="true"
        >
          <div class="connector-arrow" aria-hidden="true">
            <svg viewBox="0 0 56 24" class="connector-svg" xmlns="http://www.w3.org/2000/svg">
              <line class="connector-line" x1="6" y1="12" x2="38" y2="12" />
              <polygon class="connector-head" points="48,12 38,6 38,18" />
            </svg>
          </div>
          <span class="connector-label">{{ flowLabels[index] }}</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import dayjs from 'dayjs';
import type { Note } from '../../api/notes';
import { isTodoOverdue, type TodoItem } from '../../api/todos';
import { sortTodosByDueAt } from '../../utils/todoSort';
import {
  WORKFLOW_DRAG_MIME,
  canDropOnStage,
  isStageDropTarget,
  parseWorkflowDragPayload,
  type WorkflowDragPayload,
  type WorkflowDropEvent,
  type WorkflowStageKey,
} from '../../utils/workflowDrag';
import TodoQuickActions from './TodoQuickActions.vue';

export type { WorkflowStageKey };

const props = withDefaults(
  defineProps<{
    variant?: 'default' | 'hero';
    showGuide?: boolean;
    actionTodos?: TodoItem[];
    parallelTodos?: TodoItem[];
    recentNotes?: Note[];
    todayCompleted?: number;
    loading?: boolean;
    /** 待办列达到该数量时提示「查看全部」（与后端看板上限对齐） */
    listCapHint?: number;
  }>(),
  {
    variant: 'default',
    showGuide: true,
    actionTodos: () => [],
    parallelTodos: () => [],
    recentNotes: () => [],
    todayCompleted: 0,
    loading: false,
    listCapHint: 12,
  },
);

const emit = defineEmits<{
  complete: [item: TodoItem];
  parallel: [item: TodoItem];
  pause: [item: TodoItem];
  'open-note': [noteId: string];
  'go-notes': [];
  'go-board': [];
  'stage-click': [key: WorkflowStageKey];
  'workflow-drop': [event: WorkflowDropEvent];
}>();

const dragging = ref<WorkflowDragPayload | null>(null);
const dropTarget = ref<WorkflowStageKey | null>(null);
const dropInvalid = ref(false);

const flowLabels = ['提取', '排队', '开做', '完成'];

const isDropHighlight = (stageKey: WorkflowStageKey) =>
  dropTarget.value === stageKey && !dropInvalid.value;

const isDropInvalid = (stageKey: WorkflowStageKey) =>
  dropTarget.value === stageKey && dropInvalid.value;

const isDraggingNote = (noteId: string) =>
  dragging.value?.type === 'note' && dragging.value.note?.id === noteId;

const isDraggingTodo = (todoId: string) =>
  dragging.value?.type === 'todo' && dragging.value.todo?.id === todoId;

const clearDragState = () => {
  dragging.value = null;
  dropTarget.value = null;
  dropInvalid.value = false;
};

const setDragPayload = (event: DragEvent, payload: WorkflowDragPayload) => {
  dragging.value = payload;
  dropTarget.value = null;
  dropInvalid.value = false;
  event.dataTransfer!.effectAllowed = 'move';
  event.dataTransfer!.setData(WORKFLOW_DRAG_MIME, JSON.stringify(payload));
};

const onDragStartNote = (event: DragEvent, note: Note) => {
  setDragPayload(event, {
    type: 'note',
    fromStage: 'notes',
    note: { id: note.id, title: note.title, workspaceId: note.workspaceId },
  });
};

const onDragStartTodo = (event: DragEvent, item: TodoItem, fromStage: WorkflowStageKey) => {
  if (fromStage !== 'action' && fromStage !== 'parallel') return;
  setDragPayload(event, {
    type: 'todo',
    fromStage,
    todo: item,
  });
};

const resolveDragPayload = (event: DragEvent): WorkflowDragPayload | null => {
  if (dragging.value) return dragging.value;
  return parseWorkflowDragPayload(event.dataTransfer?.getData(WORKFLOW_DRAG_MIME) || '');
};

const updateDropTarget = (event: DragEvent, stageKey: WorkflowStageKey) => {
  event.preventDefault();
  const payload = resolveDragPayload(event);
  if (!payload || !isStageDropTarget(stageKey)) {
    dropTarget.value = stageKey;
    dropInvalid.value = true;
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'none';
    return;
  }
  const valid = canDropOnStage(payload.fromStage, stageKey, payload.type);
  dropTarget.value = stageKey;
  dropInvalid.value = !valid;
  if (event.dataTransfer) event.dataTransfer.dropEffect = valid ? 'move' : 'none';
};

const onStageDragOver = (event: DragEvent, stageKey: WorkflowStageKey) => {
  updateDropTarget(event, stageKey);
};

const onStageDragEnter = (event: DragEvent, stageKey: WorkflowStageKey) => {
  updateDropTarget(event, stageKey);
};

const onStageDragLeave = (event: DragEvent, stageKey: WorkflowStageKey) => {
  const related = event.relatedTarget as Node | null;
  const current = event.currentTarget as HTMLElement | null;
  if (current && related && current.contains(related)) return;
  if (dropTarget.value === stageKey) {
    dropTarget.value = null;
    dropInvalid.value = false;
  }
};

const onStageDrop = (event: DragEvent, stageKey: WorkflowStageKey) => {
  event.preventDefault();
  event.stopPropagation();
  const payload = resolveDragPayload(event);
  clearDragState();
  if (!payload || !canDropOnStage(payload.fromStage, stageKey, payload.type)) return;
  emit('workflow-drop', { ...payload, toStage: stageKey });
};

const formatDue = (item: TodoItem) => {
  if (!item.dueAt) return '';
  return isTodoOverdue(item)
    ? `已逾期 ${dayjs(item.dueAt).format('MM-DD HH:mm')}`
    : `截止 ${dayjs(item.dueAt).format('MM-DD HH:mm')}`;
};

const stages = computed(() => [
  {
    key: 'notes' as const,
    icon: '📄',
    title: '知识输入',
    accent: '#64748b',
    count: props.recentNotes.length,
    todos: [] as TodoItem[],
    hasContent: props.recentNotes.length > 0,
    emptyHint: '写文档',
    cta: '去写文档',
  },
  {
    key: 'action' as const,
    icon: '⚡',
    title: '待办队列',
    accent: '#1677ff',
    count: props.actionTodos.length,
    todos: sortTodosByDueAt(props.actionTodos),
    hasContent: props.actionTodos.length > 0,
    emptyHint: '提取或新建',
    cta: '加入队列',
  },
  {
    key: 'parallel' as const,
    icon: '🔀',
    title: '进行中',
    accent: '#0891b2',
    count: props.parallelTodos.length,
    todos: sortTodosByDueAt(props.parallelTodos),
    hasContent: props.parallelTodos.length > 0,
    emptyHint: '从队列加入',
    cta: '',
  },
  {
    key: 'done' as const,
    icon: '✓',
    title: '今日完成',
    accent: '#16a34a',
    count: props.todayCompleted,
    todos: [] as TodoItem[],
    hasContent: props.todayCompleted > 0,
    emptyHint: '完成后显示',
    cta: '',
  },
]);
</script>

<style scoped>
.workflow-board {
  width: 100%;
}

.workflow-guide {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
}

.workflow-guide strong {
  font-weight: 600;
}

.variant-hero .workflow-guide {
  color: var(--noto-text-muted, #64748b);
}

.variant-default .workflow-guide {
  color: #64748b;
}

.workflow-board.is-loading {
  opacity: 0.7;
  pointer-events: none;
}

.workflow-track {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 0;
  overflow-x: auto;
  overflow-y: visible;
  padding: 8px 6px 14px;
  scroll-snap-type: x proximity;
  -webkit-overflow-scrolling: touch;
}

.workflow-stage {
  flex: 0 0 auto;
  width: var(--stage-width, 200px);
  min-width: var(--stage-width, 200px);
  scroll-snap-align: start;
  min-height: 200px;
  padding: 14px;
  border-radius: 14px;
  border: 2px solid #e2e8f0;
  background: #fff;
  text-align: left;
  display: flex;
  flex-direction: column;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.15s;
  font: inherit;
  color: inherit;
}

.stage-header {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 10px;
  width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font: inherit;
  color: inherit;
}

.stage-header:hover .stage-titles h4 {
  color: var(--stage-accent, #1677ff);
}

.workflow-stage.stage-drop-target {
  border-color: var(--stage-accent, #1677ff);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--stage-accent, #1677ff) 22%, transparent);
}

.workflow-stage.stage-drop-invalid {
  border-color: #ffa39e;
  box-shadow: 0 0 0 3px rgba(255, 77, 79, 0.15);
}

.stage-body.is-drop-zone {
  min-height: 120px;
}

.variant-hero .workflow-track {
  --stage-width: 212px;
  padding: 8px 0 14px;
}

.variant-hero .workflow-stage {
  background: var(--noto-surface, #fff);
  border-color: var(--noto-border, #eaeaea);
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
  min-height: 210px;
  height: auto;
  padding: 14px 16px;
}

.workflow-stage {
  height: auto;
}

.workflow-stage.stage-active {
  border-color: color-mix(in srgb, var(--stage-accent) 45%, #e2e8f0);
}

.workflow-stage.stage-empty {
  border-style: dashed;
  background: #fafbfc;
}

.variant-hero .workflow-stage.stage-empty {
  background: #f9fafb;
}

.workflow-stage:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 28px color-mix(in srgb, var(--stage-accent) 18%, transparent);
}

.stage-icon {
  font-size: 20px;
  line-height: 1;
}

.stage-titles {
  flex: 1;
  min-width: 0;
}

.stage-titles h4 {
  margin: 0;
  font-size: 14px;
  color: #0f172a;
  line-height: 1.35;
  word-break: break-word;
}

.stage-count {
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.stage-body {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.mini-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mini-list--scrollable {
  max-height: min(320px, 42vh);
  overflow-y: auto;
  padding-right: 2px;
}

.mini-item {
  padding: 8px 10px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--stage-accent) 8%, #f8fafc);
  font-size: 12px;
  color: #334155;
  line-height: 1.45;
  word-break: break-word;
}

.draggable-item {
  cursor: grab;
  user-select: none;
}

.draggable-item:active {
  cursor: grabbing;
}

.draggable-item.is-dragging {
  opacity: 0.45;
}

.mini-item.clickable {
  cursor: grab;
}

.mini-item.clickable:hover {
  background: #eff6ff;
}

.drag-grip {
  display: inline-block;
  margin-right: 4px;
  color: #94a3b8;
  font-size: 11px;
  vertical-align: middle;
}

.drag-label {
  vertical-align: middle;
}

.mini-title {
  display: block;
  white-space: normal;
  word-break: break-word;
  line-height: 1.4;
}

.mini-due {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  color: #64748b;
}

.mini-due.overdue {
  color: #cf1322;
  font-weight: 500;
}

.mini-row {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
}

.mini-content {
  width: 100%;
}

.mini-row :deep(.todo-quick-actions) {
  align-self: flex-end;
}

.mini-actions {
  align-self: flex-end;
}

.stage-hint {
  margin: auto 0;
  text-align: center;
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.5;
}

.stage-cta {
  display: block;
  margin-top: 8px;
  font-size: 11px;
  color: var(--stage-accent, #1677ff);
  text-align: center;
  cursor: pointer;
}

.stage-more {
  display: block;
  width: 100%;
  margin-top: 6px;
  padding: 4px 0;
  border: none;
  background: transparent;
  font-size: 11px;
  color: var(--stage-accent, #1677ff);
  text-align: center;
  cursor: pointer;
}

.stage-more:hover {
  text-decoration: underline;
}

.stage-cta:hover {
  text-decoration: underline;
}

.done-ring {
  margin: auto;
  text-align: center;
}

.done-num {
  display: block;
  font-size: 32px;
  font-weight: 700;
  color: var(--stage-accent);
  line-height: 1.1;
}

.done-label {
  font-size: 11px;
  color: #64748b;
}

.done-ring--droppable {
  transform: scale(1.04);
}

.done-ring--droppable .done-num {
  color: #16a34a;
}

.workflow-connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 64px;
  min-width: 64px;
  max-width: 64px;
  padding: 4px 6px;
  align-self: center;
  pointer-events: none;
  color: #64748b;
}

.workflow-connector--hero {
  width: 48px;
  min-width: 48px;
  max-width: 48px;
  padding: 4px 2px;
  border-radius: 10px;
  background: #f1f5f9;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.06);
}

.connector-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 28px;
}

.connector-svg {
  display: block;
  width: 52px;
  height: 24px;
  flex-shrink: 0;
  overflow: visible;
}

.connector-line {
  stroke: currentColor;
  stroke-width: 2.5;
  stroke-linecap: round;
}

.connector-head {
  fill: currentColor;
  stroke: none;
}

.workflow-connector--hero {
  color: #94a3b8;
}

.workflow-connector--hero .connector-line {
  stroke: #94a3b8;
}

.workflow-connector--hero .connector-head {
  fill: #94a3b8;
}

.connector-label {
  font-size: 10px;
  margin-top: 4px;
  white-space: nowrap;
  letter-spacing: 0.02em;
  color: inherit;
  line-height: 1.2;
}

.workflow-connector--hero .connector-label {
  color: var(--noto-text-muted, #64748b);
  font-weight: 500;
}

/* 宽屏：Grid 固定箭头列，节点不再挤占箭头空间 */
@media (min-width: 1100px) {
  .variant-hero .workflow-track {
    overflow-x: visible;
    display: grid;
    grid-template-columns:
      minmax(0, 1fr) 48px minmax(0, 1fr) 48px minmax(0, 1fr) 48px minmax(0, 1fr);
    align-items: center;
    gap: 0;
    padding-left: 0;
    padding-right: 0;
  }

  .variant-hero .workflow-stage {
    width: auto;
    min-width: 0;
    align-self: stretch;
  }

  .variant-hero .workflow-connector {
    justify-self: center;
  }
}

@media (max-width: 900px) {
  .workflow-track {
    flex-direction: column;
    overflow-x: visible;
    gap: 0;
    padding: 8px 0 12px;
  }

  .variant-hero .workflow-track {
    display: flex;
    --stage-width: 100%;
  }

  .workflow-stage {
    width: 100%;
    min-width: 0;
    min-height: auto;
  }

  .workflow-connector,
  .workflow-connector--hero {
    width: 100%;
    min-width: 0;
    max-width: none;
    padding: 10px 0;
    margin: 2px 0;
  }

  .connector-arrow .connector-svg {
    transform: rotate(90deg);
  }

  .connector-label {
    margin-top: 6px;
  }
}
</style>
