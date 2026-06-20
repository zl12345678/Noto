<template>
  <view class="import-page page-safe">
    <view class="hero">
      <text class="eyebrow">Import</text>
      <text class="title">导入笔记</text>
      <text class="desc">{{ folderId ? '导入到当前文件夹' : '导入到知识库根目录' }}</text>
    </view>

    <view class="card form-card">
      <view class="field">
        <text class="label">标题</text>
        <input v-model="title" class="input" placeholder="留空时使用内容首行或文件名" />
      </view>
      <view class="field">
        <text class="label">粘贴 Markdown / 文本</text>
        <textarea v-model="content" class="textarea" placeholder="把文档内容粘贴到这里" :maxlength="-1" />
      </view>
      <view class="row">
        <text>导入后用 AI 结构化</text>
        <switch :checked="structureWithAi" @change="setStructureWithAi" />
      </view>
      <button class="btn-primary" :loading="importing" @click="importText">导入文本</button>
      <button class="secondary-btn" :loading="importing" @click="chooseAndImportFile">选择文件导入</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app';
import { ref } from 'vue';
import { importNoteClip, importNoteFilePath } from '../../api/notes';
import { ensureAuthPage } from '../../stores/auth';

const workspaceId = ref('');
const folderId = ref<string | null>(null);
const title = ref('');
const content = ref('');
const structureWithAi = ref(false);
const importing = ref(false);

function setStructureWithAi(event: Event) {
  structureWithAi.value = Boolean((event as unknown as { detail?: { value?: boolean } }).detail?.value);
}

function fallbackTitle() {
  const firstLine = content.value.split('\n').map((line) => line.trim()).find(Boolean);
  return title.value.trim() || firstLine?.replace(/^#+\s*/, '').slice(0, 60) || '导入文档';
}

async function importText() {
  if (!workspaceId.value) {
    uni.showToast({ title: '缺少知识库', icon: 'none' });
    return;
  }
  if (!content.value.trim()) {
    uni.showToast({ title: '请粘贴要导入的内容', icon: 'none' });
    return;
  }
  importing.value = true;
  try {
    const result = await importNoteClip({
      workspaceId: workspaceId.value,
      folderId: folderId.value,
      title: fallbackTitle(),
      content: content.value,
      contentType: 'markdown',
      structureWithAi: structureWithAi.value,
    });
    uni.showToast({ title: '导入成功', icon: 'success' });
    uni.redirectTo({ url: `/pages/notes/detail?id=${result.noteId}` });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '导入失败', icon: 'none' });
  } finally {
    importing.value = false;
  }
}

function chooseAndImportFile() {
  if (!workspaceId.value) {
    uni.showToast({ title: '缺少知识库', icon: 'none' });
    return;
  }
  const chooseFile = (uni as any).chooseFile;
  if (!chooseFile) {
    uni.showToast({ title: '当前平台不支持选择文件', icon: 'none' });
    return;
  }
  chooseFile({
    count: 1,
    success: async (res: any) => {
      const file = res.tempFiles?.[0];
      const filePath = file?.path || res.tempFilePaths?.[0];
      if (!filePath) return;
      importing.value = true;
      try {
        const result = await importNoteFilePath({
          workspaceId: workspaceId.value,
          folderId: folderId.value,
          filePath,
          title: title.value,
          name: file?.name,
        });
        uni.showToast({ title: '导入成功', icon: 'success' });
        uni.redirectTo({ url: `/pages/notes/detail?id=${result.noteId}` });
      } catch (e: any) {
        uni.showToast({ title: e?.message || '导入失败', icon: 'none' });
      } finally {
        importing.value = false;
      }
    },
  });
}

onLoad((query) => {
  if (!ensureAuthPage()) return;
  workspaceId.value = String(query?.workspaceId || '');
  folderId.value = query?.folderId ? String(query.folderId) : null;
});
</script>

<style scoped lang="scss">
.import-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f6f3ee 0%, #f8f7f4 100%);
}

.hero {
  padding: 32rpx;
  margin-bottom: 22rpx;
  border-radius: 28rpx;
  background: #17201f;
  color: #fff;
}

.eyebrow {
  display: block;
  color: rgba(255, 255, 255, 0.58);
  font-size: 22rpx;
  font-weight: 650;
  margin-bottom: 10rpx;
}

.title {
  display: block;
  font-size: 50rpx;
  font-weight: 760;
  line-height: 1.12;
}

.desc {
  display: block;
  margin-top: 14rpx;
  color: rgba(255, 255, 255, 0.68);
  font-size: 25rpx;
}

.form-card {
  padding: 30rpx;
}

.field {
  margin-bottom: 26rpx;
}

.label {
  display: block;
  margin-bottom: 12rpx;
  color: #57534e;
  font-size: 26rpx;
  font-weight: 650;
}

.input,
.textarea {
  width: 100%;
  padding: 0 24rpx;
  border: 1rpx solid #e7e5e4;
  border-radius: 16rpx;
  background: #fafaf9;
  box-sizing: border-box;
  font-size: 28rpx;
}

.input {
  height: 84rpx;
}

.textarea {
  min-height: 380rpx;
  padding-top: 20rpx;
  line-height: 1.7;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 8rpx 0 28rpx;
  color: #292524;
  font-size: 27rpx;
}

.secondary-btn {
  height: 88rpx;
  line-height: 88rpx;
  margin-top: 18rpx;
  border-radius: 18rpx;
  color: #0f766e;
  background: #ecfeff;
  font-size: 28rpx;
  font-weight: 700;
}
</style>
