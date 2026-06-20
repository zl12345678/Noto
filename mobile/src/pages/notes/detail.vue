<template>
  <view class="read-page">
    <view v-if="loading" class="empty page-safe">加载中…</view>
    <template v-else-if="note">
      <scroll-view scroll-y class="read-body page-safe" :class="{ 'with-bar': true }">
        <view class="title-panel">
          <view class="meta-row">
            <text>{{ note.isFavorite ? '已收藏' : '普通文档' }}</text>
            <text>{{ noteTime }}</text>
          </view>
          <text v-if="!editing" class="title">{{ note.title || '无标题' }}</text>
          <input v-else v-model="draftTitle" class="title-input" placeholder="标题" />
          <view v-if="note.tags?.length && !editing" class="tag-row">
            <text v-for="tag in note.tags" :key="tag.id" class="tag">{{ tag.name }}</text>
          </view>
        </view>

        <view v-if="note.summary && !editing" class="summary">
          <text class="summary-label">摘要</text>
          <text>{{ note.summary }}</text>
        </view>

        <view class="content-panel">
          <textarea
            v-if="editing"
            v-model="draftContent"
            class="editor"
            placeholder="输入 Markdown 正文…"
            :maxlength="-1"
          />
          <rich-text v-else class="content markdown-content" :nodes="renderedContent" />
        </view>
      </scroll-view>

      <view class="fixed-bottom">
        <template v-if="editing">
          <TouchButton block @click="cancelEdit">取消</TouchButton>
          <TouchButton variant="primary" block :loading="saving" @click="save">保存</TouchButton>
        </template>
        <template v-else>
          <TouchButton variant="primary" block @click="startEdit">编辑</TouchButton>
          <TouchButton block :loading="aiLoading || uploading" @click="openMoreActions">更多</TouchButton>
        </template>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import TouchButton from '../../components/TouchButton.vue';
import {
  deleteNote,
  getNote,
  listRelatedNotes,
  patchNoteFavorite,
  patchNoteStatus,
  updateNote,
  type Note,
} from '../../api/notes';
import {
  confirmExtractTodosByAi,
  previewExtractTodosByAi,
  summarizeNoteByAi,
  transformNoteByAi,
  type NoteTransformMode,
} from '../../api/ai';
import { linkDriveFileToNote, listDriveFiles, type DriveFile } from '../../api/drive';
import { buildShareUrl, createNoteShare } from '../../api/share';
import { createTag, listTags, type Tag } from '../../api/tags';
import {
  deleteAttachment,
  listNoteAttachments,
  uploadNoteAttachmentPath,
  type AttachmentVO,
} from '../../api/attachments';
import { ensureAuthPage } from '../../stores/auth';
import { formatDateTime } from '../../utils/format';
import { formatFileSize } from '../../utils/format';
import { openExternalUrl } from '../../utils/openFile';
import { pickShareOptions } from '../../utils/shareOptions';

const note = ref<Note | null>(null);
const loading = ref(false);
const editing = ref(false);
const saving = ref(false);
const uploading = ref(false);
const aiLoading = ref(false);
const draftTitle = ref('');
const draftContent = ref('');
const tags = ref<Tag[]>([]);
const attachments = ref<AttachmentVO[]>([]);
let noteId = '';

const noteTime = computed(() => formatDateTime(note.value?.lastEditedAt || note.value?.updatedAt) || '未编辑');
const renderedContent = computed(() => renderMarkdown(note.value?.content || '空文档'));

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function renderInlineMarkdown(value: string) {
  let html = escapeHtml(value);
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  html = html.replace(/__([^_]+)__/g, '<strong>$1</strong>');
  html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>');
  html = html.replace(/_([^_]+)_/g, '<em>$1</em>');
  html = html.replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2">$1</a>');
  return html;
}

