<template>
  <div class="auth-page">
    <div class="auth-shell">
      <div class="auth-panel-shell">
        <div class="auth-hero">
          <div>
            <span class="auth-eyebrow">Noto 知微</span>
            <h1>笔记、待办与提醒，在一个工作台里衔接</h1>
            <p>写文档时关联任务，到期时收到提醒，需要时让 AI 帮你整理要点。</p>
          </div>
          <div class="auth-features">
            <div>
              <strong>文档</strong>
              <span>多知识库与 Markdown</span>
            </div>
            <div>
              <strong>待办</strong>
              <span>队列与进行中看板</span>
            </div>
            <div>
              <strong>提醒</strong>
              <span>到期通知与复盘</span>
            </div>
          </div>
        </div>
      </div>

      <div class="auth-card-shell">
        <div class="auth-card">
          <div class="auth-card-head">
            <span class="auth-eyebrow">登录</span>
            <h2>欢迎回来</h2>
            <span>使用账号密码进入工作台</span>
          </div>

          <a-form layout="vertical" :model="formState" class="auth-form" @finish="handleLogin">
            <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
              <a-input v-model:value="formState.username" placeholder="你的用户名" size="large" />
            </a-form-item>
            <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
              <a-input-password v-model:value="formState.password" placeholder="你的密码" size="large" />
            </a-form-item>
            <div class="auth-form-actions">
              <a-checkbox v-model:checked="formState.rememberMe">记住密码</a-checkbox>
              <a-button type="link" style="padding: 0" @click="handleForgotPassword">忘记密码</a-button>
            </div>
            <a-button type="primary" html-type="submit" block size="large" :loading="loading">登录</a-button>
            <div class="auth-footer-link">
              还没有账号？<a @click="goRegister">注册</a>
            </div>
          </a-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { useAuthStore } from '../../store/auth';

const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);

const formState = reactive({
  username: 'admin',
  password: 'admin123',
  rememberMe: true,
});

const handleLogin = async () => {
  loading.value = true;
  try {
    await authStore.login(formState);
    message.success('登录成功');
    await router.push('/');
  } catch {
    message.error('登录失败，请检查账号和密码');
  } finally {
    loading.value = false;
  }
};

const goRegister = () => {
  router.push('/register');
};

const handleForgotPassword = () => {
  router.push('/forgot-password');
};
</script>
