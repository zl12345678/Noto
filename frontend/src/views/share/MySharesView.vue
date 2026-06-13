<template>
  <div class="my-shares-page noto-page">
    <a-card class="shares-hero" :bordered="false">
      <div class="hero-row">
        <div>
          <p class="noto-page-eyebrow">分享管理</p>
          <h2 class="noto-page-title">我的分享</h2>
          <p class="hero-desc">集中查看、复制链接或关闭已创建的分享，无需逐个打开文档或网盘文件</p>
        </div>
        <a-button :loading="loading" @click="loadShares">刷新</a-button>
      </div>
      <a-segmented
        v-model:value="typeFilter"
        :options="typeFilterOptions"
        class="type-filter"
        @change="applyFilter"
      />
    </a-card>

    <a-card class="shares-table-card noto-surface-card" :bordered="false" :loading="loading">
      <a-table
        v-if="filteredShares.length"
        :data-source="filteredShares"
        :columns="columns"
        :pagination="false"
        row-key="token"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <div class="title-cell">
              <a-tag :color="typeColor(record.resourceType)" class="type-tag">
                {{ typeLabel(record.resourceType) }}
              </a-tag>
              <span class="share-title">{{ record.title || '未命名' }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'passwordProtected'">
            {{ record.passwordProtected ? '是' : '否' }}
          </template>
          <template v-else-if="column.key === 'viewCount'">
            {{ record.viewCount ?? 0 }}
          </template>
          <template v-else-if="column.key === 'expiresAt'">
            {{ record.expiresAt ? formatTime(record.expiresAt) : '永久' }}
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ record.createdAt ? formatTime(record.createdAt) : '—' }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space wrap>
              <a-button type="link" size="small" @click="copyShare(record)">复制链接</a-button>
              <a-button type="link" size="small" @click="openSharePage(record)">预览</a-button>
              <a-button
                v-if="canOpenSource(record)"
                type="link"
                size="small"
                @click="openSource(record)"
              >
                打开来源
              </a-button>
              <a-button type="link" size="small" danger @click="handleRevoke(record)">关闭分享</a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <EmptyState
        v-else-if="!loading"
        :title="emptyTitle"
        :description="emptyDescription"
        preset="search"
      />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Modal, message } from 'ant-design-vue';
import EmptyState from '../../components/common/EmptyState.vue';
import {
  buildShareUrl,
  listMyShares,
  revokeShareByToken,
  type ShareLink,
} from '../../api/share';

const router = useRouter();

const loading = ref(false);
const shares = ref<ShareLink[]>([]);
const typeFilter = ref<'all' | ShareLink['resourceType']>('all');

const typeFilterOptions = [
  { label: '全部', value: 'all' },
  { label: '文档', value: 'NOTE' },
  { label: '文件', value: 'ATTACHMENT' },
  { label: '批量', value: 'BATCH' },
];

const filteredShares = computed(() => {
  if (typeFilter.value === 'all') return shares.value;
  return shares.value.filter((item) => item.resourceType === typeFilter.value);
});

const emptyTitle = computed(() =>
  typeFilter.value === 'all' ? '暂无有效分享' : `暂无${typeLabel(typeFilter.value as ShareLink['resourceType'])}分享`,
);

const emptyDescription = computed(() =>
  typeFilter.value === 'all'
    ? '在文档或网盘中创建分享后，会出现在这里'
    : '切换筛选或去对应页面创建新的分享',
);

const columns = [
  { title: '分享内容', key: 'title', ellipsis: true },
  { title: '密码', key: 'passwordProtected', width: 64 },
  { title: '访问', key: 'viewCount', width: 64 },
  { title: '有效期', key: 'expiresAt', width: 140 },
  { title: '创建时间', key: 'createdAt', width: 140 },
  { title: '操作', key: 'actions', width: 280 },
];

const typeLabel = (type: ShareLink['resourceType']) => {
  if (type === 'NOTE') return '文档';
  if (type === 'BATCH') return '批量';
  return '文件';
};

const typeColor = (type: ShareLink['resourceType']) => {
  if (type === 'NOTE') return 'blue';
  if (type === 'BATCH') return 'purple';
  return 'cyan';
};

const formatTime = (value: string) => dayjs(value).format('MM-DD HH:mm');

const applyFilter = () => {
  // segmented v-model already updates typeFilter
};

const loadShares = async () => {
  loading.value = true;
  try {
    shares.value = await listMyShares();
  } catch (error: any) {
    message.error(error?.message || '加载分享列表失败');
  } finally {
    loading.value = false;
  }
};

const copyShare = async (record: ShareLink) => {
  try {
    await navigator.clipboard.writeText(buildShareUrl(record.sharePath));
    message.success('链接已复制');
  } catch {
    message.error('复制失败');
  }
};

const openSharePage = (record: ShareLink) => {
  window.open(buildShareUrl(record.sharePath), '_blank', 'noopener');
};

const canOpenSource = (record: ShareLink) => {
  if (record.resourceType === 'BATCH') return false;
  if (!record.resourceId) return false;
  return !String(record.title || '').includes('已删除');
};

const openSource = (record: ShareLink) => {
  if (!record.resourceId) return;
  if (record.resourceType === 'NOTE') {
    router.push({
      path: `/notes/${record.resourceId}`,
      query: record.workspaceId ? { workspace: String(record.workspaceId) } : undefined,
    });
    return;
  }
  if (record.resourceType === 'ATTACHMENT') {
    router.push({
      path: '/drive',
      query: record.workspaceId ? { workspace: String(record.workspaceId) } : undefined,
    });
  }
};

const handleRevoke = (record: ShareLink) => {
  Modal.confirm({
    title: '关闭此分享？',
    content: record.title || '关闭后链接将立即失效',
    okText: '关闭分享',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await revokeShareByToken(record.token);
      message.success('已关闭分享');
      shares.value = shares.value.filter((item) => item.token !== record.token);
    },
  });
};

onMounted(loadShares);
</script>

<style scoped>
.shares-hero {
  border-radius: var(--noto-radius-lg);
  margin-bottom: 16px;
}

.hero-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.hero-desc {
  margin: 8px 0 0;
  color: var(--noto-text-muted);
  font-size: 14px;
}

.type-filter {
  margin-top: 16px;
  max-width: 360px;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.type-tag {
  margin: 0;
  flex-shrink: 0;
}

.share-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
