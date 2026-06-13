<template>
  <div v-if="noteId" class="attachment-panel" :class="{ embedded }">
    <div v-if="!embedded" class="panel-head">
      <span class="panel-title">附件</span>
      <span class="panel-count">{{ attachments.length }}</span>
      <a-space :size="4">
        <a-button type="link" size="small" :disabled="!workspaceId || !noteId" @click="pickModalOpen = true">
          从网盘
        </a-button>
        <a-upload :show-upload-list="false" :before-upload="handleUpload" :disabled="uploading">
          <a-button type="link" size="small" :loading="uploading">上传</a-button>
        </a-upload>
      </a-space>
    </div>

    <div v-else class="panel-head embedded-head">
      <a-space :size="8">
        <a-upload :show-upload-list="false" :before-upload="handleUpload" :disabled="uploading">
          <a-button type="primary" size="small" :loading="uploading">上传附件</a-button>
        </a-upload>
        <a-button size="small" :disabled="!workspaceId || !noteId" @click="pickModalOpen = true">
          从网盘关联
        </a-button>
      </a-space>
      <span class="panel-count">{{ attachments.length }} 个文件</span>
    </div>

    <a-spin :spinning="loading">
      <ul v-if="attachments.length" class="attachment-list">
        <li v-for="item in attachments" :key="item.id" class="attachment-item">
          <div class="attachment-meta">
            <a :href="item.fileUrl" target="_blank" rel="noopener" class="attachment-name">
              {{ item.fileName }}
            </a>
            <span class="attachment-size">{{ formatSize(item.fileSize) }}</span>
          </div>
          <a-space :size="0">
            <a-button type="text" size="small" @click="handleInsert(item)">插入正文</a-button>
            <a-button type="text" size="small" @click="openShare(item)">分享</a-button>
            <a-button type="text" size="small" danger @click="handleUnlink(item)">解除关联</a-button>
          </a-space>
        </li>
      </ul>
      <p v-else class="empty-hint">暂无附件，可上传或从网盘关联</p>
    </a-spin>

    <DrivePickModal
      v-model:open="pickModalOpen"
      :workspace-id="workspaceId"
      :note-id="noteId"
      @linked="loadAttachments"
    />

    <ShareLinkModal
      v-model:open="shareModalOpen"
      resource-type="ATTACHMENT"
      :resource-id="shareTargetId"
      title="分享附件"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { Modal, message } from 'ant-design-vue';
import DrivePickModal from '../drive/DrivePickModal.vue';
import ShareLinkModal from '../share/ShareLinkModal.vue';
import {
  listNoteAttachments,
  unlinkAttachmentFromNote,
  uploadAttachment,
  type AttachmentVO,
} from '../../api/attachments';
import { buildAttachmentMarkdown } from '../../utils/attachmentMarkdown';

const props = defineProps<{
  noteId?: string;
  workspaceId?: string;
  embedded?: boolean;
}>();

const emit = defineEmits<{
  updated: [count: number];
  insert: [markdown: string];
}>();

const attachments = ref<AttachmentVO[]>([]);
const loading = ref(false);
const uploading = ref(false);
const pickModalOpen = ref(false);
const shareModalOpen = ref(false);
const shareTargetId = ref<string>();

const notifyUpdated = () => {
  emit('updated', attachments.value.length);
};

const loadAttachments = async () => {
  if (!props.noteId) {
    attachments.value = [];
    notifyUpdated();
    return;
  }
  loading.value = true;
  try {
    attachments.value = await listNoteAttachments(props.noteId);
    notifyUpdated();
  } catch (error: any) {
    message.error(error?.message || '加载附件失败');
  } finally {
    loading.value = false;
  }
};

watch(
  () => props.noteId,
  () => {
    void loadAttachments();
  },
  { immediate: true },
);

const formatSize = (bytes: number) => {
  if (!bytes || bytes < 1024) return `${bytes || 0} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const handleUpload = async (file: File) => {
  if (!props.noteId) {
    message.warning('请先保存文档');
    return false;
  }
  uploading.value = true;
  try {
    await uploadAttachment(props.noteId, file);
    message.success('上传成功');
    await loadAttachments();
  } catch (error: any) {
    message.error(error?.message || '上传失败');
  } finally {
    uploading.value = false;
  }
  return false;
};

const handleInsert = (item: AttachmentVO) => {
  emit('insert', buildAttachmentMarkdown(item));
  message.success('已插入到正文');
};

const openShare = (item: AttachmentVO) => {
  shareTargetId.value = item.id;
  shareModalOpen.value = true;
};

const handleUnlink = (item: AttachmentVO) => {
  if (!props.noteId) return;
  Modal.confirm({
    title: '解除与本文档的关联？',
    content: `${item.fileName} 仍保留在网盘中`,
    okText: '解除关联',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await unlinkAttachmentFromNote(item.id, props.noteId!);
      message.success('已解除关联');
      await loadAttachments();
    },
  });
};

defineExpose({ reload: loadAttachments });
</script>

<style scoped>
.attachment-panel {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
}

.attachment-panel.embedded {
  margin: 0;
  padding: 14px;
  border: none;
  border-radius: 0;
  background: transparent;
}

.panel-head,
.embedded-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.embedded-head {
  justify-content: space-between;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.panel-count {
  font-size: 12px;
  color: #94a3b8;
}

.attachment-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.attachment-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #eef2f7;
}

.embedded .attachment-item {
  background: #f8fafc;
}

.attachment-meta {
  min-width: 0;
  flex: 1;
}

.attachment-name {
  display: block;
  font-size: 13px;
  color: #2563eb;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.attachment-size {
  font-size: 11px;
  color: #94a3b8;
}

.empty-hint {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
  padding: 24px 0;
}
</style>
