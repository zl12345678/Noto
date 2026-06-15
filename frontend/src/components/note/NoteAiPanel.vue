<template>
  <aside class="note-ai-panel">
    <header class="panel-head">
      <div class="panel-brand">
        <span class="brand-icon">
          <ThunderboltOutlined />
        </span>
        <div class="brand-text">
          <span class="brand-title">AI 助手</span>
          <span class="brand-status" :class="aiEnabled ? 'is-on' : 'is-off'">
            {{ aiEnabled ? '在线' : '未启用' }}
          </span>
        </div>
      </div>
      <button type="button" class="icon-btn" aria-label="关闭 AI 助手" @click="emit('close')">
        <CloseOutlined />
      </button>
    </header>

    <div v-if="!aiEnabled" class="disabled-banner">
      配置 NOTO_AI_ENABLED 与 API Key 后可用
    </div>

    <div v-if="preview.type" class="preview-card">
      <div class="preview-head">
        <span class="preview-badge">{{ previewTitle }}</span>
        <button type="button" class="text-btn" @click="clearPreview">关闭</button>
      </div>

      <template v-if="preview.type === 'summary'">
        <p class="preview-text">{{ preview.summary?.summary }}</p>
        <ul v-if="preview.summary?.keyPoints?.length" class="preview-list">
          <li v-for="(point, index) in preview.summary.keyPoints" :key="`kp-${index}`">{{ point }}</li>
        </ul>
        <button type="button" class="primary-chip" @click="applySummary">写入摘要</button>
      </template>

      <template v-else-if="preview.type === 'transform'">
        <pre class="preview-markdown">{{ preview.content }}</pre>
        <button
          v-if="preview.selectionOriginal"
          type="button"
          class="primary-chip"
          @click="applySelectionReplace"
        >
          替换选中
        </button>
        <button v-else type="button" class="primary-chip" @click="applyTransform">应用到正文</button>
      </template>

      <template v-else-if="preview.type === 'ask'">
        <p class="preview-text">{{ preview.answer }}</p>
      </template>
    </div>

    <nav class="seg-nav" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        role="tab"
        class="seg-item"
        :class="{ active: activeTab === tab.key }"
        :aria-selected="activeTab === tab.key"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </nav>

    <div class="panel-body">
      <section v-show="activeTab === 'quick'" class="tab-panel">
        <div class="chip-grid">
          <button
            type="button"
            class="action-chip"
            :disabled="!aiEnabled"
            :class="{ loading: loadingAction === 'summarize' }"
            @click="runSummarize"
          >
            <FileTextOutlined />
            <span>摘要</span>
          </button>
          <button
            type="button"
            class="action-chip"
            :disabled="!aiEnabled"
            :class="{ loading: loadingAction === 'polish' }"
            @click="runTransform('polish')"
          >
            <HighlightOutlined />
            <span>润色</span>
          </button>
          <button
            type="button"
            class="action-chip"
            :disabled="!aiEnabled"
            :class="{ loading: loadingAction === 'bulletize' }"
            @click="runTransform('bulletize')"
          >
            <UnorderedListOutlined />
            <span>条文化</span>
          </button>
          <button type="button" class="action-chip" @click="emit('extract-todos', 'ai')">
            <RobotOutlined />
            <span>提取待办</span>
          </button>
          <button type="button" class="action-chip action-chip--muted" @click="emit('extract-todos', 'markdown')">
            <ScanOutlined />
            <span>规则提取</span>
          </button>
        </div>
        <label class="mini-switch">
          <a-switch v-model:checked="autoSummaryOnSave" size="small" @change="persistAutoSummary" />
          <span>保存时自动摘要</span>
        </label>
      </section>

      <section v-show="activeTab === 'write'" class="tab-panel">
        <p class="section-caption">模板</p>
        <div class="chip-grid chip-grid--2">
          <button
            v-for="item in templateActions"
            :key="item.mode"
            type="button"
            class="action-chip action-chip--flat"
            :disabled="!aiEnabled"
            :class="{ loading: loadingAction === item.mode }"
            @click="runTransform(item.mode)"
          >
            {{ item.label }}
          </button>
        </div>
        <p class="section-caption">选中改写</p>
        <div class="chip-row">
          <button
            v-for="item in selectionActions"
            :key="item.mode"
            type="button"
            class="action-chip action-chip--flat"
            :disabled="!aiEnabled"
            :class="{ loading: selectionLoading === item.mode }"
            @click="runSelectionTransform(item.mode)"
          >
            {{ item.label }}
          </button>
        </div>
      </section>

      <section v-show="activeTab === 'ask'" class="tab-panel">
        <div class="ask-box">
          <a-textarea
            v-model:value="questionInput"
            :rows="2"
            placeholder="问这篇文档…"
            :disabled="!aiEnabled"
            class="ask-input"
            @keydown.enter.exact.prevent="askCurrentNote"
          />
          <button
            type="button"
            class="ask-send"
            :disabled="!aiEnabled || asking"
            :class="{ loading: asking }"
            aria-label="提问"
            @click="askCurrentNote"
          >
            <SendOutlined />
          </button>
        </div>
        <button
          v-if="aiEnabled"
          type="button"
          class="ghost-chip"
          :class="{ loading: reindexing }"
          @click="runReindex"
        >
          <ReloadOutlined />
          <span>{{ reindexing ? '索引中…' : '重建索引' }}</span>
        </button>
        <p v-if="aiEnabled && ragStatus" class="rag-status-line">
          知识库向量：{{ ragStatus.indexedChunks }} 片段
          <span v-if="!ragStatus.ragAvailable" class="rag-status-warn">（未启用 pgvector）</span>
        </p>
      </section>

      <section v-show="activeTab === 'related'" class="tab-panel">
        <a-spin :spinning="loadingRelated" size="small">
          <ul v-if="relatedNotes.length" class="related-list">
            <li v-for="item in relatedNotes" :key="item.id" @click="emit('open-note', item.id)">
              <FileOutlined class="related-icon" />
              <div class="related-body">
                <span class="related-title">{{ item.title }}</span>
                <span class="related-meta">{{ item.summary || item.excerpt || '暂无摘要' }}</span>
              </div>
            </li>
          </ul>
          <p v-else class="empty-hint">相关文档将显示在这里</p>
        </a-spin>
      </section>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  CloseOutlined,
  FileOutlined,
  FileTextOutlined,
  HighlightOutlined,
  ReloadOutlined,
  RobotOutlined,
  ScanOutlined,
  SendOutlined,
  ThunderboltOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons-vue';
