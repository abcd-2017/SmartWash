<template>
  <div class="not-found-page">
    <div class="not-found-card">
      <div class="not-found-brand">
        <img src="@/assets/logo.svg" alt="SmartWash" class="not-found-logo" />
        <span class="not-found-brand-name">SmartWash</span>
      </div>
      <div class="not-found-code">404</div>
      <div class="not-found-title">页面不存在</div>
      <div class="not-found-desc">抱歉，您访问的页面不存在或已被移除</div>
      <div class="not-found-actions">
        <el-button type="primary" @click="goBack">
          {{ hasInternalBack ? '返回上一页' : '返回首页' }}
        </el-button>
        <el-button @click="goHome">{{ isLoggedIn ? '返回首页' : '去登录' }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
// 404 兜底页：替代原先通配路由直接 redirect 首页的行为，明确告知用户路径无效
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const authStore = useAuthStore();

// 登录态决定次级按钮去向：未登录去登录页（路由守卫也会拦回登录页），已登录回首页
const isLoggedIn = computed(() => !!authStore.token);

// 站内来源判断：Vue Router 在 push 时会把来源地址写入 history.state.back
// （初始直连进入时该字段为 null/undefined），据此区分「站内跳转来的 404」与「直接打开的 404」；
// window.history.length > 1 不可靠——浏览器历史里可能存有站外页面，会把用户带回站外
const hasInternalBack = computed(() => {
  const back = window.history.state?.back;
  return typeof back === 'string' && back.length > 0;
});

// 有站内来源则退回上一页（404 常见于误点链接），否则降级为返回首页
const goBack = () => {
  if (hasInternalBack.value) {
    router.back();
  } else {
    router.push('/');
  }
};

const goHome = () => {
  router.push(isLoggedIn.value ? '/' : '/login');
};
</script>

<style scoped>
.not-found-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--bg-page);
}

.not-found-card {
  text-align: center;
  padding: 40px 56px;
  background: var(--bg-card);
  border-radius: 16px;
  box-shadow: var(--shadow-brand);
}

/* 404 品牌行(live 打磨 v1):对齐登录/投递页品牌语言,让兜底页有产品归属 */
.not-found-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 24px;
}

.not-found-logo {
  width: 24px;
  height: 24px;
}

.not-found-brand-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.not-found-code {
  font-size: 64px;
  font-weight: 700;
  color: var(--color-primary);
  line-height: 1;
  letter-spacing: -2px;
}

.not-found-title {
  margin-top: 16px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.not-found-desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

/* 出路按钮组：主/次按钮横排居中 */
.not-found-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
}
</style>
