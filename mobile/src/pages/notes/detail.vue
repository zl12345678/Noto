<template>
  <view class="read-page">
    <view v-if="loading" class="empty page-safe">加载中…</view>
    <template v-else-if="note">
      <scroll-view scroll-y class="read-body page-safe" :class="{ 'with-bar': true }">
        <text v-if="!editing" class="title">{{ note.title }}</text>
        <input v-else v-model="draftTitle" class="title-input" placeholder="标题" />

        <view v-if="note.summary && !editing" class="summary card">
          <text class="summary-label">摘要</text>
          <text>{{ note.summary }}</text>
        </view>

        <textarea
          v-if="editing"
          v-model="draftContent"
          class="editor"
          placeholder="输入 Markdown 正文…"
          :maxlength="-1"
        />
        <text v-else class="content">{{ note.content || '（空文档）' }}</text>
      </scroll-view>

      <view class="fixed-bottom">
        <template v-if="editing">
          <TouchButton block @click="cancelEdit">取消</TouchButton>
          <TouchButton variant="primary" block :loading="saving" @click="save">保存</TouchButton>
        </template>
        <template v-else>
          <TouchButton block @click="toggleFavorite">{{ note.isFavorite ? '已收藏' : '收藏' }}</TouchButton>
          <TouchButton variant="primary" block @click="startEdit">编辑</TouchButton>
        </template>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app';
import { ref } from 'vue';
import TouchButton from '../../components/TouchButton.vue';
import { getNote, patchNoteFavorite, updateNote, type Note } from '../../api/notes';
import { ensureAuthPage } from '../../stores/auth';

const note = ref<Note | null>(null);
const loading = ref(false);
const editing = ref(false);
const saving = ref(false);
const draftTitle = ref('');
const draftContent = ref('');
let noteId = '';

function startEdit() {
  draftTitle.value = note.value?.title || '';
  draftContent.value = note.value?.content || '';
  editing.value = true;
}

function cancelEdit() {
  editing.value = false;
}

async function save() {
  if (!note.value) return;
  saving.value = true;
  try {
    note.value = await updateNote(noteId, {
      title: draftTitle.value.trim() || '无标题',
      content: draftContent.value,
    });
    editing.value = false;
    uni.setNavigationBarTitle({ title: note.value.title || '阅读' });
    uni.showToast({ title: '已保存', icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '保存失败', icon: 'none' });
  } finally {
    saving.value = false;
  }
}

async function toggleFavorite() {
  if (!note.value) return;
  note.value = await patchNoteFavorite(noteId, !note.value.isFavorite);
  uni.showToast({ title: note.value.isFavorite ? '已收藏' : '已取消', icon: 'none' });
}

onLoad(async (query) => {
  if (!ensureAuthPage()) return;
  noteId = query?.id as string;
  if (!noteId) {
    uni.showToast({ title: '文档不存在', icon: 'none' });
    return;
  }
  loading.value = true;
  try {
    note.value = await getNote(noteId);
    uni.setNavigationBarTitle({ title: note.value.title || '阅读' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped lang="scss">
.read-page { min-height: 100vh; background: #f7f6f3; }

.read-body.with-bar {
  padding-bottom: calc(140rpx + env(safe-area-inset-bottom));
}

.title {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  line-height: 1.35;
  margin-bottom: 24rpx;
}

.title-input {
  width: 100%;
  font-size: 40rpx;
  font-weight: 700;
  margin-bottom: 24rpx;
  padding: 16rpx;
  background: #fff;
  border-radius: 16rpx;
  box-sizing: border-box;
}

.summary {
  margin-bottom: 24rpx;
  background: #ecfeff;
}

.summary-label {
  display: block;
  font-size: 22rpx;
  color: #0891b2;
  margin-bottom: 8rpx;
}

.content {
  display: block;
  white-space: pre-wrap;
  line-height: 1.85;
  font-size: 30rpx;
  color: #292524;
}

.editor {
  width: 100%;
  min-height: 60vh;
  padding: 24rpx;
  background: #fff;
  border-radius: 16rpx;
  line-height: 1.7;
  font-size: 28rpx;
  box-sizing: border-box;
}
</style>