import {
  askAiStream,
  reindexNoteRag,
  getNoteRagStatus,
  stripKnowledgeGaps,
  type NoteRagIndexStatus,
  summarizeNoteByAi,
  transformNoteByAi,
  transformSelectionStream,
  type NoteSummaryResult,
  type NoteTransformMode,
  type SelectionTransformMode,
} from '../../api/ai';
import { listRelatedNotes, type Note } from '../../api/notes';
import { getAiUserSettings, updateAiUserSettings } from '../../api/settings';

const AUTO_SUMMARY_KEY = 'noto-auto-summary-on-save';

const tabs = [
  { key: 'quick', label: '常用' },
  { key: 'write', label: '写作' },
  { key: 'ask', label: '问答' },
  { key: 'related', label: '相关' },
] as const;

const templateActions: { mode: NoteTransformMode; label: string }[] = [
  { mode: 'structure', label: '会议纪要' },
  { mode: 'template_weekly', label: '周报' },
  { mode: 'template_retro', label: '复盘' },
  { mode: 'template_proposal', label: '方案' },
];

const selectionActions: { mode: SelectionTransformMode; label: string }[] = [
  { mode: 'concise', label: '简洁' },
  { mode: 'expand', label: '扩写' },
  { mode: 'formal', label: '正式' },
];

const props = defineProps<{
  noteId: string;
  title: string;
  content: string;
  summary?: string | null;
  workspaceId?: string | null;
  aiEnabled?: boolean;
  getSelection?: () => string;
}>();

const emit = defineEmits<{
  'apply-content': [content: string];
  'apply-summary': [summary: string];
  'extract-todos': [source: 'ai' | 'markdown'];
  'open-note': [noteId: string];
  'replace-selection': [payload: { original: string; replacement: string }];
  close: [];
}>();