function renderMarkdown(markdown: string) {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n');
  const html: string[] = [];
  let listType: 'ul' | 'ol' | null = null;
  let inCode = false;
  let codeLines: string[] = [];

  const closeList = () => {
    if (!listType) return;
    html.push(`</${listType}>`);
    listType = null;
  };

  lines.forEach((rawLine) => {
    const line = rawLine.trimEnd();
    const trimmed = line.trim();

    if (trimmed.startsWith('```')) {
      if (inCode) {
        html.push(`<pre><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`);
        codeLines = [];
        inCode = false;
      } else {
        closeList();
        inCode = true;
      }
      return;
    }

    if (inCode) {
      codeLines.push(line);
      return;
    }

    if (!trimmed) {
      closeList();
      return;
    }

    const heading = trimmed.match(/^(#{1,6})\s+(.+)$/);
    if (heading) {
      closeList();
      const level = Math.min(heading[1].length, 4);
      html.push(`<h${level}>${renderInlineMarkdown(heading[2])}</h${level}>`);
      return;
    }

    const quote = trimmed.match(/^>\s?(.+)$/);
    if (quote) {
      closeList();
      html.push(`<blockquote>${renderInlineMarkdown(quote[1])}</blockquote>`);
      return;
    }

    const unordered = trimmed.match(/^[-*+]\s+(.+)$/);
    if (unordered) {
      if (listType !== 'ul') {
        closeList();
        html.push('<ul>');
        listType = 'ul';
      }
      html.push(`<li>${renderInlineMarkdown(unordered[1])}</li>`);
      return;
    }

    const ordered = trimmed.match(/^\d+[.)]\s+(.+)$/);
    if (ordered) {
      if (listType !== 'ol') {
        closeList();
        html.push('<ol>');
        listType = 'ol';
      }
      html.push(`<li>${renderInlineMarkdown(ordered[1])}</li>`);
      return;
    }

    closeList();
    html.push(`<p>${renderInlineMarkdown(trimmed)}</p>`);
  });

  if (inCode) {
    html.push(`<pre><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`);
  }
  closeList();
  return html.join('');
}

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
      folderId: note.value.folderId ?? null,
      parentId: note.value.parentId ?? null,
      status: note.value.status ?? 0,
      isFavorite: note.value.isFavorite,
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

function noteDraft() {
  return {
    title: note.value?.title || '无标题',
    content: note.value?.content || '',
  };
}

async function persistNoteContent(content: string) {
  if (!note.value) return;
  note.value = await updateNote(noteId, {
    title: note.value.title || '无标题',
    content,
    folderId: note.value.folderId ?? null,
    parentId: note.value.parentId ?? null,
    status: note.value.status ?? 0,
    isFavorite: note.value.isFavorite,
    summary: note.value.summary ?? null,
    tagIds: note.value.tags?.map((item) => item.id) || [],
  });
}

async function toggleFavorite() {
  if (!note.value) return;
  note.value = await patchNoteFavorite(noteId, !note.value.isFavorite);
  uni.showToast({ title: note.value.isFavorite ? '已收藏' : '已取消', icon: 'none' });
}

async function toggleArchive() {
  if (!note.value) return;
  try {
    note.value = await patchNoteStatus(noteId, note.value.status === 1 ? 0 : 1);
    uni.showToast({ title: note.value.status === 1 ? '已归档' : '已恢复', icon: 'none' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '操作失败', icon: 'none' });
  }
}

async function shareNote() {
  if (!note.value) return;
  const options = await pickShareOptions();
  if (options === null) return;
  try {
    const link = await createNoteShare(noteId, options);
    const url = buildShareUrl(link.sharePath);
    uni.setClipboardData({
      data: url,
      success: () => uni.showToast({ title: '分享链接已复制', icon: 'none' }),
    });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '创建分享失败', icon: 'none' });
  }
}

function openAiActions() {
  uni.showActionSheet({
    itemList: ['生成摘要', '润色全文', '条文化', '结构化', '周报草稿', '复盘草稿', '提取待办', '相关笔记'],
    success: async (res) => {
      if (res.tapIndex === 0) await runAiSummary();
      if (res.tapIndex === 1) await runAiTransform('polish');
      if (res.tapIndex === 2) await runAiTransform('bulletize');
      if (res.tapIndex === 3) await runAiTransform('structure');
      if (res.tapIndex === 4) await runAiTransform('template_weekly');
      if (res.tapIndex === 5) await runAiTransform('template_retro');
      if (res.tapIndex === 6) await runAiExtractTodos();
      if (res.tapIndex === 7) await openRelatedNotes();
    },
  });
}

function openMoreActions() {
  if (!note.value) return;
  const items = [
    note.value.isFavorite ? '取消收藏' : '收藏',
    note.value.status === 1 ? '恢复文档' : '归档',
    'AI 处理',
    '标签',
    `附件（${attachments.value.length}）`,
    '分享',
    '删除',
  ];
  uni.showActionSheet({
    itemList: items,
    success: async (res) => {
      if (res.tapIndex === 0) await toggleFavorite();
      if (res.tapIndex === 1) await toggleArchive();
      if (res.tapIndex === 2) openAiActions();
      if (res.tapIndex === 3) await openTagManager();
      if (res.tapIndex === 4) openAttachmentManager();
      if (res.tapIndex === 5) await shareNote();
      if (res.tapIndex === 6) onDelete();
    },
  });
}

