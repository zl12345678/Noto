<template>
  <a-modal
    v-model:open="visible"
    title="导入文档"
    width="720px"
    :confirm-loading="loading"
    ok-text="导入"
    cancel-text="取消"
    @ok="handleSubmit"
  >
    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="clip" tab="剪藏粘贴">
        <p class="modal-desc">粘贴网页 / Markdown / 纯文本，可选 AI 结构化。</p>
        <a-form layout="vertical">
          <a-form-item label="知识库" required>
            <a-select
              v-model:value="form.workspaceId"
              placeholder="选择知识库"
              :options="workspaceOptions"
            />
          </a-form-item>

          <a-form-item label="放入分组">
            <a-select
              v-model:value="form.folderId"
              allow-clear
              placeholder="顶级（无分组）"
              :options="folderOptions"
            />
          </a-form-item>

          <a-form-item label="标题" required>
            <a-input v-model:value="form.title" placeholder="文档标题" allow-clear />
          </a-form-item>

          <a-form-item label="来源 URL（可选）">
            <a-input v-model:value="form.sourceUrl" placeholder="https://..." allow-clear />
          </a-form-item>

          <a-form-item label="内容格式">
            <a-radio-group v-model:value="form.contentType" option-type="button" :options="contentTypeOptions" />
          </a-form-item>

          <a-form-item label="内容" required>
            <a-textarea
              v-model:value="form.content"
              :rows="10"
              placeholder="粘贴剪藏正文…"
              allow-clear
            />
          </a-form-item>

          <a-form-item>
            <a-checkbox v-model:checked="form.structureWithAi">导入后用 AI 结构化为会议纪要</a-checkbox>
          </a-form-item>
        </a-form>
      </a-tab-pane>

      <a-tab-pane key="file" tab="本地文件">
        <p class="modal-desc">支持 .md / .markdown / .txt，可多选批量导入。</p>
        <a-form layout="vertical">
          <a-form-item label="知识库" required>
            <a-select
              v-model:value="form.workspaceId"
              placeholder="选择知识库"
              :options="workspaceOptions"
            />
          </a-form-item>

          <a-form-item label="放入分组">
            <a-select
              v-model:value="form.folderId"
              allow-clear
              placeholder="顶级（无分组）"
              :options="folderOptions"
            />
          </a-form-item>

          <a-form-item label="选择文件" required>
            <a-upload-dragger
              v-model:file-list="fileList"
              :before-upload="beforeUpload"
              :multiple="true"
              accept=".md,.markdown,.txt,text/plain,text/markdown"
              :show-upload-list="{ showRemoveIcon: true }"
            >
              <p class="upload-icon">📄</p>
              <p class="upload-title">点击或拖拽 Markdown / 文本文件到此处</p>
              <p class="upload-hint">将按文件名创建文档标题，也可一次选择多个文件</p>
            </a-upload-dragger>
          </a-form-item>
        </a-form>
      </a-tab-pane>
    </a-tabs>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import type { UploadFile } from 'ant-design-vue';
import { importNoteClip, importNoteFile } from '../../api/notes';
import type { Workspace } from '../../api/workspaces';

const props = defineProps<{
  workspaces: Workspace[];
  defaultWorkspaceId?: string;
  /** 打开弹窗时预填的分组（如从文件夹右键导入） */
  defaultFolderId?: string | null;
  folderOptions?: Array<{ label: string; value: string }>;
}>();

const visible = defineModel<boolean>('open', { default: false });

const emit = defineEmits<{
  imported: [
    payload: {
      noteIds: string[];
      workspaceId: string;
      folderId: string | null;
    },
  ];
}>();

const router = useRouter();
const loading = ref(false);
const activeTab = ref<'clip' | 'file'>('clip');
const fileList = ref<UploadFile[]>([]);

const form = reactive({
  workspaceId: '' as string,
  folderId: undefined as string | undefined,
  title: '',
  sourceUrl: '',
  content: '',
  contentType: 'plain' as 'plain' | 'markdown' | 'html',
  structureWithAi: false,
});

const contentTypeOptions = [
  { label: '纯文本', value: 'plain' },
  { label: 'Markdown', value: 'markdown' },
  { label: 'HTML', value: 'html' },
];

