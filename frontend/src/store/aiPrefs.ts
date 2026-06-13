import { defineStore } from 'pinia';
import { ref } from 'vue';
import { getAiStatus } from '../api/ai';
import { getAiUserSettings } from '../api/settings';

export const useAiPrefsStore = defineStore('aiPrefs', () => {
  const enabled = ref(false);
  const autoExtractTodosOnSave = ref(false);
  const primaryWorkspaceId = ref<string | undefined>();
  const initialized = ref(false);
  let loadPromise: Promise<void> | null = null;

  async function ensureLoaded(force = false) {
    if (!force && initialized.value) {
      return;
    }
    if (!force && loadPromise) {
      return loadPromise;
    }
    loadPromise = (async () => {
      const [statusResult, settingsResult] = await Promise.allSettled([
        getAiStatus(),
        getAiUserSettings(),
      ]);
      if (statusResult.status === 'fulfilled') {
        enabled.value = Boolean(statusResult.value?.enabled);
      } else {
        enabled.value = false;
      }
      if (settingsResult.status === 'fulfilled') {
        const settings = settingsResult.value;
        autoExtractTodosOnSave.value = settings.autoExtractTodosOnSave ?? false;
        primaryWorkspaceId.value = settings.primaryWorkspaceId || undefined;
      }
      initialized.value = true;
    })().finally(() => {
      loadPromise = null;
    });
    return loadPromise;
  }

  async function reset() {
    enabled.value = false;
    autoExtractTodosOnSave.value = false;
    primaryWorkspaceId.value = undefined;
    initialized.value = false;
    loadPromise = null;
  }

  return {
    enabled,
    autoExtractTodosOnSave,
    primaryWorkspaceId,
    initialized,
    ensureLoaded,
    reset,
  };
});
