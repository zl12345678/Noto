<template>
  <aside
    ref="panelRef"
    class="note-outline-panel"
    :class="{ 'note-outline-panel--drawer': drawer }"
    tabindex="-1"
    aria-label="文档大纲"
    @keydown="onKeydown"
  >
    <div class="note-outline-head">
      <span class="note-outline-title">大纲</span>
      <a-button type="text" size="small" aria-label="关闭大纲" @click="emit('close')">×</a-button>
    </div>
    <div class="note-outline-controls">
      <span class="note-outline-label">显示至</span>
      <a-select
        v-model:value="maxLevelModel"
        size="small"
        :options="levelOptions"
        style="flex: 1; min-width: 0"
      />
    </div>
    <div v-if="items.length" class="note-outline-search">
      <a-input
        v-model:value="searchModel"
        size="small"
        allow-clear
        placeholder="搜索标题"
        aria-label="搜索大纲"
      />
    </div>
    <div v-if="!items.length" class="note-outline-empty">
      <p>用 <code># 标题</code> 生成大纲，例如 <code>## 章节</code></p>
      <a-button v-if="showInsertTemplate" type="primary" size="small" block @click="emit('insert-template')">
        插入标题模板
      </a-button>
    </div>
    <ul v-else ref="listRef" class="note-outline-list" role="listbox">
      <li
        v-for="(item, listIdx) in items"
        :key="`${item.index}-${item.start}`"
        class="note-outline-item"
        :class="[
          `note-outline-item--h${item.level}`,
          { 'is-active': item.index === activeHeadingIndex },
          { 'is-focused': listIdx === focusIndex },
        ]"
        :style="{ paddingLeft: `${8 + (item.level - 1) * 12}px` }"
        role="option"
        :aria-selected="item.index === activeHeadingIndex"
      >
        <button
          type="button"
          class="note-outline-link"
          @click="onSelect(item, $event)"
          @focus="focusIndex = listIdx"
        >
          {{ item.title || '（无标题）' }}
        </button>
      </li>
    </ul>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import type { MarkdownHeadingItem } from '../../utils/markdownOutline';

const props = withDefaults(
  defineProps<{
    items: MarkdownHeadingItem[];
    maxLevel: number;
    activeHeadingIndex?: number;
    searchKeyword?: string;
    drawer?: boolean;
    showInsertTemplate?: boolean;
  }>(),
  {
    activeHeadingIndex: -1,
    searchKeyword: '',
    drawer: false,
    showInsertTemplate: false,
  },
);

const emit = defineEmits<{
  close: [];
  select: [item: MarkdownHeadingItem];
  'update:maxLevel': [level: number];
  'update:searchKeyword': [q: string];
  'insert-template': [];
}>();

const panelRef = ref<HTMLElement | null>(null);
const listRef = ref<HTMLElement | null>(null);
const focusIndex = ref(0);

const levelOptions = [
  { label: 'H1 一级', value: 1 },
  { label: 'H2 二级', value: 2 },
  { label: 'H3 三级', value: 3 },
  { label: 'H4 四级', value: 4 },
  { label: 'H5 五级', value: 5 },
  { label: 'H6 六级', value: 6 },
];

const maxLevelModel = computed({
  get: () => props.maxLevel,
  set: (v: number) => emit('update:maxLevel', v),
});

const searchModel = computed({
  get: () => props.searchKeyword,
  set: (v: string) => emit('update:searchKeyword', v),
});

const onSelect = (item: MarkdownHeadingItem, event?: MouseEvent) => {
  emit('select', item);
  const btn = event?.currentTarget as HTMLButtonElement | undefined;
  btn?.blur();
};

