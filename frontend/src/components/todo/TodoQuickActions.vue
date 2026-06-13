<template>
  <div class="todo-quick-actions" :class="[`size-${size}`]">
    <a-tooltip title="标记完成">
      <button type="button" class="action-btn action-btn--done" aria-label="完成" @click.stop="emit('complete', item)">
        <CheckOutlined />
      </button>
    </a-tooltip>
    <a-tooltip v-if="showReminder" title="设提醒">
      <button type="button" class="action-btn" aria-label="设提醒" @click.stop="emit('reminder', item)">
        <BellOutlined />
      </button>
    </a-tooltip>
    <a-dropdown v-if="menuItems.length" :trigger="['click']" placement="bottomRight">
      <button type="button" class="action-btn" aria-label="更多操作" @click.stop>
        <MoreOutlined />
      </button>
      <template #overlay>
        <a-menu :items="menuItems" @click="onMenuClick" />
      </template>
    </a-dropdown>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { BellOutlined, CheckOutlined, MoreOutlined } from '@ant-design/icons-vue';
import type { MenuProps } from 'ant-design-vue';
import { TODO_STATUS, isTodoOverdue, type TodoItem } from '../../api/todos';

const props = withDefaults(
  defineProps<{
    item: TodoItem;
    size?: 'mini' | 'normal';
    showParallel?: boolean;
    showPause?: boolean;
    showReminder?: boolean;
    showPostpone?: boolean;
    showOpenNote?: boolean;
    showBreakdown?: boolean;
  }>(),
  {
    size: 'normal',
    showParallel: false,
    showPause: false,
    showReminder: true,
    showPostpone: true,
    showOpenNote: true,
    showBreakdown: false,
  },
);

const emit = defineEmits<{
  complete: [item: TodoItem];
  parallel: [item: TodoItem];
  pause: [item: TodoItem];
  'open-note': [item: TodoItem];
  reminder: [item: TodoItem];
  postpone: [item: TodoItem];
  breakdown: [item: TodoItem];
}>();

const canPostpone = computed(() => {
  if (!props.showPostpone) return false;
  if (props.item.status === TODO_STATUS.COMPLETED || props.item.status === TODO_STATUS.CANCELLED) {
    return false;
  }
  return isTodoOverdue(props.item) || !!props.item.dueAt;
});

const menuItems = computed<MenuProps['items']>(() => {
  const items: NonNullable<MenuProps['items']> = [];
  if (props.showBreakdown) {
    items.push({ key: 'breakdown', label: 'AI 拆解' });
  }
  if (canPostpone.value) {
    items.push({ key: 'postpone', label: '推迟到明天' });
  }
  if (props.showParallel && props.item.status === TODO_STATUS.PENDING) {
    items.push({ key: 'parallel', label: '加入进行中' });
  }
  if (props.showPause && props.item.status === TODO_STATUS.IN_PROGRESS) {
    items.push({ key: 'pause', label: '暂停回队列' });
  }
  if (props.showOpenNote && props.item.noteId) {
    items.push({ key: 'note', label: '打开文档' });
  }
  return items;
});

const onMenuClick: MenuProps['onClick'] = ({ key }) => {
  if (key === 'breakdown') emit('breakdown', props.item);
  if (key === 'postpone') emit('postpone', props.item);
  if (key === 'parallel') emit('parallel', props.item);
  if (key === 'pause') emit('pause', props.item);
  if (key === 'note') emit('open-note', props.item);
};
</script>

<style scoped>
.todo-quick-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.size-mini .action-btn {
  width: 24px;
  height: 24px;
  font-size: 12px;
}

.action-btn:hover {
  background: rgba(22, 119, 255, 0.08);
  color: #1677ff;
}

.action-btn--done:hover {
  background: rgba(82, 196, 26, 0.12);
  color: #389e0d;
}
</style>
