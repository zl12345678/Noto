<template>
  <Draggable
    ref="treeRef"
    :model-value="modelValue"
    class="note-doc-tree"
    text-key="title"
    :indent="16"
    :default-open="false"
    :disable-drag="!dragEnabled"
    :each-draggable="eachDraggable"
    :each-droppable="eachDroppable"
    :root-droppable="true"
    update-behavior="new"
    :stat-handler="handleStat"
    :drag-open-delay="300"
    @update:model-value="emit('update:modelValue', $event)"
    @change="onTreeChange"
    @open:node="onOpenNode"
    @close:node="onCloseNode"
  >
    <template #default="{ node, stat }">
      <div
        class="tree-node-row"
        :class="{
          'is-selected': selectedKeys.includes(node.key),
          'is-folder': node.nodeType === 'folder',
          'is-note': node.nodeType === 'note',
        }"
        @click="emit('select', node.key)"
        @contextmenu.prevent="emit('contextmenu', $event, node)"
      >
        <OpenIcon
          v-if="stat.children.length"
          :open="stat.open"
          class="tree-open-icon"
          @click.stop="stat.open = !stat.open"
        />
        <span v-else class="tree-open-spacer" />
        <span
          class="tree-node-label"
          :class="{
            'is-note': node.nodeType === 'note',
            'is-folder': node.nodeType === 'folder',
          }"
        >
          {{ node.title }}
        </span>
      </div>
    </template>
  </Draggable>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { Draggable, OpenIcon, dragContext } from '@he-tree/vue';
import type { Stat } from '@he-tree/tree-utils';
import type { HeTreeDropTargetInfo } from '../../utils/noteTreeDrag';
import '@he-tree/vue/style/default.css';

export type NoteTreeNode = {
  key: string;
  title: string;
  nodeType: 'folder' | 'note' | 'root';
  rawId?: string;
  sortOrder?: number;
  children?: NoteTreeNode[];
  isLeaf?: boolean;
};

const props = defineProps<{
  modelValue: NoteTreeNode[];
  selectedKeys: string[];
  expandedKeys: string[];
  dragEnabled: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: NoteTreeNode[]];
  'update:expandedKeys': [keys: string[]];
  select: [key: string];
  'tree-change': [payload: { dragKey: string; targetInfo: HeTreeDropTargetInfo | null; treeData: NoteTreeNode[] }];
  contextmenu: [event: MouseEvent, node: NoteTreeNode];
}>();

const treeRef = ref<InstanceType<typeof Draggable> | null>(null);

const handleStat = (stat: Stat<NoteTreeNode>) => {
  stat.open = props.expandedKeys.includes(stat.data.key);
  return stat;
};

const eachDraggable = (stat: Stat<NoteTreeNode>) => {
  if (!props.dragEnabled) return false;
  return stat.data.nodeType === 'folder' || stat.data.nodeType === 'note';
};

const eachDroppable = (stat: Stat<NoteTreeNode>) => {
  if (!props.dragEnabled) return false;
  return true;
};

const syncExpandedToStats = () => {
  const tree = treeRef.value as { statsFlat?: Stat<NoteTreeNode>[] } | null;
  tree?.statsFlat?.forEach((stat) => {
    stat.open = props.expandedKeys.includes(stat.data.key);
  });
};

watch(() => props.expandedKeys, syncExpandedToStats, { deep: true });
watch(() => props.modelValue, () => {
  syncExpandedToStats();
});

const onOpenNode = (stat: Stat<NoteTreeNode>) => {
  const key = stat.data.key;
  if (!props.expandedKeys.includes(key)) {
    emit('update:expandedKeys', [...props.expandedKeys, key]);
  }
};

const onCloseNode = (stat: Stat<NoteTreeNode>) => {
  emit('update:expandedKeys', props.expandedKeys.filter((key) => key !== stat.data.key));
};

const readTreeData = (): NoteTreeNode[] => {
  const tree = treeRef.value as { getData?: () => NoteTreeNode[] } | null;
  return tree?.getData?.() ?? props.modelValue;
};

const onTreeChange = () => {
  const dragKey = dragContext.dragNode?.data?.key;
  if (!dragKey) return;
  emit('tree-change', {
    dragKey,
    targetInfo: dragContext.targetInfo as HeTreeDropTargetInfo | null,
    treeData: readTreeData(),
  });
};

defineExpose({ treeRef, readTreeData });
</script>

<style scoped>
.note-doc-tree {
  --he-tree-drag-placeholder-bg: rgba(15, 23, 42, 0.06);
  --he-tree-drag-placeholder-border: rgba(15, 23, 42, 0.2);
}

.note-doc-tree :deep(.tree-node) {
  padding: 1px 0;
}

.note-doc-tree :deep(.drag-placeholder) {
  border-radius: 8px;
  border: 1px dashed rgba(15, 23, 42, 0.28);
  background: rgba(15, 23, 42, 0.04);
}

.tree-node-row {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 28px;
  padding: 0 4px;
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
}

.tree-node-row.is-selected {
  background: rgba(15, 23, 42, 0.06);
}

.tree-node-row:hover {
  background: rgba(15, 23, 42, 0.04);
}

.tree-node-row.is-selected:hover {
  background: rgba(15, 23, 42, 0.08);
}

.tree-open-icon,
.tree-open-spacer {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
}

.tree-open-icon {
  color: #98a2b3;
  cursor: pointer;
}

.tree-node-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 22px;
}

.tree-node-label.is-folder {
  font-weight: 600;
  color: #344054;
}

.tree-node-label.is-note {
  color: #475467;
}

</style>
