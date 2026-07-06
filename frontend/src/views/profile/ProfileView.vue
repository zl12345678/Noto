<template>
  <div class="profile-page">
    <a-card v-if="isMobile" class="profile-card mobile-shortcuts" :bordered="false">
      <div class="card-head">
        <h1>模块入口</h1>
        <span>与桌面版相同能力，布局为移动端适配</span>
      </div>
      <a-space direction="vertical" style="width: 100%">
        <a-button block size="large" @click="router.push('/ai')">AI 助手</a-button>
        <a-button block size="large" @click="router.push('/drive')">网盘</a-button>
        <a-button block size="large" @click="router.push('/shares')">我的分享</a-button>
        <a-button block size="large" @click="router.push('/reminders')">提醒</a-button>
        <a-button block size="large" @click="router.push('/search')">搜索</a-button>
        <a-button block size="large" danger @click="handleLogout">退出登录</a-button>
      </a-space>
    </a-card>
    <a-row :gutter="20">
      <a-col :xs="24" :lg="8">
        <a-card class="profile-card" :bordered="false">
          <div class="avatar-wrap">
            <a-avatar :src="authStore.currentUser?.avatarUrl" :size="72" :style="avatarStyle">
              {{ avatarText }}
            </a-avatar>
          </div>
          <h2>{{ displayName }}</h2>
          <p>{{ authStore.currentUser?.email || '未获取邮箱' }}</p>
          <a-divider />
          <a-upload :showUploadList="false" :beforeUpload="beforeAvatarUpload">
            <a-button block>更换头像</a-button>
          </a-upload>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="16">
        <a-card class="profile-card" :bordered="false">
          <div class="card-head">
            <p class="eyebrow">Account</p>
            <h1>账号设置</h1>
            <span>管理昵称、密码和登录信息</span>
          </div>

          <a-form layout="vertical" :model="formState" @finish="handleSave">
            <a-form-item label="昵称" name="nickname" :rules="[{ required: true, message: '请输入昵称' }]">
              <a-input v-model:value="formState.nickname" size="large" />
            </a-form-item>
            <p v-if="isDemoAccount" class="field-hint">演示账号不支持修改密码。</p>
            <a-form-item
              label="当前密码"
              name="oldPassword"
              :rules="!isDemoAccount && formState.newPassword ? [{ required: true, message: '修改密码请输入当前密码' }] : []"
            >
              <a-input-password
                v-model:value="formState.oldPassword"
                size="large"
                placeholder="仅修改密码时填写"
                :disabled="isDemoAccount"
              />
            </a-form-item>
            <a-form-item label="新密码" name="newPassword" :rules="optionalPasswordFieldRules">
              <a-input-password
                v-model:value="formState.newPassword"
                size="large"
                placeholder="留空表示不修改"
                :disabled="isDemoAccount"
              />
            </a-form-item>
            <a-form-item
              v-if="!isDemoAccount && formState.newPassword"
              label="确认新密码"
              name="confirmPassword"
              :rules="[{ required: true, message: '请再次输入新密码' }, confirmPasswordRule(() => formState.newPassword)]"
            >
              <a-input-password v-model:value="formState.confirmPassword" size="large" placeholder="再次输入新密码" />
            </a-form-item>
            <a-button type="primary" html-type="submit" :loading="loading">保存修改</a-button>
          </a-form>
        </a-card>

        <a-card class="profile-card ai-card" :bordered="false">
          <div class="card-head">
            <p class="eyebrow ai-eyebrow">AI</p>
            <h1>AI 偏好</h1>
            <span>同步到账号，跨设备生效</span>
          </div>
          <a-spin :spinning="aiSettingsLoading">
            <a-form layout="vertical">
              <a-form-item label="保存时自动摘要">
                <a-switch v-model:checked="aiSettings.autoSummaryOnSave" />
              </a-form-item>
              <a-form-item label="默认问答范围">
                <a-select v-model:value="aiSettings.defaultScope" :options="scopeOptions" />
              </a-form-item>
              <a-form-item label="回答风格">
                <a-select v-model:value="aiSettings.answerStyle" :options="styleOptions" />
              </a-form-item>
              <a-form-item label="每日 AI 行动建议">
                <a-switch v-model:checked="aiSettings.dailyDigestEnabled" />
                <p class="field-hint">开启后，每天在指定时间自动生成今日行动建议（首页可查看）</p>
              </a-form-item>
              <a-form-item v-if="aiSettings.dailyDigestEnabled" label="生成时间">
                <a-select v-model:value="aiSettings.dailyDigestHour" :options="hourOptions" style="width: 120px" />
              </a-form-item>
              <a-form-item label="办事信任模式">
                <a-switch v-model:checked="aiSettings.agentTrustMode" />
                <p class="field-hint">开启后，仅含一个待办或提醒的简单指令将自动执行，无需二次确认</p>
              </a-form-item>

              <a-divider>轻量记忆</a-divider>
              <p class="section-desc">显式配置 AI 记住的偏好（非聊天记录），用于问答、办事与今日建议。</p>
              <a-form-item label="常用知识库">
                <a-select
                  v-model:value="aiSettings.primaryWorkspaceId"
                  allow-clear
                  placeholder="未设置（使用列表首个）"
                  :options="workspaceOptions"
                />
              </a-form-item>
              <a-form-item label="主攻项目">
                <a-select
                  v-model:value="aiSettings.focusProjects"
                  mode="tags"
                  placeholder="输入后回车，最多 5 个"
                  :max-tag-count="5"
                />
                <p class="field-hint">如「知微 MVP」「客户 A 项目」—— AI 会优先关联这些主题</p>
              </a-form-item>
              <a-form-item label="办事偏好">
                <a-textarea
                  v-model:value="aiSettings.agentPreferences"
                  :rows="2"
                  :maxlength="240"
                  show-count
                  placeholder="如：待办默认短期行动；提醒优先设在工作日上午"
                />
              </a-form-item>

              <a-button type="primary" :loading="aiSettingsSaving" @click="saveAiSettings">
                保存 AI 偏好
              </a-button>
            </a-form>
          </a-spin>
        </a-card>

        <a-card class="profile-card ai-card" :bordered="false">
          <div class="card-head">
            <p class="eyebrow ai-eyebrow">Automation</p>
            <h1>自动化规则</h1>
            <span>按你的节奏主动参与，关键操作仍须确认</span>
          </div>
          <a-spin :spinning="aiSettingsLoading">
            <a-form layout="vertical">
              <a-form-item label="每周五生成复盘笔记">
                <a-switch v-model:checked="aiSettings.weeklyRetroEnabled" />
                <p class="field-hint">开启后，每周五在指定时间自动合成「本周复盘」笔记（写入常用知识库）</p>
              </a-form-item>
              <a-form-item v-if="aiSettings.weeklyRetroEnabled" label="生成时间">
                <a-select v-model:value="aiSettings.weeklyRetroHour" :options="hourOptions" style="width: 120px" />
              </a-form-item>
              <a-form-item label="会议笔记保存后提取待办">
                <a-switch v-model:checked="aiSettings.autoExtractTodosOnSave" />
                <p class="field-hint">识别会议/纪要类文档，保存后自动弹出待办预览（须确认后才会入库）</p>
              </a-form-item>
              <a-button type="primary" :loading="aiSettingsSaving" @click="saveAiSettings">保存自动化规则</a-button>
            </a-form>
          </a-spin>
        </a-card>

        <a-card class="profile-card notify-card" :bordered="false">
          <div class="card-head">
            <p class="eyebrow">Notify</p>
            <h1>提醒通知</h1>
            <span>到期提醒与周期总结在后台时通过系统通知推送</span>
          </div>
          <a-form layout="vertical">
            <a-form-item label="浏览器系统通知">
              <a-switch
                v-model:checked="browserNotifyEnabled"
                :disabled="!browserNotifySupported"
                :loading="browserNotifyLoading"
                @change="onBrowserNotifyChange"
              />
              <p class="field-hint">
                <template v-if="!browserNotifySupported">当前浏览器不支持系统通知</template>
                <template v-else-if="browserNotifyPermission === 'denied'">
                  通知权限已被拒绝，请在浏览器站点设置中重新开启
                </template>
                <template v-else>
                  页面在后台时，待办到期、周期总结（每日建议/每周复盘）与逾期待办会弹出系统通知；页内仍会显示提醒卡片
                </template>
              </p>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watchEffect } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { useBreakpoint } from '../../composables/useBreakpoint';