const scrollActiveIntoView = () => {
  const idx = props.items.findIndex((i) => i.index === props.activeHeadingIndex);
  if (idx < 0 || !listRef.value) return;
  const el = listRef.value.children[idx] as HTMLElement | undefined;
  if (!el) return;
  const list = listRef.value;
  const elTop = el.offsetTop;
  const elBottom = elTop + el.offsetHeight;
  const viewTop = list.scrollTop;
  const viewBottom = viewTop + list.clientHeight;
  const margin = 8;
  if (elTop >= viewTop + margin && elBottom <= viewBottom - margin) return;
  const target =
    elTop < viewTop
      ? elTop - margin
      : elBottom - list.clientHeight + margin;
  list.scrollTo({ top: Math.max(0, target), behavior: 'smooth' });
};

const scrollListToActive = () => void nextTick(() => scrollActiveIntoView());

watch(
  () => props.items.length,
  () => {
    focusIndex.value = 0;
    scrollListToActive();
  },
);

watch(
  () => props.activeHeadingIndex,
  (active) => {
    if (active < 0) return;
    const idx = props.items.findIndex((i) => i.index === active);
    if (idx >= 0) focusIndex.value = idx;
    scrollListToActive();
    const activeBtn = listRef.value?.children[idx]?.querySelector('button');
    if (activeBtn && document.activeElement?.closest('.note-outline-panel') === panelRef.value) {
      activeBtn.focus({ preventScroll: true });
    }
  },
);

const onKeydown = (event: KeyboardEvent) => {
  if (!props.items.length) {
    if (event.key === 'Escape') emit('close');
    return;
  }
  if (event.key === 'Escape') {
    emit('close');
    return;
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault();
    focusIndex.value = Math.min(focusIndex.value + 1, props.items.length - 1);
    return;
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault();
    focusIndex.value = Math.max(focusIndex.value - 1, 0);
    return;
  }
  if (event.key === 'Enter') {
    event.preventDefault();
    const item = props.items[focusIndex.value];
    if (item) onSelect(item);
  }
};

defineExpose({
  focusPanel: () => panelRef.value?.focus(),
  scrollListToActive,
});
</script>

<style scoped>
.note-outline-panel {
  flex-shrink: 0;
  width: 220px;
  max-width: 36vw;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-left: 1px solid var(--noto-border-soft, #eef2f7);
  background: var(--noto-canvas-alt, #fafcff);
  outline: none;
}

.note-outline-panel--drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 45;
  max-width: min(88vw, 300px);
  width: min(88vw, 300px);
  box-shadow: -8px 0 24px rgba(15, 23, 42, 0.12);
}

.note-outline-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-bottom: 1px solid var(--noto-border-soft, #eef2f7);
}

.note-outline-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--noto-text, #101828);
}

.note-outline-controls,
.note-outline-search {
  padding: 8px 10px;
  border-bottom: 1px solid var(--noto-border-soft, #eef2f7);
}

.note-outline-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.note-outline-label {
  font-size: 12px;
  color: var(--noto-text-muted, #64748b);
  white-space: nowrap;
}

.note-outline-empty {
  padding: 16px 12px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--noto-text-muted, #94a3b8);
}

.note-outline-empty code {
  font-size: 11px;
  padding: 1px 4px;
  border-radius: 4px;
  background: rgba(8, 145, 178, 0.08);
}

.note-outline-list {
  list-style: none;
  margin: 0;
  padding: 8px 0 12px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.note-outline-item {
  margin: 0;
}

.note-outline-link {
  display: block;
  width: 100%;
  padding: 5px 10px 5px 0;
  border: none;
  background: transparent;
  text-align: left;
  font-size: 12px;
  line-height: 1.45;
  color: var(--noto-text-muted, #475569);
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.12s, color 0.12s;
}

.note-outline-link:hover,
.note-outline-item.is-focused .note-outline-link {
  background: rgba(8, 145, 178, 0.08);
  color: var(--noto-accent-deep, #0891b2);
}

.note-outline-item.is-active .note-outline-link {
  background: rgba(8, 145, 178, 0.14);
  color: var(--noto-accent-deep, #0891b2);
  font-weight: 600;
}

.note-outline-item--h1 .note-outline-link {
  font-weight: 600;
  color: var(--noto-text, #101828);
}
</style>