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

    <p v-if="markdownText" class="message-text">{{ markdownText }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
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
  /** @deprecated 聊天区统一纯文本渲染，避免 MdPreview 切换导致 patch 错误 */
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

.intro-text,
.message-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.7;
  color: #101828;
}

.result-blocks {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
