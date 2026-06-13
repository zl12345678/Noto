<template>
  <div class="auth-page">
    <div class="auth-shell auth-shell--single">
      <div class="auth-card-shell">
        <div class="auth-card">
          <div class="auth-card-head">
            <span class="auth-eyebrow">密码帮助</span>
            <h2>忘记密码？</h2>
            <span>出于安全考虑，系统不提供未登录的邮箱重置。请使用演示账号登录后在「个人设置」中修改密码。</span>
          </div>

          <a-alert type="info" show-icon class="demo-hint">
            <template #message>演示账号</template>
            <template #description>
              用户名 <strong>admin</strong>，密码 <strong>admin123</strong>。登录后可在个人设置修改密码。
            </template>
          </a-alert>

          <div class="auth-footer-link">
            <a-button type="primary" block size="large" @click="goLogin">返回登录</a-button>
            <p class="secondary-link">
              已登录？<a @click="goProfile">前往个人设置</a>
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { useAuthStore } from '../../store/auth';

const router = useRouter();
const authStore = useAuthStore();

const goLogin = () => router.push('/login');

const goProfile = () => {
  if (authStore.isAuthenticated) {
    router.push('/profile');
    return;
  }
  router.push('/login');
};
</script>

<style scoped>
.demo-hint {
  margin-bottom: 20px;
}

.secondary-link {
  margin-top: 16px;
  text-align: center;
  color: var(--noto-text-muted, #64748b);
  font-size: 13px;
}
</style>
