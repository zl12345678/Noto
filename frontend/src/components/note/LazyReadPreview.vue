<template>
  <div ref="rootRef" class="lazy-read-preview">
    <template v-for="chunk in chunks" :key="chunk.key">
      <div
        v-if="visibleKeys.has(chunk.key)"
        class="lazy-read-chunk"
        :data-chunk-key="chunk.key"
      >
        <SafeMdPreview
          :ref="(el) => setChunkRef(chunk.key, el)"
          :model-value="chunk.markdown"
          :enable-heading-collapse="enableHeadingCollapse"
          @headings-ready="onChunkHeadingsReady"
        />
      </div>
      <div v-else class="lazy-read-placeholder" :data-chunk-key="chunk.key">
        <span>加载中…</span>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import SafeMdPreview from '../common/SafeMdPreview.vue';
import {
  activeHeadingIndexAtOffset,
  parseMarkdownHeadings,
  splitMarkdownByTopHeadings,
} from '../../utils/markdownOutline';
const props = withDefaults(
  defineProps<{
    modelValue: string;
    enableHeadingCollapse?: boolean;
  }>(),
  { enableHeadingCollapse: true },
);

const emit = defineEmits<{
  'headings-ready': [count: number];
}>();

const onChunkHeadingsReady = () => {
  const total = rootRef.value?.querySelectorAll('h1, h2, h3, h4, h5, h6').length ?? 0;
  emit('headings-ready', total);
};

const LAZY_CHARS = 100_000;

const useLazy = computed(() => props.modelValue.length >= LAZY_CHARS);

const chunks = computed(() => {
  if (!useLazy.value) {
    return [{ key: '0', markdown: props.modelValue, firstHeadingIndex: -1 }];
  }
  return splitMarkdownByTopHeadings(props.modelValue);
});

const visibleKeys = ref(new Set<string>());
const chunkRefs = new Map<string, InstanceType<typeof SafeMdPreview> | null>();
const rootRef = ref<HTMLElement | null>(null);
let io: IntersectionObserver | null = null;

const countHeadingsInMarkdown = (md: string) => md.match(/^(#{1,6})\s+/gm)?.length ?? 0;

const setChunkRef = (key: string, el: unknown) => {
  if (el && typeof el === 'object' && 'scrollToHeadingIndex' in el) {
    chunkRefs.set(key, el as InstanceType<typeof SafeMdPreview>);
  } else {
    chunkRefs.delete(key);
  }
};

const ensureInitialVisible = () => {
  const keys = chunks.value.map((c) => c.key);
  const next = new Set<string>();
  keys.slice(0, 2).forEach((k) => next.add(k));
  if (keys.length > 2) next.add(keys[keys.length - 1]);
  visibleKeys.value = next;
};

const setupObserver = () => {
  io?.disconnect();
  if (!useLazy.value || !rootRef.value) return;
  io = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        const key = (entry.target as HTMLElement).dataset.chunkKey;
        if (key) visibleKeys.value = new Set([...visibleKeys.value, key]);
      });
    },
    { root: null, rootMargin: '400px 0px', threshold: 0 },
  );
  rootRef.value.querySelectorAll('[data-chunk-key]').forEach((el) => io?.observe(el));
};

const resolveChunkForGlobalIndex = (index: number) => {
  let acc = 0;
  for (const chunk of chunks.value) {
    const n = countHeadingsInMarkdown(chunk.markdown);
    if (index < acc + n) {
      return { chunk, localIndex: index - acc };
    }
    acc += n;
  }
  return null;
};

function scrollToHeadingIndex(index: number) {
  if (!useLazy.value) {
    chunkRefs.get('0')?.scrollToHeadingIndex?.(index);
    return;
  }
  const hit = resolveChunkForGlobalIndex(index);
  if (!hit) return;
  visibleKeys.value = new Set([...visibleKeys.value, hit.chunk.key]);
  void nextTick(() => {
    chunkRefs.get(hit.chunk.key)?.scrollToHeadingIndex?.(hit.localIndex);
  });
}

function expandForHeadingIndex(index: number) {
  if (!useLazy.value) {
    chunkRefs.get('0')?.expandForHeadingIndex?.(index);
    return;
  }
  const hit = resolveChunkForGlobalIndex(index);
  if (!hit) return;
  visibleKeys.value = new Set([...visibleKeys.value, hit.chunk.key]);
  void nextTick(() => {
    chunkRefs.get(hit.chunk.key)?.expandForHeadingIndex?.(hit.localIndex);
  });
}

