<template>
  <a-modal
    v-model:open="open"
    title="从网盘关联"
    ok-text="关联所选"
    cancel-text="取消"
    :confirm-loading="linking"
    :ok-button-props="{ disabled: !selectedIds.length }"
    @ok="handleOk"
  >
    <a-spin :spinning="loading">
      <a-checkbox-group v-if="candidates.length" v-model:value="selectedIds" class="file-list">
        <a-checkbox
          v-for="item in candidates"
          :key="item.id"
          :value="item.id"
          class="file-option"
        >
          <div class="file-meta">
            <span class="file-name">{{ item.fileName }}</span>
            <span class="file-size">{{ formatSize(item.fileSize) }}</span>
          </div>
        </a-checkbox>
      </a-checkbox-group>
      <p v-else class="empty-hint">网盘中暂无可关联文件，请先在网盘页上传</p>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { listDriveFiles, linkDriveFileToNote, type DriveFile } from '../../api/drive';
import { listNoteAttachments } from '../../api/attachments';

const open = defineModel<boolean>('open', { default: false });

const props = defineProps<{
  workspaceId?: string;
  noteId?: string;
}>();

const emit = defineEmits<{
  linked: [];
}>();

const files = ref<DriveFile[]>([]);
const linkedIds = ref<Set<string>>(new Set());
const selectedIds = ref<string[]>([]);
const loading = ref(false);
const linking = ref(false);

const candidates = computed(() =>
  files.value.filter((item) => !linkedIds.value.has(item.id)),
);

const formatSize = (bytes: number) => {
  if (!bytes || bytes < 1024) return `${bytes || 0} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const loadData = async () => {
  if (!props.workspaceId || !props.noteId) {
    files.value = [];
    linkedIds.value = new Set();
    return;
  }
  loading.value = true;
  try {
    const [driveFiles, noteFiles] = await Promise.all([
      listDriveFiles({ workspaceId: props.workspaceId }),
      listNoteAttachments(props.noteId),
    ]);
    files.value = driveFiles;
    linkedIds.value = new Set(noteFiles.map((item) => item.id));
    selectedIds.value = [];
  } catch (error: any) {
    message.error(error?.message || '加载网盘文件失败');
  } finally {
    loading.value = false;
  }
};

watch(
  () => [open.value, props.workspaceId, props.noteId] as const,
  ([visible]) => {
    if (visible) {
      void loadData();
    }
  },
);

const handleOk = async () => {
  if (!props.noteId || !selectedIds.value.length) return;
  linking.value = true;
  try {
    await Promise.all(
      selectedIds.value.map((attachmentId) => linkDriveFileToNote(attachmentId, props.noteId!)),
    );
    message.success(`已关联 ${selectedIds.value.length} 个文件`);
    open.value = false;
    emit('linked');
  } catch (error: any) {
    message.error(error?.message || '关联失败');
  } finally {
    linking.value = false;
  }
};
</script>

<style scoped>
.file-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  max-height: 360px;
  overflow: auto;
}

.file-option {
  display: flex;
  align-items: flex-start;
  width: 100%;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--noto-border-soft, #eef2f7);
}

.file-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.file-name {
  font-size: 13px;
  color: var(--noto-text, #0f172a);
  word-break: break-all;
}

.file-size {
  font-size: 11px;
  color: var(--noto-text-muted, #94a3b8);
}

.empty-hint {
  margin: 0;
  padding: 24px 0;
  text-align: center;
  color: var(--noto-text-muted, #94a3b8);
  font-size: 13px;
}
</style>
