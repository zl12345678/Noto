import { createRouter, createWebHistory } from 'vue-router';
import { message } from 'ant-design-vue';
import { useAuthStore } from '../store/auth';
import AppLayout from '../layouts/AppLayout.vue';

const LoginView = () => import('../views/auth/LoginView.vue');
const RegisterView = () => import('../views/auth/RegisterView.vue');
const ForgotPasswordView = () => import('../views/auth/ForgotPasswordView.vue');
const DashboardView = () => import('../views/dashboard/DashboardView.vue');
const ProfileView = () => import('../views/profile/ProfileView.vue');
const NotesView = () => import('../views/notes/NotesView.vue');
const SearchView = () => import('../views/search/SearchView.vue');
const TodosView = () => import('../views/todos/TodosView.vue');
const RemindersView = () => import('../views/reminders/RemindersView.vue');
const AiView = () => import('../views/ai/AiView.vue');
const DriveView = () => import('../views/drive/DriveView.vue');
const MySharesView = () => import('../views/share/MySharesView.vue');
const ShareView = () => import('../views/share/ShareView.vue');

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true, guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: { public: true, guestOnly: true },
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: ForgotPasswordView,
      meta: { public: true, guestOnly: true },
    },
    {
      path: '/share/:token',
      name: 'share',
      component: ShareView,
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
        {
          path: 'profile',
          name: 'profile',
          component: ProfileView,
        },
        {
          path: 'notes/:id?',
          name: 'notes',
          component: NotesView,
        },
        {
          path: 'search',
          name: 'search',
          component: SearchView,
        },
        {
          path: 'drive',
          name: 'drive',
          component: DriveView,
        },
        {
          path: 'shares',
          name: 'my-shares',
          component: MySharesView,
        },
        {
          path: 'todos',
          name: 'todos',
          component: TodosView,
        },
        {
          path: 'reminders',
          name: 'reminders',
          component: RemindersView,
        },
        {
          path: 'ai',
          name: 'ai',
          component: AiView,
        },
        {
          path: 'ai/agent',
          redirect: '/ai',
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
    if (authStore.isAuthenticated && to.meta.guestOnly) {
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
