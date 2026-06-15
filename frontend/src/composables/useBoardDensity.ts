import { computed, ref, watch } from 'vue';

export type BoardDensity = 'comfortable' | 'compact';

const STORAGE_KEY = 'noto-board-density';

function readDensity(): BoardDensity {
  const raw = localStorage.getItem(STORAGE_KEY);
  return raw === 'compact' ? 'compact' : 'comfortable';
}

const density = ref<BoardDensity>(readDensity());

watch(density, (value) => {
  localStorage.setItem(STORAGE_KEY, value);
});

export function useBoardDensity() {
  const isCompact = computed(() => density.value === 'compact');

  function toggleDensity() {
    density.value = density.value === 'compact' ? 'comfortable' : 'compact';
  }

  function setDensity(value: BoardDensity) {
    density.value = value;
  }

  return {
    density,
    isCompact,
    toggleDensity,
    setDensity,
    densityClass: computed(() => (isCompact.value ? 'board-density--compact' : '')),
  };
}
