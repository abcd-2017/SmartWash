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
import { passwordLengthRule } from '@/utils/validators';
import { ElMessage } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import { login } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';
import logger from '@/utils/logger';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const formRef = ref(null);
const loginForm = reactive({
  username: '',
  password: '',
});

const rules = reactive({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, passwordLengthRule()],
});

const loading = ref(false);

const handleLogin = async () => {
  if (!formRef.value) return;
  // 请求在途时拦截 Enter 重复触发（按钮 :loading 只拦点击，拦不住表单回车）
  if (loading.value) return;

  try {
    await formRef.value.validate();
    loading.value = true;

    // 后端登录响应 data 为 { token, refreshToken, role }，role 为该管理员实际角色名
    const data = await login(loginForm);
    if (!data?.token) {
      throw new Error('登录响应缺少凭证，请稍后重试');
    }
    // 登录态统一写入 Pinia store（内部持久化到 localStorage），角色以后端返回为准；
    // refreshToken 供 http 层 401 自动续签使用
    authStore.login(data.token, data.role, data.refreshToken);

    ElMessage.success('登录成功');
    // 低频关键动作留痕：用户名可能是手机号形态，不记用户名明文，仅记后端返回的角色名
    logger.info('管理员登录成功', `role=${data.role}`);
    // 原路返回：仅接受站内路径（/ 开头），防止 redirect 参数被外链利用
    const redirect = route.query.redirect;
    router.push(typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/');
  } catch (error) {
    // validate 校验失败 reject 的是字段对象（fieldValue 含密码明文），非 Error 时仅记文本，防止密码入日志
    logger.error('管理员登录失败:', error instanceof Error ? error : '表单校验未通过');
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
  background: var(--bg-page);
  padding: 16px;
}

.login-panel {
  display: flex;
  width: 840px;
  max-width: 100%;
  min-height: 520px;
  background: var(--bg-card);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--shadow-brand);
}

/* --- 左侧品牌区（暗面板：文字用暗侧专属灰阶 token，不走亮色 token） --- */
.brand-panel {
  display: flex;
  flex-direction: column;
  width: 46%;
  padding: 48px 40px;
  /* 墨色外壳渐变（外壳即墨色规则） */
  background: var(--sidebar-gradient);
  color: var(--sidebar-text-strong);
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
  /* 品牌字登记档 18px/700（DESIGN.md）：与侧栏品牌名同档，避免与右侧 20px 表单标题争抢层级 */
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.brand-slogan {
  margin-top: 8px;
  font-size: 13px;
  color: var(--sidebar-text);
}

.brand-features {
  margin: auto 0;
  padding: 0;
  list-style: none;
  font-size: 14px;
  color: var(--sidebar-text);
  /* 行高收紧到 2.0：三行功能列表在暗面板内更利落，避免过疏散漫 */
  line-height: 2;
}

.brand-footer {
  font-size: 12px;
  color: var(--sidebar-text-sub); /* 暗面板弱化字：不再混用亮侧 --text-muted（外壳即墨色规则） */
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
  color: var(--text-primary);
}

.form-sub {
  /* 标题与说明贴紧(4px)、说明与表单拉开(20px)，形成有意的疏密节奏 */
  margin: 4px 0 20px;
  font-size: 13px;
  color: var(--text-secondary);
}

.login-btn {
  width: 100%;
  height: 42px;
  margin-top: 8px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 8px;
  transition:
    transform var(--motion-base) var(--ease-decelerate),
    box-shadow var(--motion-base) var(--ease-decelerate);
}

.login-btn:not(.is-disabled):hover {
  transform: translateY(-1px);
}

.footer-text {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  color: var(--text-secondary);
}

:deep(.el-input__wrapper) {
  height: 40px;
  border-radius: 8px;
  background: var(--bg-subtle);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  border: 1.5px solid var(--border-color);
  transition:
    border-color var(--motion-fast) var(--ease-standard),
    box-shadow var(--motion-fast) var(--ease-standard);
}

:deep(.el-input__wrapper:hover) {
  border-color: var(--border-hover);
}

:deep(.el-input__wrapper.is-focus) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

:deep(.el-input__inner) {
  color: var(--text-primary);
}

:deep(.el-input__inner::placeholder) {
  color: var(--text-muted);
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
