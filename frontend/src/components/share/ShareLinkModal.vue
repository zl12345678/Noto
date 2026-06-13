<template>
  <a-modal
    :open="open"
    :title="title"
    :confirm-loading="creating"
    :ok-text="shareLink ? '关闭' : '创建分享'"
    cancel-text="取消"
    width="520px"
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
          <span>访问 {{ shareLink.viewCount ?? 0 }} 次</span>
          <span v-if="shareLink.lastViewedAt"> · 最近访问 {{ formatTime(shareLink.lastViewedAt) }}</span>
        </div>
        <p v-if="shareLink.passwordProtected" class="share-meta">已启用访问密码</p>
        <p v-if="shareLink.expiresAt" class="share-meta">有效期至 {{ formatTime(shareLink.expiresAt) }}</p>
        <p v-else class="share-meta">永久有效</p>
        <a-space direction="vertical" style="width: 100%">
          <a-button block :loading="loading" @click="refreshShare">刷新统计</a-button>
          <a-button danger block :loading="revoking" @click="handleRevoke">关闭分享</a-button>
        </a-space>
      </div>
      <div v-else class="share-empty">
        <p class="share-desc">{{ description }}</p>
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
  createAttachmentShare,
  createNoteShare,
  getAttachmentShare,
  getNoteShare,
  getShareInfo,
  revokeAttachmentShare,
  revokeNoteShare,
  revokeShareByToken,
  type ShareLink,
} from '../../api/share';

const props = defineProps<{
  open: boolean;
  resourceType: 'NOTE' | 'ATTACHMENT';
  resourceId?: string;
  title?: string;
  description?: string;
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

const modalTitle = computed(() => props.title ?? (props.resourceType === 'NOTE' ? '分享文档' : '分享文件'));
const title = modalTitle;
const description = computed(
  () =>
    props.description ??
    (props.resourceType === 'NOTE'
      ? '创建链接后，持有链接的人可只读查看文档内容。'
      : '创建链接后，持有链接的人可查看并下载该文件。'),
);

const fullShareUrl = computed(() => (shareLink.value ? buildShareUrl(shareLink.value.sharePath) : ''));

const formatTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm');

const buildPayload = () => {
  const payload: { expiresInDays?: number; password?: string } = {};
  if (expiresInDays.value > 0) payload.expiresInDays = expiresInDays.value;
  if (password.value.trim()) payload.password = password.value.trim();
  return payload;
};

const loadShare = async () => {
  if (!props.resourceId) {
    shareLink.value = null;
    return;
  }
  loading.value = true;
  try {
    shareLink.value =
      props.resourceType === 'NOTE'
        ? await getNoteShare(props.resourceId)
        : await getAttachmentShare(props.resourceId);
  } catch (error: any) {
    message.error(error?.message || '加载分享状态失败');
  } finally {
    loading.value = false;
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

const handleOk = async () => {
  if (shareLink.value) {
    emit('update:open', false);
    return;
  }
  await handleCreate();
};

const handleCreate = async () => {
  if (shareLink.value || !props.resourceId) {
    emit('update:open', false);
    return;
  }
  creating.value = true;
  try {
    const payload = buildPayload();
    shareLink.value =
      props.resourceType === 'NOTE'
        ? await createNoteShare(props.resourceId, payload)
        : await createAttachmentShare(props.resourceId, payload);
    message.success('分享链接已创建');
  } catch (error: any) {
    message.error(error?.message || '创建分享失败');
  } finally {
    creating.value = false;
  }
};

const handleRevoke = async () => {
  if (!props.resourceId && !shareLink.value?.token) return;
  revoking.value = true;
  try {
    if (shareLink.value?.token && shareLink.value.resourceType === 'BATCH') {
      await revokeShareByToken(shareLink.value.token);
    } else if (props.resourceType === 'NOTE') {
      await revokeNoteShare(props.resourceId!);
    } else {
      await revokeAttachmentShare(props.resourceId!);
    }
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
  () => [props.open, props.resourceId] as const,
  ([open]) => {
    if (open) {
      expiresInDays.value = 0;
      password.value = '';
      loadShare();
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
