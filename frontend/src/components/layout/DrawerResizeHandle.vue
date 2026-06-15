<template>
  <div
    class="drawer-resize-handle"
    title="拖拽调整抽屉宽度"
    @mousedown="onMouseDown"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue';

const emit = defineEmits<{
  resize: [delta: number];
}>();

const dragging = ref(false);
let startX = 0;

function onMouseDown(event: MouseEvent) {
  if (event.button !== 0) return;
  dragging.value = true;
  startX = event.clientX;
  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';
  document.addEventListener('mousemove', onMouseMove);
  document.addEventListener('mouseup', onMouseUp);
  event.preventDefault();
  event.stopPropagation();
}

function onMouseMove(event: MouseEvent) {
  if (!dragging.value) return;
  const delta = event.clientX - startX;
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
</script>

<style scoped>
.drawer-resize-handle {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 8px;
  z-index: 2;
  cursor: col-resize;
  transform: translateX(-50%);
}

.drawer-resize-handle:hover {
  background: var(--noto-resize-hover-bg);
}
</style>
