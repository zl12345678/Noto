<template>
  <div class="auth-page">
    <div class="auth-shell auth-shell--single">
      <div class="auth-card-shell">
        <div class="auth-card">
          <div class="auth-card-head">
            <span class="auth-eyebrow">注册</span>
            <h2>创建账号</h2>
            <span>填写信息后即可使用 Noto</span>
          </div>

          <a-form layout="vertical" :model="formState" class="auth-form" @finish="handleRegister">
            <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
              <a-input v-model:value="formState.username" placeholder="用于登录" size="large" />
            </a-form-item>
            <a-form-item label="邮箱" name="email" :rules="[{ required: true, type: 'email', message: '请输入有效邮箱' }]">
              <a-input v-model:value="formState.email" placeholder="example@mail.com" size="large" />
            </a-form-item>
            <a-form-item label="昵称" name="nickname" :rules="[{ required: true, message: '请输入昵称' }]">
              <a-input v-model:value="formState.nickname" placeholder="显示名称" size="large" />
            </a-form-item>
            <a-form-item label="密码" name="password" :rules="passwordFieldRules">
              <a-input-password v-model:value="formState.password" placeholder="8–64 位" size="large" />
            </a-form-item>
            <a-button type="primary" block size="large" html-type="submit" :loading="loading">注册并登录</a-button>
          </a-form>

          <div class="auth-footer-link">
            已有账号？<a @click="goLogin">返回登录</a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { useAuthStore } from '../../store/auth';
import { passwordFieldRules } from '../../utils/passwordRules';

const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);

const formState = reactive({
  username: '',
  email: '',
  nickname: '',
  password: '',
});

const handleRegister = async () => {
  loading.value = true;
  try {
    await authStore.register(formState);
    message.success('注册成功');
    await router.push({ path: '/', query: { welcome: '1' } });
  } catch (error: any) {
    message.error(error?.message || '注册失败，请检查填写内容');
  } finally {
    loading.value = false;
  }
};

const goLogin = () => router.push('/login');
</script>
