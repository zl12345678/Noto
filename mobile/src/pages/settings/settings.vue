<template>
  <view class="page">
    <view class="card">
      <view class="avatar-row">
        <image v-if="avatarUrl" class="avatar" :src="avatarUrl" mode="aspectFill" />
        <view v-else class="avatar placeholder">{{ avatarText }}</view>
        <view class="avatar-copy">
          <text class="section-title">账号资料</text>
          <text class="hint">{{ authState.user?.email || authState.user?.username }}</text>
        </view>
      </view>
      <view class="field">
        <text class="label">昵称</text>
        <input v-model="nickname" class="input" />
      </view>
      <view class="field">
        <text class="label">头像地址</text>
        <input v-model="avatarUrl" class="input" placeholder="http(s) 地址或站内路径" />
      </view>
      <button class="btn-primary" :loading="saving" @click="saveProfile">保存资料</button>
    </view>

    <view class="card section">
      <text class="section-title">修改密码</text>
      <view class="field">
        <text class="label">当前密码</text>
        <input v-model="oldPassword" class="input" password placeholder="请输入当前密码" />
      </view>
      <view class="field">
        <text class="label">新密码</text>
        <input v-model="newPassword" class="input" password placeholder="8-64 位" />
      </view>
      <view class="field">
        <text class="label">确认新密码</text>
        <input v-model="confirmPassword" class="input" password placeholder="再次输入新密码" />
      </view>
      <button class="btn-primary" :loading="savingPassword" @click="savePassword">更新密码</button>
    </view>

    <view class="card section">
      <view class="section-head">
        <text class="section-title">知识库管理</text>
        <button size="mini" class="mini-btn" @click="openCreateWorkspace">新建</button>
      </view>
      <view v-if="workspaces.length" class="workspace-list">
        <view v-for="item in workspaces" :key="item.id" class="workspace-row" @click="openWorkspaceActions(item)">
          <view class="workspace-main">
            <text class="workspace-name">{{ item.name }}</text>
            <text v-if="item.description" class="hint">{{ item.description }}</text>
          </view>
          <text class="muted">›</text>
        </view>
      </view>
      <view v-else class="empty-inline">暂无知识库</view>
    </view>

    <view class="card section">
      <text class="section-title">AI 偏好</text>
      <view class="row">
        <text>保存时自动摘要</text>
        <switch :checked="aiSettings.autoSummaryOnSave" @change="setAutoSummary" />
      </view>
      <view class="picker-row">
        <text>默认问答范围</text>
        <picker :range="scopeOptions" range-key="label" :value="scopeIndex" @change="setScope">
          <view class="picker-value">{{ scopeOptions[scopeIndex]?.label }}</view>
        </picker>
      </view>
      <view class="picker-row">
        <text>回答风格</text>
        <picker :range="styleOptions" range-key="label" :value="styleIndex" @change="setStyle">
          <view class="picker-value">{{ styleOptions[styleIndex]?.label }}</view>
        </picker>
      </view>
      <view class="row">
        <text>每日 AI 行动建议</text>
        <switch :checked="aiSettings.dailyDigestEnabled" @change="setDailyDigest" />
      </view>
      <view v-if="aiSettings.dailyDigestEnabled" class="picker-row">
        <text>生成时间</text>
        <picker :range="hourOptions" :value="aiSettings.dailyDigestHour" @change="setDailyDigestHour">
          <view class="picker-value">{{ hourLabel(aiSettings.dailyDigestHour) }}</view>
        </picker>
      </view>
      <view class="row">
        <text>办事信任模式</text>
        <switch :checked="aiSettings.agentTrustMode" @change="setAgentTrust" />
      </view>
      <view class="picker-row">
        <text>常用知识库</text>
        <picker :range="workspacePickerOptions" range-key="label" :value="workspaceIndex" @change="setPrimaryWorkspace">
          <view class="picker-value">{{ workspacePickerOptions[workspaceIndex]?.label }}</view>
        </picker>
      </view>
      <view class="field compact">
        <text class="label">主攻项目</text>
        <input v-model="focusProjectsText" class="input" placeholder="用逗号分隔，最多 5 个" />
      </view>
      <view class="field compact">
        <text class="label">办事偏好</text>
        <textarea v-model="agentPreferences" class="textarea" placeholder="如：待办默认短期行动；提醒优先设在工作日上午" :maxlength="240" />
      </view>
      <button size="mini" class="btn-primary" :loading="savingAi" @click="saveAi">保存 AI 设置</button>
    </view>

    <view class="card section">
      <text class="section-title">自动化规则</text>
      <view class="row">
        <text>每周五生成复盘笔记</text>
        <switch :checked="aiSettings.weeklyRetroEnabled" @change="setWeeklyRetro" />
      </view>
      <view v-if="aiSettings.weeklyRetroEnabled" class="picker-row">
        <text>生成时间</text>
        <picker :range="hourOptions" :value="aiSettings.weeklyRetroHour" @change="setWeeklyRetroHour">
          <view class="picker-value">{{ hourLabel(aiSettings.weeklyRetroHour) }}</view>
        </picker>
      </view>
      <view class="row">
        <text>会议笔记保存后提取待办</text>
        <switch :checked="aiSettings.autoExtractTodosOnSave" @change="setAutoExtractTodos" />
      </view>
      <button size="mini" class="btn-primary" :loading="savingAi" @click="saveAi">保存自动化规则</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import { changePassword, updateAvatar, updateProfile } from '../../api/profile';
