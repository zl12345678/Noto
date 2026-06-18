<template>
  <view class="page">
    <view class="card">
      <view class="field">
        <text class="label">昵称</text>
        <input v-model="nickname" class="input" />
      </view>
      <button class="btn-primary" :loading="saving" @click="saveProfile">保存资料</button>
    </view>

    <view class="card section">
      <text class="section-title">AI 偏好</text>
      <view class="row">
        <text>保存时自动摘要</text>
        <switch :checked="aiSettings.autoSummaryOnSave" @change="aiSettings.autoSummaryOnSave = $event.detail.value" />
      </view>
      <view class="row">
        <text>每日 AI 行动建议</text>
        <switch :checked="aiSettings.dailyDigestEnabled" @change="aiSettings.dailyDigestEnabled = $event.detail.value" />
      </view>
      <view class="row">
        <text>办事信任模式</text>
        <switch :checked="aiSettings.agentTrustMode" @change="aiSettings.agentTrustMode = $event.detail.value" />
      </view>
      <button size="mini" class="btn-primary" :loading="savingAi" @click="saveAi">保存 AI 设置</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import { updateProfile } from '../../api/profile';
import { getAiUserSettings, updateAiUserSettings, type AiUserSettings } from '../../api/settings';
import { authState, ensureAuthPage, fetchCurrentUser } from '../../stores/auth';

const nickname = ref('');
const saving = ref(false);
const savingAi = ref(false);
const aiSettings = ref<AiUserSettings>({
  autoSummaryOnSave: false,
  defaultScope: 'workspace',
  answerStyle: 'balanced',
  dailyDigestEnabled: false,
  dailyDigestHour: 9,
  agentTrustMode: false,
});

async function saveProfile() {
  saving.value = true;
  try {
    const user = await updateProfile({ nickname: nickname.value.trim() });
    authState.user = user;
    uni.showToast({ title: '已保存', icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '保存失败', icon: 'none' });
  } finally {
    saving.value = false;
  }
}

async function saveAi() {
  savingAi.value = true;
  try {
    await updateAiUserSettings({ ...aiSettings.value });
    uni.showToast({ title: 'AI 设置已保存', icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '保存失败', icon: 'none' });
  } finally {
    savingAi.value = false;
  }
}

onShow(async () => {
  if (!ensureAuthPage()) return;
  await fetchCurrentUser();
  nickname.value = authState.user?.nickname || '';
  try {
    aiSettings.value = await getAiUserSettings();
  } catch {
    // ignore
  }
});
</script>

<style scoped lang="scss">
.page { padding: 24rpx; }
.field { margin-bottom: 24rpx; }
.label { display: block; margin-bottom: 12rpx; font-size: 26rpx; }
.input {
  height: 84rpx;
  padding: 0 24rpx;
  background: #fafaf9;
  border-radius: 16rpx;
  border: 1rpx solid #e7e5e4;
}
.section { margin-top: 24rpx; padding: 28rpx; }
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f5f5f4;
}
</style>
