<template>
  <Teleport to="body">
    <div
      v-if="visible && selectedText"
      class="selection-toolbar"
      :style="toolbarStyle"
      @mousedown.prevent
    >
      <div class="toolbar-group">
        <button
          v-for="action in textActions"
          :key="action.mode"
          type="button"
          class="toolbar-btn"
          :disabled="!!loading"
          @click="emitTransform(action.mode)"
        >
          <a-spin v-if="loading === action.mode" size="small" />
          <span v-else>{{ action.label }}</span>
        </button>
        <button
          type="button"
          class="toolbar-btn toolbar-btn--accent"
          :disabled="!!loading"
          @click="emitTodo"
        >
          <a-spin v-if="loading === 'todo'" size="small" />
          <span v-else>转待办</span>
        </button>
      </div>
    </div>

    <div v-if="previewOpen" class="selection-preview-overlay" @mousedown.self="closePreview">
      <div class="selection-preview-panel">
        <header class="preview-head">
          <h4>{{ previewTitle }}</h4>
          <a-button type="text" size="small" @click="closePreview">关闭</a-button>
        </header>
        <div class="preview-body">
          <a-spin v-if="loading && !previewContent" />
          <pre v-else class="preview-text">{{ previewContent || '生成中…' }}</pre>
        </div>
        <footer class="preview-foot">
          <a-button @click="closePreview">取消</a-button>
          <a-button type="primary" :disabled="!previewContent.trim() || !!loading" @click="applyPreview">
            {{ previewMode === 'todo' ? '创建待办' : '替换选中' }}
          </a-button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { createTodo } from '../../api/todos';
import { transformSelectionStream, type SelectionTransformMode } from '../../api/ai';

const props = defineProps<{
  noteId?: string;
  workspaceId?: string | null;
  title?: string;
  content?: string;
  aiEnabled?: boolean;
  editorSelector?: string;
}>();

const emit = defineEmits<{
  replace: [payload: { original: string; replacement: string }];
  'todo-created': [];
}>();

const visible = ref(false);
const selectedText = ref('');
const position = ref({ top: 0, left: 0 });
const loading = ref<SelectionTransformMode | 'todo' | null>(null);
const previewOpen = ref(false);
const previewContent = ref('');
const previewMode = ref<SelectionTransformMode | 'todo' | null>(null);
const previewOriginal = ref('');

const editorSelector = computed(() => props.editorSelector || '.noto-markdown-editor .cm-editor');

const textActions: { mode: SelectionTransformMode; label: string }[] = [
  { mode: 'concise', label: '润色' },
  { mode: 'expand', label: '扩写' },
];

const toolbarStyle = computed(() => ({
  top: `${position.value.top}px`,
  left: `${position.value.left}px`,
}));

const previewTitle = computed(() => {
  if (previewMode.value === 'todo') return '转待办预览';
  if (previewMode.value === 'expand') return '扩写预览';
  return '润色预览';
});

const draftPayload = () => ({
  title: props.title || '',
  content: props.content || '',
});

const updateSelection = () => {
  if (!props.aiEnabled || !props.noteId) {
    visible.value = false;
    return;
  }

  const selection = window.getSelection();
  if (!selection || selection.isCollapsed || selection.rangeCount === 0) {
    visible.value = false;
    selectedText.value = '';
    return;
  }

  const editorRoot = document.querySelector(editorSelector.value);
  const anchor = selection.anchorNode;
  if (!editorRoot || !anchor || !editorRoot.contains(anchor)) {
    visible.value = false;
    selectedText.value = '';
    return;
  }

  const text = selection.toString().trim();
  if (!text || text.length < 2) {
    visible.value = false;
    selectedText.value = '';
    return;
  }

  const range = selection.getRangeAt(0);
  const rect = range.getBoundingClientRect();
  if (!rect.width && !rect.height) {
    visible.value = false;
    return;
  }

  selectedText.value = text;
  position.value = {
    top: Math.max(8, rect.top - 44),
    left: Math.min(window.innerWidth - 280, Math.max(8, rect.left + rect.width / 2 - 120)),
  };
  visible.value = true;
};

const hideToolbar = () => {
  if (previewOpen.value) return;
  visible.value = false;
};

