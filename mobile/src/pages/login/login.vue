<template>
  <view class="login-page">
    <view class="hero">
      <text class="brand">Noto</text>
      <text class="subtitle">知微 · 移动工作台</text>
    </view>

    <view class="form card">
      <view class="field">
        <text class="label">用户名</text>
        <input v-model="username" class="input" placeholder="admin" />
      </view>
      <view class="field">
        <text class="label">密码</text>
        <input v-model="password" class="input" password placeholder="请输入密码" />
      </view>
      <button class="btn-primary submit" :loading="loading" @click="onSubmit">登录</button>
      <text class="link" @click="goRegister">没有账号？注册</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { login } from '../../stores/auth';

const username = ref('admin');
const password = ref('');
const loading = ref(false);

async function onSubmit() {
  if (!username.value || !password.value) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' });
    return;
  }
  loading.value = true;
  try {
    await login({ username: username.value, password: password.value });
    uni.switchTab({ url: '/pages/home/home' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '登录失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

function goRegister() {
  uni.navigateTo({ url: '/pages/register/register' });
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  padding: 120rpx 40rpx 40rpx;
}

.hero {
  margin-bottom: 48rpx;
}

.brand {
  display: block;
  font-size: 72rpx;
  font-weight: 700;
  color: #0891b2;
}

.subtitle {
  display: block;
  margin-top: 12rpx;
  color: #78716c;
  font-size: 28rpx;
}

.form {
  padding: 36rpx;
}

.field {
  margin-bottom: 28rpx;
}

.label {
  display: block;
  margin-bottom: 12rpx;
  font-size: 26rpx;
  color: #57534e;
}

.input {
  height: 84rpx;
  padding: 0 24rpx;
  background: #fafaf9;
  border-radius: 16rpx;
  border: 1rpx solid #e7e5e4;
}

.submit {
  margin-top: 12rpx;
  height: 88rpx;
  line-height: 88rpx;
}

.link {
  display: block;
  text-align: center;
  margin-top: 24rpx;
  color: #0891b2;
  font-size: 28rpx;
}
</style>
