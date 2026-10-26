<template>
  <div class="login-page">
    <div class="login-panel">
      <!-- 左侧品牌区：深色渐变（规范: 2026-05-03-ui-redesign-design.md） -->
      <aside class="brand-panel">
        <div class="brand-logo-row">
          <img src="@/assets/logo.svg" alt="SmartWash" class="brand-logo" />
          <span class="brand-name">SmartWash</span>
        </div>
        <div class="brand-slogan">智能洗衣管理系统</div>
        <ul class="brand-features">
          <li>订单 · 支付 · 充值全链路管理</li>
          <li>学校 / 寄存柜 / 洗护套餐基础数据</li>
          <li>优惠券营销与角色权限体系</li>
        </ul>
        <div class="brand-footer">SmartWash Admin Console</div>
      </aside>

      <!-- 右侧表单区 -->
      <section class="form-panel">
        <h1 class="form-title">欢迎回来</h1>
        <p class="form-sub">请使用管理员账号登录</p>

        <el-form
          ref="formRef"
          :model="loginForm"
          :rules="rules"
          label-position="top"
          @keyup.enter="handleLogin"
        >
          <el-form-item label="用户名" prop="username">
            <el-input v-model="loginForm.username" placeholder="请输入用户名" clearable />
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
            <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">
              登 录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="footer-text">SmartWash Admin</div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import { login } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const authStore = useAuthStore();
const formRef = ref(null);
const loginForm = reactive({
  username: '',
  password: '',
});

const rules = reactive({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 16, message: '密码长度在6到16个字符', trigger: 'blur' },
  ],
});

const loading = ref(false);

const handleLogin = async () => {
  if (!formRef.value) return;
  // 请求在途时拦截 Enter 重复触发（按钮 :loading 只拦点击，拦不住表单回车）
  if (loading.value) return;

  try {
    await formRef.value.validate();
    loading.value = true;

    // 后端登录响应 data 已改为对象 { token, role }，role 为该管理员实际角色名
    const data = await login(loginForm);
    if (!data?.token) {
      throw new Error('登录响应缺少凭证，请稍后重试');
    }
    // 登录态统一写入 Pinia store（内部持久化到 localStorage），角色以后端返回为准
    authStore.login(data.token, data.role);

    ElMessage.success('登录成功');
    router.push('/');
  } catch (error) {
    ElMessage.error(error.message || '登录失败');
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
  padding: 16px;
}

.login-panel {
  display: flex;
  width: 840px;
  max-width: 100%;
  min-height: 520px;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow:
    0 4px 32px rgba(99, 102, 241, 0.08),
    0 1px 2px rgba(0, 0, 0, 0.04);
  /* 舞台进场：整体淡入 + 轻微放大，控制在 1 个关键元素内 */
  animation: login-panel-in var(--motion-base) var(--ease-decelerate) both;
}

@keyframes login-panel-in {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.99);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* --- 左侧品牌区 --- */
.brand-panel {
  display: flex;
  flex-direction: column;
  width: 46%;
  padding: 48px 40px;
  background: linear-gradient(135deg, #0f172a, #1e293b, #312e81);
  color: #f1f5f9;
}

.brand-logo-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-logo {
  width: 40px;
  height: 40px;
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.brand-slogan {
  margin-top: 8px;
  font-size: 13px;
  color: #cbd5e1;
}

.brand-features {
  margin: auto 0;
  padding: 0;
  list-style: none;
  font-size: 14px;
  color: #cbd5e1;
  line-height: 2.2;
}

.brand-footer {
  font-size: 12px;
  color: #94a3b8;
}

/* --- 右侧表单区 --- */
.form-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px 44px;
}

.form-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.form-sub {
  margin: 6px 0 24px;
  font-size: 13px;
  color: #64748b;
}

.login-btn {
  width: 100%;
  height: 42px;
  margin-top: 8px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 8px;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.login-btn:not(.is-disabled):hover {
  transform: translateY(-1px);
}

.footer-text {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  color: #64748b;
}

:deep(.el-input__wrapper) {
  height: 40px;
  border-radius: 8px;
  background: #f8fafc;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  border: 1.5px solid #e2e8f0;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

:deep(.el-input__wrapper:hover) {
  border-color: #cbd5e1;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

:deep(.el-input__inner) {
  color: #0f172a;
}

:deep(.el-input__inner::placeholder) {
  color: #94a3b8;
}

/* 窄屏收敛为单栏表单 */
@media (max-width: 880px) {
  .brand-panel {
    display: none;
  }

  .login-panel {
    width: 400px;
    min-height: auto;
  }
}
</style>
