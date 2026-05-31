<template>
  <div class="login-page">
    <div class="login-shell">
      <div class="hero-panel">
        <div class="hero-badge">Noto · 知微</div>
        <h1>把知识、笔记与 AI 工作流放在一个更顺手的后台里</h1>
        <p>
          登录后即可管理笔记、检索内容、使用 AI 辅助与待办提醒。界面采用统一的蓝白层次与轻量阴影，
          保持清爽、专业、现代。
        </p>
        <div class="hero-metrics">
          <div>
            <strong>统一</strong>
            <span>视觉语言</span>
          </div>
          <div>
            <strong>轻量</strong>
            <span>操作负担</span>
          </div>
          <div>
            <strong>高效</strong>
            <span>登录体验</span>
          </div>
        </div>
      </div>

      <a-card class="login-card" :bordered="false">
        <div class="card-head">
          <p class="eyebrow">欢迎回来</p>
          <h2>登录账号</h2>
          <span>请输入用户名和密码继续</span>
        </div>

        <a-form layout="vertical" :model="formState" class="login-form" @finish="handleLogin">
          <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
            <a-input v-model:value="formState.username" placeholder="admin" size="large" />
          </a-form-item>
          <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
            <a-input-password v-model:value="formState.password" placeholder="admin123" size="large" />
          </a-form-item>
          <div class="form-actions">
            <a-checkbox v-model:checked="formState.rememberMe">记住密码</a-checkbox>
            <a-button type="link" class="forget-link" @click="handleForgotPassword">忘记密码</a-button>
          </div>
          <a-button type="primary" html-type="submit" block size="large" :loading="loading">登录</a-button>
          <div class="form-footer">
            还没有账号？<a @click="goRegister">立即注册</a>
          </div>
        </a-form>
      </a-card>
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
    message.error('登录失败，请检查账号密码');
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

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background:
    radial-gradient(circle at top left, rgba(22, 119, 255, 0.18), transparent 36%),
    linear-gradient(180deg, #f7faff 0%, #eef4ff 100%);
}

.form-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -4px 0 18px;
}

.forget-link {
  padding: 0;
}

.form-footer {
  margin-top: 16px;
  text-align: center;
  color: #667085;
}

.login-shell {
  width: min(1080px, 100%);
  display: grid;
  grid-template-columns: 1.2fr 0.9fr;
  gap: 28px;
  align-items: stretch;
}

.hero-panel,
.login-card {
  border-radius: 24px;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.08);
}

.hero-panel {
  padding: 48px;
  color: #fff;
  background: linear-gradient(135deg, #1677ff 0%, #4f8cff 55%, #7aa7ff 100%);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.hero-badge,
.eyebrow {
  display: inline-flex;
  width: fit-content;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-panel h1 {
  margin: 20px 0 16px;
  font-size: 38px;
  line-height: 1.15;
  max-width: 12ch;
}

.hero-panel p {
  margin: 0;
  max-width: 52ch;
  font-size: 15px;
  line-height: 1.8;
  opacity: 0.92;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 32px;
}

.hero-metrics div {
  padding: 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(10px);
}

.hero-metrics strong {
  display: block;
  font-size: 18px;
}

.hero-metrics span {
  font-size: 13px;
  opacity: 0.88;
}

.login-card {
  padding: 44px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(18px);
}

.card-head h2 {
  margin: 12px 0 6px;
  font-size: 28px;
}

.card-head span {
  color: #667085;
}

.login-form {
  margin-top: 28px;
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .hero-panel h1 {
    font-size: 30px;
  }

  .hero-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
