<template>
  <button
    class="touch-btn"
    :class="[variant, { block, disabled }]"
    :loading="loading"
    :disabled="disabled"
    @click="$emit('click')"
  >
    <slot />
  </button>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'ghost' | 'danger';
    block?: boolean;
    loading?: boolean;
    disabled?: boolean;
  }>(),
  { variant: 'ghost', block: false, loading: false, disabled: false },
);

defineEmits<{ click: [] }>();
</script>

<style scoped lang="scss">
.touch-btn {
  min-height: 80rpx;
  line-height: 80rpx;
  padding: 0 28rpx;
  margin: 0;
  font-size: 28rpx;
  border-radius: 16rpx;
  border: 1rpx solid #e7e5e4;
  background: #fff;
  color: #1c1917;
}

.touch-btn.block {
  width: 100%;
}

.touch-btn.primary {
  background: #0891b2;
  color: #fff;
  border-color: #0891b2;
}

.touch-btn.danger {
  color: #b91c1c;
  border-color: #fecaca;
  background: #fff;
}

.touch-btn.disabled {
  opacity: 0.5;
}
</style>
