<template>
  <div class="search-page noto-page">
    <a-card class="search-hero" :bordered="false">
      <h2 class="noto-page-title">搜索</h2>
      <div class="search-bar">
        <a-input-search
          v-model:value="keyword"
          placeholder="输入关键词搜索..."
          size="large"
          allow-clear
          enter-button="搜索"
          @search="handleSearch"
        />
      </div>
      <a-space wrap class="search-filters">
        <a-select
          v-model:value="filters.workspaceId"
          allow-clear
          placeholder="全部知识库"
          style="min-width: 180px"
          :options="workspaceOptions"
          @change="handleSearch"
        />
        <a-select
          v-model:value="filters.tagId"
          allow-clear
          placeholder="全部标签"
          style="min-width: 160px"
          :options="tagOptions"
          :disabled="!filters.workspaceId"
          @change="handleSearch"
        />
        <a-select
          v-model:value="filters.folderId"
          allow-clear
          placeholder="全部分组"
          style="min-width: 160px"
          :options="folderOptions"
          :disabled="!filters.workspaceId"
          @change="handleSearch"
        />
      </a-space>
    </a-card>

    <a-card class="search-results" :bordered="false" :loading="loading">
      <div v-if="searched" class="results-head">
        <span v-if="total > 0">找到 {{ total }} 条结果</span>
        <span v-else>未找到匹配文档</span>
      </div>

      <a-list
        v-if="results.length"
        :data-source="results"
        item-layout="vertical"
        :pagination="pagination"
      >
        <template #renderItem="{ item }">
          <a-list-item class="result-item noto-interactive-row" @click="openNote(item)">
            <a-list-item-meta>
              <template #title>
                <span class="result-title">{{ item.title || '未命名文档' }}</span>
              </template>
              <template #description>
                <p class="result-snippet" v-html="item.highlight || escapeHtml(item.snippet)" />
                <a-space wrap :size="8" class="result-meta">
                  <a-tag v-if="item.workspaceName" color="blue">{{ item.workspaceName }}</a-tag>
                  <a-tag v-for="tag in item.tags || []" :key="tag.id" :color="tag.color || 'processing'">
                    {{ tag.name }}
                  </a-tag>
                  <span v-if="item.lastEditedAt" class="result-time">
                    {{ formatTime(item.lastEditedAt) }}
                  </span>
                </a-space>
              </template>
            </a-list-item-meta>
          </a-list-item>
        </template>
      </a-list>

      <EmptyState
        v-else-if="searched && !loading"
        title="未找到匹配文档"
        description="换个关键词试试，或调整知识库/标签/分组筛选"
        preset="search"
      />
      <EmptyState
        v-else-if="!searched"
        title="输入关键词开始搜索"
        description="支持标题与正文检索，点击结果可跳转到匹配段落"
        preset="search"
      />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { searchNotes, type SearchResult } from '../../api/search';
import { buildSearchCacheKey, getSearchCache, setSearchCache } from '../../utils/searchCache';
import { listFolders } from '../../api/folders';
import { listTags } from '../../api/tags';
import { listWorkspaces, type Workspace } from '../../api/workspaces';
import { buildNoteRouteQuery } from '../../utils/noteNavigation';
import EmptyState from '../../components/common/EmptyState.vue';

