<template>
  <div ref="rootRef" class="safe-md-preview">
    <MdPreview
      :model-value="modelValue"
      :theme="previewTheme"
      :preview-theme="previewTheme === 'dark' ? 'github-dark' : 'github'"
      :code-theme="previewTheme === 'dark' ? 'atom-one-dark' : 'atom'"
      :show-code-row-number="true"
      language="zh-CN"
      :no-mermaid="true"
      :no-katex="true"
      @on-html-changed="scheduleBind"
      @on-remount="scheduleBind"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { MdPreview } from 'md-editor-v3';
import { useThemeStore } from '../../store/theme';
import {
  activeHeadingIndexAtOffset,
  parseMarkdownHeadings,
  slugifyHeadingTitle,
} from '../../utils/markdownOutline';
import 'md-editor-v3/lib/preview.css';

const props = withDefaults(
  defineProps<{
    modelValue: string;
    /** 大文档关闭标题折叠与 MutationObserver，减轻卡顿 */
    enableHeadingCollapse?: boolean;
  }>(),
  {
    enableHeadingCollapse: true,
  },
);

const emit = defineEmits<{
  'headings-ready': [count: number];
}>();

const themeStore = useThemeStore();
const { resolved } = storeToRefs(themeStore);
const previewTheme = computed(() => (resolved.value === 'dark' ? 'dark' : 'light'));

const rootRef = ref<HTMLElement | null>(null);
const COLLAPSED_CLASS = 'noto-heading-collapsed';
const TOGGLE_CLASS = 'noto-heading-toggle';
const HEADING_CLASS = 'noto-collapsible-heading';

const isHeading = (el: Element): el is HTMLElement => /^H[1-6]$/.test(el.tagName);

const headingLevel = (el: HTMLElement) => Number(el.tagName.slice(1));

const findPreviewRoot = (): HTMLElement | null => {
  const root = rootRef.value;
  if (!root) return null;
  return root.querySelector('.md-editor-preview') as HTMLElement | null;
};

const getHeadings = () => {
  const preview = findPreviewRoot();
  if (!preview) return [] as HTMLElement[];
  return [...preview.querySelectorAll('h1, h2, h3, h4, h5, h6')] as HTMLElement[];
};

const expandHeadingEl = (heading: HTMLElement) => {
  const preview = findPreviewRoot();
  if (!preview || !heading.classList.contains(COLLAPSED_CLASS)) return;
  toggleHeadingSection(heading, preview);
};