const workspaceOptions = computed(() =>
  props.workspaces.map((item) => ({ label: item.name, value: item.id })),
);

const folderOptions = computed(() => props.folderOptions ?? []);

const resetForm = () => {
  form.title = '';
  form.sourceUrl = '';
  form.content = '';
  form.contentType = 'plain';
  form.structureWithAi = false;
  form.folderId = undefined;
  fileList.value = [];
  activeTab.value = 'clip';
};

const beforeUpload = () => false;

const isSupportedFile = (file: File) => {
  const name = file.name.toLowerCase();
  return name.endsWith('.md') || name.endsWith('.markdown') || name.endsWith('.txt');
};

watch(visible, (open) => {
  if (!open) {
    resetForm();
    return;
  }
  if (!form.workspaceId && props.defaultWorkspaceId) {
    form.workspaceId = props.defaultWorkspaceId;
  } else if (!form.workspaceId && props.workspaces[0]) {
    form.workspaceId = props.workspaces[0].id;
  }
  if (props.defaultFolderId) {
    form.folderId = props.defaultFolderId;
  } else {
    form.folderId = undefined;
  }
});

const handleSubmit = async () => {
  if (!form.workspaceId) {
    message.warning('请选择知识库');
    return Promise.reject();
  }

  if (activeTab.value === 'file') {
    return handleFileImport();
  }

  if (!form.title.trim()) {
    message.warning('请输入标题');
    return Promise.reject();
  }
  if (!form.content.trim()) {
    message.warning('请输入内容');
    return Promise.reject();
  }

  loading.value = true;
  try {
    const result = await importNoteClip({
      workspaceId: form.workspaceId,
      title: form.title.trim(),
      content: form.content.trim(),
      sourceUrl: form.sourceUrl.trim() || undefined,
      contentType: form.contentType,
      folderId: form.folderId ?? null,
      structureWithAi: form.structureWithAi,
    });
    message.success(result.structured ? '已导入并完成 AI 结构化' : '已导入到知识库');
    visible.value = false;
    emit('imported', {
      noteIds: [result.noteId],
      workspaceId: form.workspaceId,
      folderId: form.folderId ?? null,
    });
    router.push({
      path: `/notes/${result.noteId}`,
      query: {
        from: 'import',
        extract: '1',
        ...(form.workspaceId ? { workspace: form.workspaceId } : {}),
      },
    });
  } catch (error: any) {
    message.error(error?.message || '导入失败');
    return Promise.reject();
  } finally {
    loading.value = false;
  }
};

const handleFileImport = async () => {
  const rawFiles = fileList.value
    .map((item) => item.originFileObj)
    .filter((file): file is File => file instanceof File);

  if (!rawFiles.length) {
    message.warning('请选择至少一个文件');
    return Promise.reject();
  }

  const unsupported = rawFiles.find((file) => !isSupportedFile(file));
  if (unsupported) {
    message.warning(`不支持该文件类型：${unsupported.name}`);
    return Promise.reject();
  }

  loading.value = true;
  try {
    let lastNoteId = '';
    for (const file of rawFiles) {
      const result = await importNoteFile(form.workspaceId, file, form.folderId ?? null);
      lastNoteId = result.noteId;
    }
    message.success(rawFiles.length > 1 ? `已导入 ${rawFiles.length} 篇文档` : '已导入到知识库');
    visible.value = false;
    if (lastNoteId) {
      emit('imported', {
        noteIds: [lastNoteId],
        workspaceId: form.workspaceId,
        folderId: form.folderId ?? null,
      });
      router.push({
        path: `/notes/${lastNoteId}`,
        query: {
          from: 'import',
          ...(form.workspaceId ? { workspace: form.workspaceId } : {}),
        },
      });
    }
  } catch (error: any) {
    message.error(error?.message || '导入失败');
    return Promise.reject();
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.modal-desc {
  margin: 0 0 16px;
  color: var(--noto-text-muted, #64748b);
  font-size: 13px;
  line-height: 1.6;
}

.upload-icon {
  margin: 0 0 8px;
  font-size: 28px;
}

.upload-title {
  margin: 0 0 4px;
  font-size: 14px;
  color: var(--noto-text);
}

.upload-hint {
  margin: 0;
  font-size: 12px;
  color: var(--noto-text-muted, #64748b);
}
</style>
