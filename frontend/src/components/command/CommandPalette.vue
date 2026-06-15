<template>
  <Teleport to="body">
    <div v-if="open" ref="overlayRef" class="cmdk-overlay" tabindex="-1" @mousedown.self="close" @keydown="onOverlayKeydown">
      <div
        class="cmdk-panel"
        role="dialog"
        aria-modal="true"
        aria-label="命令面板"
        aria-describedby="cmdk-help"
      >
        <div class="cmdk-input-wrap">
          <SearchOutlined class="cmdk-input-icon" />
          <input
            ref="inputRef"
            v-model="query"
            class="cmdk-input"
            type="text"
            placeholder="搜索、问 AI、办事…"
            autocomplete="off"
            @keydown="onInputKeydown"
          />
          <kbd class="cmdk-kbd">Esc</kbd>
        </div>

        <div v-if="loading" class="cmdk-loading">
          <a-spin size="small" />
          <span>搜索中…</span>
        </div>

        <ul v-else-if="visibleItems.length" class="cmdk-list">
          <li
            v-for="(item, index) in visibleItems"
            :key="item.id"
            class="cmdk-item"
            :class="{ active: index === activeIndex }"
            @mouseenter="activeIndex = index"
            @click="runItem(item)"
          >
            <component :is="item.icon" v-if="item.icon" class="cmdk-item-icon" />
            <span v-else class="cmdk-item-dot" :class="item.dotClass" />
            <div class="cmdk-item-body">
              <span class="cmdk-item-label">{{ item.label }}</span>
              <span v-if="item.hint" class="cmdk-item-hint">{{ item.hint }}</span>
            </div>
            <span v-if="item.badge" class="cmdk-item-badge">{{ item.badge }}</span>
          </li>
        </ul>

        <div v-else class="cmdk-empty">没有匹配项，试试换个关键词</div>

        <div id="cmdk-help" class="cmdk-footer">
          <span><kbd>↑↓</kbd> 选择</span>
          <span><kbd>Enter</kbd> 确认</span>
          <span><kbd>Esc</kbd> 关闭</span>
          <span><kbd>?</kbd> 快捷键</span>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch, type Component } from 'vue';
