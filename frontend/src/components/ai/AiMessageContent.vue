<template>
  <div class="ai-message-content">
    <p v-if="introText" class="intro-text">{{ introText }}</p>

    <div v-if="resultBlocks.length" class="result-blocks">
      <AiResultList
        v-for="block in resultBlocks"
        :key="block.id"
        :title="block.title"
        :kind="block.kind"
        :count="block.count"
        :items="block.items"
        :groups="block.groups"
        :workspace-id="workspaceId"
      />
    </div>

    <div v-if="markdownText" class="markdown-body">
      <pre v-if="streaming" class="stream-text">{{ markdownText }}</pre>
      <SafeMdPreview v-else :model-value="markdownText" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import SafeMdPreview from '../common/SafeMdPreview.vue';
import type { AiAgentStep } from '../../api/aiAgent';
import AiResultList from './AiResultList.vue';
import {
  buildAgentResultBlocks,
  extractIntroText,
  normalizeAiMarkdown,
} from '../../utils/aiMessageFormat';

const props = defineProps<{
  content: string;
  agentSteps?: AiAgentStep[];
  workspaceId?: string;
  streaming?: boolean;
}>();

const resultBlocks = computed(() => buildAgentResultBlocks(props.agentSteps));

const introText = computed(() =>
  extractIntroText(props.content, resultBlocks.value.length > 0),
);

const markdownText = computed(() => {
  if (resultBlocks.value.length > 0) return '';
  return normalizeAiMarkdown(props.content);
});
</script>

<style scoped>
.ai-message-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.intro-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #101828;
  white-space: pre-wrap;
}

.result-blocks {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stream-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.7;
  color: #101828;
}

.markdown-body :deep(.md-editor-preview) {
  padding: 0;
  background: transparent;
  color: #101828;
  font-size: 14px;
  line-height: 1.7;
}

.markdown-body :deep(.md-editor-preview-wrapper) {
  padding: 0;
}

.markdown-body :deep(.md-editor) {
  border: none;
  background: transparent;
  box-shadow: none;
}

.markdown-body :deep(.md-editor-preview h4) {
  margin: 12px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: #0e7490;
}

.markdown-body :deep(.md-editor-preview h4:first-child) {
  margin-top: 0;
}

.markdown-body :deep(.md-editor-preview p) {
  margin: 0 0 8px;
}

.markdown-body :deep(.md-editor-preview ul) {
  margin: 4px 0 8px;
  padding-left: 1.2em;
}

.markdown-body :deep(.md-editor-preview li) {
  margin: 4px 0;
}

.markdown-body :deep(.md-editor-preview strong) {
  color: #0f172a;
  font-weight: 600;
}
</style>
