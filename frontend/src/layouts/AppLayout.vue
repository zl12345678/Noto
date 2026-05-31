<template>
  <a-layout class="app-layout">
    <a-layout-sider class="sider" :width="240" theme="light">
      <div class="logo">
        <span class="logo-mark">N</span>
        <span class="logo-text">Noto · 知微</span>
      </div>
      <a-menu mode="inline" :selectedKeys="['dashboard']">
        <a-menu-item key="dashboard">首页</a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="header">
        <div class="header-left">
          <span class="page-title">知识管理与 AI 辅助平台</span>
          <span class="page-subtitle">统一、清爽、现代的工作台</span>
        </div>
        <div class="header-right">
          <a-dropdown placement="bottomRight">
            <a class="user-trigger" @click.prevent>
              <a-avatar :src="authStore.currentUser?.avatarUrl" :style="avatarStyle">
                {{ avatarText }}
              </a-avatar>
              <div class="user-meta">
                <span class="user-name">{{ displayName }}</span>
                <span class="user-email">{{ authStore.currentUser?.email || '未获取邮箱' }}</span>
              </div>
            </a>
            <template #overlay>
              <a-menu class="user-menu">
                <a-menu-item key="profile" disabled>
                  {{ authStore.currentUser?.email || '未获取邮箱' }}
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" danger @click="handleLogout">退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>
      <a-layout-content class="content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { useAuthStore } from '../store/auth';

const authStore = useAuthStore();
const router = useRouter();

const displayName = computed(() => authStore.currentUser?.nickname || authStore.currentUser?.username || '用户');
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase());
const avatarStyle = computed(() => ({ backgroundColor: '#1677ff' }));

async function handleLogout() {
  await authStore.logout();
  message.success('已退出登录');
  router.push('/login');
}
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  background: #f5f7fb;
}

.sider {
  border-right: 1px solid #eef2f7;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 64px;
  padding: 0 20px;
  font-weight: 700;
}

.logo-mark {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #1677ff, #69b1ff);
}

.logo-text {
  font-size: 15px;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid #eef2f7;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.page-title {
  font-size: 16px;
  font-weight: 700;
  color: #101828;
}

.page-subtitle {
  font-size: 12px;
  color: #667085;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 16px;
  color: inherit;
  transition: background-color 0.2s ease, box-shadow 0.2s ease;
}

.user-trigger:hover {
  background: #f8fafc;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.user-name {
  font-weight: 600;
  color: #101828;
}

.user-email {
  font-size: 12px;
  color: #667085;
}

.content {
  padding: 24px;
}

.user-menu {
  min-width: 220px;
}
</style>