async function runAiSummary() {
  if (!note.value) return;
  aiLoading.value = true;
  try {
    const result = await summarizeNoteByAi(noteId, noteDraft(), true);
    note.value = await getNote(noteId);
    const points = result.keyPoints?.slice(0, 4).map((item) => `- ${item}`).join('\n') || '';
    uni.showModal({
      title: '摘要已生成',
      content: [result.summary, points].filter(Boolean).join('\n\n').slice(0, 900),
      showCancel: false,
    });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '生成摘要失败', icon: 'none' });
  } finally {
    aiLoading.value = false;
  }
}

async function runAiTransform(mode: NoteTransformMode) {
  if (!note.value) return;
  aiLoading.value = true;
  try {
    const result = await transformNoteByAi(noteId, { mode, ...noteDraft() });
    uni.showModal({
      title: transformTitle(mode),
      content: result.content.slice(0, 900),
      confirmText: '应用',
      cancelText: '取消',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await persistNoteContent(result.content);
          uni.showToast({ title: '已应用到正文', icon: 'success' });
        } catch (e: any) {
          uni.showToast({ title: e?.message || '保存失败', icon: 'none' });
        }
      },
    });
  } catch (e: any) {
    uni.showToast({ title: e?.message || 'AI 处理失败', icon: 'none' });
  } finally {
    aiLoading.value = false;
  }
}

function transformTitle(mode: NoteTransformMode) {
  const labels: Record<NoteTransformMode, string> = {
    polish: '润色预览',
    bulletize: '条文化预览',
    structure: '结构化预览',
    template_weekly: '周报草稿',
    template_retro: '复盘草稿',
    template_proposal: '方案草稿',
  };
  return labels[mode];
}

async function runAiExtractTodos() {
  if (!note.value) return;
  aiLoading.value = true;
  try {
    const preview = await previewExtractTodosByAi(noteId);
    const candidates = (preview.suggestions || []).filter((item) => !item.duplicate);
    if (!candidates.length) {
      uni.showToast({ title: '没有可创建的待办', icon: 'none' });
      return;
    }
    const list = candidates.slice(0, 8).map((item, index) => `${index + 1}. ${item.title}`).join('\n');
    uni.showModal({
      title: `提取到 ${candidates.length} 条待办`,
      content: list,
      confirmText: '创建',
      cancelText: '取消',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          const result = await confirmExtractTodosByAi(noteId, candidates);
          uni.showToast({ title: `已创建 ${result.createdCount} 条`, icon: 'success' });
        } catch (e: any) {
          uni.showToast({ title: e?.message || '创建失败', icon: 'none' });
        }
      },
    });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '提取失败', icon: 'none' });
  } finally {
    aiLoading.value = false;
  }
}

async function openRelatedNotes() {
  aiLoading.value = true;
  try {
    const related = await listRelatedNotes(noteId, 8);
    if (!related.length) {
      uni.showToast({ title: '暂无相关笔记', icon: 'none' });
      return;
    }
    uni.showActionSheet({
      itemList: related.map((item) => item.title || '无标题').slice(0, 8),
      success: (res) => {
        const target = related[res.tapIndex];
        if (target) uni.navigateTo({ url: `/pages/notes/detail?id=${target.id}` });
      },
    });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载相关笔记失败', icon: 'none' });
  } finally {
    aiLoading.value = false;
  }
}

async function loadTags() {
  if (!note.value?.workspaceId) {
    tags.value = [];
    return;
  }
  try {
    tags.value = await listTags(String(note.value.workspaceId));
  } catch {
    tags.value = [];
  }
}

async function openTagManager() {
  if (!note.value) return;
  await loadTags();
  uni.showActionSheet({
    itemList: ['选择已有标签', '新建标签'],
    success: async (res) => {
      if (res.tapIndex === 0) chooseTag();
      if (res.tapIndex === 1) createAndAttachTag();
    },
  });
}

function chooseTag() {
  if (!note.value) return;
  if (!tags.value.length) {
    uni.showToast({ title: '暂无标签，请先新建', icon: 'none' });
    return;
  }
  uni.showActionSheet({
    itemList: tags.value.map((item) => item.name).slice(0, 20),
    success: async (res) => {
      const tag = tags.value[res.tapIndex];
      if (!tag) return;
      await saveTagIds([...new Set([...(note.value?.tags?.map((item) => item.id) || []), tag.id])]);
    },
  });
}

