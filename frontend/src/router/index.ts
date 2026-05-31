import { createRouter, createWebHistory } from 'vue-router';
import { message } from 'ant-design-vue';
import { useAuthStore } from '../store/auth';
import LoginView from '../views/auth/LoginView.vue';
import RegisterView from '../views/auth/RegisterView.vue';
import ForgotPasswordView from '../views/auth/ForgotPasswordView.vue';
import AppLayout from '../layouts/AppLayout.vue';
import DashboardView from '../views/dashboard/DashboardView.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: { public: true },
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: ForgotPasswordView,
      meta: { public: true },
    },
    {
      path: '/',
      component: AppLayout,
      children: [
        {
          path: '',
          name: 'dashboard',
          component: DashboardView,
        },
      ],
    },
  ],
});

let authInitialized = false;

router.beforeEach(async (to) => {
  const authStore = useAuthStore();
  if (!authInitialized) {
    authStore.restore();
    if (authStore.isAuthenticated && !authStore.currentUser) {
      try {
        await authStore.fetchCurrentUser();
      } catch {
        authStore.logout();
      }
    }
    authInitialized = true;
  }

  if (to.meta.public) {
    if (authStore.isAuthenticated) {
      return '/';
    }
    return true;
  }

  if (!authStore.isAuthenticated) {
    message.warning('请先登录');
    return '/login';
  }

  return true;
});

export default router;
