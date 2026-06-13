<template>
  <a-modal
    v-model:open="open"
    title="关联到文档"
    ok-text="关联"
    cancel-text="取消"
    :confirm-loading="linking"
    :ok-button-props="{ disabled: !selectedNoteId }"
    @ok="handleOk"
  >
    <a-input-search
      v-model:value="keyword"
      placeholder="搜索文档标题"
      allow-clear
      class="search-input"
      @search="loadNotes"
    />
    <a-spin :spinning="loading">
      <a-radio-group v-if="notes.length" v-model:value="selectedNoteId" class="note-list">
        <a-radio v-for="item in notes" :key="item.id" :value="item.id" class="note-option">
          <span class="note-title">{{ item.title || '未命名文档' }}</span>
        </a-radio>
      </a-radio-group>
      <p v-else class="empty-hint">暂无可选文档</p>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { listNotes, type Note } from '../../api/notes';
import { linkDriveFileToNote } from '../../api/drive';

const open = defineModel<boolean>('open', { default: false });

const props = defineProps<{
  workspaceId?: string;
  attachmentId?: string;
}>();

const emit = defineEmits<{
  linked: [];
}>();

const keyword = ref('');
const notes = ref<Note[]>([]);
const selectedNoteId = ref<string>();
const loading = ref(false);
const linking = ref(false);

const loadNotes = async () => {
  if (!props.workspaceId) {
    notes.value = [];
    return;
  }
  loading.value = true;
  try {
    const page = await listNotes({
      workspaceId: props.workspaceId,
      keyword: keyword.value.trim() || undefined,
      size: 50,
      page: 1,
    });
    notes.value = page.records;
    if (!notes.value.some((item) => item.id === selectedNoteId.value)) {
      selectedNoteId.value = undefined;
    }
  } catch (error: any) {
    message.error(error?.message || '加载文档失败');
  } finally {
    loading.value = false;
  }
};

watch(
  () => [open.value, props.workspaceId] as const,
  ([visible]) => {
    if (visible) {
      keyword.value = '';
      selectedNoteId.value = undefined;
      void loadNotes();
    }
  },
);

const handleOk = async () => {
  if (!props.attachmentId || !selectedNoteId.value) return;
  linking.value = true;
  try {
    await linkDriveFileToNote(props.attachmentId, selectedNoteId.value);
    message.success('已关联到文档');
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
.search-input {
  margin-bottom: 12px;
}

.note-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  max-height: 320px;
  overflow: auto;
}

.note-option {
  display: flex;
  align-items: flex-start;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--noto-border-soft, #eef2f7);
}

.note-title {
  font-size: 13px;
  color: var(--noto-text, #0f172a);
}

.empty-hint {
  margin: 0;
  padding: 24px 0;
  text-align: center;
  color: var(--noto-text-muted, #94a3b8);
  font-size: 13px;
}
</style>