function escapeHtml(text?: string | null) {
  if (!text) return '';
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

const route = useRoute();
const router = useRouter();

const keyword = ref('');
const loading = ref(false);
const searched = ref(false);
const results = ref<SearchResult[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const workspaces = ref<Workspace[]>([]);
const tags = ref<{ id: string; name: string; color?: string | null }[]>([]);
const folders = ref<{ id: string; name: string }[]>([]);

const filters = reactive({
  workspaceId: undefined as string | undefined,
  tagId: undefined as string | undefined,
  folderId: undefined as string | undefined,
});

const workspaceOptions = computed(() =>
  workspaces.value.map((item) => ({ label: item.name, value: item.id })),
);

const tagOptions = computed(() =>
  tags.value.map((item) => ({ label: item.name, value: item.id })),
);

const folderOptions = computed(() =>
  folders.value.map((item) => ({ label: item.name, value: item.id })),
);

const pagination = computed(() => ({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: false,
  onChange: (next: number) => {
    page.value = next;
    loadResults();
  },
}));

const formatTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm');

const loadResults = async () => {
  const q = keyword.value.trim();
  if (!q) {
    results.value = [];
    total.value = 0;
    searched.value = false;
    return;
  }
  const cacheKey = buildSearchCacheKey({
    keyword: q,
    page: page.value,
    size: pageSize.value,
    workspaceId: filters.workspaceId ?? null,
    tagId: filters.tagId ?? null,
    folderId: filters.folderId ?? null,
  });
  const cached = getSearchCache(cacheKey);
  if (cached) {
    results.value = cached.records || [];
    total.value = cached.total || 0;
    searched.value = true;
    const query: Record<string, string> = { q };
    if (filters.workspaceId) query.workspace = filters.workspaceId;
    if (filters.tagId) query.tag = filters.tagId;
    if (filters.folderId) query.folder = filters.folderId;
    router.replace({ path: '/search', query });
    return;
  }
  loading.value = true;
  try {
    const data = await searchNotes({
      keyword: q,
      page: page.value,
      size: pageSize.value,
      workspaceId: filters.workspaceId,
      tagId: filters.tagId,
      folderId: filters.folderId,
    });
    setSearchCache(cacheKey, data);
    results.value = data.records || [];
    total.value = data.total || 0;
    searched.value = true;
    const query: Record<string, string> = { q };
    if (filters.workspaceId) query.workspace = filters.workspaceId;
    if (filters.tagId) query.tag = filters.tagId;
    if (filters.folderId) query.folder = filters.folderId;
    router.replace({ path: '/search', query });
  } catch (error: any) {
    message.error(error?.message || '搜索失败');
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  page.value = 1;
  loadResults();
};

const openNote = (item: SearchResult) => {
  router.push({
    path: `/notes/${item.noteId}`,
    query: buildNoteRouteQuery({
      from: 'search',
      workspaceId: item.workspaceId,
      keyword: keyword.value.trim(),
      offsetStart: item.offsetStart,
      offsetEnd: item.offsetEnd,
    }),
  });
};

const loadFolders = async (workspaceId?: string) => {
  if (!workspaceId) {
    folders.value = [];
    return;
  }
  folders.value = await listFolders(workspaceId);
};

const loadTags = async (workspaceId?: string) => {
  if (!workspaceId) {
    tags.value = [];
    return;
  }
  tags.value = await listTags(workspaceId);
};

watch(
  () => filters.workspaceId,
  async (workspaceId) => {
    filters.tagId = undefined;
    filters.folderId = undefined;
    await Promise.all([loadTags(workspaceId), loadFolders(workspaceId)]);
  },
);

let keywordDebounceTimer: ReturnType<typeof setTimeout> | null = null;
watch(keyword, () => {
  if (keywordDebounceTimer) clearTimeout(keywordDebounceTimer);
  keywordDebounceTimer = setTimeout(() => {
    page.value = 1;
    void loadResults();
  }, 400);
});

onMounted(async () => {
  workspaces.value = await listWorkspaces();
  const q = typeof route.query.q === 'string' ? route.query.q : '';
  const workspace = typeof route.query.workspace === 'string' ? route.query.workspace : undefined;
  const tag = typeof route.query.tag === 'string' ? route.query.tag : undefined;
  const folder = typeof route.query.folder === 'string' ? route.query.folder : undefined;
  if (workspace) {
    filters.workspaceId = workspace;
    await Promise.all([loadTags(workspace), loadFolders(workspace)]);
  }
  if (tag) filters.tagId = tag;
  if (folder) filters.folderId = folder;
  if (q) {
    keyword.value = q;
    handleSearch();
  }
});
</script>

<style scoped>
.search-hero,
.search-results {
  border-radius: var(--noto-radius-lg);
}

.search-bar {
  max-width: 640px;
  margin: 16px 0;
}

.search-filters {
  margin-top: 4px;
}

.results-head {
  margin-bottom: 12px;
  color: var(--noto-text-muted);
  font-variant-numeric: tabular-nums;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--noto-text);
  letter-spacing: -0.02em;
}

.result-snippet {
  margin: 8px 0 0;
  color: var(--noto-text-muted);
  line-height: 1.65;
  max-width: 65ch;
}

.result-snippet :deep(em) {
  font-style: normal;
  color: var(--noto-primary);
  background: var(--noto-pastel-blue);
  padding: 0 3px;
  border-radius: 4px;
}

.result-meta {
  margin-top: 10px;
}

.result-time {
  color: #98a2b3;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
</style>
