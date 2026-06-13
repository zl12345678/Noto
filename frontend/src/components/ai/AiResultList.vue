<template>
  <div class="result-list">
    <div class="result-list-head">
      <span class="result-list-icon" :class="`result-list-icon--${kind}`">{{ icon }}</span>
      <div class="result-list-head-text">
        <strong>{{ title }}</strong>
        <span v-if="count != null" class="result-count">{{ count }} 项</span>
      </div>
    </div>

    <div v-if="groups?.length" class="catalog-groups">
      <section v-for="(group, groupIndex) in groups" :key="`${group.name}-${groupIndex}`" class="catalog-group">
        <div class="catalog-group-head">
          <span class="catalog-group-name">{{ group.name }}</span>
          <span v-if="group.countLabel" class="catalog-group-meta">{{ group.countLabel }}</span>
        </div>
        <ul class="item-grid">
          <li
            v-for="(item, index) in group.items"
            :key="`${item.id ?? item.title}-${index}`"
            class="item-card"
            :class="{ clickable: canOpen(item) }"
            @click="openItem(item)"
          >
            <div class="item-main">
              <span class="item-title">{{ item.title }}</span>
              <span v-if="item.meta" class="item-meta">{{ item.meta }}</span>
            </div>
            <span v-if="canOpen(item)" class="item-action-hint">查看</span>
          </li>
        </ul>
      </section>
    </div>

    <ul v-else-if="items?.length" class="item-grid">
      <li
        v-for="(item, index) in items"
        :key="`${item.id ?? item.title}-${index}`"
        class="item-card"
        :class="{ clickable: canOpen(item) && !hasInlineActions(item) }"
        @click="handleCardClick(item)"
      >
        <div class="item-main">
          <span class="item-title">{{ item.title }}</span>
          <span v-if="item.meta" class="item-meta">{{ item.meta }}</span>
        </div>

        <div v-if="hasInlineActions(item)" class="item-actions" @click.stop>
          <button type="button" class="action-btn" @click="previewShare(item)">预览</button>
          <button type="button" class="action-btn" @click="copyShare(item)">复制</button>
        </div>

        <template v-else>
          <span v-if="item.badge" class="item-badge" :class="`item-badge--${item.badgeTone || 'default'}`">
            {{ item.badge }}
          </span>
          <span v-else-if="canOpen(item)" class="item-action-hint">查看</span>
        </template>
      </li>
    </ul>

    <p v-else class="result-empty">暂无数据</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { buildShareUrl } from '../../api/share';
import { buildNoteRouteQuery } from '../../utils/noteNavigation';
import type { CatalogGroup, ResultItemKind, ResultListItem } from '../../utils/aiMessageFormat';

const props = defineProps<{
  title: string;
  kind: ResultItemKind | 'catalog';
  count?: number;
  items?: ResultListItem[];
  groups?: CatalogGroup[];
  workspaceId?: string;
}>();

const router = useRouter();
const route = useRoute();

const icon = computed(() => {
  switch (props.kind) {
    case 'note':
    case 'catalog':
      return '📄';
    case 'todo':
      return '☑';
    case 'reminder':
      return '⏰';
    case 'drive-file':
      return '📁';
    case 'drive-folder':
      return '🗂️';
    case 'share':
      return '🔗';
    case 'tag':
      return '🏷';
    case 'folder':
      return '📂';
    default:
      return '•';
  }
});

function hasInlineActions(item: ResultListItem) {
  return item.link?.type === 'share' && item.actions?.includes('preview');
}

function canOpen(item: ResultListItem) {
  return Boolean(item.link);
}

function handleCardClick(item: ResultListItem) {
  if (hasInlineActions(item)) return;
  openItem(item);
}

function openItem(item: ResultListItem) {
  const link = item.link;
  if (!link) return;

  if (link.type === 'note' && link.id != null) {
    router.push({
      path: `/notes/${String(link.id)}`,
      query: buildNoteRouteQuery({
        from: route.name === 'ai' ? 'ai' : undefined,
        workspaceId: props.workspaceId,
      }),
    });
    return;
  }

  if (link.type === 'todo' && link.id != null) {
    router.push({
      path: '/todos',
      query: {
        view: 'all',
        todoId: String(link.id),
        ...(props.workspaceId ? { workspace: props.workspaceId } : {}),
      },
    });
    return;
  }

  if (link.type === 'reminder' && link.id != null) {
    router.push({
      path: '/reminders',
      query: {
        reminderId: String(link.id),
        ...(props.workspaceId ? { workspace: props.workspaceId } : {}),
      },
    });
    return;
  }

  if (link.type === 'drive') {
    router.push({
      path: '/drive',
      query: props.workspaceId ? { workspace: props.workspaceId } : {},
    });
    return;
  }

  if (link.type === 'share' && link.url) {
    previewShare(item);
  }
}

function previewShare(item: ResultListItem) {
  const url = item.link?.url;
  if (!url) return;
  window.open(buildShareUrl(url), '_blank', 'noopener');
}

async function copyShare(item: ResultListItem) {
  const url = item.link?.url;
  if (!url) return;
  try {
    await navigator.clipboard.writeText(buildShareUrl(url));
    message.success('分享链接已复制');
  } catch {
    message.error('复制失败');
  }
}
</script>

<style scoped>
.result-list {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
}

.result-list-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-bottom: 1px solid #e2e8f0;
}

.result-list-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
}

.result-list-head-text {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.result-list-head-text strong {
  font-size: 13px;
  color: #0f172a;
}

.result-count {
  font-size: 11px;
  color: #64748b;
  padding: 1px 8px;
  border-radius: 999px;
  background: #e2e8f0;
}

.catalog-groups {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.catalog-group + .catalog-group {
  border-top: 1px solid #f1f5f9;
}

.catalog-group-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px 4px;
}

.catalog-group-name {
  font-size: 12px;
  font-weight: 600;
  color: #0e7490;
}

.catalog-group-meta {
  font-size: 11px;
  color: #94a3b8;
}

.item-grid {
  list-style: none;
  margin: 0;
  padding: 4px 8px 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.item-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid transparent;
}

.item-card.clickable {
  cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s;
}

.item-card.clickable:hover {
  border-color: rgba(14, 116, 144, 0.25);
  background: rgba(8, 145, 178, 0.06);
}

.item-main {
  min-width: 0;
  flex: 1;
}

.item-title {
  display: block;
  font-size: 13px;
  color: #1e293b;
  line-height: 1.45;
  word-break: break-word;
}

.item-meta {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.4;
}

.item-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.action-btn {
  margin: 0;
  padding: 3px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  background: #fff;
  color: #0e7490;
  font-size: 11px;
  line-height: 1.4;
  cursor: pointer;
  transition: background-color 0.15s, border-color 0.15s;
}

.action-btn:hover {
  border-color: rgba(14, 116, 144, 0.35);
  background: rgba(8, 145, 178, 0.08);
}

.item-badge {
  flex-shrink: 0;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  white-space: nowrap;
}

.item-badge--default {
  color: #475569;
  background: #e2e8f0;
}

.item-badge--success {
  color: #047857;
  background: #d1fae5;
}

.item-badge--warning {
  color: #b45309;
  background: #fef3c7;
}

.item-badge--muted {
  color: #64748b;
  background: #f1f5f9;
}

.item-action-hint {
  flex-shrink: 0;
  font-size: 11px;
  color: #0e7490;
}

.result-empty {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  color: #94a3b8;
}
</style>