import { getAiUserSettings, updateAiUserSettings, type AiUserSettings } from '../../api/settings';
import {
  createWorkspace,
  deleteWorkspace,
  listWorkspaces,
  updateWorkspace,
  type Workspace,
} from '../../api/workspaces';
import { authState, ensureAuthPage, fetchCurrentUser } from '../../stores/auth';

const nickname = ref('');
const avatarUrl = ref('');
const oldPassword = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const focusProjectsText = ref('');
const agentPreferences = ref('');
const workspaces = ref<Workspace[]>([]);
const saving = ref(false);
const savingPassword = ref(false);
const savingAi = ref(false);
const aiSettings = ref<AiUserSettings>({
  autoSummaryOnSave: false,
  defaultScope: 'workspace',
  answerStyle: 'balanced',
  dailyDigestEnabled: false,
  dailyDigestHour: 9,
  agentTrustMode: false,
  primaryWorkspaceId: null,
  focusProjects: [],
  agentPreferences: '',
  weeklyRetroEnabled: false,
  weeklyRetroHour: 17,
  autoExtractTodosOnSave: false,
});

const scopeOptions = [
  { label: '知识库', value: 'workspace' },
  { label: '单篇文档', value: 'note' },
] as const;
const styleOptions = [
  { label: '简洁', value: 'concise' },
  { label: '平衡', value: 'balanced' },
  { label: '详细', value: 'detailed' },
] as const;
const hourOptions = Array.from({ length: 24 }, (_, hour) => hourLabel(hour));

const avatarText = computed(() => (authState.user?.nickname || authState.user?.username || '用').slice(0, 1));
const scopeIndex = computed(() => Math.max(0, scopeOptions.findIndex((item) => item.value === aiSettings.value.defaultScope)));
const styleIndex = computed(() => Math.max(0, styleOptions.findIndex((item) => item.value === aiSettings.value.answerStyle)));
const workspacePickerOptions = computed(() => [
  { label: '未设置', value: '' },
  ...workspaces.value.map((item) => ({ label: item.name, value: String(item.id) })),
]);
const workspaceIndex = computed(() => {
  const value = aiSettings.value.primaryWorkspaceId ? String(aiSettings.value.primaryWorkspaceId) : '';
  return Math.max(0, workspacePickerOptions.value.findIndex((item) => item.value === value));
});

function hourLabel(hour: number) {
  return `${String(hour).padStart(2, '0')}:00`;
}

