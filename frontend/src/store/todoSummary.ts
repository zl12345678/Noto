import { defineStore } from 'pinia';
import { ref } from 'vue';
import { getDashboardStats } from '../api/dashboard';

export const useTodoSummaryStore = defineStore('todoSummary', () => {
  const pendingCount = ref(0);
  const overdueCount = ref(0);

  async function refresh() {
    try {
      const stats = await getDashboardStats();
      pendingCount.value = stats.pendingTodos ?? 0;
      overdueCount.value = stats.overdueTodos ?? 0;
    } catch {
      pendingCount.value = 0;
      overdueCount.value = 0;
    }
  }

  function applyStats(pending: number, overdue: number) {
    pendingCount.value = pending;
    overdueCount.value = overdue;
  }

  return {
    pendingCount,
    overdueCount,
    refresh,
    applyStats,
  };
});
