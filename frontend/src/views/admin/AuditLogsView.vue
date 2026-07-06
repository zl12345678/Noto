<template>
  <div class="audit-page noto-page">
    <a-card class="audit-hero" :bordered="false">
      <div class="hero-row">
        <div>
          <p class="noto-page-eyebrow">管理员</p>
          <h2 class="noto-page-title">操作日志</h2>
          <p class="hero-desc">查看用户访问、API 操作、来源 IP 和客户端信息</p>
        </div>
        <a-button :loading="loading" @click="loadData">刷新</a-button>
      </div>

      <div class="stat-grid">
        <div class="stat-card">
          <span>总操作</span>
          <strong>{{ summary?.totalActions ?? 0 }}</strong>
        </div>
        <div class="stat-card">
          <span>今日操作</span>
          <strong>{{ summary?.todayActions ?? 0 }}</strong>
        </div>
        <div class="stat-card">
          <span>访问用户</span>
          <strong>{{ summary?.uniqueUsers ?? 0 }}</strong>
        </div>
        <div class="stat-card">
          <span>今日用户</span>
          <strong>{{ summary?.todayUniqueUsers ?? 0 }}</strong>
        </div>
        <div class="stat-card">
          <span>访问 IP</span>
          <strong>{{ summary?.uniqueIps ?? 0 }}</strong>
        </div>
        <div class="stat-card">
          <span>今日 IP</span>
          <strong>{{ summary?.todayUniqueIps ?? 0 }}</strong>
        </div>
      </div>
    </a-card>

    <a-card class="filter-card noto-surface-card" :bordered="false">
      <a-form layout="inline" class="filter-form">
        <a-form-item label="用户 ID">
          <a-input v-model:value="filters.userId" allow-clear placeholder="如 1" />
        </a-form-item>
        <a-form-item label="操作">
          <a-input v-model:value="filters.actionType" allow-clear placeholder="如 api.POST 或 auth.login" />
        </a-form-item>
        <a-form-item label="IP">
          <a-input v-model:value="filters.ipAddress" allow-clear placeholder="如 192.168" />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="applyFilters">查询</a-button>
            <a-button @click="resetFilters">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card class="audit-table-card noto-surface-card" :bordered="false">
      <a-table
        :data-source="records"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        size="middle"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'user'">
            <div class="user-cell">
              <strong>{{ record.nickname || record.username || '未知用户' }}</strong>
              <span>#{{ record.userId || '-' }} {{ record.username || '' }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'actionType'">
            <a-tag :color="actionColor(record.actionType)">{{ actionLabel(record.actionType) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'ipAddress'">
            <span class="mono">{{ record.ipAddress || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'detail'">
            <div class="detail-cell">
              <span>{{ detailSummary(record) }}</span>
              <a-popover v-if="record.detail" title="操作详情" trigger="click">
                <template #content>
                  <pre class="detail-json">{{ JSON.stringify(record.detail, null, 2) }}</pre>
                </template>
                <a-button type="link" size="small">详情</a-button>
              </a-popover>
            </div>
          </template>
          <template v-else-if="column.key === 'userAgent'">
            <span class="agent-text" :title="record.userAgent">{{ record.userAgent || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import {
  getAdminAuditSummary,
  listAdminAuditLogs,
  type AdminAuditLog,
  type AdminAuditSummary,
} from '../../api/adminAudit';

const loading = ref(false);
const records = ref<AdminAuditLog[]>([]);
const summary = ref<AdminAuditSummary | null>(null);
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);

const filters = reactive({
  userId: '',
  actionType: '',
  ipAddress: '',
});

const columns = [
  { title: '用户', key: 'user', width: 170 },
  { title: '操作', key: 'actionType', width: 220 },
  { title: 'IP', key: 'ipAddress', width: 150 },
  { title: '详情', key: 'detail', ellipsis: true },
  { title: '客户端', key: 'userAgent', width: 240, ellipsis: true },
  { title: '时间', key: 'createdAt', width: 170 },
];

const pagination = computed(() => ({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (n: number) => `共 ${n} 条`,
}));

async function loadData() {
  loading.value = true;
  try {
    const params = buildParams();
    const [pageData, summaryData] = await Promise.all([
      listAdminAuditLogs(params),
      getAdminAuditSummary(),
    ]);
    records.value = pageData.records || [];
    total.value = pageData.total || 0;
    summary.value = summaryData;
  } catch (error: any) {
    message.error(error?.message || '加载操作日志失败');
  } finally {
    loading.value = false;
  }
}

function buildParams() {
  return {
    page: page.value,
    size: pageSize.value,
    userId: filters.userId.trim() || undefined,
    actionType: filters.actionType.trim() || undefined,
    ipAddress: filters.ipAddress.trim() || undefined,
  };
}

function applyFilters() {
  page.value = 1;
  void loadData();
}

function resetFilters() {
  filters.userId = '';
  filters.actionType = '';
  filters.ipAddress = '';
  page.value = 1;
  void loadData();
}

function onTableChange(next: any) {
  page.value = next.current || 1;
  pageSize.value = next.pageSize || 20;
  void loadData();
}

function actionLabel(action: string) {
  if (action === 'auth.login') return '登录';
  if (action.startsWith('api.')) return action.replace('api.', '');
  if (action.startsWith('ai.')) return `AI · ${action.slice(3)}`;
  return action;
}

function actionColor(action: string) {
  if (action === 'auth.login') return 'green';
  if (action.includes('POST') || action.includes('PATCH')) return 'blue';
  if (action.includes('DELETE')) return 'red';
  if (action.startsWith('ai.')) return 'purple';
  return 'default';
}

function detailSummary(record: AdminAuditLog) {
  const detail = record.detail || {};
  const path = detail.path ? String(detail.path) : '';
  const status = detail.status ? `状态 ${detail.status}` : '';
  const duration = detail.durationMs != null ? `${detail.durationMs}ms` : '';
  return [path, status, duration].filter(Boolean).join(' · ') || record.resourceType || '-';
}

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';
}

onMounted(loadData);
</script>

<style scoped>
.audit-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.audit-hero,
.filter-card,
.audit-table-card {
  border-radius: 8px;
}

.hero-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.hero-desc {
  margin: 6px 0 0;
  color: #64748b;
}

.stat-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.stat-card {
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.stat-card span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.stat-card strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 22px;
  line-height: 1.2;
}

.filter-form {
  gap: 8px;
}

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-cell span,
.agent-text {
  color: #64748b;
  font-size: 12px;
}

.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}

.detail-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.detail-json {
  max-width: 520px;
  max-height: 360px;
  margin: 0;
  overflow: auto;
  font-size: 12px;
}

@media (max-width: 900px) {
  .hero-row {
    flex-direction: column;
  }

  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
