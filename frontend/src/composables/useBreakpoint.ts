import { onMounted, onUnmounted, ref, computed } from 'vue';

/** 与 docs/MULTI_PLATFORM.md 一致：< 768px 为移动布局 */
export const MOBILE_MAX_WIDTH = 767;

export function useBreakpoint() {
  const width = ref(typeof window !== 'undefined' ? window.innerWidth : 1024);

  const update = () => {
    width.value = window.innerWidth;
  };

  onMounted(() => {
    update();
    window.addEventListener('resize', update, { passive: true });
  });

  onUnmounted(() => {
    window.removeEventListener('resize', update);
  });

  const isMobile = computed(() => width.value <= MOBILE_MAX_WIDTH);
  const isDesktop = computed(() => width.value > MOBILE_MAX_WIDTH);

  return { width, isMobile, isDesktop };
}