const activeTab = ref<(typeof tabs)[number]['key']>('quick');
const loadingAction = ref<NoteTransformMode | 'summarize' | null>(null);
const selectionLoading = ref<SelectionTransformMode | null>(null);
const asking = ref(false);
const loadingRelated = ref(false);
const reindexing = ref(false);
const ragStatus = ref<NoteRagIndexStatus | null>(null);
const questionInput = ref('');
const relatedNotes = ref<Note[]>([]);
const autoSummaryOnSave = ref(localStorage.getItem(AUTO_SUMMARY_KEY) === 'true');

const preview = ref<{
  type: 'summary' | 'transform' | 'ask' | null;
  summary?: NoteSummaryResult;
  content?: string;
  answer?: string;
  transformMode?: string;
  selectionOriginal?: string;
}>({ type: null });

const transformLabel: Record<string, string> = {
  polish: '润色预览',
  bulletize: '条文化预览',
  structure: '结构化预览',
  template_weekly: '周报草稿预览',
  template_retro: '复盘草稿预览',
  template_proposal: '方案初稿预览',
  concise: '简洁版预览',
  expand: '扩写预览',
  formal: '正式语气预览',
};

const previewTitle = computed(() => {
  if (preview.value.type === 'summary') return '摘要预览';
  if (preview.value.type === 'transform' && preview.value.transformMode) {
    return transformLabel[preview.value.transformMode] || '改写预览';
  }
  if (preview.value.type === 'ask') return '回答';
  return '';
});

const draftPayload = () => ({
  title: props.title,
  content: props.content,
});

const persistAutoSummary = () => {
  localStorage.setItem(AUTO_SUMMARY_KEY, autoSummaryOnSave.value ? 'true' : 'false');
  void updateAiUserSettings({ autoSummaryOnSave: autoSummaryOnSave.value }).catch(() => {
    // 离线或未登录时仅保留本地
  });
};

const clearPreview = () => {
  preview.value = { type: null };
};

const loadRelated = async () => {
  if (!props.noteId) return;
  loadingRelated.value = true;
  try {
    relatedNotes.value = await listRelatedNotes(props.noteId);
  } catch {
    relatedNotes.value = [];
  } finally {
    loadingRelated.value = false;
  }
};

const runReindex = async () => {
  if (!props.noteId) return;
  reindexing.value = true;
  try {
    const result = await reindexNoteRag(props.noteId);
    message.success(`本篇已索引 ${result.indexedChunks} 个片段`);
    await loadRagStatus();
  } catch (error: any) {
    message.error(error?.message || '重建索引失败');
  } finally {
    reindexing.value = false;
  }
};

const loadRagStatus = async () => {
  if (!props.aiEnabled || !props.workspaceId) {
    ragStatus.value = null;
    return;
  }
  try {
    ragStatus.value = await getNoteRagStatus(props.workspaceId);
  } catch {
    ragStatus.value = null;
  }
};

const runSummarize = async () => {
  loadingAction.value = 'summarize';
  try {
    const result = await summarizeNoteByAi(props.noteId, draftPayload(), false);
    preview.value = { type: 'summary', summary: result };
  } catch (error: any) {
    message.error(error?.message || '生成摘要失败');
  } finally {
    loadingAction.value = null;
  }
};

const runTransform = async (mode: NoteTransformMode) => {
  loadingAction.value = mode;
  try {
    const result = await transformNoteByAi(props.noteId, { mode, ...draftPayload() });
    preview.value = { type: 'transform', content: result.content, transformMode: mode };
  } catch (error: any) {
    message.error(error?.message || '处理失败');
  } finally {
    loadingAction.value = null;
  }
};

const runSelectionTransform = async (mode: SelectionTransformMode) => {
  const selected = props.getSelection?.() || '';
  if (!selected) {
    message.warning('请先在正文中选中一段文字');
    return;
  }
  selectionLoading.value = mode;
  preview.value = { type: 'transform', content: '', transformMode: mode, selectionOriginal: selected };
  try {
    await transformSelectionStream(
      props.noteId,
      { mode, selectedText: selected, ...draftPayload() },
      {
        onToken: (token) => {
          preview.value = {
            ...preview.value,
            type: 'transform',
            content: `${preview.value.content || ''}${token}`,
          };
        },
        onError: (errorMessage) => {
          message.error(errorMessage || '改写失败');
        },
      },
    );
  } catch (error: any) {
    message.error(error?.message || '改写失败');
    clearPreview();
  } finally {
    selectionLoading.value = null;
  }
};

