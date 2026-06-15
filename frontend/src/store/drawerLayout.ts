import { defineStore } from 'pinia';
import { ref, watch } from 'vue';

const AI_MIN = 280;
const AI_MAX = 520;
const AI_DEFAULT = 360;
const STORAGE_KEY = 'noto-drawer-layout';

type StoredDrawerLayout = {
  aiWidth?: number;
};

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function readStored(): StoredDrawerLayout | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as StoredDrawerLayout;
  } catch {
    return null;
  }
}

const stored = readStored();

export const useDrawerLayoutStore = defineStore('drawerLayout', () => {
  const aiWidth = ref(clamp(stored?.aiWidth ?? AI_DEFAULT, AI_MIN, AI_MAX));

  let persistTimer: ReturnType<typeof setTimeout> | undefined;

  watch(aiWidth, (width) => {
    if (persistTimer) clearTimeout(persistTimer);
    persistTimer = setTimeout(() => {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ aiWidth: width }));
    }, 200);
  });

  function resizeAi(delta: number) {
    aiWidth.value = clamp(aiWidth.value - delta, AI_MIN, AI_MAX);
  }

  return {
    aiWidth,
    resizeAi,
  };
});
