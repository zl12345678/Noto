import { defineStore } from 'pinia';
import { computed, ref, watch } from 'vue';

export type ThemeMode = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

const STORAGE_KEY = 'noto-theme-mode';

function readStoredMode(): ThemeMode {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (raw === 'light' || raw === 'dark' || raw === 'system') return raw;
  return 'system';
}

function resolveTheme(mode: ThemeMode): ResolvedTheme {
  if (mode === 'light') return 'light';
  if (mode === 'dark') return 'dark';
  if (typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    return 'dark';
  }
  return 'light';
}

function applyDocumentTheme(resolved: ResolvedTheme) {
  document.documentElement.setAttribute('data-theme', resolved);
  document.documentElement.style.colorScheme = resolved;
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(readStoredMode());
  const resolved = ref<ResolvedTheme>(resolveTheme(mode.value));

  let media: MediaQueryList | null = null;

  function syncResolved() {
    resolved.value = resolveTheme(mode.value);
    applyDocumentTheme(resolved.value);
  }

  function setMode(next: ThemeMode) {
    mode.value = next;
    localStorage.setItem(STORAGE_KEY, next);
    syncResolved();
  }

  function cycleMode() {
    const order: ThemeMode[] = ['light', 'dark', 'system'];
    const index = order.indexOf(mode.value);
    setMode(order[(index + 1) % order.length] ?? 'system');
  }

  const modeLabel = computed(() => {
    if (mode.value === 'light') return '浅色';
    if (mode.value === 'dark') return '深色';
    return '跟随系统';
  });

  watch(mode, syncResolved);

  function init() {
    syncResolved();
    if (typeof window === 'undefined') return;
    media = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => {
      if (mode.value === 'system') syncResolved();
    };
    media.addEventListener('change', onChange);
    return () => media?.removeEventListener('change', onChange);
  }

  return {
    mode,
    resolved,
    modeLabel,
    setMode,
    cycleMode,
    init,
  };
});