function createAndAttachTag() {
  if (!note.value?.workspaceId) return;
  uni.showModal({
    title: '新建标签',
    editable: true,
    placeholderText: '标签名称',
    success: async (res) => {
      if (!res.confirm || !res.content?.trim() || !note.value?.workspaceId) return;
      try {
        const tag = await createTag({ workspaceId: String(note.value.workspaceId), name: res.content.trim() });
        await saveTagIds([...new Set([...(note.value.tags?.map((item) => item.id) || []), tag.id])]);
      } catch (e: any) {
        uni.showToast({ title: e?.message || '创建标签失败', icon: 'none' });
      }
    },
  });
}

async function saveTagIds(tagIds: string[]) {
  if (!note.value) return;
  try {
    note.value = await updateNote(noteId, {
      title: note.value.title || '无标题',
      content: note.value.content || '',
      folderId: note.value.folderId ?? null,
      parentId: note.value.parentId ?? null,
      status: note.value.status ?? 0,
      isFavorite: note.value.isFavorite,
      summary: note.value.summary ?? null,
      tagIds,
    });
    uni.showToast({ title: '标签已更新', icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '更新标签失败', icon: 'none' });
  }
}

async function loadAttachments() {
  if (!noteId) return;
  try {
    attachments.value = await listNoteAttachments(noteId);
  } catch {
    attachments.value = [];
  }
}

function openAttachmentManager() {
  uni.showActionSheet({
    itemList: [`查看附件（${attachments.value.length}）`, '上传附件', '从网盘关联'],
    success: async (res) => {
      if (res.tapIndex === 0) openAttachmentList();
      if (res.tapIndex === 1) chooseAndUploadAttachment();
      if (res.tapIndex === 2) linkFromDrive();
    },
  });
}

function openAttachmentList() {
  if (!attachments.value.length) {
    uni.showToast({ title: '暂无附件', icon: 'none' });
    return;
  }
  uni.showActionSheet({
    itemList: attachments.value.map((item) => `${item.fileName} · ${formatFileSize(item.fileSize)}`).slice(0, 20),
    success: (res) => {
      const attachment = attachments.value[res.tapIndex];
      if (attachment) openAttachmentActions(attachment);
    },
  });
}

function openAttachmentActions(attachment: AttachmentVO) {
  uni.showActionSheet({
    itemList: ['打开', '删除'],
    success: async (res) => {
      if (res.tapIndex === 0) openAttachment(attachment);
      if (res.tapIndex === 1) confirmDeleteAttachment(attachment);
    },
  });
}

function openAttachment(attachment: AttachmentVO) {
  openExternalUrl(attachment.fileUrl, attachment.fileName);
}

function chooseAndUploadAttachment() {
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
      uploading.value = true;
      try {
        await uploadNoteAttachmentPath({ noteId, filePath, name: file?.name });
        await loadAttachments();
        uni.showToast({ title: '上传成功', icon: 'success' });
      } catch (e: any) {
        uni.showToast({ title: e?.message || '上传失败', icon: 'none' });
      } finally {
        uploading.value = false;
      }
    },
  });
}

async function linkFromDrive() {
  if (!note.value?.workspaceId) return;
  try {
    const [driveFiles, currentAttachments] = await Promise.all([
      listDriveFiles({ workspaceId: String(note.value.workspaceId) }),
      listNoteAttachments(noteId),
    ]);
    const linkedIds = new Set(currentAttachments.map((item) => String(item.id)));
    const candidates = driveFiles.filter((item) => !linkedIds.has(String(item.id)));
    if (!candidates.length) {
      uni.showToast({ title: '网盘暂无可关联文件', icon: 'none' });
      return;
    }
    chooseDriveFileToLink(candidates);
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载网盘文件失败', icon: 'none' });
  }
}

function chooseDriveFileToLink(candidates: DriveFile[]) {
  uni.showActionSheet({
    itemList: candidates.map((item) => item.fileName).slice(0, 20),
    success: async (res) => {
      const file = candidates[res.tapIndex];
      if (!file) return;
      try {
        await linkDriveFileToNote(file.id, noteId);
        await loadAttachments();
        uni.showToast({ title: '已关联文件', icon: 'success' });
      } catch (e: any) {
        uni.showToast({ title: e?.message || '关联失败', icon: 'none' });
      }
    },
  });
}

