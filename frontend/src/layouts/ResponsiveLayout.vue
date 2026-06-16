<template>
  <MobileLayout v-if="isMobile" />
  <AppLayout v-else />
</template>

<script setup lang="ts">
import { useBreakpoint } from '../composables/useBreakpoint';
import AppLayout from './AppLayout.vue';
import MobileLayout from './MobileLayout.vue';

const { isMobile } = useBreakpoint();
</script>