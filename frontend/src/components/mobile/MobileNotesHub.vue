<template>
  <div class="mobile-notes-hub">
    <a-input-search
      v-model:value="keyword"
      placeholder="搜索文档"
      allow-clear
      class="mobile-notes-hub-search"
      @search="onSearch"
    />
    <div v-if="workspaces.length" class="mobile-notes-kb-list">
        <button
          v-for="(ws, index) in workspaces"
          :key="ws.id"
          type="button"
          class="mobile-notes-kb-item"
          @click="openWorkspace(String(ws.id))"
        >
          <span class="mobile-notes-kb-dot" :style="{ background: dotColor(index) }" />
          <span class="mobile-notes-kb-name">{{ ws.name }}</span>
          <span class="mobile-notes-kb-arrow">›</span>
        </button>
      </div>
    <EmptyState v-else title="暂无知识库" description="请在桌面端创建知识库" preset="note" compact />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { storeToRefs } from 'pinia';
import { useWorkspaceStore } from '../../store/workspace';
import EmptyState from '../common/EmptyState.vue';

const router = useRouter();
const workspaceStore = useWorkspaceStore();
const { items: workspaces, loading } = storeToRefs(workspaceStore);

const keyword = ref('');

const DOTS = ['#0891b2', '#10b981', '#f59e0b', '#8b5cf6', '#06b6d4'];

function dotColor(index: number) {
  return DOTS[index % DOTS.length];
}

function openWorkspace(workspaceId: string) {
  router.push({ path: '/notes', query: { workspace: workspaceId } });
}

function onSearch() {
  const q = keyword.value.trim();
  if (!q) return;
  router.push({ path: '/search', query: { q } });
}
</script>