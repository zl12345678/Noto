<template>
  <a-modal
    :open="open"
    :title="`批量分享（${fileIds.length} 个文件）`"
    :confirm-loading="creating"
    :ok-text="shareLink ? '关闭' : '创建分享'"
    cancel-text="取消"
    width="560px"
    @update:open="emit('update:open', $event)"
    @ok="handleOk"
  >
    <a-spin :spinning="loading">
      <div v-if="shareLink" class="share-active">
        <p class="share-label">分享链接</p>
        <a-input-group compact class="share-input-group">
          <a-input :value="fullShareUrl" readonly />
          <a-button type="primary" @click="copyLink">复制</a-button>
        </a-input-group>
        <div class="share-stats">
          <span>包含 {{ shareLink.itemCount ?? fileIds.length }} 个文件</span>
          <span> · 访问 {{ shareLink.viewCount ?? 0 }} 次</span>
          <span v-if="shareLink.lastViewedAt"> · 最近 {{ formatTime(shareLink.lastViewedAt) }}</span>
        </div>
        <p v-if="shareLink.passwordProtected" class="share-meta">已启用访问密码</p>
        <p v-if="shareLink.expiresAt" class="share-meta">有效期至 {{ formatTime(shareLink.expiresAt) }}</p>
        <p v-else class="share-meta">永久有效</p>
        <a-space direction="vertical" style="width: 100%">
          <a-button block :loading="loading" @click="refreshShare">刷新统计</a-button>
          <a-button danger block :loading="revoking" @click="handleRevoke">关闭分享</a-button>
        </a-space>
      </div>
      <div v-else>
        <p class="share-desc">将创建一个链接，访客可在同一页面查看并下载所选文件。</p>
        <a-form layout="vertical">
          <a-form-item label="有效期">
            <a-select v-model:value="expiresInDays" style="width: 100%">
              <a-select-option :value="0">永久有效</a-select-option>
              <a-select-option :value="7">7 天</a-select-option>
              <a-select-option :value="30">30 天</a-select-option>
              <a-select-option :value="90">90 天</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="访问密码（可选）">
            <a-input-password
              v-model:value="password"
              placeholder="4–64 位，留空表示不设密码"
              autocomplete="new-password"
            />
          </a-form-item>
        </a-form>
      </div>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  buildShareUrl,
  createBatchAttachmentShare,
  getShareInfo,
  revokeShareByToken,
  type ShareLink,
} from '../../api/share';

const props = defineProps<{
  open: boolean;
  fileIds: string[];
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
}>();

const loading = ref(false);
const creating = ref(false);
const revoking = ref(false);
const shareLink = ref<ShareLink | null>(null);
const expiresInDays = ref(0);
const password = ref('');

const fullShareUrl = computed(() => (shareLink.value ? buildShareUrl(shareLink.value.sharePath) : ''));

const formatTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm');

const handleOk = async () => {
  if (shareLink.value) {
    emit('update:open', false);
    return;
  }
  await handleCreate();
};

const handleCreate = async () => {
  if (!props.fileIds.length) {
    message.warning('请选择要分享的文件');
    return;
  }
  creating.value = true;
  try {
    const payload: { ids: string[]; expiresInDays?: number; password?: string } = {
      ids: props.fileIds,
    };
    if (expiresInDays.value > 0) payload.expiresInDays = expiresInDays.value;
    if (password.value.trim()) payload.password = password.value.trim();
    shareLink.value = await createBatchAttachmentShare(payload);
    message.success('批量分享链接已创建');
  } catch (error: any) {
    message.error(error?.message || '创建批量分享失败');
  } finally {
    creating.value = false;
  }
};

const refreshShare = async () => {
  if (!shareLink.value?.token) return;
  loading.value = true;
  try {
    shareLink.value = await getShareInfo(shareLink.value.token);
  } catch (error: any) {
    message.error(error?.message || '刷新统计失败');
  } finally {
    loading.value = false;
  }
};

const handleRevoke = async () => {
  if (!shareLink.value?.token) return;
  revoking.value = true;
  try {
    await revokeShareByToken(shareLink.value.token);
    shareLink.value = null;
    message.success('已关闭分享');
  } catch (error: any) {
    message.error(error?.message || '关闭分享失败');
  } finally {
    revoking.value = false;
  }
};

const copyLink = async () => {
  if (!fullShareUrl.value) return;
  try {
    await navigator.clipboard.writeText(fullShareUrl.value);
    message.success('链接已复制');
  } catch {
    message.error('复制失败，请手动选择链接');
  }
};

watch(
  () => props.open,
  (open) => {
    if (open) {
      shareLink.value = null;
      expiresInDays.value = 0;
      password.value = '';
    }
  },
);
</script>

<style scoped>
.share-desc {
  margin: 0 0 12px;
  color: var(--noto-text-secondary, #64748b);
  line-height: 1.6;
}

.share-label {
  margin: 0 0 8px;
  font-weight: 500;
}

.share-input-group {
  display: flex;
  width: 100%;
}

.share-input-group :deep(.ant-input) {
  flex: 1;
}

.share-stats,
.share-meta {
  margin: 10px 0 8px;
  font-size: 12px;
  color: var(--noto-text-secondary, #64748b);
}
</style>
