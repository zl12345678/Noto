<template>
  <a-modal
    v-model:open="visible"
    title="导入剪藏"
    width="720px"
    :confirm-loading="loading"
    ok-text="导入到知识库"
    cancel-text="取消"
    @ok="handleSubmit"
  >
    <p class="modal-desc">粘贴网页/Markdown/纯文本，可选 AI 结构化。</p>

    <a-form layout="vertical">
      <a-form-item label="知识库" required>
        <a-select
          v-model:value="form.workspaceId"
          placeholder="选择知识库"
          :options="workspaceOptions"
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
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { importNoteClip } from '../../api/notes';
import type { Workspace } from '../../api/workspaces';

const props = defineProps<{
  workspaces: Workspace[];
  defaultWorkspaceId?: string;
}>();

const visible = defineModel<boolean>('open', { default: false });

const router = useRouter();
const loading = ref(false);

const form = reactive({
  workspaceId: '' as string,
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

const resetForm = () => {
  form.title = '';
  form.sourceUrl = '';
  form.content = '';
  form.contentType = 'plain';
  form.structureWithAi = false;
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
});

const handleSubmit = async () => {
  if (!form.workspaceId) {
    message.warning('请选择知识库');
    return Promise.reject();
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
      structureWithAi: form.structureWithAi,
    });
    message.success(result.structured ? '已导入并完成 AI 结构化' : '已导入到知识库');
    visible.value = false;
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
</script>

<style scoped>
.modal-desc {
  margin: 0 0 16px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}
</style>