import { changePassword, updateProfile } from '../../api/profile';
import { updateAvatar } from '../../api/avatar';
import { getAiUserSettings, updateAiUserSettings } from '../../api/settings';
import { listWorkspaces, type Workspace } from '../../api/workspaces';
import { useAuthStore } from '../../store/auth';
import {
  getBrowserNotifyPermission,
  isBrowserNotifyEnabled,
  isBrowserNotifySupported,
  requestBrowserNotifyPermission,
  setBrowserNotifyEnabled,
} from '../../utils/browserNotification';
import { confirmPasswordRule, extractApiErrorMessage, optionalPasswordFieldRules } from '../../utils/passwordRules';

const authStore = useAuthStore();
const router = useRouter();
const { isMobile } = useBreakpoint();
const loading = ref(false);

async function handleLogout() {
  await authStore.logout();
  message.success('已退出登录');
  router.push('/login');
}
const aiSettingsLoading = ref(false);
const aiSettingsSaving = ref(false);

const aiSettings = reactive({
  autoSummaryOnSave: false,
  defaultScope: 'workspace' as 'workspace' | 'note',
  answerStyle: 'balanced' as 'concise' | 'balanced' | 'detailed',
  dailyDigestEnabled: false,
  dailyDigestHour: 8,
  agentTrustMode: false,
  primaryWorkspaceId: undefined as string | undefined,
  focusProjects: [] as string[],
  agentPreferences: '',
  weeklyRetroEnabled: false,
  weeklyRetroHour: 17,
  autoExtractTodosOnSave: false,
});