function confirmDeleteAttachment(attachment: AttachmentVO) {
  uni.showModal({
    title: '删除附件',
    content: `确定删除「${attachment.fileName}」吗？`,
    confirmColor: '#b91c1c',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await deleteAttachment(attachment.id);
        await loadAttachments();
        uni.showToast({ title: '附件已删除', icon: 'none' });
      } catch (e: any) {
        uni.showToast({ title: e?.message || '删除失败', icon: 'none' });
      }
    },
  });
}

function onDelete() {
  if (!note.value) return;
  uni.showModal({
    title: '删除文档',
    content: `确定删除「${note.value.title || '无标题'}」吗？`,
    confirmColor: '#b91c1c',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await deleteNote(noteId);
        uni.showToast({ title: '已删除', icon: 'none' });
        uni.navigateBack();
      } catch (e: any) {
        uni.showToast({ title: e?.message || '删除失败', icon: 'none' });
      }
    },
  });
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
    await loadTags();
    await loadAttachments();
    uni.setNavigationBarTitle({ title: note.value.title || '阅读' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped lang="scss">
.read-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f6f3ee 0%, #f8f7f4 48%, #f7f6f3 100%);
}

.read-body.with-bar {
  padding-bottom: calc(140rpx + env(safe-area-inset-bottom));
}

.title-panel {
  padding: 32rpx 30rpx 34rpx;
  margin-bottom: 22rpx;
  border-radius: 28rpx;
  background: #17201f;
  color: #fff;
  box-shadow: 0 20rpx 44rpx rgba(23, 32, 31, 0.16);
}

.meta-row {
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
  margin-bottom: 18rpx;
  color: rgba(255, 255, 255, 0.58);
  font-size: 23rpx;
}

.title {
  display: block;
  font-size: 48rpx;
  font-weight: 760;
  line-height: 1.22;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 20rpx;
}

.tag {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(103, 232, 249, 0.16);
  color: #a5f3fc;
  font-size: 22rpx;
  font-weight: 650;
}

.title-input {
  width: 100%;
  min-height: 86rpx;
  color: #1c1917;
  font-size: 40rpx;
  font-weight: 720;
  padding: 18rpx 20rpx;
  background: rgba(255, 255, 255, 0.94);
  border-radius: 16rpx;
  box-sizing: border-box;
}

.summary {
  padding: 24rpx 26rpx;
  margin-bottom: 22rpx;
  border-radius: 22rpx;
  background: #ecfeff;
  color: #164e63;
  line-height: 1.65;
  font-size: 27rpx;
}

.summary-label {
  display: block;
  font-size: 22rpx;
  color: #0891b2;
  font-weight: 700;
  margin-bottom: 10rpx;
}

.content-panel {
  padding: 30rpx 28rpx;
  border-radius: 26rpx;
  background: #fff;
  box-shadow: 0 10rpx 30rpx rgba(68, 64, 60, 0.06);
}

.content {
  display: block;
  line-height: 1.9;
  font-size: 31rpx;
  color: #292524;
}

.markdown-content {
  word-break: break-word;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  display: block;
  margin: 22rpx 0 14rpx;
  color: #1c1917;
  font-weight: 760;
  line-height: 1.28;
}

.markdown-content :deep(h1) {
  font-size: 40rpx;
}

.markdown-content :deep(h2) {
  font-size: 36rpx;
}

.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  font-size: 32rpx;
}

.markdown-content :deep(p),
.markdown-content :deep(blockquote),
.markdown-content :deep(ul),
.markdown-content :deep(ol),
.markdown-content :deep(pre) {
  display: block;
  margin: 0 0 20rpx;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  padding-left: 38rpx;
}

.markdown-content :deep(li) {
  display: list-item;
  margin-bottom: 8rpx;
  line-height: 1.8;
}

.markdown-content :deep(blockquote) {
  padding: 14rpx 18rpx;
  border-left: 6rpx solid #67e8f9;
  border-radius: 12rpx;
  background: #ecfeff;
  color: #164e63;
}

.markdown-content :deep(code) {
  padding: 2rpx 8rpx;
  border-radius: 8rpx;
  background: #f5f5f4;
  color: #0f766e;
  font-size: 28rpx;
}

.markdown-content :deep(pre) {
  overflow: auto;
  padding: 18rpx;
  border-radius: 16rpx;
  background: #1c1917;
  color: #fafaf9;
  white-space: pre;
}

.markdown-content :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.markdown-content :deep(strong) {
  font-weight: 760;
}

.markdown-content :deep(a) {
  color: #0891b2;
}

.editor {
  width: 100%;
  min-height: 60vh;
  padding: 0;
  background: transparent;
  line-height: 1.8;
  font-size: 30rpx;
  color: #292524;
  box-sizing: border-box;
}
</style>
