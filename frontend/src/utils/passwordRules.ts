export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 64;

export const passwordFieldRules = [
  { required: true, message: '请输入密码' },
  { min: PASSWORD_MIN, max: PASSWORD_MAX, message: `密码长度需在 ${PASSWORD_MIN} 到 ${PASSWORD_MAX} 个字符之间` },
];

export const optionalPasswordFieldRules = [
  {
    validator(_rule: unknown, value: string) {
      if (!value) {
        return Promise.resolve();
      }
      if (value.length < PASSWORD_MIN || value.length > PASSWORD_MAX) {
        return Promise.reject(`密码长度需在 ${PASSWORD_MIN} 到 ${PASSWORD_MAX} 个字符之间`);
      }
      return Promise.resolve();
    },
  },
];

export function confirmPasswordRule(getPassword: () => string) {
  return {
    validator(_rule: unknown, value: string) {
      if (!value || value === getPassword()) {
        return Promise.resolve();
      }
      return Promise.reject('两次输入的密码不一致');
    },
  };
}

export function extractApiErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return message || fallback;
}
