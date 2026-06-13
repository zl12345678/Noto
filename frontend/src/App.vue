<template>
  <a-config-provider :theme="notoTheme">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { notoTheme } from './theme/notoTheme';
</script>
