<template>
  <div
    ref="editorRootRef"
    class="noto-markdown-editor"
    :class="{ 'is-compact': compact, 'noto-markdown-editor--fill': fillHeight }"
  >
    <MdEditor
      ref="mdEditorRef"
      :model-value="modelValue"
      :theme="editorTheme"
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
import { storeToRefs } from 'pinia';
import { message } from 'ant-design-vue';
import { EditorSelection } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import { MdEditor } from 'md-editor-v3';
import type { Footers, ToolbarNames } from 'md-editor-v3';
import { uploadAttachment } from '../../api/attachments';
import { useThemeStore } from '../../store/theme';
import { parseMarkdownHeadings } from '../../utils/markdownOutline';
import 'md-editor-v3/lib/style.css';

const themeStore = useThemeStore();
const { resolved } = storeToRefs(themeStore);
const editorTheme = computed(() => (resolved.value === 'dark' ? 'dark' : 'light'));

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

function scrollToRange(
  start?: number,
  end?: number,
  options?: { clearSelection?: boolean },
) {
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
    const clearSelection = options?.clearSelection === true;
    const safeEnd =
      end != null && end > safeStart && !clearSelection
        ? Math.min(end, docLen)
        : safeStart;

    view.dispatch({
      selection: clearSelection
        ? EditorSelection.cursor(safeStart)
        : EditorSelection.create([EditorSelection.range(safeStart, safeEnd)]),
      effects: EditorView.scrollIntoView(safeStart, { y: 'start', x: 'nearest' }),
    });
  };

  nextTick(() => attempt(40));
}