const applySummary = () => {
  const summary = preview.value.summary?.summary?.trim();
  if (!summary) {
    message.warning('暂无摘要内容');
    return;
  }
  emit('apply-summary', summary);
};

const applyTransform = () => {
  const content = preview.value.content?.trim();
  if (!content) {
    message.warning('暂无内容可应用');
    return;
  }
  emit('apply-content', content);
  message.success('已应用到正文');
  clearPreview();
};

const applySelectionReplace = () => {
  const replacement = preview.value.content?.trim();
  const original = preview.value.selectionOriginal?.trim();
  if (!replacement || !original) {
    message.warning('暂无内容可替换');
    return;
  }
  emit('replace-selection', { original, replacement });
  message.success('已替换选中段落');
  clearPreview();
};

const askCurrentNote = async () => {
  const question = questionInput.value.trim();
  if (!question) {
    message.warning('请输入问题');
    return;
  }
  if (!props.workspaceId) {
    message.warning('当前文档缺少知识库信息');
    return;
  }
  asking.value = true;
  preview.value = { type: 'ask', answer: '' };
  try {
    await askAiStream(
      {
        workspaceId: props.workspaceId,
        question,
        scope: 'note',
        targetId: props.noteId,
      },
      {
        onToken: (token) => {
          preview.value = {
            type: 'ask',
            answer: `${preview.value.answer || ''}${token}`,
          };
        },
        onError: (errorMessage) => {
          message.error(errorMessage || '问答失败');
        },
      },
    );
    if (preview.value.answer) {
      preview.value.answer = stripKnowledgeGaps(preview.value.answer);
    }
    if (!preview.value.answer?.trim()) {
      preview.value.answer = '（无回答内容）';
    }
  } catch (error: any) {
    preview.value = { type: null };
    message.error(error?.message || '问答失败');
  } finally {
    asking.value = false;
  }
};

watch(
  () => props.noteId,
  () => {
    clearPreview();
    questionInput.value = '';
    void loadRelated();
  },
  { immediate: true },
);

watch(
  () => [props.workspaceId, props.aiEnabled] as const,
  () => {
    void loadRagStatus();
  },
  { immediate: true },
);

onMounted(async () => {
  try {
    const settings = await getAiUserSettings();
    autoSummaryOnSave.value = settings.autoSummaryOnSave;
    localStorage.setItem(AUTO_SUMMARY_KEY, settings.autoSummaryOnSave ? 'true' : 'false');
  } catch {
    // 保持 localStorage 回退
  }
});

defineExpose({
  autoSummaryOnSave,
  runSummarizeQuiet: async () => {
    if (!props.aiEnabled || !autoSummaryOnSave.value) return null;
    if (props.summary?.trim()) return null;
    if (!props.content.trim()) return null;
    try {
      return await summarizeNoteByAi(props.noteId, draftPayload(), false);
    } catch {
      return null;
    }
  },
});
</script>

<style scoped>
.note-ai-panel {
  --ai-accent: #6366f1;
  --ai-accent-soft: rgba(99, 102, 241, 0.1);
  --ai-border: rgba(15, 23, 42, 0.06);
  --ai-text: #0f172a;
  --ai-muted: #94a3b8;
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: inherit;
  background: linear-gradient(180deg, #fafbff 0%, #f8fafc 48%, #f1f5f9 100%);
  font-size: 12px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
  padding: 10px 10px 8px;
}

.panel-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.brand-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, #818cf8 0%, #6366f1 100%);
  color: #fff;
  font-size: 13px;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.28);
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.brand-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ai-text);
  line-height: 1.2;
}

.brand-status {
  font-size: 10px;
  line-height: 1.2;
}

.brand-status.is-on {
  color: #10b981;
}

.brand-status.is-off {
  color: var(--ai-muted);
}

.icon-btn,
.text-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--ai-muted);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: color 0.15s, background 0.15s;
}

.icon-btn {
  width: 24px;
  height: 24px;
  font-size: 11px;
}

.icon-btn:hover,
.text-btn:hover {
  color: #64748b;
  background: rgba(15, 23, 42, 0.04);
}

.text-btn {
  font-size: 11px;
  padding: 2px 6px;
}

