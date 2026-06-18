<template>
  <view class="page-safe">
    <view class="search-box">
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索笔记标题与内容"
        focus
        confirm-type="search"
        @confirm="search"
      />
    </view>

    <view v-if="loading" class="empty">搜索中…</view>
    <view v-else-if="searched && !results.length" class="empty card">无匹配结果</view>
    <view v-else-if="!searched" class="hint muted">输入关键词后回车搜索</view>

    <view
      v-for="item in results"
      :key="item.noteId"
      class="card result"
      @click="openNote(item.noteId)"
    >
      <text class="title">{{ item.title }}</text>
      <text class="snippet muted">{{ item.snippet }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { searchNotes, type SearchResult } from '../../api/search';
import { ensureAuthPage } from '../../stores/auth';

const keyword = ref('');
const results = ref<SearchResult[]>([]);
const loading = ref(false);
const searched = ref(false);

async function search() {
  if (!keyword.value.trim()) return;
  loading.value = true;
  searched.value = true;
  try {
    const data = await searchNotes({ keyword: keyword.value.trim(), size: 30 });
    results.value = data.records;
  } catch (e: any) {
    uni.showToast({ title: e?.message || '搜索失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

function openNote(id: string) {
  uni.navigateTo({ url: `/pages/notes/detail?id=${id}` });
}

onLoad(() => {
  ensureAuthPage();
});
</script>

<style scoped lang="scss">
.search-box { margin-bottom: 24rpx; }
.search-input {
  height: 88rpx;
  padding: 0 28rpx;
  background: #fff;
  border-radius: 20rpx;
  border: 1rpx solid #e7e5e4;
  font-size: 30rpx;
}
.hint { text-align: center; padding: 40rpx; }
.result .title { display: block; font-size: 32rpx; font-weight: 600; margin-bottom: 8rpx; }
.snippet { display: block; line-height: 1.5; }
</style>
