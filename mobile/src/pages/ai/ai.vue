<template>
  <view class="ai-page">
    <view class="ai-nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <text class="ai-title">AI 助手</text>
      <picker :range="workspaceNames" :value="workspaceIndex" @change="onWorkspaceChange">
        <view class="ws-picker">{{ currentWorkspaceName || '选择知识库' }} ▾</view>
      </picker>
    </view>

    <scroll-view scroll-y class="chat-log" :scroll-top="scrollTop" :style="{ paddingBottom: inputAreaHeight + 'px' }">
      <view v-if="!messages.length && !loading" class="empty-state">
        <text class="empty-title">问知识库、待办或文档</text>
        <text class="muted">点击下方快捷问题开始</text>
      </view>
      <view v-for="(msg, idx) in messages" :key="idx" class="msg" :class="msg.role">
        <text class="bubble">{{ msg.content }}</text>
        <view v-if="msg.references?.length" class="refs">
          <text
            v-for="ref in msg.references"
            :key="ref.noteId"
            class="ref-link"
            @click="openNote(ref.noteId)"
          >📄 {{ ref.noteTitle }}</text>
        </view>
      </view>
      <view v-if="loading" class="typing">正在思考…</view>
    </scroll-view>

    <view class="input-area" :style="{ paddingBottom: 'calc(16rpx + env(safe-area-inset-bottom))' }">
      <scroll-view scroll-x class="chips" :show-scrollbar="false">
        <text v-for="chip in quickQuestions" :key="chip" class="chip" @click="askQuick(chip)">{{ chip }}</text>
      </scroll-view>
      <view class="input-row">
        <input
          v-model="question"
          class="input"
          placeholder="输入问题…"
          confirm-type="send"
          :adjust-position="true"
          @confirm="send"
        />
        <button class="send-btn" :loading="loading" @click="send">发送</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { askAi, type AiReference } from '../../api/ai';
import { ensureAuthPage } from '../../stores/auth';
import { refreshWorkspaces, workspaceState } from '../../stores/workspace';

type ChatMsg = { role: 'user' | 'assistant'; content: string; references?: AiReference[] };

const quickQuestions = [
  '今天先做哪几件？',
  '有哪些逾期待办？',
  '总结最近笔记',
];

const statusBarHeight = ref(0);
const inputAreaHeight = ref(160);
const workspaceIndex = ref(0);
const messages = ref<ChatMsg[]>([]);
const question = ref('');
const loading = ref(false);
const scrollTop = ref(0);

const workspaceNames = computed(() => workspaceState.items.map((w) => w.name));
const currentWorkspace = computed(() => workspaceState.items[workspaceIndex.value]);
const currentWorkspaceName = computed(() => currentWorkspace.value?.name || '');

function onWorkspaceChange(e: { detail: { value: string } }) {
  workspaceIndex.value = Number(e.detail.value);
  messages.value = [];
}

function openNote(id: string) {
  uni.navigateTo({ url: `/pages/notes/detail?id=${id}` });
}

function askQuick(text: string) {
  question.value = text;
  send();
}

async function send() {
  const q = question.value.trim();
  if (!q || loading.value) return;
  if (!currentWorkspace.value) {
    uni.showToast({ title: '请先选择知识库', icon: 'none' });
    return;
  }
  messages.value.push({ role: 'user', content: q });
  question.value = '';
  loading.value = true;
  scrollTop.value += 999;
  try {
    const result = await askAi({
      workspaceId: String(currentWorkspace.value.id),
      question: q,
      scope: 'workspace',
    });
    messages.value.push({ role: 'assistant', content: result.answer, references: result.references });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '问答失败', icon: 'none' });
  } finally {
    loading.value = false;
    scrollTop.value += 999;
  }
}

onShow(async () => {
  if (!ensureAuthPage()) return;
  const sys = uni.getSystemInfoSync();
  statusBarHeight.value = sys.statusBarHeight || 0;
  await refreshWorkspaces();
});
</script>

<style scoped lang="scss">
.ai-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f7f6f3;
}

.ai-nav {
  padding: 16rpx 24rpx 20rpx;
  background: #f7f6f3;
  border-bottom: 1rpx solid #e7e5e4;
}

.ai-title {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  margin-bottom: 8rpx;
}

.ws-picker {
  font-size: 26rpx;
  color: #0891b2;
}

.chat-log {
  flex: 1;
  padding: 24rpx;
  box-sizing: border-box;
}

.empty-state {
  padding: 80rpx 40rpx;
  text-align: center;
}

.empty-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 12rpx;
}

.msg {
  margin-bottom: 24rpx;
  display: flex;
  flex-direction: column;
}

.msg.user {
  align-items: flex-end;
}

.bubble {
  max-width: 88%;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  background: #fff;
  line-height: 1.65;
  white-space: pre-wrap;
  font-size: 28rpx;
}

.msg.user .bubble {
  background: #0891b2;
  color: #fff;
}

.refs { margin-top: 12rpx; }

.ref-link {
  display: block;
  color: #0891b2;
  font-size: 26rpx;
  margin-top: 8rpx;
}

.typing {
  text-align: center;
  color: #78716c;
  font-size: 26rpx;
}

.input-area {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(100rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #e7e5e4;
  padding: 12rpx 24rpx 16rpx;
}

.chips {
  white-space: nowrap;
  margin-bottom: 12rpx;
}

.chip {
  display: inline-block;
  margin-right: 12rpx;
  padding: 12rpx 20rpx;
  background: #ecfeff;
  color: #0891b2;
  border-radius: 999rpx;
  font-size: 24rpx;
}

.input-row {
  display: flex;
  gap: 12rpx;
  align-items: center;
}

.input {
  flex: 1;
  height: 80rpx;
  padding: 0 24rpx;
  background: #f5f5f4;
  border-radius: 999rpx;
  font-size: 28rpx;
}

.send-btn {
  min-width: 120rpx;
  height: 80rpx;
  line-height: 80rpx;
  margin: 0;
  padding: 0 24rpx;
  background: #0891b2;
  color: #fff;
  border-radius: 999rpx;
  font-size: 28rpx;
}
</style>
