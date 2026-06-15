<template>
  <a-card v-if="overdueCount > 0" :bordered="false" class="overdue-advice-card">
    <div class="card-head">
      <div>
        <h3>逾期待办 · AI 建议</h3>
        <p class="card-desc">共 {{ overdueCount }} 项逾期</p>
      </div>
      <a-button type="primary" ghost :loading="loading" @click="loadAdvice">获取建议</a-button>
    </div>

    <div v-if="advice?.summary" class="advice-summary">{{ advice.summary }}</div>

    <a-list v-if="advice?.suggestions?.length" :data-source="advice.suggestions" item-layout="horizontal">
      <template #renderItem="{ item }">
        <a-list-item>
          <a-list-item-meta :title="item.title" :description="item.reason">
            <template #avatar>
              <a-tag :color="actionColor(item.action)">{{ actionLabel(item.action) }}</a-tag>
            </template>
          </a-list-item-meta>
          <template #actions>
            <a-button type="link" size="small" @click="emit('apply', item)">执行</a-button>
          </template>
        </a-list-item>
      </template>
    </a-list>
  </a-card>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { getOverdueTodoAdvice, type TodoOverdueAdvice, type TodoOverdueAdviceItem } from '../../api/ai';
import { OVERDUE_ACTION_LABEL } from '../../utils/todoAssist';

const props = defineProps<{
  overdueCount: number;
  workspaceId?: string;
}>();

const emit = defineEmits<{
  apply: [item: TodoOverdueAdviceItem];
}>();

const loading = ref(false);
const advice = ref<TodoOverdueAdvice | null>(null);

const actionLabel = (action: string) => OVERDUE_ACTION_LABEL[action] || action;
const actionColor = (action: string) => {
  if (action === 'reschedule') return 'blue';
  if (action === 'breakdown') return 'purple';
  if (action === 'archive') return 'default';
  if (action === 'complete') return 'green';
  return 'default';
};

const loadAdvice = async () => {
  loading.value = true;
  try {
    advice.value = await getOverdueTodoAdvice(props.workspaceId);
    if (!advice.value.suggestions?.length) {
      message.info('暂无具体建议');
    }
  } catch (error: any) {
    message.error(error?.message || '获取建议失败');
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.overdue-advice-card {
  margin-bottom: 16px;
  border: 1px solid var(--noto-overdue-card-border);
  background: var(--noto-overdue-card-bg);
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.card-head h3 {
  margin: 0;
  font-size: 15px;
  color: var(--noto-overdue-card-title, #991b1b);
}

.card-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--noto-overdue-card-desc, #b45309);
}

.advice-summary {
  margin-bottom: 12px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--noto-surface);
  color: var(--noto-text-muted);
  font-size: 13px;
}
</style>
