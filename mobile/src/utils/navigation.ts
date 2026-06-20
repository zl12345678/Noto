const LOGIN_PATH = '/pages/login/login';
const AUTH_PAGE_ROUTES = new Set(['pages/login/login', 'pages/register/register']);

let loginRedirecting = false;

export function getCurrentRoute(): string {
  const pages = getCurrentPages();
  return pages.length ? pages[pages.length - 1].route || '' : '';
}

export function isOnAuthPage(): boolean {
  return AUTH_PAGE_ROUTES.has(getCurrentRoute());
}

export function redirectToLogin() {
  if (loginRedirecting || isOnAuthPage()) {
    return;
  }
  loginRedirecting = true;
  uni.reLaunch({
    url: LOGIN_PATH,
    complete: () => {
      setTimeout(() => {
        loginRedirecting = false;
      }, 300);
    },
  });
}