function switchValue(event: Event) {
  return Boolean((event as unknown as { detail?: { value?: boolean } }).detail?.value);
}

function setAutoSummary(event: Event) {
  aiSettings.value.autoSummaryOnSave = switchValue(event);
}

function setDailyDigest(event: Event) {
  aiSettings.value.dailyDigestEnabled = switchValue(event);
}

function setAgentTrust(event: Event) {
  aiSettings.value.agentTrustMode = switchValue(event);
}

function setWeeklyRetro(event: Event) {
  aiSettings.value.weeklyRetroEnabled = switchValue(event);
}

function setAutoExtractTodos(event: Event) {
  aiSettings.value.autoExtractTodosOnSave = switchValue(event);
}

function pickerIndex(event: Event) {
  return Number((event as unknown as { detail?: { value?: number | string } }).detail?.value || 0);
}

function setScope(event: Event) {
  aiSettings.value.defaultScope = scopeOptions[pickerIndex(event)]?.value || 'workspace';
}

function setStyle(event: Event) {
  aiSettings.value.answerStyle = styleOptions[pickerIndex(event)]?.value || 'balanced';
}

function setDailyDigestHour(event: Event) {
  aiSettings.value.dailyDigestHour = pickerIndex(event);
}

function setWeeklyRetroHour(event: Event) {
  aiSettings.value.weeklyRetroHour = pickerIndex(event);
}

function setPrimaryWorkspace(event: Event) {
  const option = workspacePickerOptions.value[pickerIndex(event)];
  aiSettings.value.primaryWorkspaceId = option?.value || null;
}

async function loadWorkspaces() {
  try {
    workspaces.value = await listWorkspaces();
  } catch {
    workspaces.value = [];
  }
}

async function saveProfile() {
  saving.value = true;
  try {
    const user = await updateProfile({ nickname: nickname.value.trim() });
    authState.user = user;
    const trimmedAvatar = avatarUrl.value.trim();
    if (trimmedAvatar !== (user.avatarUrl || '')) {
      authState.user = await updateAvatar({ avatarUrl: trimmedAvatar });
    }
    await fetchCurrentUser();
    uni.showToast({ title: '资料已保存', icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '保存失败', icon: 'none' });
  } finally {
    saving.value = false;
  }
}

function openCreateWorkspace() {
  uni.showModal({
    title: '新建知识库',
    editable: true,
    placeholderText: '知识库名称',
    success: async (res) => {
      if (!res.confirm || !res.content?.trim()) return;
      try {
        await createWorkspace({ name: res.content.trim() });
        await loadWorkspaces();
        uni.showToast({ title: '已创建', icon: 'success' });
      } catch (e: any) {
        uni.showToast({ title: e?.message || '创建失败', icon: 'none' });
      }
    },
  });
}

function openWorkspaceActions(workspace: Workspace) {
  uni.showActionSheet({
    itemList: ['重命名', '删除'],
    success: async (res) => {
      if (res.tapIndex === 0) openRenameWorkspace(workspace);
      if (res.tapIndex === 1) confirmDeleteWorkspace(workspace);
    },
  });
}

function openRenameWorkspace(workspace: Workspace) {
  uni.showModal({
    title: '重命名知识库',
    editable: true,
    placeholderText: '知识库名称',
    content: workspace.name,
    success: async (res) => {
      if (!res.confirm || !res.content?.trim()) return;
      try {
        await updateWorkspace(workspace.id, {
          name: res.content.trim(),
          description: workspace.description || undefined,
        });
        await loadWorkspaces();
        uni.showToast({ title: '已重命名', icon: 'success' });
      } catch (e: any) {
        uni.showToast({ title: e?.message || '重命名失败', icon: 'none' });
      }
    },
  });
}

