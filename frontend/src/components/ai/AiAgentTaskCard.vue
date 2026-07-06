<template>
  <div v-if="!readOnlyListMode" class="agent-task-card">
    <div class="result-head">
      <div>
        <p class="result-label">执行方案</p>
        <p v-if="task.instruction" class="instruction-text">{{ task.instruction }}</p>
      </div>
      <a-space size="small" wrap>
        <a-tag :color="statusColor(task.status)">{{ statusLabel(task.status) }}</a-tag>
        <a-tag v-if="task.autoExecuted" color="purple">信任模式已自动执行</a-tag>
      </a-space>
    </div>

    <p v-if="task.assistantReply && !hideAssistantReply" class="assistant-reply">{{ task.assistantReply }}</p>

    <div v-if="actionableSteps.length" class="plan-overview">
      <span class="field-label">勾选需要执行的项目（不必全部执行）</span>
      <p>{{ planOverview }}</p>
    </div>

    <div v-if="task.steps?.length" class="step-list">
      <div
        v-for="(step, index) in task.steps"
        :key="step.id"
        class="step-row"
        :class="stepRowClass(step)"
      >
        <a-checkbox
          v-if="step.status === 'pending_confirm'"
          :checked="selectedStepIds.has(step.id)"
          class="step-check"
          @update:checked="(checked: boolean) => toggleStep(step.id, checked)"
        />
        <div v-else class="step-check-placeholder" />

        <div class="step-index">{{ index + 1 }}</div>
        <div class="step-body">
          <div class="step-title-row">
            <strong>{{ present(step).title }}</strong>
            <a-tag v-if="stepCategory(step)" size="small">{{ stepCategory(step) }}</a-tag>
            <a-tag v-if="step.status === 'done'" color="success" size="small">已完成</a-tag>
            <a-tag v-else-if="step.status === 'skipped'" size="small">已跳过</a-tag>
            <a-tag v-else-if="step.status === 'pending_confirm'" color="warning" size="small">待确认</a-tag>
            <a-button
              v-if="step.status === 'pending_confirm'"
              type="link"
              size="small"
              class="step-edit"
              @click="openEdit(step)"
            >
              修改
            </a-button>
          </div>
          <p class="step-summary">{{ presentDone(step) || present(step).summary }}</p>
          <p v-if="present(step).detail && step.status === 'pending_confirm'" class="step-detail">
            {{ present(step).detail }}
          </p>
        </div>
      </div>
    </div>

    <div v-if="pendingSteps.length" class="action-bar">
      <a-button
        type="primary"
        :loading="confirming"
        :disabled="selectedPendingCount === 0"
        @click="confirmSelected"
      >
        执行已选（{{ selectedPendingCount }} 项）
      </a-button>
      <span class="action-hint">{{ actionHint }}</span>
    </div>

    <a-result
      v-else-if="showDoneResult"
      status="success"
      title="处理完成"
      sub-title="已执行你选中的项目，可在笔记、待办和提醒中查看"
      class="done-result"
    />

    <a-modal
      v-model:open="editOpen"
      title="修改待确认项"
      ok-text="保存修改"
      cancel-text="取消"
      :confirm-loading="savingEdit"
      @ok="saveEdit"
    >
      <a-form layout="vertical" class="edit-form">
        <a-form-item
          v-for="field in editFields"
          :key="field.key"
          :label="field.label"
        >
          <a-textarea
            v-if="field.multiline"
            v-model:value="editForm[field.key]"
            :rows="4"
            :placeholder="field.placeholder"
          />
          <a-input
            v-else
            v-model:value="editForm[field.key]"
            :placeholder="field.placeholder"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  AI_TASK_STATUS,
  AI_TASK_STATUS_LABEL,
  confirmAgentTask,
  updateAgentTaskStep,
  type AiAgentStep,
  type AiAgentTask,
} from '../../api/aiAgent';
import {
  buildPlanOverview,
  presentStep,
  presentStepDone,
  stepCategoryLabel,
} from '../../utils/agentStepPresentation';
import { isReadOnlyListTask } from '../../utils/aiMessageFormat';

const props = defineProps<{
  task: AiAgentTask;
  hideAssistantReply?: boolean;
}>();

const emit = defineEmits<{
  taskUpdated: [task: AiAgentTask];
}>();

const confirming = ref(false);
const savingEdit = ref(false);
const editOpen = ref(false);
const editingStep = ref<AiAgentStep | null>(null);
const editForm = ref<Record<string, string>>({});
const selectedStepIds = ref<Set<string>>(new Set());

const pendingSteps = computed(
  () => props.task.steps?.filter((step) => step.status === 'pending_confirm') || [],
);

const actionableSteps = computed(
  () => props.task.steps?.filter((step) => isActionStep(step.tool)) || [],
);

