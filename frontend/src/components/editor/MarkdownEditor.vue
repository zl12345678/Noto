<template>
  <div
    ref="editorRootRef"
    class="noto-markdown-editor"
    :class="{ 'is-compact': compact, 'noto-markdown-editor--fill': fillHeight }"
  >
    <MdEditor
      ref="mdEditorRef"
      :model-value="modelValue"
      :toolbars="toolbarItems"
      :toolbars-exclude="excludedToolbars"
      :preview="preview"
      language="zh-CN"
      :placeholder="placeholder"
      preview-theme="github"
      code-theme="atom"
      :show-code-row-number="true"
      :scroll-auto="true"
      :footers="footers"
      :style="editorStyle"
      @update:model-value="handleUpdate"
      @on-save="handleSave"
      @on-upload-img="handleUploadImg"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { EditorSelection } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import { MdEditor } from 'md-editor-v3';
import type { Footers, ToolbarNames } from 'md-editor-v3';
import { uploadAttachment } from '../../api/attachments';
import 'md-editor-v3/lib/style.css';

const props = withDefaults(
  defineProps<{
    modelValue: string;
    noteId?: string;
    height?: string;
    compact?: boolean;
    placeholder?: string;
    preview?: boolean;
    scrollToStart?: number;
    scrollToEnd?: number;
  }>(),
  {
    modelValue: '',
    noteId: undefined,
    height: '560px',
    compact: false,
    placeholder: '开始书写 Markdown，支持标题、列表、代码块、表格等…',
    preview: true,
    scrollToStart: undefined,
    scrollToEnd: undefined,
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: string];
  save: [value: string];
}>();

const fullToolbars: ToolbarNames[] = [
  'bold',
  'underline',
  'italic',
  'strikeThrough',
  '-',
  'title',
  'sub',
  'sup',
  'quote',
  'unorderedList',
  'orderedList',
  'task',
  '-',
  'codeRow',
  'code',
  'link',
  'image',
  'table',
  '-',
  'revoke',
  'next',
  'save',
  '-',
  'pageFullscreen',
  'fullscreen',
  'preview',
  'catalog',
];

const compactToolbars: ToolbarNames[] = [
  'bold',
  'italic',
  'underline',
  'title',
  'quote',
  'unorderedList',
  'orderedList',
  'task',
  'code',
  'link',
  'image',
  'table',
  '-',
  'revoke',
  'next',
  '-',
  'preview',
  'fullscreen',
];

const excludedToolbars: ToolbarNames[] = ['mermaid', 'katex', 'htmlPreview'];

const footers: Footers[] = ['markdownTotal', '=', 'scrollSwitch'];

const toolbarItems = computed(() => (props.compact ? compactToolbars : fullToolbars));
const fillHeight = computed(() => props.height === '100%');
const editorStyle = computed(() => (fillHeight.value ? undefined : { height: props.height }));
const editorRootRef = ref<HTMLElement | null>(null);
const mdEditorRef = ref<{ getEditorView?: () => EditorView | undefined } | null>(null);

function handleUpdate(value: string) {
  emit('update:modelValue', value);
}

function handleSave(value: string) {
  emit('save', value);
}

async function handleUploadImg(files: File[], callback: (urls: string[]) => void) {
  if (!props.noteId) {
    message.warning('请先保存文档后再上传图片');
    return;
  }
  if (!files.length) {
    return;
  }
  try {
    const urls: string[] = [];
    for (const file of files) {
      const result = await uploadAttachment(props.noteId, file);
      urls.push(result.fileUrl);
    }
    callback(urls);
    message.success(files.length > 1 ? `已上传 ${files.length} 张图片` : '图片上传成功');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '图片上传失败');
  }
}

function scrollToRange(start?: number, end?: number) {
  if (start == null || start < 0) return;

  const attempt = (triesLeft: number) => {
    const view = mdEditorRef.value?.getEditorView?.();
    if (!view) {
      if (triesLeft > 0) requestAnimationFrame(() => attempt(triesLeft - 1));
      return;
    }

    const docLen = view.state.doc.length;
    if (docLen === 0 && triesLeft > 0) {
      requestAnimationFrame(() => attempt(triesLeft - 1));
      return;
    }

    const safeStart = Math.min(Math.max(0, start), docLen);
    const safeEnd =
      end != null && end > safeStart
        ? Math.min(end, docLen)
        : Math.min(safeStart + 1, docLen);

    view.dispatch({
      selection: EditorSelection.create([EditorSelection.range(safeStart, safeEnd)]),
      effects: EditorView.scrollIntoView(safeStart, { y: 'center', x: 'nearest' }),
    });
  };

  nextTick(() => attempt(40));
}

watch(
  () => [props.scrollToStart, props.scrollToEnd, props.modelValue] as const,
  ([start, end]) => scrollToRange(start, end),
  { flush: 'post' },
);

function getSelectedText(): string {
  const selection = window.getSelection();
  if (!selection || selection.isCollapsed) return '';
  const editorRoot = document.querySelector('.noto-markdown-editor .cm-editor');
  if (!editorRoot || !selection.anchorNode || !editorRoot.contains(selection.anchorNode)) {
    return '';
  }
  return selection.toString().trim();
}

function insertSnippet(text: string) {
  const view = mdEditorRef.value?.getEditorView?.();
  if (!view) {
    const next = `${props.modelValue}${text}`;
    emit('update:modelValue', next);
    return;
  }
  const selection = view.state.selection.main;
  view.dispatch({
    changes: { from: selection.from, to: selection.to, insert: text },
    selection: EditorSelection.cursor(selection.from + text.length),
    effects: EditorView.scrollIntoView(selection.from + text.length, { y: 'nearest' }),
  });
  emit('update:modelValue', view.state.doc.toString());
}

defineExpose({ getSelectedText, scrollToRange, insertSnippet });
</script>

<style scoped>
.noto-markdown-editor {
  width: 100%;
}

.noto-markdown-editor--fill {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.noto-markdown-editor--fill :deep(.md-editor) {
  flex: 1;
  min-height: 0;
  height: auto !important;
}

.noto-markdown-editor :deep(.md-editor) {
  border-radius: 16px;
  border: 1px solid #e6edf7;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
  overflow: hidden;
}

.noto-markdown-editor :deep(.md-editor-toolbar-wrapper) {
  background: linear-gradient(180deg, #fafcff 0%, #f5f8fc 100%);
  border-bottom: 1px solid #eef2f7;
}

.noto-markdown-editor :deep(.md-editor-footer) {
  background: #fafcff;
  border-top: 1px solid #eef2f7;
  color: #667085;
}

.noto-markdown-editor.is-compact :deep(.md-editor-toolbar-wrapper) {
  padding: 4px 8px;
}

.noto-markdown-editor :deep(.cm-scroller),
.noto-markdown-editor :deep(.md-editor-input-wrapper) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 14px;
  line-height: 1.7;
}

.noto-markdown-editor :deep(.md-editor-preview-wrapper) {
  background: #f8fafc;
}

.noto-markdown-editor :deep(.cm-selectionBackground),
.noto-markdown-editor :deep(.cm-focused .cm-selectionBackground) {
  background: rgba(250, 204, 21, 0.35) !important;
}
</style>
