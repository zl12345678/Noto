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
  border-right: 1px solid var(--noto-resize-rail-border);
  background: var(--noto-resize-rail-bg);
}

.sidebar-resize-handle.collapsed.inline {
  width: 16px;
  background: var(--noto-resize-rail-collapsed-bg);
  box-shadow: var(--noto-shadow-soft);
}

.sidebar-resize-handle:hover,
.sidebar-resize-handle.dragging {
  background: var(--noto-resize-hover-bg);
}

.sidebar-resize-handle.inline:hover,
.sidebar-resize-handle.inline.dragging {
  background: var(--noto-resize-hover-bg);
}

.handle-line {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 1px;
  transform: translateX(-50%);
  background: var(--noto-resize-line);
  pointer-events: none;
}

.sidebar-resize-handle:hover .handle-line,
.sidebar-resize-handle.dragging .handle-line {
  background: var(--noto-resize-active-line);
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
  background: var(--noto-resize-arrow-bg);
  border: 1px solid var(--noto-resize-arrow-border);
  color: var(--noto-text-muted);
  font-size: 10px;
  box-shadow: var(--noto-shadow-soft);
  pointer-events: none;
}

.sidebar-resize-handle:hover .handle-arrow {
  color: var(--noto-accent-deep);
  border-color: var(--noto-accent);
}
</style>
