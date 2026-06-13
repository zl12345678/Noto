import { defineStore } from 'pinia';
import { ref } from 'vue';
import { listWorkspaces, type Workspace } from '../api/workspaces';

export const useWorkspaceStore = defineStore('workspace', () => {
  const items = ref<Workspace[]>([]);
  let loadPromise: Promise<Workspace[]> | null = null;

  async function ensureLoaded(force = false) {
    if (!force && items.value.length > 0) {
      return items.value;
    }
    if (!force && loadPromise) {
      return loadPromise;
    }
    loadPromise = listWorkspaces()
      .then((data) => {
        items.value = data;
        return data;
      })
      .finally(() => {
        loadPromise = null;
      });
    return loadPromise;
  }

  async function refresh() {
    return ensureLoaded(true);
  }

  async function reset() {
    items.value = [];
    loadPromise = null;
  }

  return {
    items,
    ensureLoaded,
    refresh,
    reset,
  };
});
