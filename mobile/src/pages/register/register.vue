<template>
  <view class="page">
    <view class="form card">
      <view class="field">
        <text class="label">用户名</text>
        <input v-model="form.username" class="input" placeholder="3-32 位" />
      </view>
      <view class="field">
        <text class="label">邮箱</text>
        <input v-model="form.email" class="input" placeholder="your@email.com" />
      </view>
      <view class="field">
        <text class="label">昵称</text>
        <input v-model="form.nickname" class="input" placeholder="显示名称" />
      </view>
      <view class="field">
        <text class="label">密码</text>
        <input v-model="form.password" class="input" password placeholder="至少 6 位" />
      </view>
      <button class="btn-primary submit" :loading="loading" @click="onSubmit">注册</button>
      <text class="link" @click="goLogin">已有账号？去登录</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { register } from '../../stores/auth';

const loading = ref(false);
const form = reactive({
  username: '',
  email: '',
  nickname: '',
  password: '',
});

async function onSubmit() {
  if (!form.username || !form.email || !form.nickname || !form.password) {
    uni.showToast({ title: '请填写完整信息', icon: 'none' });
    return;
  }
  loading.value = true;
  try {
    await register({ ...form });
    uni.switchTab({ url: '/pages/home/home' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '注册失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

function goLogin() {
  uni.navigateBack();
}
</script>

<style scoped lang="scss">
.page { padding: 24rpx; }
.form { padding: 36rpx; }
.field { margin-bottom: 28rpx; }
.label { display: block; margin-bottom: 12rpx; font-size: 26rpx; color: #57534e; }
.input {
  height: 84rpx;
  padding: 0 24rpx;
  background: #fafaf9;
  border-radius: 16rpx;
  border: 1rpx solid #e7e5e4;
}
.submit { margin-top: 12rpx; height: 88rpx; line-height: 88rpx; }
.link { display: block; text-align: center; margin-top: 24rpx; color: #0891b2; }
</style>