const workspaces = ref<Workspace[]>([]);

const browserNotifySupported = isBrowserNotifySupported();
const browserNotifyEnabled = ref(isBrowserNotifyEnabled());
const browserNotifyLoading = ref(false);
const browserNotifyPermission = ref(getBrowserNotifyPermission());

const onBrowserNotifyChange = async (checked: boolean) => {
  if (!checked) {
    setBrowserNotifyEnabled(false);
    browserNotifyEnabled.value = false;
    return;
  }
  browserNotifyLoading.value = true;
  try {
    const permission = await requestBrowserNotifyPermission();
    browserNotifyPermission.value = permission;
    if (permission === 'granted') {
      setBrowserNotifyEnabled(true);
      browserNotifyEnabled.value = true;
      message.success('已开启系统通知');
    } else {
      setBrowserNotifyEnabled(false);
      browserNotifyEnabled.value = false;
      message.warning(permission === 'denied' ? '通知权限被拒绝' : '当前环境不支持系统通知');
    }
  } finally {
    browserNotifyLoading.value = false;
  }
};

const workspaceOptions = computed(() =>
  workspaces.value.map((item) => ({ label: item.name, value: item.id })),
);

const hourOptions = Array.from({ length: 24 }, (_, hour) => ({
  value: hour,
  label: `${String(hour).padStart(2, '0')}:00`,
}));

const scopeOptions = [
  { value: 'workspace', label: '知识库' },
  { value: 'note', label: '单篇文档' },
];

const styleOptions = [
  { value: 'concise', label: '简洁' },
  { value: 'balanced', label: '平衡' },
  { value: 'detailed', label: '详细' },
];
const displayName = computed(() => authStore.currentUser?.nickname || authStore.currentUser?.username || '用户');
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase());
const avatarStyle = computed(() => ({ backgroundColor: '#1677ff' }));
const isDemoAccount = computed(() => authStore.currentUser?.username === 'demo');

const formState = reactive({
  nickname: '',
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});

watchEffect(() => {
  formState.nickname = authStore.currentUser?.nickname || '';
});

const handleSave = async () => {
  loading.value = true;
  try {
    const updatedUser = await updateProfile({ nickname: formState.nickname });
    authStore.currentUser = updatedUser;
    if (!isDemoAccount.value && formState.newPassword) {
      await changePassword({ oldPassword: formState.oldPassword, newPassword: formState.newPassword });
      formState.oldPassword = '';
      formState.newPassword = '';
      formState.confirmPassword = '';
    }
    message.success('资料已更新');
  } catch (error: unknown) {
    message.error(extractApiErrorMessage(error, '保存失败'));
  } finally {
    loading.value = false;
  }
};

