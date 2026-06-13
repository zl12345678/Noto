<template>
  <div
    class="sidebar-resize-handle"
    :class="{ collapsed, dragging, inline }"
    :style="inline ? undefined : handleStyle"
    title="拖拽调整宽度，点击箭头收起/展开"
    @mousedown="onMouseDown"
    @click="onClick"
  >
    <span class="handle-line" />
    <span class="handle-arrow">
      <RightOutlined v-if="collapsed" />
      <LeftOutlined v-else />
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { LeftOutlined, RightOutlined } from '@ant-design/icons-vue';

const props = withDefaults(
  defineProps<{
    collapsed: boolean;
    offsetLeft?: number;
    collapsedLeft?: number;
    inline?: boolean;
  }>(),
  {
    inline: false,
  },
);

const emit = defineEmits<{
  toggle: [];
  resize: [delta: number];
}>();

const dragging = ref(false);
const moved = ref(false);
let startX = 0;

const handleStyle = computed(() => {
  if (props.collapsed) {
    return { left: `${props.collapsedLeft ?? 0}px` };
  }
  if (props.offsetLeft !== undefined) {
    return { left: `${props.offsetLeft}px` };
  }
  return undefined;
});

function onMouseDown(event: MouseEvent) {
  if (event.button !== 0) return;
  dragging.value = true;
  moved.value = false;
  startX = event.clientX;
  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';
  document.addEventListener('mousemove', onMouseMove);
  document.addEventListener('mouseup', onMouseUp);
  event.preventDefault();
}

function onMouseMove(event: MouseEvent) {
  if (!dragging.value) return;
  const delta = event.clientX - startX;
  if (Math.abs(delta) > 2) {
    moved.value = true;
  }
  startX = event.clientX;
  emit('resize', delta);
}

function onMouseUp() {
  dragging.value = false;
  document.body.style.cursor = '';
  document.body.style.userSelect = '';
  document.removeEventListener('mousemove', onMouseMove);
  document.removeEventListener('mouseup', onMouseUp);
}

function onClick(event: MouseEvent) {
  if (moved.value) {
    event.preventDefault();
    event.stopPropagation();
    moved.value = false;
    return;
  }
  emit('toggle');
}
</script>

<style scoped>
.sidebar-resize-handle {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 10px;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: col-resize;
  transform: translateX(-50%);
  transition: background-color 0.15s ease;
}

.sidebar-resize-handle.inline {
  position: relative;
  top: auto;
  bottom: auto;
  transform: none;
  width: 14px;
  height: 100%;
  flex-shrink: 0;
  z-index: 2;
  border-right: 1px solid #eef2f7;
  background: #fbfcfe;
}

.sidebar-resize-handle.collapsed.inline {
  width: 16px;
  background: #f8fafc;
  box-shadow: 2px 0 8px rgba(15, 23, 42, 0.04);
}

.sidebar-resize-handle:hover,
.sidebar-resize-handle.dragging {
  background: rgba(22, 119, 255, 0.06);
}

.handle-line {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 1px;
  transform: translateX(-50%);
  background: #e5e7eb;
  pointer-events: none;
}

.sidebar-resize-handle:hover .handle-line,
.sidebar-resize-handle.dragging .handle-line {
  background: #1677ff;
  width: 2px;
}

.handle-arrow {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 28px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
  color: #667085;
  font-size: 10px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
  pointer-events: none;
}

.sidebar-resize-handle:hover .handle-arrow {
  color: #1677ff;
  border-color: #bfdbfe;
}
</style>
