<template>
  <a-modal
    v-model:open="open"
    :title="null"
    :footer="null"
    :closable="false"
    :mask-closable="false"
    width="440px"
    centered
    wrap-class-name="quick-start-modal-wrap"
    @cancel="close"
  >
    <div class="qs-modal">
      <div class="qs-hero">
        <span class="qs-icon" aria-hidden="true">✦</span>
        <h2>从这里开始</h2>
        <p class="qs-lead">三步把想法变成行动</p>
      </div>

      <ol class="qs-steps">
        <li class="qs-step">
          <span class="qs-num">1</span>
          <div class="qs-step-body">
            <strong>写文档</strong>
            <span>会议、灵感或剪藏</span>
          </div>
        </li>
        <li class="qs-step">
          <span class="qs-num">2</span>
          <div class="qs-step-body">
            <strong>提取待办</strong>
            <span>AI 从正文识别行动项</span>
          </div>
        </li>
        <li class="qs-step">
          <span class="qs-num">3</span>
          <div class="qs-step-body">
            <strong>开始执行</strong>
            <span>在待办中心推进完成</span>
          </div>
        </li>
      </ol>

      <p class="qs-tip">
        <kbd>Ctrl</kbd> + <kbd>K</kbd> 可搜索、问 AI、快速办事
      </p>

      <div class="qs-actions">
        <a-button type="primary" size="large" block @click="onWrite">
          新建第一篇文档
        </a-button>
        <a-space class="qs-secondary" size="middle">
          <a-button type="link" @click="onExtract">去提取待办</a-button>
          <a-button type="link" @click="onTodos">打开待办</a-button>
        </a-space>
        <a-button type="text" block class="qs-later" @click="close">稍后再看</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { useAuthStore } from '../../store/auth';
import { dismissOnboarding } from '../../utils/onboarding';

const open = defineModel<boolean>('open', { default: false });

const authStore = useAuthStore();

const emit = defineEmits<{
  write: [];
  extract: [];
  todos: [];
}>();

const close = () => {
  dismissOnboarding(authStore.currentUser?.id);
  open.value = false;
};

const onWrite = () => {
  close();
  emit('write');
};

const onExtract = () => {
  close();
  emit('extract');
};

const onTodos = () => {
  close();
  emit('todos');
};
</script>

<style scoped>
.qs-modal {
  padding: 4px 4px 0;
}

.qs-hero {
  text-align: center;
  margin-bottom: 20px;
}

.qs-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin-bottom: 10px;
  border-radius: 14px;
  background: linear-gradient(135deg, #1677ff 0%, #7c3aed 100%);
  color: #fff;
  font-size: 20px;
  line-height: 1;
}

.qs-hero h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #101828;
}

.qs-lead {
  margin: 6px 0 0;
  font-size: 14px;
  color: #64748b;
}

.qs-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.qs-step {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
}

.qs-num {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #1677ff;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  line-height: 28px;
  text-align: center;
}

.qs-step-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.qs-step-body strong {
  font-size: 14px;
  color: #101828;
}

.qs-step-body span {
  font-size: 12px;
  color: #64748b;
}

.qs-tip {
  margin: 16px 0 0;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
}

.qs-tip kbd {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid #e2e8f0;
  background: #fff;
  font-size: 11px;
  font-family: inherit;
}

.qs-actions {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.qs-secondary {
  justify-content: center;
  width: 100%;
}

.qs-later {
  color: #94a3b8;
  margin-top: 4px;
}
</style>

<style>
.quick-start-modal-wrap .ant-modal-content {
  border-radius: 16px;
  padding: 24px 24px 20px;
}

.quick-start-modal-wrap .ant-modal-body {
  padding: 0;
}
</style>