import { useRouter } from 'vue-router';
import {
  BulbOutlined,
  CheckSquareOutlined,
  FileTextOutlined,
  HomeOutlined,
  RobotOutlined,
  SearchOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue';
import { searchNotes, type SearchResult } from '../../api/search';
import type { Workspace } from '../../api/workspaces';
import { buildNoteRouteQuery } from '../../utils/noteNavigation';
import { buildSearchCacheKey, getSearchCache, setSearchCache } from '../../utils/searchCache';

type CommandItem = {
  id: string;
  label: string;
  hint?: string;
  badge?: string;
  icon?: Component;
  dotClass?: string;
  run: () => void;
};

const props = defineProps<{
  workspaces?: Workspace[];
  defaultWorkspaceId?: string;
}>();

const open = defineModel<boolean>('open', { default: false });

const router = useRouter();
const query = ref('');
const activeIndex = ref(0);
const loading = ref(false);
const noteResults = ref<SearchResult[]>([]);
const inputRef = ref<HTMLInputElement | null>(null);
const overlayRef = ref<HTMLDivElement | null>(null);

let searchTimer: ReturnType<typeof setTimeout> | null = null;
let searchSeq = 0;

const close = () => {
  open.value = false;
};

const resetState = () => {
  query.value = '';
  activeIndex.value = 0;
  noteResults.value = [];
  loading.value = false;
};

watch(open, async (visible) => {
  if (visible) {
    resetState();
    await nextTick();
    inputRef.value?.focus();
    inputRef.value?.select();
    overlayRef.value?.focus();
  } else if (searchTimer) {
    clearTimeout(searchTimer);
    searchTimer = null;
  }
});

watch(query, (value) => {
  activeIndex.value = 0;
  if (searchTimer) clearTimeout(searchTimer);
  const keyword = value.trim();
  if (!keyword) {
    noteResults.value = [];
    loading.value = false;
    return;
  }
  searchTimer = setTimeout(() => {
    void fetchNotes(keyword);
  }, 220);
});

const fetchNotes = async (keyword: string) => {
  const seq = ++searchSeq;
  const cacheKey = buildSearchCacheKey({ keyword, page: 1, size: 8 });
  const cached = getSearchCache(cacheKey);
  if (cached) {
    if (seq !== searchSeq) return;
    noteResults.value = cached.records || [];
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    const page = await searchNotes({ keyword, page: 1, size: 8 });
    if (seq !== searchSeq) return;
    setSearchCache(cacheKey, page);
    noteResults.value = page.records || [];
  } catch {
    if (seq !== searchSeq) return;
    noteResults.value = [];
  } finally {
    if (seq === searchSeq) loading.value = false;
  }
};

const navItems = computed<CommandItem[]>(() => {
  const items: CommandItem[] = [
    {
      id: 'nav-dashboard',
      label: '首页',
      hint: '行动看板与今日建议',
      icon: HomeOutlined,
      run: () => router.push('/'),
    },
    {
      id: 'nav-search',
      label: '搜索',
      hint: '全文检索文档',
      icon: SearchOutlined,
      run: () => router.push('/search'),
    },
    {
      id: 'nav-todos',
      label: '待办中心',
      hint: '看板与行动清单',
      icon: CheckSquareOutlined,
      run: () => router.push({ path: '/todos', query: { view: 'board' } }),
    },
    {
      id: 'nav-reminders',
      label: '提醒',
      hint: '定时提醒列表',
      icon: BulbOutlined,
      run: () => router.push('/reminders'),
    },
    {
      id: 'nav-import',
      label: '导入文档',
      hint: '粘贴网页/Markdown 入库',
      icon: FileTextOutlined,
      run: () =>
        router.push({
          path: '/notes',
          query: {
            import: '1',
            ...(props.defaultWorkspaceId ? { workspace: props.defaultWorkspaceId } : {}),
          },
        }),
    },
    {
      id: 'nav-ai',
      label: 'AI 助手',
      hint: '问答与一键办事',
      icon: RobotOutlined,
      run: () => router.push('/ai'),
    },
  ];

  for (const ws of props.workspaces || []) {
    items.push({
      id: `ws-${ws.id}`,
      label: ws.name,
      hint: '打开知识库',
      dotClass: 'blue',
      run: () => router.push({ path: '/notes', query: { workspace: String(ws.id) } }),
    });
  }
  return items;
});

const actionItems = computed<CommandItem[]>(() => {
  const keyword = query.value.trim();
  if (!keyword) return [];

  const workspaceId =
    props.defaultWorkspaceId
    || localStorage.getItem('noto-primary-workspace-id')
    || props.workspaces?.[0]?.id;
  const wsQuery = workspaceId ? { workspace: String(workspaceId) } : {};

  return [
    {
      id: 'action-synthesize',
      label: '跨文档合成',
      hint: '聚合多篇笔记生成周报/决策记录',
      icon: FileTextOutlined,
      badge: '合成',
      run: () => router.push({ path: '/ai', query: { synthesize: '1', ...wsQuery } }),
    },
    {
      id: 'action-search',
      label: `搜索文档「${keyword}」`,
      hint: '全文检索',
      icon: SearchOutlined,
      badge: '搜索',
      run: () => router.push({ path: '/search', query: { q: keyword } }),
    },
    {
      id: 'action-ask',
      label: `问 AI「${keyword}」`,
      hint: '自动查知识库回答',
      icon: RobotOutlined,
      badge: '问答',
      run: () =>
        router.push({
          path: '/ai',
          query: { q: keyword, ...wsQuery },
        }),
    },
    {
      id: 'action-agent',
      label: `办事「${keyword}」`,
      hint: '创建待办、提醒或文档',
      icon: ThunderboltOutlined,
      badge: '办事',
      run: () =>
        router.push({
          path: '/ai',
          query: { q: keyword, intent: 'agent', ...wsQuery },
        }),
    },
  ];
});

const noteItems = computed<CommandItem[]>(() =>
  noteResults.value.map((item) => ({
    id: `note-${item.noteId}`,
    label: item.title || '未命名文档',
    hint: item.snippet || item.workspaceName || undefined,
    icon: FileTextOutlined,
    badge: '文档',
    run: () =>
      router.push({
        path: `/notes/${item.noteId}`,
        query: buildNoteRouteQuery({
          from: 'search',
          workspaceId: item.workspaceId,
          keyword: query.value.trim(),
          offsetStart: item.offsetStart,
          offsetEnd: item.offsetEnd,
        }),
      }),
  })),
);

const visibleItems = computed<CommandItem[]>(() => {
  const keyword = query.value.trim().toLowerCase();
  if (!keyword) return navItems.value;

  const filteredNav = navItems.value.filter(
    (item) =>
      item.label.toLowerCase().includes(keyword) ||
      item.hint?.toLowerCase().includes(keyword),
  );

  return [...actionItems.value, ...noteItems.value, ...filteredNav];
});

watch(visibleItems, () => {
  if (activeIndex.value >= visibleItems.value.length) {
    activeIndex.value = Math.max(0, visibleItems.value.length - 1);
  }
});

const runItem = (item: CommandItem) => {
  item.run();
  close();
};

const onInputKeydown = (event: KeyboardEvent) => {
  const count = visibleItems.value.length;
  if (event.key === 'ArrowDown') {
    event.preventDefault();
    if (!count) return;
    activeIndex.value = (activeIndex.value + 1) % count;
    return;
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault();
    if (!count) return;
    activeIndex.value = (activeIndex.value - 1 + count) % count;
    return;
  }
  if (event.key === 'Enter') {
    event.preventDefault();
    const item = visibleItems.value[activeIndex.value];
    if (item) runItem(item);
    return;
  }
  if (event.key === 'Escape') {
    event.preventDefault();
    close();
  }
};

const onOverlayKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    event.preventDefault();
    close();
  }
};
</script>

