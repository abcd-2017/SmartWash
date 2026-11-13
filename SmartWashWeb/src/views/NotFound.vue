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
      <el-button type="primary" class="back-btn" @click="goHome"> 返回首页 </el-button>
    </div>
  </div>
</template>

<script setup>
// 404 兜底页：替代原先通配路由直接 redirect 首页的行为，明确告知用户路径无效
import { useRouter } from 'vue-router';

const router = useRouter();

const goHome = () => {
  router.push('/');
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

.back-btn {
  margin-top: 24px;
}
</style>
