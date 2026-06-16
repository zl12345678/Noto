<template>
  <a-config-provider :theme="activeTheme">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { buildNotoTheme } from './theme/notoTheme';
import { useThemeStore } from './store/theme';
import { useBreakpoint } from './composables/useBreakpoint';

const themeStore = useThemeStore();
const { resolved } = storeToRefs(themeStore);
const { isMobile } = useBreakpoint();

const activeTheme = computed(() => buildNotoTheme(resolved.value));

let disposeThemeListener: (() => void) | undefined;

watch(
  isMobile,
  (mobile) => {
    document.documentElement.classList.toggle('mobile-active', mobile);
  },
  { immediate: true },
);

onMounted(() => {
  disposeThemeListener = themeStore.init();
});

onUnmounted(() => {
  document.documentElement.classList.remove('mobile-active');
  disposeThemeListener?.();
});
</script>
