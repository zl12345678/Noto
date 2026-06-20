import type { ShareCreateRequest } from '../api/share';

export function pickShareOptions(): Promise<ShareCreateRequest | null> {
  return new Promise((resolve) => {
    uni.showActionSheet({
      itemList: ['永久有效', '7 天有效', '30 天有效', '设置访问密码'],
      success: (res) => {
        if (res.tapIndex === 0) resolve({});
        if (res.tapIndex === 1) resolve({ expiresInDays: 7 });
        if (res.tapIndex === 2) resolve({ expiresInDays: 30 });
        if (res.tapIndex === 3) {
          uni.showModal({
            title: '访问密码',
            editable: true,
            placeholderText: '请输入分享访问密码',
            success: (modalRes) => {
              if (!modalRes.confirm) {
                resolve(null);
                return;
              }
              const password = modalRes.content?.trim();
              if (!password) {
                uni.showToast({ title: '密码不能为空', icon: 'none' });
                resolve(null);
                return;
              }
              resolve({ password });
            },
          });
        }
      },
      fail: () => resolve(null),
    });
  });
}
