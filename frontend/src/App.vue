<template>
  <a-config-provider :theme="activeTheme">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue';
import { storeToRefs } from 'pinia';
import { buildNotoTheme } from './theme/notoTheme';
import { useThemeStore } from './store/theme';

const themeStore = useThemeStore();
const { resolved } = storeToRefs(themeStore);

const activeTheme = computed(() => buildNotoTheme(resolved.value));

let disposeThemeListener: (() => void) | undefined;

onMounted(() => {
  disposeThemeListener = themeStore.init();
});

onUnmounted(() => {
  disposeThemeListener?.();
});
</script>