const selectedPendingCount = computed(
  () => pendingSteps.value.filter((step) => selectedStepIds.value.has(step.id)).length,
);

const readOnlyListMode = computed(() => isReadOnlyListTask(props.task.steps));

const planOverview = computed(() =>
  actionableSteps.value.length ? buildPlanOverview(actionableSteps.value) : '',
);

const showDoneResult = computed(() => {
  if (pendingSteps.value.length > 0) return false;
  return props.task.steps?.some((s) => s.status === 'done' || s.status === 'skipped') ?? false;
});

const actionHint = computed(() => {
  const selected = pendingSteps.value.filter((s) => selectedStepIds.value.has(s.id));
  const hasReminder = selected.some((s) => s.tool === 'createReminder');
  const hasTodo = selected.some((s) => s.tool === 'createTodo' || s.tool === 'extractTodos');
  if (hasReminder && !hasTodo) {
    return '仅选提醒时，系统会自动创建对应待办';
  }
  return '未勾选的项将跳过，确认后才会写入';
});

watch(
  () => props.task.steps,
  (steps) => {
    const next = new Set<string>();
    steps?.forEach((step) => {
      if (step.status === 'pending_confirm') {
        next.add(step.id);
      }
    });
    selectedStepIds.value = next;
  },
  { immediate: true, deep: true },
);

const statusLabel = (status: number) => {
  if (status === AI_TASK_STATUS.AWAITING_CONFIRM) return '待确认';
  if (status === AI_TASK_STATUS.SUCCESS) return '已完成';
  if (status === AI_TASK_STATUS.FAILED) return '失败';
  if (status === AI_TASK_STATUS.PROCESSING) return '处理中';
  return AI_TASK_STATUS_LABEL[status] || '未知';
};

const statusColor = (status: number) => {
  if (status === AI_TASK_STATUS.SUCCESS) return 'success';
  if (status === AI_TASK_STATUS.FAILED) return 'error';
  if (status === AI_TASK_STATUS.AWAITING_CONFIRM) return 'warning';
  if (status === AI_TASK_STATUS.PROCESSING) return 'processing';
  return 'default';
};

const present = (step: AiAgentStep) => presentStep(step);
const presentDone = (step: AiAgentStep) => presentStepDone(step);
const stepCategory = (step: AiAgentStep) => stepCategoryLabel(step.tool);
const editFields = computed(() => buildEditableFields(editingStep.value));

function isActionStep(tool: string) {
  return ['createNote', 'extractTodos', 'createTodo', 'createReminder'].includes(tool);
}

function stepRowClass(step: AiAgentStep) {
  if (step.status === 'done') return 'done';
  if (step.status === 'skipped') return 'skipped';
  if (step.status === 'pending_confirm') return 'pending';
  return '';
}

function toggleStep(stepId: string, checked: boolean) {
  const next = new Set(selectedStepIds.value);
  if (checked) {
    next.add(stepId);
  } else {
    next.delete(stepId);
  }
  selectedStepIds.value = next;
}

function openEdit(step: AiAgentStep) {
  editingStep.value = step;
  const payload = step.actionPayload || {};
  const fields = buildEditableFields(step);
  const next: Record<string, string> = {};
  fields.forEach((field) => {
    const value = payload[field.key];
    next[field.key] = value == null ? '' : String(value);
  });
  editForm.value = next;
  editOpen.value = true;
}

const saveEdit = async () => {
  if (!editingStep.value) return;
  savingEdit.value = true;
  try {
    const original = editingStep.value.actionPayload || {};
    const nextPayload: Record<string, unknown> = { ...original };
    editFields.value.forEach((field) => {
      nextPayload[field.key] = editForm.value[field.key]?.trim() || '';
    });
    const updated = await updateAgentTaskStep(props.task.id, editingStep.value.id, nextPayload);
    emit('taskUpdated', updated);
    editOpen.value = false;
    editingStep.value = null;
    message.success('方案已修改');
  } catch (error: any) {
    message.error(error?.message || '修改失败');
  } finally {
    savingEdit.value = false;
  }
};

const confirmSelected = async () => {
  if (!selectedPendingCount.value) return;
  confirming.value = true;
  try {
    const ordered = [...(props.task.steps || [])].sort((a, b) => stepOrder(a.id) - stepOrder(b.id));
    const stepIds = ordered
      .filter((s) => s.status === 'pending_confirm' && selectedStepIds.value.has(s.id))
      .map((s) => s.id);
    const updated = await confirmAgentTask(props.task.id, stepIds);
    emit('taskUpdated', updated);
    message.success('已执行选中的项目');
  } catch (error: any) {
    message.error(error?.message || '执行失败');
  } finally {
    confirming.value = false;
  }
};

