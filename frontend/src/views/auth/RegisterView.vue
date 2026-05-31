<template>
  <div class="auth-page">
    <a-card class="auth-card" :bordered="false">
      <div class="card-head">
        <p class="eyebrow">Create account</p>
        <h1>注册账号</h1>
        <span>创建新账号并立即进入系统</span>
      </div>

      <a-form layout="vertical" :model="formState" @finish="handleRegister">
        <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
          <a-input v-model:value="formState.username" placeholder="请输入用户名" size="large" />
        </a-form-item>
        <a-form-item label="邮箱" name="email" :rules="[{ required: true, type: 'email', message: '请输入有效邮箱' }]">
          <a-input v-model:value="formState.email" placeholder="请输入邮箱" size="large" />
        </a-form-item>
        <a-form-item label="昵称" name="nickname" :rules="[{ required: true, message: '请输入昵称' }]">
          <a-input v-model:value="formState.nickname" placeholder="请输入昵称" size="large" />
        </a-form-item>
        <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
          <a-input-password v-model:value="formState.password" placeholder="请输入密码" size="large" />
        </a-form-item>
        <a-button type="primary" block size="large" html-type="submit" :loading="loading">注册并登录</a-button>
      </a-form>

      <div class="footer-link">
        已有账号？<a @click="goLogin">返回登录</a>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { useAuthStore } from '../../store/auth';

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
    await router.push('/');
  } catch (error: any) {
    message.error(error?.message || '注册失败，请检查填写内容');
  } finally {
    loading.value = false;
  }
};

const goLogin = () => router.push('/login');
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background: linear-gradient(180deg, #f7faff 0%, #eef4ff 100%);
}

.auth-card {
  width: min(460px, 100%);
  border-radius: 24px;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.08);
}

.card-head h1 {
  margin: 10px 0 6px;
  font-size: 28px;
}

.card-head span {
  color: #667085;
}

.eyebrow {
  display: inline-flex;
  padding: 6px 12px;
  border-radius: 999px;
  background: #eaf2ff;
  color: #1677ff;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.footer-link {
  margin-top: 16px;
  text-align: center;
  color: #667085;
}
</style>
