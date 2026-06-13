<template>
  <a-modal
    v-model:open="visible"
    title="设提醒"
    ok-text="创建提醒"
    cancel-text="取消"
    :confirm-loading="saving"
    @ok="handleSubmit"
  >
    <p v-if="todo" class="todo-hint">待办：{{ todo.title }}</p>

    <a-form layout="vertical">
      <a-form-item label="快捷选择">
        <a-space wrap>
          <a-button size="small" @click="applyPreset('1h')">1 小时后</a-button>
          <a-button size="small" @click="applyPreset('tonight')">今晚 20:00</a-button>
          <a-button size="small" @click="applyPreset('tomorrow')">明天 9:00</a-button>
          <a-button v-if="canBeforeDue" size="small" @click="applyPreset('before_due')">截止前 1 小时</a-button>
        </a-space>
      </a-form-item>

      <a-form-item label="触发时间" required>
        <a-date-picker
          v-model:value="triggerAt"
          show-time
          format="YYYY-MM-DD HH:mm"
          style="width: 100%"
        />
      </a-form-item>

      <a-form-item label="提醒内容">
        <a-textarea v-model:value="message" :rows="2" placeholder="可选，默认使用待办标题" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { Dayjs } from 'dayjs';
import { message } from 'ant-design-vue';
import { createReminder } from '../../api/reminders';
import type { TodoItem } from '../../api/todos';
import {
  buildReminderPresetTime,
  suggestReminderTriggerAt,
  type ReminderPreset,
} from '../../utils/todoAssist';

const visible = defineModel<boolean>('open', { default: false });

const props = defineProps<{
  todo: TodoItem | null;
}>();

const emit = defineEmits<{
  created: [];
}>();

const saving = ref(false);
const triggerAt = ref<Dayjs | null>(null);
const message = ref('');

const canBeforeDue = computed(() => {
  if (!props.todo?.dueAt) return false;
  return buildReminderPresetTime('before_due', props.todo) !== null;
});

watch(
  () => [visible.value, props.todo?.id] as const,
  ([open]) => {
    if (!open || !props.todo) return;
    triggerAt.value = suggestReminderTriggerAt(props.todo);
    message.value = `待办提醒：${props.todo.title}`;
  },
);

const applyPreset = (preset: ReminderPreset) => {
  if (!props.todo) return;
  const next = buildReminderPresetTime(preset, props.todo);
  if (!next) {
    message.warning('该快捷时间不可用');
    return;
  }
  triggerAt.value = next;
};

const handleSubmit = async () => {
  if (!props.todo || !triggerAt.value) {
    message.warning('请选择触发时间');
    return Promise.reject();
  }
  saving.value = true;
  try {
    await createReminder({
      workspaceId: props.todo.workspaceId,
      todoId: props.todo.id,
      triggerAt: triggerAt.value.format('YYYY-MM-DDTHH:mm:ss'),
      message: message.value.trim() || undefined,
      reminderType: 'once',
    });
    message.success('提醒已创建');
    visible.value = false;
    emit('created');
  } catch (error: any) {
    message.error(error?.message || '创建失败');
    return Promise.reject();
  } finally {
    saving.value = false;
  }
};
</script>

<style scoped>
.todo-hint {
  margin: 0 0 12px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  color: #334155;
  font-size: 13px;
}
</style>
