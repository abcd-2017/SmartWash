<template>
  <div class="not-found-page">
    <div class="not-found-card">
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
  padding: 48px 64px;
  background: var(--bg-card);
  border-radius: 16px;
  box-shadow: var(--shadow-brand);
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
