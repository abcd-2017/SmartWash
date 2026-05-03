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
  min-height: 100vh;
  width: 100%;
  background: #f1f5f9;
  padding: 0;
}

.login-content {
  display: flex;
  width: 100%;
  min-height: 100vh;
  background: #ffffff;
}

.login-left {
  flex: 1;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #312e81 100%);
  padding: 60px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
}

.login-left::before {
  content: "";
  position: absolute;
  top: 40px;
  left: 40px;
  right: 40px;
  bottom: 40px;
  border: 1px solid rgba(99, 102, 241, 0.12);
  border-radius: 12px;
  pointer-events: none;
}

.brand {
  display: flex;
  align-items: center;
  margin-bottom: 40px;
}

.brand h1 {
  font-size: 36px;
  font-weight: 700;
  margin: 0;
  color: #e2e8f0;
  letter-spacing: -0.5px;
}

.slogan h2 {
  font-size: 28px;
  margin-bottom: 16px;
  font-weight: 500;
  color: #cbd5e1;
  letter-spacing: -0.3px;
}

.slogan p {
  font-size: 16px;
  color: #94a3b8;
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
  color: #0f172a;
  font-weight: 600;
  letter-spacing: -0.3px;
}

.login-form {
  margin-top: 20px;
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 500;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
  transition: all 0.3s ease;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.1);
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

:deep(.el-form-item__label) {
  color: #334155;
  font-weight: 500;
}

:deep(.el-input__inner) {
  color: #0f172a;
}

:deep(.el-input__inner::placeholder) {
  color: #94a3b8;
}
</style>