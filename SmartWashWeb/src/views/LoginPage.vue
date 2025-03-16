<template>
  <div class="login-container">
    <div class="login-content">
      <div class="login-left">
        <div class="brand">
          <!-- <img src="@/assets/logo.png" alt="SmartWash Logo" class="logo" /> -->
          <h1>SmartWash</h1>
        </div>
        <div class="slogan">
          <h2>智能洗衣管理系统</h2>
          <p>让洗衣管理更简单、更高效</p>
        </div>
      </div>
      <div class="login-right">
        <div class="login-box">
          <h2 class="login-title">管理员登录</h2>
          <el-form
            ref="formRef"
            :model="loginForm"
            :rules="rules"
            label-width="80px"
            class="login-form"
            @keyup.enter="handleLogin"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                clearable
                prefix-icon="User"
              />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                show-password
                prefix-icon="Lock"
              />
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                @click="handleLogin"
                :loading="loading"
                class="login-button"
              >
                登录
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from "vue";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import { login } from "@/api/auth";
import { User, Lock } from "@element-plus/icons-vue";

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
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  width: 100vw;
  background: #f5f7fa;
  position: fixed;
  top: 0;
  left: 0;
  margin: 0;
  padding: 0;
}

.login-content {
  display: flex;
  width: 1000px;
  height: 600px;
  background: #ffffff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.06);
}

.login-left {
  flex: 1;
  background: linear-gradient(135deg, #f8faff 0%, #f0f4f8 100%);
  padding: 60px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  color: #303133;
  position: relative;
  border-right: 1px solid rgba(0, 0, 0, 0.05);
}

.login-left::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: url("data:image/svg+xml,%3Csvg width='100' height='100' viewBox='0 0 100 100' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M11 18c3.866 0 7-3.134 7-7s-3.134-7-7-7-7 3.134-7 7 3.134 7 7 7zm48 25c3.866 0 7-3.134 7-7s-3.134-7-7-7-7 3.134-7 7 3.134 7 7 7zm-43-7c1.657 0 3-1.343 3-3s-1.343-3-3-3-3 1.343-3 3 1.343 3 3 3zm63 31c1.657 0 3-1.343 3-3s-1.343-3-3-3-3 1.343-3 3 1.343 3 3 3zM34 90c1.657 0 3-1.343 3-3s-1.343-3-3-3-3 1.343-3 3 1.343 3 3 3zm56-76c1.657 0 3-1.343 3-3s-1.343-3-3-3-3 1.343-3 3 1.343 3 3 3zM12 86c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm28-65c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm23-11c2.76 0 5-2.24 5-5s-2.24-5-5-5-5 2.24-5 5 2.24 5 5 5zm-6 60c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm29 22c2.76 0 5-2.24 5-5s-2.24-5-5-5-5 2.24-5 5 2.24 5 5 5zM32 63c2.76 0 5-2.24 5-5s-2.24-5-5-5-5 2.24-5 5 2.24 5 5 5zm57-13c2.76 0 5-2.24 5-5s-2.24-5-5-5-5 2.24-5 5 2.24 5 5 5zm-9-21c1.105 0 2-.895 2-2s-.895-2-2-2-2 .895-2 2 .895 2 2 2zM60 91c1.105 0 2-.895 2-2s-.895-2-2-2-2 .895-2 2 .895 2 2 2zM35 41c1.105 0 2-.895 2-2s-.895-2-2-2-2 .895-2 2 .895 2 2 2zM12 60c1.105 0 2-.895 2-2s-.895-2-2-2-2 .895-2 2 .895 2 2 2z' fill='%234a90e2' fill-opacity='0.03' fill-rule='evenodd'/%3E%3C/svg%3E");
  opacity: 0.8;
}

.brand {
  display: flex;
  align-items: center;
  margin-bottom: 40px;
}

.logo {
  width: 60px;
  height: 60px;
  margin-right: 16px;
}

.brand h1 {
  font-size: 36px;
  font-weight: 600;
  margin: 0;
  color: #2c3e50;
  letter-spacing: -0.5px;
}

.slogan h2 {
  font-size: 28px;
  margin-bottom: 16px;
  font-weight: 500;
  color: #2c3e50;
  letter-spacing: -0.3px;
}

.slogan p {
  font-size: 16px;
  color: #5c6b7f;
  margin: 0;
  line-height: 1.6;
}

.login-right {
  flex: 1;
  padding: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
}

.login-box {
  width: 100%;
  max-width: 400px;
}

.login-title {
  margin-bottom: 32px;
  font-size: 24px;
  text-align: center;
  color: #2c3e50;
  font-weight: 500;
  letter-spacing: -0.3px;
}

.login-form {
  margin-top: 20px;
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  background: #4a90e2;
  border: none;
  transition: all 0.3s ease;
  border-radius: 8px;
  font-weight: 500;
}

.login-button:hover {
  background: #357abd;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(74, 144, 226, 0.2);
}

:deep(.el-input__wrapper) {
  box-shadow: 0 2px 8px rgba(74, 144, 226, 0.08);
  transition: all 0.3s ease;
  border-radius: 8px;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 2px 12px rgba(74, 144, 226, 0.12);
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(74, 144, 226, 0.2);
}

:deep(.el-form-item__label) {
  color: #5c6b7f;
  font-weight: 500;
}

:deep(.el-input__inner) {
  color: #2c3e50;
}

:deep(.el-input__inner::placeholder) {
  color: #a0aec0;
}
</style>