let scrollRangeJob = 0;
watch(
  () => [props.scrollToStart, props.scrollToEnd] as const,
  ([start, end]) => {
    if (start == null) return;
    const job = ++scrollRangeJob;
    requestAnimationFrame(() => {
      if (job !== scrollRangeJob) return;
      scrollToRange(start, end);
    });
  },
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

/** 关闭分栏时 md-editor 常在 input-wrapper 上滚动，而非 cm scrollDOM */
function findEditorScrollTargets(view: EditorView): HTMLElement[] {
  const root = editorRootRef.value;
  const seen = new Set<HTMLElement>();
  const add = (el: HTMLElement | null | undefined) => {
    if (el && !seen.has(el)) seen.add(el);
  };
  add(view.scrollDOM);
  add(root?.querySelector('.cm-scroller') as HTMLElement | null);
  add(root?.querySelector('.md-editor-input-wrapper') as HTMLElement | null);
  add(root?.querySelector('.md-editor-content') as HTMLElement | null);
  return [...seen];
}

/** 单栏编辑时实际滚动的容器（多为 input-wrapper，而非 cm-scroller） */
function pickEditorScrollHost(view: EditorView): HTMLElement {
  const candidates = findEditorScrollTargets(view);
  let best = view.scrollDOM;
  let bestScroll = best.scrollHeight - best.clientHeight;
  for (const el of candidates) {
    const span = el.scrollHeight - el.clientHeight;
    if (span > bestScroll + 2) {
      best = el;
      bestScroll = span;
    }
  }
  return best;
}

/** 视口探测线 → 文档字符偏移（关闭分栏时依赖 posAtCoords / coordsAtPos，不依赖 scrollTop 层） */
function readEditorProbeOffset(view: EditorView): number {
  const scroller = pickEditorScrollHost(view);
  const box = scroller.getBoundingClientRect();
  const probeY = box.top + Math.min(96, Math.max(56, box.height * 0.18));
  const pos = view.posAtCoords({ x: box.left + 24, y: probeY });
  if (pos != null) return pos;

  const headings = parseMarkdownHeadings(props.modelValue);
  for (let i = headings.length - 1; i >= 0; i -= 1) {
    const start = headings[i].start;
    if (start > view.state.doc.length) continue;
    const coords = view.coordsAtPos(start);
    if (coords && coords.top <= probeY + 2) return start;
  }

  const probeYDoc =
    scroller.scrollTop + Math.min(96, Math.max(56, scroller.clientHeight * 0.18));
  return view.lineBlockAtHeight(probeYDoc).from;
}

/** 与大纲 spy 一致的探测线位置，用于阅读/编辑模式切换时保留锚点 */
function getScrollAnchorOffset(): number {
  const view = mdEditorRef.value?.getEditorView?.();
  if (!view) return 0;
  return readEditorProbeOffset(view);
}

function findPreviewScrollEl(): HTMLElement | null {
  const root = editorRootRef.value;
  if (!root) return null;
  return root.querySelector('.md-editor-preview-wrapper') as HTMLElement | null;
}

/** 分栏预览时用户可能滚右侧预览，用标题 DOM 反推 Markdown 字符偏移 */
function readPreviewProbeOffset(previewScroller: HTMLElement): number | null {
  const preview = previewScroller.querySelector('.md-editor-preview');
  if (!preview) return null;
  const headings = [...preview.querySelectorAll('h1, h2, h3, h4, h5, h6')] as HTMLElement[];
  if (!headings.length) return null;
  const rootRect = previewScroller.getBoundingClientRect();
  const probe = rootRect.top + Math.min(96, Math.max(56, previewScroller.clientHeight * 0.18));
  let activeEl: HTMLElement | null = null;
  for (let i = 0; i < headings.length; i += 1) {
    if (headings[i].getBoundingClientRect().top <= probe + 2) activeEl = headings[i];
    else break;
  }
  if (!activeEl) return null;
  const slug = activeEl.id || activeEl.getAttribute('id') || '';
  if (!slug) return null;
  const decoded = decodeURIComponent(slug);
  const norm = (s: string) => s.toLowerCase();
  const target = norm(decoded);
  const hit = parseMarkdownHeadings(props.modelValue).find(
    (h) =>
      h.slug === decoded ||
      h.slug === slug ||
      norm(h.slug) === target,
  );
  return hit?.start ?? null;
}

function collectScrollableElements(root: HTMLElement): HTMLElement[] {
  const out: HTMLElement[] = [];
  const walk = (el: Element) => {
    if (!(el instanceof HTMLElement)) return;
    if (el.scrollHeight > el.clientHeight + 2) out.push(el);
    el.children.forEach(walk);
  };
  walk(root);
  return out;
}

let outlineSpyListenersActive = false;

function isOutlineScrollSpyActive(): boolean {
  return outlineSpyListenersActive;
}

function attachOutlineScrollSpy(onOffset: (offset: number) => void): () => void {
  let disposed = false;
  let rafCm = 0;
  let rafPreview = 0;
  let disposeCm: (() => void) | null = null;
  let disposePreview: (() => void) | null = null;
  let retryTimer: ReturnType<typeof setTimeout> | null = null;
  outlineSpyListenersActive = false;

  const emitProbe = (preferPreview: boolean) => {
    const view = mdEditorRef.value?.getEditorView?.();
    if (preferPreview) {
      const previewEl = findPreviewScrollEl();
      if (previewEl) {
        const fromPreview = readPreviewProbeOffset(previewEl);
        if (fromPreview != null) {
          onOffset(fromPreview);
          return;
        }
      }
    }
    if (view) onOffset(readEditorProbeOffset(view));
  };

  const bindWhenReady = (triesLeft: number) => {
    if (disposed) return;
    const view = mdEditorRef.value?.getEditorView?.();
    if (!view) {
      if (triesLeft > 0) {
        retryTimer = setTimeout(() => bindWhenReady(triesLeft - 1), 50);
      }
      return;
    }

    const tickCm = () => {
      rafCm = 0;
      if (disposed) return;
      emitProbe(false);
    };
    const onScrollCm = () => {
      if (rafCm) return;
      rafCm = requestAnimationFrame(tickCm);
    };
    const mdRoot = editorRootRef.value?.querySelector('.md-editor') as HTMLElement | null;
    const scrollSet = new Set<HTMLElement>(findEditorScrollTargets(view));
    if (mdRoot) {
      collectScrollableElements(mdRoot).forEach((el) => scrollSet.add(el));
    }
    const scrollTargets = [...scrollSet];
    scrollTargets.forEach((el) => el.addEventListener('scroll', onScrollCm, { passive: true }));
    const rootEl = editorRootRef.value;
    if (rootEl) {
      rootEl.addEventListener('scroll', onScrollCm, { capture: true, passive: true });
    }
    outlineSpyListenersActive = scrollTargets.length > 0;
    disposeCm = () => {
      outlineSpyListenersActive = false;
      scrollTargets.forEach((el) => el.removeEventListener('scroll', onScrollCm));
      rootEl?.removeEventListener('scroll', onScrollCm, { capture: true });
    };
    tickCm();

    const bindPreviewSpy = () => {
      disposePreview?.();
      disposePreview = null;
      if (!props.preview) return;
      const previewEl = findPreviewScrollEl();
      if (!previewEl) return;
      const tickPreview = () => {
        rafPreview = 0;
        if (disposed) return;
        emitProbe(true);
      };
      const onScrollPreview = () => {
        if (rafPreview) return;
        rafPreview = requestAnimationFrame(tickPreview);
      };
      previewEl.addEventListener('scroll', onScrollPreview, { passive: true });
      disposePreview = () => previewEl.removeEventListener('scroll', onScrollPreview);
    };
    bindPreviewSpy();
  };

  void nextTick(() => requestAnimationFrame(() => bindWhenReady(80)));

  let pollTimer: ReturnType<typeof setInterval> | null = setInterval(() => {
    if (disposed) return;
    const view = mdEditorRef.value?.getEditorView?.();
    if (!view) return;
    emitProbe(!!props.preview && !!findPreviewScrollEl());
  }, 280);

  return () => {
    disposed = true;
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
    if (retryTimer) clearTimeout(retryTimer);
    disposeCm?.();
    disposeCm = null;
    disposePreview?.();
    disposePreview = null;
    if (rafCm) cancelAnimationFrame(rafCm);
    if (rafPreview) cancelAnimationFrame(rafPreview);
  };
}

defineExpose({
  getSelectedText,
  scrollToRange,
  insertSnippet,
  attachOutlineScrollSpy,
  getScrollAnchorOffset,
  isOutlineScrollSpyActive,
});
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
  border: 1px solid var(--noto-border, #e6edf7);
  box-shadow: var(--noto-shadow-soft, 0 10px 28px rgba(15, 23, 42, 0.05));
  overflow: hidden;
}

.noto-markdown-editor :deep(.md-editor-toolbar-wrapper) {
  background: linear-gradient(180deg, var(--noto-canvas-alt, #fafcff) 0%, var(--noto-canvas, #f5f8fc) 100%);
  border-bottom: 1px solid var(--noto-border, #eef2f7);
}

.noto-markdown-editor :deep(.md-editor-footer) {
  background: var(--noto-canvas-alt, #fafcff);
  border-top: 1px solid var(--noto-border, #eef2f7);
  color: var(--noto-text-muted, #667085);
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
  background: var(--noto-canvas, #f8fafc);
}

.noto-markdown-editor :deep(.cm-selectionBackground),
.noto-markdown-editor :deep(.cm-focused .cm-selectionBackground) {
  background: rgba(250, 204, 21, 0.35) !important;
}
</style>