<style scoped>
.cmdk-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 12vh 16px 16px;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(4px);
}

.cmdk-panel {
  width: min(640px, 100%);
  border-radius: 16px;
  background: var(--noto-surface, #fff);
  box-shadow: var(--noto-shadow-ambient, 0 24px 64px rgba(15, 23, 42, 0.18));
  overflow: hidden;
}

.cmdk-input-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--noto-border, #eef2f7);
}

.cmdk-input-icon {
  color: var(--noto-text-muted, #94a3b8);
  font-size: 16px;
  flex-shrink: 0;
}

.cmdk-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 15px;
  color: var(--noto-text, #101828);
  background: transparent;
}

.cmdk-input::placeholder {
  color: var(--noto-text-muted, #94a3b8);
}

.cmdk-kbd {
  font-size: 11px;
  color: var(--noto-text-muted, #667085);
  background: var(--noto-canvas, #f8fafc);
  border: 1px solid var(--noto-border, #e4e7ec);
  border-radius: 6px;
  padding: 2px 6px;
}

.cmdk-list {
  list-style: none;
  margin: 0;
  padding: 8px;
  max-height: 360px;
  overflow-y: auto;
}

.cmdk-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.cmdk-item.active,
.cmdk-item:hover {
  background: var(--noto-pastel-blue, #f0f7ff);
}

.cmdk-item-icon {
  color: var(--noto-text-muted, #475467);
  font-size: 16px;
  flex-shrink: 0;
}

.cmdk-item-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.cmdk-item-dot.blue {
  background: #1677ff;
}

.cmdk-item-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cmdk-item-label {
  font-size: 14px;
  color: var(--noto-text, #101828);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cmdk-item-hint {
  font-size: 12px;
  color: var(--noto-text-muted, #667085);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cmdk-item-badge {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--noto-text-muted, #475467);
  background: var(--noto-canvas, #f2f4f7);
  border-radius: 999px;
  padding: 2px 8px;
}

.cmdk-loading,
.cmdk-empty {
  padding: 24px 16px;
  text-align: center;
  color: var(--noto-text-muted, #667085);
  font-size: 13px;
}

.cmdk-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.cmdk-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 16px;
  border-top: 1px solid var(--noto-border, #eef2f7);
  font-size: 11px;
  color: var(--noto-text-muted, #94a3b8);
}

.cmdk-footer kbd {
  font-size: 10px;
  background: var(--noto-canvas, #f8fafc);
  border: 1px solid var(--noto-border, #e4e7ec);
  border-radius: 4px;
  padding: 1px 4px;
  margin-right: 2px;
}
</style>