const closePreview = () => {
  previewOpen.value = false;
  previewContent.value = '';
  previewMode.value = null;
  previewOriginal.value = '';
  loading.value = null;
};

const openPreview = (mode: SelectionTransformMode | 'todo', original: string) => {
  previewOpen.value = true;
  previewContent.value = '';
  previewMode.value = mode;
  previewOriginal.value = original;
  visible.value = false;
};

const emitTransform = async (mode: SelectionTransformMode) => {
  if (!props.noteId || !selectedText.value) return;
  const original = selectedText.value;
  openPreview(mode, original);
  loading.value = mode;
  try {
    await transformSelectionStream(
      props.noteId,
      { mode, selectedText: original, ...draftPayload() },
      {
        onToken: (token) => {
          previewContent.value += token;
        },
        onError: (errorMessage) => {
          message.error(errorMessage || '改写失败');
        },
      },
    );
    if (!previewContent.value.trim()) {
      message.warning('未生成内容');
      closePreview();
    }
  } catch (error: any) {
    message.error(error?.message || '改写失败');
    closePreview();
  } finally {
    loading.value = null;
  }
};

const emitTodo = async () => {
  if (!props.noteId || !selectedText.value) return;
  const original = selectedText.value;
  const title = original.split('\n')[0].trim().slice(0, 120);
  if (!title) {
    message.warning('选中内容太短');
    return;
  }
  openPreview('todo', original);
  previewContent.value = title;
  previewMode.value = 'todo';
};

const applyPreview = async () => {
  const content = previewContent.value.trim();
  const original = previewOriginal.value.trim();
  if (!content || !original) return;

  if (previewMode.value === 'todo') {
    if (!props.workspaceId) {
      message.warning('当前文档缺少知识库信息');
      return;
    }
    loading.value = 'todo';
    try {
      await createTodo({
        title: content,
        noteId: props.noteId,
        workspaceId: props.workspaceId,
        horizon: 'action',
      });
      message.success('待办已创建');
      emit('todo-created');
      closePreview();
    } catch (error: any) {
      message.error(error?.message || '创建待办失败');
    } finally {
      loading.value = null;
    }
    return;
  }

  emit('replace', { original, replacement: content });
  message.success('已替换选中段落');
  closePreview();
};

watch(
  () => props.noteId,
  () => {
    hideToolbar();
    closePreview();
  },
);

onMounted(() => {
  document.addEventListener('selectionchange', updateSelection);
  document.addEventListener('mouseup', updateSelection);
  document.addEventListener('keydown', hideToolbar);
  window.addEventListener('scroll', hideToolbar, true);
  window.addEventListener('resize', hideToolbar);
});

onUnmounted(() => {
  document.removeEventListener('selectionchange', updateSelection);
  document.removeEventListener('mouseup', updateSelection);
  document.removeEventListener('keydown', hideToolbar);
  window.removeEventListener('scroll', hideToolbar, true);
  window.removeEventListener('resize', hideToolbar);
});
</script>

<style scoped>
.selection-toolbar {
  position: fixed;
  z-index: 1500;
  transform: translateY(-4px);
  animation: toolbar-in 0.12s ease;
}

@keyframes toolbar-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(-4px);
  }
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border-radius: 10px;
  background: #101828;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.24);
}

.toolbar-btn {
  border: none;
  background: transparent;
  color: #f8fafc;
  font-size: 12px;
  padding: 6px 10px;
  border-radius: 8px;
  cursor: pointer;
  white-space: nowrap;
  min-width: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.toolbar-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.12);
}

.toolbar-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.toolbar-btn--accent {
  background: #6366f1;
}

.toolbar-btn--accent:hover:not(:disabled) {
  background: #4f46e5;
}

.selection-preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 1600;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.36);
}

.selection-preview-panel {
  width: min(520px, 100%);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.16);
  overflow: hidden;
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #eef2f7;
}

.preview-head h4 {
  margin: 0;
  font-size: 14px;
  color: #101828;
}

.preview-body {
  padding: 16px;
  min-height: 120px;
  max-height: 280px;
  overflow-y: auto;
}

.preview-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  color: #344054;
}

.preview-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #eef2f7;
}
</style>
