import { createApp } from 'vue';
import { createPinia } from 'pinia';
import Antd from 'ant-design-vue';
import '@fontsource/jetbrains-mono/latin-400.css';
import '@fontsource/jetbrains-mono/latin-500.css';
import '@fontsource/jetbrains-mono/latin-600.css';
import 'ant-design-vue/dist/reset.css';
import './styles/global.css';
import './styles/pages.css';
import './styles/auth.css';
import App from './App.vue';
import router from './router';

createApp(App).use(createPinia()).use(router).use(Antd).mount('#app');
