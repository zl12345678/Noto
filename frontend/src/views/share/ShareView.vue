<template>
  <div class="share-page">
    <header class="share-header">
      <div class="share-brand">
        <span class="share-brand-mark">知微</span>
        <span class="share-brand-sub">Noto 分享</span>
      </div>
    </header>

    <main class="share-main">
      <a-spin :spinning="loading">
        <a-result v-if="error" status="warning" :title="error" sub-title="链接可能已失效、过期或被创建者关闭" />

        <div v-else-if="passwordGate" class="share-gate">
          <h1 class="share-title">{{ gateTitle }}</h1>
          <p class="share-meta">此分享已设置访问密码</p>
          <a-form layout="vertical" @finish="handleUnlock">
            <a-form-item label="访问密码" name="password" :rules="[{ required: true, message: '请输入访问密码' }]">
              <a-input-password v-model:value="passwordInput" placeholder="请输入密码" size="large" />
            </a-form-item>
            <a-button type="primary" html-type="submit" size="large" block :loading="unlocking">
              查看内容
            </a-button>
          </a-form>
        </div>

        <template v-else-if="content">
          <article v-if="content.resourceType === 'NOTE'" class="share-note">
            <h1 class="share-title">{{ content.title || '未命名文档' }}</h1>
            <p class="share-meta">
              <span v-if="content.sharedAt">分享于 {{ formatTime(content.sharedAt) }}</span>
              <span v-if="content.viewCount != null"> · 访问 {{ content.viewCount }} 次</span>
            </p>
            <div class="share-note-body">
              <SafeMdPreview :model-value="content.content || ''" />
            </div>
          </article>

          <article v-else-if="content.resourceType === 'BATCH'" class="share-batch">
            <h1 class="share-title">{{ content.title || '批量分享' }}</h1>
            <p class="share-meta">
              <span v-if="content.sharedAt">分享于 {{ formatTime(content.sharedAt) }}</span>
              <span v-if="content.viewCount != null"> · 访问 {{ content.viewCount }} 次</span>
            </p>
            <ul class="batch-list">
              <li v-for="file in content.files || []" :key="file.id" class="batch-item">
                <div class="batch-meta">
                  <a :href="file.fileUrl" target="_blank" rel="noopener" class="batch-name">{{ file.fileName }}</a>
                  <span class="batch-size">{{ formatSize(file.fileSize || 0) }}</span>
                </div>
                <a-button type="link" size="small" :href="file.fileUrl" target="_blank" rel="noopener">下载</a-button>
              </li>
            </ul>
          </article>

          <article v-else class="share-file">
            <div class="file-card">
              <div class="file-icon">📎</div>
              <h1 class="share-title">{{ content.fileName || content.title }}</h1>
              <p class="share-meta">
                <span v-if="content.fileSize">{{ formatSize(content.fileSize) }}</span>
                <span v-if="content.sharedAt"> · 分享于 {{ formatTime(content.sharedAt) }}</span>
                <span v-if="content.viewCount != null"> · 访问 {{ content.viewCount }} 次</span>
              </p>
              <a-space>
                <a-button type="primary" size="large" :href="content.fileUrl" target="_blank" rel="noopener">
                  查看 / 下载
                </a-button>
              </a-space>
              <div v-if="isImage(content.fileType)" class="file-preview">
                <img :src="content.fileUrl" :alt="content.fileName" />
              </div>
            </div>
          </article>
        </template>
      </a-spin>
    </main>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import SafeMdPreview from '../../components/common/SafeMdPreview.vue';
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { message } from 'ant-design-vue';
import { getPublicShare, unlockPublicShare, type SharedContent } from '../../api/share';

const route = useRoute();
const loading = ref(true);
const unlocking = ref(false);
const error = ref('');
const passwordGate = ref(false);
const gateTitle = ref('受保护的分享');
const passwordInput = ref('');
const content = ref<SharedContent | null>(null);
const shareToken = ref('');

const formatTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm');

const formatSize = (bytes: number) => {
  if (!bytes || bytes < 1024) return `${bytes || 0} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const isImage = (fileType?: string) => !!fileType && fileType.startsWith('image/');

const applyContent = (data: SharedContent) => {
  if (data.passwordRequired) {
    passwordGate.value = true;
    gateTitle.value = data.title || '受保护的分享';
    content.value = null;
    return;
  }
  passwordGate.value = false;
  content.value = data;
};

const loadShare = async () => {
  const token = String(route.params.token || '');
  if (!token) {
    error.value = '无效的分享链接';
    loading.value = false;
    return;
  }
  shareToken.value = token;
  try {
    applyContent(await getPublicShare(token));
  } catch (err: any) {
    error.value = err?.message || '分享内容不可用';
  } finally {
    loading.value = false;
  }
};

const handleUnlock = async () => {
  if (!shareToken.value) return;
  unlocking.value = true;
  try {
    applyContent(await unlockPublicShare(shareToken.value, passwordInput.value));
    if (!passwordGate.value) {
      message.success('验证成功');
    }
  } catch (err: any) {
    message.error(err?.message || '密码错误');
  } finally {
    unlocking.value = false;
  }
};

onMounted(loadShare);
</script>

<style scoped>
.share-page {
  min-height: 100vh;
  background: linear-gradient(180deg, var(--noto-canvas-alt) 0%, var(--noto-pastel-violet) 100%);
}

.share-header {
  padding: 20px 24px;
}

.share-brand {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
}

.share-brand-mark {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}

.share-brand-sub {
  font-size: 13px;
  color: #64748b;
}

.share-main {
  max-width: 860px;
  margin: 0 auto;
  padding: 0 24px 48px;
}

.share-gate,
.share-note,
.share-file,
.share-batch {
  background: var(--noto-surface);
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(15, 23, 42, 0.08);
  padding: 32px;
}

.share-title {
  margin: 0 0 8px;
  font-size: 28px;
  line-height: 1.3;
  color: #0f172a;
}

.share-meta {
  margin: 0 0 24px;
  color: #64748b;
  font-size: 13px;
}

.share-note-body :deep(.md-editor-preview) {
  padding: 0;
}

.file-card {
  text-align: center;
}

.file-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.file-preview {
  margin-top: 24px;
}

.file-preview img {
  max-width: 100%;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.12);
}

.batch-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.batch-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #eef2f7;
}

.batch-item:last-child {
  border-bottom: none;
}

.batch-meta {
  min-width: 0;
  flex: 1;
}

.batch-name {
  display: block;
  color: #0891b2;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-size {
  font-size: 12px;
  color: #64748b;
}
</style>
