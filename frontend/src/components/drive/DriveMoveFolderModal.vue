<template>
  <a-modal
    v-model:open="open"
    title="移动至文件夹"
    ok-text="移动"
    cancel-text="取消"
    :confirm-loading="moving"
    :ok-button-props="{ disabled: !canSubmit }"
    @ok="handleOk"
  >
    <p v-if="fileName" class="file-hint">
      文件：<span class="file-name">{{ fileName }}</span>
    </p>
    <a-input-search
      v-model:value="keyword"
      placeholder="搜索文件夹名称或路径"
      allow-clear
      class="search-input"
    />
    <a-spin :spinning="loading">
      <a-radio-group v-model:value="selectedTarget" class="folder-list">
        <a-radio value="uncategorized" class="folder-option">
          <span class="folder-label">未分类</span>
        </a-radio>
        <a-radio
          v-for="item in filteredFolders"
          :key="item.folder.id"
          :value="item.folder.id"
          class="folder-option"
        >
          <span class="folder-label" :style="{ paddingLeft: `${item.depth * 12}px` }">
            {{ item.folder.name }}
          </span>
          <span v-if="item.path" class="folder-path">{{ item.path }}</span>
        </a-radio>
      </a-radio-group>
      <p v-if="!filteredFolders.length && keyword.trim()" class="empty-hint">未找到匹配的文件夹</p>
      <p v-else-if="!folders.length" class="empty-hint">暂无文件夹，可选择「未分类」</p>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { moveDriveFileToFolder, type DriveFolder } from '../../api/drive';
import {
  buildDriveFolderTree,
  flattenDriveFolderTree,
  getDriveFolderPath,
} from '../../utils/driveFolderTree';

const open = defineModel<boolean>('open', { default: false });

const props = defineProps<{
  attachmentId?: string;
  fileName?: string;
  folders: DriveFolder[];
  currentFolderId?: string | null;
  loading?: boolean;
}>();

const emit = defineEmits<{
  moved: [];
}>();

const keyword = ref('');
const selectedTarget = ref<string>();
const moving = ref(false);

const flatFolders = computed(() => {
  const items = flattenDriveFolderTree(buildDriveFolderTree(props.folders)).map((item) => ({
    ...item,
    path: getDriveFolderPath(item.folder.id, props.folders),
  }));
  return items;
});

const filteredFolders = computed(() => {
  const q = keyword.value.trim().toLowerCase();
  if (!q) return flatFolders.value;
  return flatFolders.value.filter(
    (item) =>
      item.folder.name.toLowerCase().includes(q) ||
      item.path.toLowerCase().includes(q),
  );
});

const canSubmit = computed(() => Boolean(props.attachmentId && selectedTarget.value));

watch(
  () => [open.value, props.attachmentId, props.currentFolderId] as const,
  ([visible]) => {
    if (!visible) return;
    keyword.value = '';
    selectedTarget.value = props.currentFolderId ?? 'uncategorized';
  },
);

const handleOk = async () => {
  if (!props.attachmentId || !selectedTarget.value) return;
  moving.value = true;
  try {
    const folderId = selectedTarget.value === 'uncategorized' ? null : selectedTarget.value;
    await moveDriveFileToFolder(props.attachmentId, folderId);
    message.success('已移动');
    open.value = false;
    emit('moved');
  } catch (error: any) {
    message.error(error?.message || '移动失败');
  } finally {
    moving.value = false;
  }
};
</script>

<style scoped>
.file-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--noto-text-muted, #64748b);
}

.file-name {
  color: var(--noto-text, #0f172a);
  font-weight: 600;
}

.search-input {
  margin-bottom: 12px;
}

.folder-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  max-height: 360px;
  overflow: auto;
  padding-right: 4px;
}

.folder-option {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--noto-border-soft, #eef2f7);
}

.folder-option :deep(.ant-radio) {
  margin-top: 2px;
}

.folder-label {
  display: block;
  font-size: 13px;
  color: var(--noto-text, #0f172a);
  line-height: 1.4;
}

.folder-path {
  display: block;
  margin-top: 2px;
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
