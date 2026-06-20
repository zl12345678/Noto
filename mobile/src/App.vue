<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app';
import { isAuthenticated } from './stores/auth';
import { redirectToLogin } from './utils/navigation';

onLaunch(() => {
  if (!isAuthenticated()) {
    redirectToLogin();
  }
});
</script>

<style lang="scss">
@import './uni.scss';

page {
  background: $noto-bg;
  color: $noto-text;
  font-size: 28rpx;
}

.page-safe {
  min-height: 100vh;
  padding: 24rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.card {
  background: $noto-card;
  border-radius: 20rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(28, 25, 23, 0.04);
}

.list-row {
  min-height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f5f5f4;
}

.list-row:last-child {
  border-bottom: none;
}

.segment {
  display: flex;
  gap: 12rpx;
  margin-bottom: 24rpx;
  padding: 8rpx;
  background: #eceae6;
  border-radius: 16rpx;
}

.segment-item {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  border-radius: 12rpx;
  font-size: 26rpx;
  color: $noto-muted;
}

.segment-item.active {
  background: #fff;
  color: $noto-primary;
  font-weight: 600;
}

.banner-warn {
  background: #fff7ed;
  border: 1rpx solid #fed7aa;
  color: #c2410c;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 20rpx;
  font-size: 26rpx;
}

.fixed-bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(247, 246, 243, 0.96);
  border-top: 1rpx solid $noto-border;
  display: flex;
  gap: 16rpx;
  z-index: 10;
}

.fixed-bottom .touch-btn {
  flex: 1;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}

.muted {
  color: $noto-muted;
  font-size: 24rpx;
}

.btn-primary {
  background: $noto-primary;
  color: #fff;
  border-radius: 16rpx;
  font-size: 30rpx;
}

.empty {
  text-align: center;
  padding: 80rpx 40rpx;
  color: $noto-muted;
}
</style>