function confirmDeleteWorkspace(workspace: Workspace) {
  uni.showModal({
    title: '删除知识库',
    content: `确定删除「${workspace.name}」吗？相关文档和文件可能会受影响。`,
    confirmColor: '#b91c1c',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await deleteWorkspace(workspace.id);
        await loadWorkspaces();
        if (aiSettings.value.primaryWorkspaceId === workspace.id) {
          aiSettings.value.primaryWorkspaceId = null;
        }
        uni.showToast({ title: '已删除', icon: 'none' });
      } catch (e: any) {
        uni.showToast({ title: e?.message || '删除失败', icon: 'none' });
      }
    },
  });
}

async function savePassword() {
  if (!oldPassword.value || !newPassword.value) {
    uni.showToast({ title: '请填写当前密码和新密码', icon: 'none' });
    return;
  }
  if (newPassword.value.length < 8 || newPassword.value.length > 64) {
    uni.showToast({ title: '新密码需为 8 到 64 位', icon: 'none' });
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    uni.showToast({ title: '两次新密码不一致', icon: 'none' });
    return;
  }
  savingPassword.value = true;
  try {
    await changePassword({ oldPassword: oldPassword.value, newPassword: newPassword.value });
    oldPassword.value = '';
    newPassword.value = '';
    confirmPassword.value = '';
    uni.showToast({ title: '密码已更新', icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '更新失败', icon: 'none' });
  } finally {
    savingPassword.value = false;
  }
}

async function saveAi() {
  savingAi.value = true;
  try {
    const focusProjects = focusProjectsText.value
      .split(/[,，]/)
      .map((item) => item.trim())
      .filter(Boolean)
      .slice(0, 5);
    const saved = await updateAiUserSettings({
      ...aiSettings.value,
      primaryWorkspaceId: aiSettings.value.primaryWorkspaceId || null,
      focusProjects,
      agentPreferences: agentPreferences.value.trim() || null,
    });
    aiSettings.value = saved;
    focusProjectsText.value = (saved.focusProjects || []).join('，');
    agentPreferences.value = saved.agentPreferences || '';
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
  avatarUrl.value = authState.user?.avatarUrl || '';
  await loadWorkspaces();
  try {
    aiSettings.value = await getAiUserSettings();
    focusProjectsText.value = (aiSettings.value.focusProjects || []).join('，');
    agentPreferences.value = aiSettings.value.agentPreferences || '';
  } catch {
    // ignore
  }
});
</script>

<style scoped lang="scss">
.page { padding: 24rpx; }
.avatar-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 26rpx;
}
.avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 56rpx;
  background: #e7e5e4;
}
.avatar.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: #0f766e;
  font-size: 42rpx;
  font-weight: 700;
}
.avatar-copy {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 8rpx;
}
.field { margin-bottom: 24rpx; }
.field.compact { margin-top: 24rpx; }
.label { display: block; margin-bottom: 12rpx; font-size: 26rpx; }
.hint {
  color: #78716c;
  font-size: 24rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.input {
  height: 84rpx;
  padding: 0 24rpx;
  background: #fafaf9;
  border-radius: 16rpx;
  border: 1rpx solid #e7e5e4;
}
.textarea {
  width: 100%;
  min-height: 140rpx;
  padding: 20rpx 24rpx;
  background: #fafaf9;
  border-radius: 16rpx;
  border: 1rpx solid #e7e5e4;
  box-sizing: border-box;
  font-size: 28rpx;
}
.section { margin-top: 24rpx; padding: 28rpx; }
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-bottom: 12rpx;
}
.mini-btn {
  margin: 0;
  color: #fff;
  background: #0f766e;
  border-radius: 999rpx;
}
.workspace-list {
  display: grid;
  gap: 10rpx;
}
.workspace-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f5f5f4;
}
.workspace-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 6rpx;
}
.workspace-name {
  color: #292524;
  font-size: 29rpx;
  font-weight: 650;
}
.empty-inline {
  padding: 18rpx 0 6rpx;
  color: #a8a29e;
  font-size: 26rpx;
}
.row,
.picker-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f5f5f4;
}
.picker-value {
  min-width: 150rpx;
  color: #0f766e;
  text-align: right;
  font-weight: 650;
}
</style>