const beforeAvatarUpload = async (file: File) => {
  loading.value = true;
  try {
    const avatarUrl = URL.createObjectURL(file);
    const updatedUser = await updateAvatar({ avatarUrl });
    authStore.currentUser = updatedUser;
    message.success('头像已更新');
  } catch (error: any) {
    message.error(error?.message || '头像更新失败');
  } finally {
    loading.value = false;
  }
  return false;
};

const loadAiSettings = async () => {
  aiSettingsLoading.value = true;
  try {
    const settings = await getAiUserSettings();
    aiSettings.autoSummaryOnSave = settings.autoSummaryOnSave;
    aiSettings.defaultScope = settings.defaultScope;
    aiSettings.answerStyle = settings.answerStyle;
    aiSettings.dailyDigestEnabled = settings.dailyDigestEnabled ?? false;
    aiSettings.dailyDigestHour = settings.dailyDigestHour ?? 8;
    aiSettings.agentTrustMode = settings.agentTrustMode ?? false;
    aiSettings.primaryWorkspaceId = settings.primaryWorkspaceId || undefined;
    aiSettings.focusProjects = settings.focusProjects ? [...settings.focusProjects] : [];
    aiSettings.agentPreferences = settings.agentPreferences || '';
    aiSettings.weeklyRetroEnabled = settings.weeklyRetroEnabled ?? false;
    aiSettings.weeklyRetroHour = settings.weeklyRetroHour ?? 17;
    aiSettings.autoExtractTodosOnSave = settings.autoExtractTodosOnSave ?? false;
    localStorage.setItem('noto-auto-summary-on-save', settings.autoSummaryOnSave ? 'true' : 'false');
    if (settings.primaryWorkspaceId) {
      localStorage.setItem('noto-primary-workspace-id', settings.primaryWorkspaceId);
    } else {
      localStorage.removeItem('noto-primary-workspace-id');
    }
  } catch {
    // 未登录或接口不可用时保持默认值
  } finally {
    aiSettingsLoading.value = false;
  }
};

const saveAiSettings = async () => {
  aiSettingsSaving.value = true;
  try {
    const saved = await updateAiUserSettings({
      ...aiSettings,
      primaryWorkspaceId: aiSettings.primaryWorkspaceId || 0,
      focusProjects: aiSettings.focusProjects.slice(0, 5),
      agentPreferences: aiSettings.agentPreferences.trim() || null,
    });
    localStorage.setItem('noto-auto-summary-on-save', aiSettings.autoSummaryOnSave ? 'true' : 'false');
    if (saved.primaryWorkspaceId) {
      localStorage.setItem('noto-primary-workspace-id', saved.primaryWorkspaceId);
    } else {
      localStorage.removeItem('noto-primary-workspace-id');
    }
    message.success('AI 偏好已保存');
  } catch (error: any) {
    message.error(error?.message || '保存失败');
  } finally {
    aiSettingsSaving.value = false;
  }
};

onMounted(() => {
  void listWorkspaces().then((items) => {
    workspaces.value = items;
  });
  void loadAiSettings();
});
</script>

<style scoped>
.profile-page {
  display: grid;
  gap: 20px;
}

.profile-card {
  border-radius: 20px;
  box-shadow: 0 14px 35px rgba(15, 23, 42, 0.06);
}

.avatar-wrap {
  margin-bottom: 16px;
}

.profile-card h2 {
  margin: 0 0 8px;
  font-size: 22px;
}

.profile-card p {
  color: #667085;
  margin: 0;
}

.card-head h1 {
  margin: 10px 0 6px;
  font-size: 28px;
}

.card-head span {
  color: #667085;
}

.eyebrow {
  display: inline-flex;
  padding: 6px 12px;
  border-radius: 999px;
  background: #eaf2ff;
  color: #1677ff;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.ai-card {
  margin-top: 20px;
}

.field-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}

.section-desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: #667085;
  line-height: 1.5;
}

.ai-eyebrow {
  background: #f0f0ff;
  color: #6366f1;
}
</style>