function scrollToHeadingSlug(slug: string): boolean {
  if (!useLazy.value) {
    return chunkRefs.get('0')?.scrollToHeadingSlug?.(slug) ?? false;
  }
  for (const chunk of chunks.value) {
    visibleKeys.value = new Set([...visibleKeys.value, chunk.key]);
    const preview = chunkRefs.get(chunk.key);
    if (preview?.scrollToHeadingSlug?.(slug)) return true;
  }
  return false;
}

function getScrollAnchorOffset(scrollRoot: HTMLElement): number {
  if (!useLazy.value) {
    return chunkRefs.get('0')?.getScrollAnchorOffset?.(scrollRoot) ?? 0;
  }
  const rootRect = scrollRoot.getBoundingClientRect();
  const probe = rootRect.top + Math.min(96, Math.max(56, rootRect.height * 0.18));
  const headings = [
    ...scrollRoot.querySelectorAll('h1, h2, h3, h4, h5, h6'),
  ] as HTMLElement[];
  let activeEl: HTMLElement | null = null;
  for (let i = 0; i < headings.length; i += 1) {
    if (headings[i].getBoundingClientRect().top <= probe + 2) activeEl = headings[i];
    else break;
  }
  if (!activeEl) return 0;
  const chunkLocal = Number(activeEl.dataset.notoHeadingIndex ?? 0);
  const chunkKey = activeEl.closest('[data-chunk-key]')?.dataset.chunkKey;
  const chunk = chunkKey ? chunks.value.find((c) => c.key === chunkKey) : undefined;
  const globalIdx =
    chunk && chunk.firstHeadingIndex >= 0
      ? chunk.firstHeadingIndex + chunkLocal
      : chunkLocal;
  const parsed = parseMarkdownHeadings(props.modelValue);
  return parsed[globalIdx]?.start ?? 0;
}

function scrollToAnchorOffset(scrollRoot: HTMLElement, offset: number): boolean {
  if (!useLazy.value) {
    return chunkRefs.get('0')?.scrollToAnchorOffset?.(scrollRoot, offset) ?? false;
  }
  const parsed = parseMarkdownHeadings(props.modelValue);
  const headingIdx = activeHeadingIndexAtOffset(parsed, offset);
  if (headingIdx < 0) {
    if (offset <= 0) {
      scrollRoot.scrollTop = 0;
      return true;
    }
    return false;
  }
  scrollToHeadingIndex(headingIdx);
  return true;
}

function attachScrollSpy(container: HTMLElement, onActive: (headingIndex: number) => void) {
  if (!useLazy.value) {
    return chunkRefs.get('0')?.attachScrollSpy?.(container, onActive) ?? (() => {});
  }

  let raf = 0;
  const tick = () => {
    raf = 0;
    const rootRect = container.getBoundingClientRect();
    const probe = rootRect.top + Math.min(96, Math.max(56, rootRect.height * 0.18));
    const headings = [
      ...container.querySelectorAll('h1, h2, h3, h4, h5, h6'),
    ] as HTMLElement[];
    if (!headings.length) return;
    let activeEl: HTMLElement | null = null;
    for (let i = 0; i < headings.length; i += 1) {
      if (headings[i].getBoundingClientRect().top <= probe + 2) activeEl = headings[i];
      else break;
    }
    if (!activeEl) return;
    const chunkLocal = Number(activeEl.dataset.notoHeadingIndex ?? 0);
    const chunkKey = activeEl.closest('[data-chunk-key]')?.dataset.chunkKey;
    const chunk = chunkKey ? chunks.value.find((c) => c.key === chunkKey) : undefined;
    const globalIdx =
      chunk && chunk.firstHeadingIndex >= 0
        ? chunk.firstHeadingIndex + chunkLocal
        : chunkLocal;
    onActive(globalIdx);
  };
  const onScroll = () => {
    if (raf) return;
    raf = requestAnimationFrame(tick);
  };
  container.addEventListener('scroll', onScroll, { passive: true });
  tick();
  return () => {
    container.removeEventListener('scroll', onScroll);
    if (raf) cancelAnimationFrame(raf);
  };
}

watch(
  () => props.modelValue,
  () => {
    ensureInitialVisible();
    setTimeout(() => setupObserver(), 100);
  },
  { immediate: true },
);

onMounted(() => setupObserver());

onBeforeUnmount(() => {
  io?.disconnect();
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
.lazy-read-preview {
  width: 100%;
}

.lazy-read-placeholder {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--noto-text-muted, #94a3b8);
  font-size: 12px;
  background: var(--noto-canvas-alt, #fafcff);
  border-radius: 8px;
  margin: 8px 0;
}
</style>