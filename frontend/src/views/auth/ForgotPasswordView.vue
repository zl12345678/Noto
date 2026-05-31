<template>
  <div class="auth-page">
    <a-card class="auth-card" :bordered="false">
      <div class="card-head">
        <p class="eyebrow">Reset password</p>
        <h1>忘记密码</h1>
        <span>通过邮箱重置你的登录密码</span>
      </div>

      <a-form layout="vertical" :model="formState" @finish="handleReset">
        <a-form-item label="邮箱" name="email" :rules="[{ required: true, type: 'email', message: '请输入有效邮箱' }]">
          <a-input v-model:value="formState.email" placeholder="请输入邮箱" size="large" />
        </a-form-item>
        <a-form-item label="新密码" name="newPassword" :rules="[{ required: true, message: '请输入新密码' }]">
          <a-input-password v-model:value="formState.newPassword" placeholder="请输入新密码" size="large" />
        </a-form-item>
        <a-button type="primary" block size="large" html-type="submit" :loading="loading">重置密码</a-button>
      </a-form>

      <div class="footer-link">
        想起密码了？<a @click="goLogin">返回登录</a>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { forgotPassword } from '../../api/auth';

const router = useRouter();
const loading = ref(false);

const formState = reactive({
  email: '',
  newPassword: '',
});

const handleReset = async () => {
  loading.value = true;
  try {
    await forgotPassword(formState);
    message.success('密码已重置，请使用新密码登录');
    await router.push('/login');
  } catch {
    message.error('重置失败，请检查邮箱和密码');
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
