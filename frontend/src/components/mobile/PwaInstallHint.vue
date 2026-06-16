<template>
  <div v-if="visible" class="pwa-install-hint">
    <span class="pwa-install-text">{{ hintText }}</span>
    <a-button v-if="canPromptInstall" type="primary" size="small" @click="onInstall">安装</a-button>
    <a-button type="text" size="small" @click="dismiss">稍后</a-button>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';

const DISMISS_KEY = 'noto-pwa-install-dismissed';
const DISMISS_TTL_MS = 7 * 24 * 60 * 60 * 1000;

const visible = ref(false);
const canPromptInstall = ref(false);
let deferredPrompt: BeforeInstallPromptEvent | null = null;

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

function isStandalone() {
  if (typeof window === 'undefined') return false;
  return (
    window.matchMedia('(display-mode: standalone)').matches
    || (window.navigator as Navigator & { standalone?: boolean }).standalone === true
  );
}

function isIosSafari() {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent;
  const isIos = /iPad|iPhone|iPod/.test(ua);
  const isSafari = /Safari/.test(ua) && !/CriOS|FxiOS|EdgiOS/.test(ua);
  return isIos && isSafari;
}

function isDismissedRecently() {
  const raw = localStorage.getItem(DISMISS_KEY);
  if (!raw || raw === 'installed') return raw === 'installed';
  const ts = Number(raw);
  if (!Number.isFinite(ts)) return true;
  return Date.now() - ts < DISMISS_TTL_MS;
}

const hintText = computed(() => {
  if (canPromptInstall.value) return '安装到主屏幕，随时看待办与提醒';
  if (isIosSafari()) return 'Safari：分享 →「添加到主屏幕」可安装 PWA';
  return '安装到主屏幕，随时看待办与提醒';
});

function dismiss() {
  visible.value = false;
  localStorage.setItem(DISMISS_KEY, String(Date.now()));
}

async function onInstall() {
  if (!deferredPrompt) return;
  await deferredPrompt.prompt();
  const { outcome } = await deferredPrompt.userChoice;
  deferredPrompt = null;
  visible.value = false;
  if (outcome === 'accepted') {
    localStorage.setItem(DISMISS_KEY, 'installed');
  }
}

function onBeforeInstall(e: Event) {
  e.preventDefault();
  deferredPrompt = e as BeforeInstallPromptEvent;
  canPromptInstall.value = true;
  if (isDismissedRecently()) return;
  visible.value = true;
}

function maybeShowIosHint() {
  if (isStandalone() || isDismissedRecently()) return;
  if (isIosSafari()) {
    canPromptInstall.value = false;
    visible.value = true;
  }
}

onMounted(() => {
  if (isStandalone()) return;
  window.addEventListener('beforeinstallprompt', onBeforeInstall);
  maybeShowIosHint();
});

onUnmounted(() => {
  window.removeEventListener('beforeinstallprompt', onBeforeInstall);
});
</script>

<style scoped>
.pwa-install-hint {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--noto-pastel-blue, #e0f2fe);
  border: 1px solid var(--noto-border);
  font-size: 13px;
}

.pwa-install-text {
  flex: 1;
  min-width: 140px;
  color: var(--noto-text);
  line-height: 1.45;
}
</style>