/** 按 Markdown 标题顺序（第 n 个 h1–h6）滚动到阅读视图对应位置 */
function scrollToHeadingIndex(index: number) {
  const headings = getHeadings();
  const el = headings[index];
  if (!el) return;
  expandHeadingEl(el);
  el.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function expandForHeadingIndex(index: number) {
  const el = getHeadings()[index];
  if (el) expandHeadingEl(el);
}

function findHeadingBySlug(slug: string): HTMLElement | undefined {
  if (!slug) return undefined;
  const decoded = decodeURIComponent(slug);
  const norm = (s: string) => s.toLowerCase();
  const target = norm(decoded);
  return getHeadings().find((h) => {
    const id = h.id || h.getAttribute('id') || '';
    const ds = h.dataset.notoHeadingSlug || '';
    return (
      id === decoded ||
      id === slug ||
      norm(id) === target ||
      ds === decoded ||
      norm(ds) === target
    );
  });
}

function scrollToHeadingSlug(slug: string) {
  const el = findHeadingBySlug(slug);
  if (!el) return false;
  expandHeadingEl(el);
  el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  return true;
}

/** 当前阅读位置：探测线以上最后一个标题（正文在标题下方时仍保持该标题高亮） */
function resolveActiveHeadingIndex(scrollRoot: HTMLElement): number {
  const headings = getHeadings();
  if (!headings.length) return -1;
  const rootRect = scrollRoot.getBoundingClientRect();
  const probe = rootRect.top + Math.min(96, Math.max(56, rootRect.height * 0.18));
  let activeDomIdx = -1;
  for (let i = 0; i < headings.length; i += 1) {
    const top = headings[i].getBoundingClientRect().top;
    if (top <= probe + 2) activeDomIdx = i;
    else break;
  }
  if (activeDomIdx < 0) return -1;
  const raw = headings[activeDomIdx]?.dataset.notoHeadingIndex;
  const parsed = raw != null ? Number(raw) : activeDomIdx;
  return Number.isFinite(parsed) ? parsed : activeDomIdx;
}

/** 阅读区顶部探测线对应的 Markdown 字符偏移（用于与编辑模式对齐） */
function getScrollAnchorOffset(scrollRoot: HTMLElement): number {
  const idx = resolveActiveHeadingIndex(scrollRoot);
  if (idx < 0) return 0;
  const parsed = parseMarkdownHeadings(props.modelValue);
  const item = parsed[idx];
  return item?.start ?? 0;
}

/** 将 scrollRoot 滚到与字符偏移大致对齐的位置 */
function scrollToAnchorOffset(scrollRoot: HTMLElement, offset: number): boolean {
  const parsed = parseMarkdownHeadings(props.modelValue);
  const headingIdx = activeHeadingIndexAtOffset(parsed, offset);
  if (headingIdx < 0) {
    if (offset <= 0) {
      scrollRoot.scrollTop = 0;
      return true;
    }
    return false;
  }
  const headings = getHeadings();
  const domIdx = headings.findIndex(
    (h) => Number(h.dataset.notoHeadingIndex) === headingIdx,
  );
  const el = domIdx >= 0 ? headings[domIdx] : headings[headingIdx];
  if (!el) return false;
  expandHeadingEl(el);
  const rootRect = scrollRoot.getBoundingClientRect();
  const elRect = el.getBoundingClientRect();
  const probe = Math.min(96, Math.max(56, scrollRoot.clientHeight * 0.18));
  scrollRoot.scrollTop = Math.max(0, scrollRoot.scrollTop + (elRect.top - rootRect.top - probe));
  return true;
}

function attachScrollSpy(
  scrollRoot: HTMLElement,
  onActive: (headingIndex: number) => void,
): () => void {
  let raf = 0;
  const tick = () => {
    raf = 0;
    const idx = resolveActiveHeadingIndex(scrollRoot);
    if (idx >= 0) onActive(idx);
  };
  const onScroll = () => {
    if (raf) return;
    raf = requestAnimationFrame(tick);
  };
  scrollRoot.addEventListener('scroll', onScroll, { passive: true });
  tick();
  return () => {
    scrollRoot.removeEventListener('scroll', onScroll);
    if (raf) cancelAnimationFrame(raf);
  };
}

const sectionEnd = (heading: HTMLElement, container: HTMLElement): Element | null => {
  const level = headingLevel(heading);
  let cursor: Element | null = heading.nextElementSibling;
  while (cursor && cursor !== container) {
    if (isHeading(cursor) && headingLevel(cursor) <= level) break;
    cursor = cursor.nextElementSibling;
  }
  return cursor;
};

const setToggleExpanded = (btn: HTMLElement, collapsed: boolean) => {
  btn.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
  btn.textContent = collapsed ? '▸' : '▾';
};

const toggleHeadingSection = (heading: HTMLElement, container: HTMLElement) => {
  const collapsed = heading.classList.toggle(COLLAPSED_CLASS);
  const btn = heading.querySelector(`.${TOGGLE_CLASS}`) as HTMLElement | null;
  if (btn) setToggleExpanded(btn, collapsed);

  let node: Element | null = heading.nextElementSibling;
  const end = sectionEnd(heading, container);
  while (node && node !== end) {
    if (node instanceof HTMLElement) {
      node.style.display = collapsed ? 'none' : '';
    }
    node = node.nextElementSibling;
  }
};

const onToggleClick = (event: Event) => {
  event.preventDefault();
  event.stopPropagation();
  const btn = event.currentTarget as HTMLElement;
  const heading = btn.closest('h1, h2, h3, h4, h5, h6') as HTMLElement | null;
  const preview = findPreviewRoot();
  if (!heading || !preview) return;
  toggleHeadingSection(heading, preview);
};

const stampHeadingIndices = () => {
  const preview = findPreviewRoot();
  if (!preview) return 0;
  const parsed = parseMarkdownHeadings(props.modelValue);
  preview.querySelectorAll('h1, h2, h3, h4, h5, h6').forEach((el, idx) => {
    const heading = el as HTMLElement;
    heading.dataset.notoHeadingIndex = String(idx);
    const meta = parsed[idx];
    if (meta) {
      heading.id = meta.slug;
      heading.dataset.notoHeadingSlug = meta.slug;
    } else {
      const text = heading.textContent?.replace(/^[\s▾▸]+/, '').trim() ?? '';
      const slug = slugifyHeadingTitle(text) || `section-${idx}`;
      heading.id = slug;
      heading.dataset.notoHeadingSlug = slug;
    }
  });
  return preview.querySelectorAll('h1, h2, h3, h4, h5, h6').length;
};

const decorateHeadings = () => {
  const preview = findPreviewRoot();
  if (!preview) return false;

  stampHeadingIndices();

  preview.querySelectorAll('h1, h2, h3, h4, h5, h6').forEach((el) => {
    const heading = el as HTMLElement;
    heading.classList.add(HEADING_CLASS);

    let btn = heading.querySelector(`.${TOGGLE_CLASS}`) as HTMLElement | null;
    if (!btn) {
      btn = document.createElement('button');
      btn.type = 'button';
      btn.className = TOGGLE_CLASS;
      btn.setAttribute('aria-label', '折叠或展开本节');
      btn.addEventListener('click', onToggleClick);
      heading.insertBefore(btn, heading.firstChild);
    }

    const collapsed = heading.classList.contains(COLLAPSED_CLASS);
    setToggleExpanded(btn, collapsed);
  });
  return true;
};

let bindTimer: ReturnType<typeof setTimeout> | null = null;
let observer: MutationObserver | null = null;

let lastHeadingsReadyCount = -1;

const schedulePreviewPrepare = () => {
  if (bindTimer) clearTimeout(bindTimer);
  bindTimer = setTimeout(() => {
    bindTimer = null;
    void nextTick(() => {
      if (props.enableHeadingCollapse) decorateHeadings();
      else stampHeadingIndices();
      const count = getHeadings().length;
      if (count !== lastHeadingsReadyCount) {
        lastHeadingsReadyCount = count;
        emit('headings-ready', count);
      }
    });
  }, 50);
};

const scheduleBind = () => schedulePreviewPrepare();

const stopObserver = () => {
  observer?.disconnect();
  observer = null;
};

const startObserver = () => {
  if (!props.enableHeadingCollapse) {
    stopObserver();
    return;
  }
  const root = rootRef.value;
  if (!root || observer) return;
  observer = new MutationObserver(() => scheduleBind());
  observer.observe(root, { childList: true, subtree: true });
};

watch(
  () => props.modelValue,
  () => {
    lastHeadingsReadyCount = -1;
    scheduleBind();
  },
  { immediate: true },
);

watch(
  () => props.enableHeadingCollapse,
  (enabled) => {
    if (!enabled) {
      if (bindTimer) clearTimeout(bindTimer);
      bindTimer = null;
      stopObserver();
      return;
    }
    startObserver();
    scheduleBind();
  },
);

watch(rootRef, (el) => {
  if (el && props.enableHeadingCollapse) {
    startObserver();
    scheduleBind();
  }
});

onBeforeUnmount(() => {
  if (bindTimer) clearTimeout(bindTimer);
  observer?.disconnect();
  observer = null;
  const root = rootRef.value;
  if (root) {
    root.querySelectorAll(`.${TOGGLE_CLASS}`).forEach((btn) => {
      btn.removeEventListener('click', onToggleClick);
    });
  }
});

defineExpose({
  scrollToHeadingIndex,
  expandForHeadingIndex,
  scrollToHeadingSlug,
  attachScrollSpy,
  getScrollAnchorOffset,
  scrollToAnchorOffset,
});
</script>

<style scoped>
.safe-md-preview {
  width: 100%;
  min-height: 0;
}

.safe-md-preview :deep(.md-editor) {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  height: auto !important;
  min-height: 0 !important;
}

.safe-md-preview :deep(.md-editor-preview-wrapper) {
  padding: 0;
  background: transparent;
  height: auto !important;
  max-height: none !important;
  overflow: visible !important;
}

.safe-md-preview :deep(.md-editor-preview) {
  color: var(--noto-text, #101828);
  overflow: visible !important;
  max-height: none !important;
}

.safe-md-preview :deep(.noto-collapsible-heading) {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.safe-md-preview :deep(.noto-heading-toggle) {
  flex-shrink: 0;
  margin: 0;
  padding: 0;
  width: 22px;
  height: 22px;
  line-height: 22px;
  border: none;
  border-radius: 6px;
  background: var(--noto-pastel-blue, #e0f2fe);
  color: var(--noto-accent-deep, #0891b2);
  font-size: 14px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s ease;
}

.safe-md-preview :deep(.noto-heading-toggle:hover) {
  background: var(--noto-accent-muted, #bae6fd);
}
</style>