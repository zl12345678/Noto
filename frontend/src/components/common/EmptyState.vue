<template>
  <div class="noto-empty" :class="{ 'noto-empty--compact': compact }">
    <component :is="resolvedIcon" v-if="resolvedIcon" class="noto-empty-icon" />
    <h3 v-if="title" class="noto-empty-title">{{ title }}</h3>
    <p v-if="description" class="noto-empty-desc">{{ description }}</p>
    <div v-if="$slots.default" class="noto-empty-actions">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue';
import {
  FileSearchOutlined,
  FileTextOutlined,
  InboxOutlined,
  StarOutlined,
} from '@ant-design/icons-vue';

const props = withDefaults(
  defineProps<{
    title?: string;
    description?: string;
    /** 预设图标：default | note | search | todo | favorite | archive */
    preset?: 'default' | 'note' | 'search' | 'todo' | 'favorite' | 'archive';
    compact?: boolean;
  }>(),
  {
    preset: 'default',
    compact: false,
  },
);

const presetIcons: Record<string, Component> = {
  default: InboxOutlined,
  note: FileTextOutlined,
  search: FileSearchOutlined,
  todo: InboxOutlined,
  favorite: StarOutlined,
  archive: InboxOutlined,
};

const resolvedIcon = computed(() => presetIcons[props.preset] || presetIcons.default);
</script>

<style scoped>
.noto-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  text-align: center;
}

.noto-empty--compact {
  padding: 16px 12px;
}

.noto-empty--compact .noto-empty-icon {
  font-size: 28px;
}

.noto-empty--compact .noto-empty-title {
  font-size: 14px;
}

.noto-empty-icon {
  margin-bottom: 12px;
  color: rgba(0, 0, 0, 0.25);
  font-size: 40px;
}

.noto-empty-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}

.noto-empty-desc {
  margin: 0 0 16px;
  max-width: 320px;
  font-size: 13px;
  line-height: 1.5;
  color: rgba(0, 0, 0, 0.45);
}

.noto-empty--compact .noto-empty-desc {
  margin-bottom: 8px;
}

.noto-empty-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
</style>