function stepOrder(stepId: string): number {
  const digits = stepId.replace(/\D/g, '');
  return digits ? Number(digits) : Number.MAX_SAFE_INTEGER;
}

interface EditableField {
  key: string;
  label: string;
  placeholder?: string;
  multiline?: boolean;
}

function buildEditableFields(step: AiAgentStep | null): EditableField[] {
  if (!step) return [];
  const payload = step.actionPayload || {};
  const preferred = preferredEditableFields(step.tool);
  const fields = preferred.filter((field) => Object.prototype.hasOwnProperty.call(payload, field.key));
  Object.keys(payload).forEach((key) => {
    if (!fields.some((field) => field.key === key) && isEditablePayloadValue(payload[key])) {
      fields.push({
        key,
        label: payloadFieldLabel(key),
        placeholder: payloadFieldPlaceholder(key),
        multiline: key === 'content',
      });
    }
  });
  return fields;
}

function preferredEditableFields(tool: string): EditableField[] {
  const common = {
    title: { key: 'title', label: '标题', placeholder: '请输入标题' },
    content: { key: 'content', label: '内容', placeholder: '请输入内容', multiline: true },
    dueAt: { key: 'dueAt', label: '截止时间', placeholder: '例如 2026-07-07T18:00:00' },
    triggerAt: { key: 'triggerAt', label: '提醒时间', placeholder: '例如 2026-07-07T18:00:00' },
    message: { key: 'message', label: '提醒内容', placeholder: '请输入提醒内容' },
    todoTitle: { key: 'todoTitle', label: '关联待办', placeholder: '请输入关联待办标题' },
  } satisfies Record<string, EditableField>;

  switch (tool) {
    case 'createTodo':
    case 'updateTodo':
      return [common.title, common.dueAt];
    case 'createReminder':
      return [common.message, common.triggerAt, common.todoTitle];
    case 'createNote':
    case 'updateNote':
      return [common.title, common.content];
    default:
      return [];
  }
}

function isEditablePayloadValue(value: unknown) {
  return value == null || ['string', 'number', 'boolean'].includes(typeof value);
}

function payloadFieldLabel(key: string) {
  const labels: Record<string, string> = {
    title: '标题',
    content: '内容',
    dueAt: '截止时间',
    triggerAt: '提醒时间',
    message: '提醒内容',
    todoTitle: '关联待办',
    horizon: '时间范围',
    priority: '优先级',
  };
  return labels[key] || key;
}

function payloadFieldPlaceholder(key: string) {
  if (key === 'dueAt' || key === 'triggerAt') return '例如 2026-07-07T18:00:00';
  return undefined;
}
</script>

<style scoped>
.agent-task-card {
  margin-top: 4px;
}

.result-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.result-label {
  margin: 0 0 4px;
  font-size: 12px;
  color: #94a3b8;
}

.instruction-text {
  margin: 0;
  color: #1e293b;
  font-size: 14px;
  font-weight: 500;
}

.assistant-reply {
  margin: 0 0 12px;
  color: #334155;
  font-size: 14px;
  line-height: 1.6;
}

.field-label {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.plan-overview {
  margin-bottom: 12px;
  padding: 10px 12px;
  background: linear-gradient(135deg, #eef2ff 0%, #f5f3ff 100%);
  border-radius: 10px;
}

.plan-overview p {
  margin: 0;
  color: #4338ca;
  font-size: 13px;
  font-weight: 500;
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.step-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.step-check {
  margin-top: 2px;
}

.step-check-placeholder {
  width: 16px;
  flex-shrink: 0;
}

.step-row.pending {
  border-color: #fcd34d;
  background: #fffbeb;
}

.step-row.done {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.step-row.skipped {
  border-color: #e2e8f0;
  background: #f8fafc;
  opacity: 0.75;
}

.step-index {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #e2e8f0;
  color: #475569;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step-row.done .step-index {
  background: #22c55e;
  color: #fff;
}

.step-row.pending .step-index {
  background: #f59e0b;
  color: #fff;
}

.step-body {
  flex: 1;
  min-width: 0;
}

.step-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 4px;
}

.step-edit {
  height: 22px;
  padding: 0 2px;
  font-size: 12px;
}

.step-summary {
  margin: 0;
  color: #334155;
  font-size: 13px;
}

.step-detail {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
  white-space: pre-line;
}

.action-bar {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.action-hint {
  font-size: 12px;
  color: #94a3b8;
}

.done-result {
  padding: 8px 0 0;
}

.done-result :deep(.ant-result-icon) {
  margin-bottom: 8px;
}

.done-result :deep(.ant-result-icon > .anticon) {
  font-size: 36px;
}

.edit-form {
  padding-top: 4px;
}
</style>
