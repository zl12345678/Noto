<template>
  <div class="dashboard-page">
    <a-row :gutter="20">
      <a-col :xs="24" :md="16">
        <a-card class="hero-card" :bordered="false">
          <div class="hero-copy">
            <p class="eyebrow">Dashboard</p>
            <h2>欢迎回来，{{ authStore.currentUser?.nickname || authStore.currentUser?.username || '用户' }}</h2>
            <p>
              这里会承载笔记、搜索、AI、待办与提醒等核心模块的概览入口，当前已经完成登录态、JWT 与用户信息联通。
            </p>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :md="8">
        <a-card class="info-card" :bordered="false">
          <p class="info-label">当前身份</p>
          <strong>{{ authStore.currentUser?.nickname || authStore.currentUser?.username || '用户' }}</strong>
          <span>{{ authStore.currentUser?.email || '未获取邮箱' }}</span>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '../../store/auth';

const authStore = useAuthStore();
</script>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 20px;
}

.hero-card,
.info-card {
  border-radius: 20px;
  box-shadow: 0 14px 35px rgba(15, 23, 42, 0.06);
}

.hero-card {
  min-height: 220px;
  background: linear-gradient(135deg, #1677ff 0%, #5d8ff7 100%);
  color: #fff;
}

.hero-copy h2 {
  margin: 10px 0 12px;
  font-size: 28px;
}

.hero-copy p {
  margin: 0;
  max-width: 56ch;
  line-height: 1.8;
  opacity: 0.92;
}

.eyebrow,
.info-label {
  display: inline-flex;
  width: fit-content;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.eyebrow {
  background: rgba(255, 255, 255, 0.16);
}

.info-label {
  background: #eaf2ff;
  color: #1677ff;
}

.info-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
}

.info-card strong {
  font-size: 22px;
}

.info-card span {
  color: #667085;
}
</style>
