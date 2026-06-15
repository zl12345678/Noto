import { ref, watch, type Ref } from 'vue';
import {
  filterHeadingsByKeyword,
  filterHeadingsByMaxLevel,
  parseMarkdownHeadings,
  type MarkdownHeadingItem,
} from '../utils/markdownOutline';

export function useDebouncedOutline(
  content: Ref<string>,
  maxLevel: Ref<number>,
  searchKeyword: Ref<string>,
  debounceMs = 280,
) {
  const allHeadings = ref<MarkdownHeadingItem[]>([]);
  const filteredHeadings = ref<MarkdownHeadingItem[]>([]);
  let timer: ReturnType<typeof setTimeout> | null = null;

  const recompute = () => {
    const parsed = parseMarkdownHeadings(content.value);
    allHeadings.value = parsed;
    const byLevel = filterHeadingsByMaxLevel(parsed, maxLevel.value);
    filteredHeadings.value = filterHeadingsByKeyword(byLevel, searchKeyword.value);
  };

  const schedule = () => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      timer = null;
      recompute();
    }, debounceMs);
  };

  watch([content, maxLevel, searchKeyword], schedule, { immediate: true });

  return { allHeadings, filteredHeadings, recompute };
}