<template>
  <a-modal
    v-model:open="visible"
    title="跨文档合成"
    width="720px"
    :confirm-loading="loading"
    ok-text="生成并保存为新笔记"
    cancel-text="取消"
    @ok="handleSubmit"
  >
    <p class="modal-desc">按主题或时间范围聚合多篇笔记，生成决策记录、项目现状或周报草稿。</p>

    <a-form layout="vertical">
      <a-form-item label="知识库" required>
        <a-select
          v-model:value="form.workspaceId"
          placeholder="选择知识库"
          :options="workspaceOptions"
        />
      </a-form-item>

      <a-form-item label="主题 / 关键词" required>
        <a-input v-model:value="form.topic" placeholder="例如：产品迭代、Q2 规划" allow-clear />
      </a-form-item>

      <a-form-item label="输出模板" required>
        <a-radio-group v-model:value="form.template" option-type="button" :options="templateOptions" />
      </a-form-item>

      <a-form-item label="时间范围（可选）">
        <a-space>
          <a-date-picker v-model:value="form.dateFrom" placeholder="起始日期" value-format="YYYY-MM-DD" />
          <span>至</span>
          <a-date-picker v-model:value="form.dateTo" placeholder="结束日期" value-format="YYYY-MM-DD" />
        </a-space>
        <p class="field-hint">留空则按主题检索最近相关文档</p>
      </a-form-item>

      <a-form-item>
        <a-checkbox v-model:checked="form.saveAsNote">生成后保存为新笔记</a-checkbox>
      </a-form-item>
    </a-form>

    <div v-if="preview" class="preview-panel">
      <div class="preview-head">
        <h4>{{ preview.title }}</h4>
        <a-space>
          <a-button v-if="preview.createdNoteId" type="link" size="small" @click="openCreatedNote">
            打开新笔记
          </a-button>
        </a-space>
      </div>
      <p v-if="preview.sources?.length" class="source-hint">
        已综合 {{ preview.sources.length }} 篇文档：
        {{ preview.sources.map((s) => s.noteTitle).join('、') }}
      </p>
      <pre class="preview-content">{{ preview.content }}</pre>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { synthesizeNotesByAi, type NoteSynthesizeResult, type NoteSynthesizeTemplate } from '../../api/ai';
import type { Workspace } from '../../api/workspaces';

const props = defineProps<{
  workspaces: Workspace[];
  defaultWorkspaceId?: string;
}>();

const visible = defineModel<boolean>('open', { default: false });

const router = useRouter();
const loading = ref(false);
const preview = ref<NoteSynthesizeResult | null>(null);

const form = reactive({
  workspaceId: '' as string,
  topic: '',
  template: 'weekly' as NoteSynthesizeTemplate,
  dateFrom: undefined as string | undefined,
  dateTo: undefined as string | undefined,
  saveAsNote: true,
});

const templateOptions = [
  { label: '周报', value: 'weekly' },
  { label: '项目现状', value: 'status' },
  { label: '决策记录', value: 'decision' },
  { label: '复盘', value: 'retro' },
];

const workspaceOptions = computed(() =>
  props.workspaces.map((item) => ({ label: item.name, value: item.id })),
);

watch(visible, (open) => {
  if (!open) {
    preview.value = null;
    return;
  }
  if (!form.workspaceId && props.defaultWorkspaceId) {
    form.workspaceId = props.defaultWorkspaceId;
  } else if (!form.workspaceId && props.workspaces[0]) {
    form.workspaceId = props.workspaces[0].id;
  }
});

const openCreatedNote = () => {
  if (!preview.value?.createdNoteId) return;
  router.push({
    path: `/notes/${preview.value.createdNoteId}`,
    query: {
      from: 'ai',
      ...(form.workspaceId ? { workspace: form.workspaceId } : {}),
    },
  });
  visible.value = false;
};

const handleSubmit = async () => {
  if (!form.workspaceId) {
    message.warning('请选择知识库');
    return Promise.reject();
  }
  if (!form.topic.trim()) {
    message.warning('请输入主题');
    return Promise.reject();
  }

  loading.value = true;
  try {
    const result = await synthesizeNotesByAi({
      workspaceId: form.workspaceId,
      topic: form.topic.trim(),
      template: form.template,
      dateFrom: form.dateFrom,
      dateTo: form.dateTo,
      saveAsNote: form.saveAsNote,
    });
    preview.value = result;
    if (result.createdNoteId) {
      message.success('已生成并保存为新笔记');
      visible.value = false;
      router.push({
        path: `/notes/${result.createdNoteId}`,
        query: {
          from: 'ai',
          ...(form.workspaceId ? { workspace: form.workspaceId } : {}),
        },
      });
      return;
    }
    message.success('合成完成');
  } catch (error: any) {
    message.error(error?.message || '合成失败');
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
}

.field-hint {
  margin: 6px 0 0;
  color: #94a3b8;
  font-size: 12px;
}

.preview-panel {
  margin-top: 16px;
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.preview-head h4 {
  margin: 0;
  font-size: 14px;
  color: #0f172a;
}

.source-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #64748b;
}

.preview-content {
  margin: 10px 0 0;
  max-height: 280px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
  color: #334155;
}
</style>
