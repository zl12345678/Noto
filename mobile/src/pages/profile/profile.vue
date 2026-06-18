<template>
  <view class="page-safe">
    <view class="profile card">
      <text class="nickname">{{ user?.nickname || user?.username || '用户' }}</text>
      <text class="muted">@{{ user?.username }}</text>
    </view>

    <view class="menu card">
      <text class="menu-head">常用</text>
      <view class="list-row" @click="go('/pages/search/search')">
        <text>搜索笔记</text>
        <text class="muted">›</text>
      </view>
      <view class="list-row" @click="go('/pages/reminders/reminders')">
        <text>提醒</text>
        <text class="muted">›</text>
      </view>
    </view>

    <view class="menu card">
      <text class="menu-head">资料库</text>
      <view class="list-row" @click="go('/pages/drive/drive')">
        <text>网盘</text>
        <text class="muted">›</text>
      </view>
      <view class="list-row" @click="go('/pages/shares/shares')">
        <text>我的分享</text>
        <text class="muted">›</text>
      </view>
    </view>

    <view class="menu card">
      <view class="list-row" @click="go('/pages/settings/settings')">
        <text>账号与 AI 设置</text>
        <text class="muted">›</text>
      </view>
    </view>

    <TouchButton variant="danger" block @click="onLogout">退出登录</TouchButton>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import TouchButton from '../../components/TouchButton.vue';
import { authState, ensureAuthPage, fetchCurrentUser, logout } from '../../stores/auth';

const user = computed(() => authState.user);

function go(url: string) {
  uni.navigateTo({ url });
}

async function onLogout() {
  await logout();
  uni.reLaunch({ url: '/pages/login/login' });
}

onShow(async () => {
  if (!ensureAuthPage()) return;
  try {
    await fetchCurrentUser();
  } catch {
    // ignore
  }
});
</script>

<style scoped lang="scss">
.profile { padding: 36rpx; }
.nickname { display: block; font-size: 40rpx; font-weight: 700; margin-bottom: 8rpx; }
.menu-head {
  display: block;
  font-size: 24rpx;
  color: #78716c;
  margin-bottom: 8rpx;
}
.menu { padding-top: 20rpx; }
</style>
