<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <img src="@/assets/logo.svg" alt="SmartWash" class="brand-logo" />
        <div class="brand-name">SmartWash</div>
        <div class="brand-sub">智能洗衣管理系统</div>
      </div>

      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button
            :loading="loading"
            class="login-btn"
            color="#1e293b"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="footer-text">SmartWash Admin</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from "vue";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import { login } from "@/api/auth";

const router = useRouter();
const formRef = ref(null);
const loginForm = reactive({
  username: "",
  password: "",
});

const rules = reactive({
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, max: 16, message: "密码长度在6到16个字符", trigger: "blur" },
  ],
});

const loading = ref(false);

const handleLogin = async () => {
  if (!formRef.value) return;

  try {
    await formRef.value.validate();
    loading.value = true;

    const { data: token } = await login(loginForm);
    localStorage.setItem("token", token);

    ElMessage.success("登录成功");
    router.push("/");
  } catch (error) {
    ElMessage.error(error.message || "登录失败");
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f1f5f9;
}

.login-card {
  width: 380px;
  background: #fff;
  border-radius: 20px;
  padding: 40px 36px;
  box-shadow:
    0 4px 32px rgba(99, 102, 241, 0.08),
    0 1px 2px rgba(0, 0, 0, 0.04);
}

.brand {
  text-align: center;
  margin-bottom: 28px;
}

.brand-logo {
  width: 52px;
  height: 52px;
  margin: 0 auto 14px;
  display: block;
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.3px;
}

.brand-sub {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}

.login-btn {
  width: 100%;
  height: 42px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 10px;
}

.footer-text {
  text-align: center;
  font-size: 10px;
  color: #cbd5e1;
  margin-top: 16px;
}

:deep(.el-form-item__label) {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
}

:deep(.el-input__wrapper) {
  height: 40px;
  border-radius: 10px;
  background: #f8fafc;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  border: 1.5px solid #e2e8f0;
  transition: all 0.3s ease;
}

:deep(.el-input__wrapper:hover) {
  border-color: #cbd5e1;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}

:deep(.el-input__inner) {
  color: #0f172a;
}

:deep(.el-input__inner::placeholder) {
  color: #94a3b8;
}
</style>