.disabled-banner {
  flex-shrink: 0;
  margin: 0 10px 6px;
  padding: 6px 8px;
  border-radius: 8px;
  background: rgba(245, 158, 11, 0.1);
  color: #b45309;
  font-size: 11px;
  line-height: 1.4;
}

.preview-card {
  flex-shrink: 0;
  margin: 0 10px 8px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid var(--ai-border);
  border-left: 3px solid var(--ai-accent);
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
  max-height: 200px;
  overflow: auto;
  backdrop-filter: blur(8px);
}

.preview-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.preview-badge {
  font-size: 10px;
  font-weight: 600;
  color: var(--ai-accent);
  letter-spacing: 0.02em;
}

.preview-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: #334155;
  white-space: pre-wrap;
}

.preview-list {
  margin: 6px 0 0;
  padding-left: 16px;
  font-size: 11px;
  color: #64748b;
}

.preview-markdown {
  margin: 0;
  max-height: 120px;
  overflow: auto;
  padding: 6px 8px;
  border-radius: 6px;
  background: #f8fafc;
  font-size: 11px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}

.primary-chip {
  margin-top: 8px;
  width: 100%;
  height: 28px;
  border: none;
  border-radius: 7px;
  background: linear-gradient(135deg, #818cf8 0%, #6366f1 100%);
  color: #fff;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s, transform 0.15s;
}

.primary-chip:hover {
  opacity: 0.92;
}

.seg-nav {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  margin: 0 10px 8px;
  padding: 3px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid var(--ai-border);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.seg-item {
  height: 24px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #64748b;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.18s, color 0.18s, box-shadow 0.18s;
}

.seg-item:hover {
  color: #334155;
}

.seg-item.active {
  background: #fff;
  color: var(--ai-accent);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.08);
}

.panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 10px 12px;
}

.tab-panel {
  animation: fade-in 0.18s ease;
}

@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(2px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.chip-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.chip-grid--2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.chip-row {
  display: flex;
  gap: 6px;
}

.chip-row .action-chip {
  flex: 1;
  min-width: 0;
}

.action-chip,
.ghost-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 30px;
  padding: 0 8px;
  border: 1px solid var(--ai-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.86);
  color: #334155;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, transform 0.12s, box-shadow 0.15s;
}

.rag-status-line {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--ai-muted);
  line-height: 1.45;
}

.rag-status-warn {
  color: #d97706;
}

.action-chip--flat {
  background: rgba(255, 255, 255, 0.65);
}

.action-chip--muted {
  color: #64748b;
}

.action-chip:hover:not(:disabled),
.ghost-chip:hover:not(:disabled) {
  border-color: rgba(99, 102, 241, 0.28);
  background: #fff;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.08);
}

.action-chip:active:not(:disabled) {
  transform: scale(0.98);
}

.action-chip:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.action-chip.loading,
.ghost-chip.loading,
.ask-send.loading {
  opacity: 0.65;
  pointer-events: none;
}

.section-caption {
  margin: 0 0 6px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ai-muted);
}

.section-caption + .chip-grid,
.section-caption + .chip-row {
  margin-bottom: 12px;
}

.mini-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 6px 8px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.55);
  color: #64748b;
  font-size: 11px;
  cursor: pointer;
}

.ask-box {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  padding: 6px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid var(--ai-border);
}

.ask-input {
  flex: 1;
  min-width: 0;
  font-size: 12px !important;
}

.ask-input :deep(textarea) {
  padding: 4px 0 !important;
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  resize: none;
}

.ask-send {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #818cf8 0%, #6366f1 100%);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.15s, transform 0.12s;
}

.ask-send:hover:not(:disabled) {
  opacity: 0.92;
}

.ask-send:active:not(:disabled) {
  transform: scale(0.96);
}

.ask-send:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ghost-chip {
  width: 100%;
  margin-top: 8px;
  min-height: 28px;
  background: transparent;
  color: #64748b;
}

.related-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.related-list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 7px 8px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid transparent;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.related-list li:hover {
  border-color: rgba(99, 102, 241, 0.2);
  background: #fff;
}

.related-icon {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 11px;
  color: #cbd5e1;
}

.related-body {
  min-width: 0;
  flex: 1;
}

.related-title {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--ai-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-meta {
  display: block;
  margin-top: 1px;
  font-size: 10px;
  color: var(--ai-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--ai-muted);
  line-height: 1.5;
  text-align: center;
}
</style>
