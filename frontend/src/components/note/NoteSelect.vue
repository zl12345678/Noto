<template>
  <a-select
    :value="normalizedValue"
    allow-clear
    show-search
    :placeholder="placeholderText"
    :disabled="disabled || !workspaceId"
    :loading="loading"
    :filter-option="false"
    :options="options"
    @update:value="onUpdate"
    @search="onSearch"
    @dropdown-visible-change="onDropdownVisibleChange"
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { getNote, listNotes } from '../../api/notes';

const props = withDefaults(
  defineProps<{
    workspaceId?: string;
    disabled?: boolean;
    placeholder?: string;
  }>(),
  {
    workspaceId: undefined,
    disabled: false,
    placeholder: '搜索并选择文档（可选）',
  },
);

const model = defineModel<string | undefined>({ default: undefined });

const loading = ref(false);
const options = ref<{ label: string; value: string }[]>([]);
let searchTimer: ReturnType<typeof setTimeout> | null = null;

const normalizedValue = computed(() => model.value || undefined);

const placeholderText = computed(() => {
  if (!props.workspaceId) return '请先选择知识库';
  return props.placeholder;
});

const onUpdate = (value: string | undefined) => {
  model.value = value || undefined;
};

const ensureSelectedInOptions = async () => {
  const id = model.value;
  if (!id || options.value.some((item) => item.value === id)) return;
  try {
    const note = await getNote(id);
    if (!props.workspaceId || note.workspaceId === props.workspaceId) {
      options.value = [{ label: note.title || '未命名文档', value: note.id }, ...options.value];
    }
  } catch {
    // 文档可能已删除，忽略
  }
};

const loadNotes = async (keyword = '') => {
  if (!props.workspaceId) {
    options.value = [];
    return;
  }
  loading.value = true;
  try {
    const page = await listNotes({
      page: 1,
      size: 30,
      workspaceId: props.workspaceId,
      keyword: keyword.trim() || undefined,
    });
    options.value = (page.records || []).map((item) => ({
      label: item.title || '未命名文档',
      value: item.id,
    }));
    await ensureSelectedInOptions();
  } finally {
    loading.value = false;
  }
};

const onSearch = (keyword: string) => {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    void loadNotes(keyword);
  }, 300);
};

const onDropdownVisibleChange = (open: boolean) => {
  if (open && props.workspaceId) {
    void loadNotes();
  }
};

const syncNoteWithWorkspace = async () => {
  if (!model.value || !props.workspaceId) return;
  try {
    const note = await getNote(model.value);
    if (note.workspaceId !== props.workspaceId) {
      model.value = undefined;
    }
  } catch {
    model.value = undefined;
  }
};

watch(
  () => props.workspaceId,
  async (next, prev) => {
    if (next !== prev && prev !== undefined) {
      await syncNoteWithWorkspace();
    }
    if (next) {
      await loadNotes();
    } else {
      options.value = [];
    }
  },
);

watch(
  () => model.value,
  () => {
    void ensureSelectedInOptions();
  },
  { immediate: true },
);
</script>
