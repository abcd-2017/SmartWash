<template>
  <div class="page-container dashboard">
    <!-- 页面标题区 -->
    <div class="page-header">
      <h2>工作台</h2>
      <p>欢迎使用 SmartWash 智能洗衣管理系统</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card stat-card--indigo">
        <div class="stat-card__icon">
          <el-icon :size="28"><School /></el-icon>
        </div>
        <div class="stat-card__info">
          <span class="stat-card__label">学校总数</span>
          <span class="stat-card__value">{{ stats.schoolCount }}</span>
        </div>
      </div>
      <div class="stat-card stat-card--blue">
        <div class="stat-card__icon">
          <el-icon :size="28"><UserFilled /></el-icon>
        </div>
        <div class="stat-card__info">
          <span class="stat-card__label">用户总数</span>
          <span class="stat-card__value">{{ stats.userCount }}</span>
        </div>
      </div>
      <div class="stat-card stat-card--emerald">
        <div class="stat-card__icon">
          <el-icon :size="28"><Document /></el-icon>
        </div>
        <div class="stat-card__info">
          <span class="stat-card__label">今日订单</span>
          <span class="stat-card__value">{{ stats.todayOrderCount }}</span>
        </div>
      </div>
      <div class="stat-card stat-card--amber">
        <div class="stat-card__icon">
          <el-icon :size="28"><Coin /></el-icon>
        </div>
        <div class="stat-card__info">
          <span class="stat-card__label">今日收入</span>
          <span class="stat-card__value">¥{{ Number(stats.todayIncome).toFixed(2) }}</span>
        </div>
      </div>
    </div>

    <!-- 快捷操作区 -->
    <div class="quick-actions">
      <h3 class="quick-actions__title">快捷操作</h3>
      <div class="quick-actions__grid">
        <router-link to="/orders" class="quick-action-item">
          <el-icon :size="24"><Document /></el-icon>
          <span>订单管理</span>
        </router-link>
        <router-link to="/users" class="quick-action-item">
          <el-icon :size="24"><UserFilled /></el-icon>
          <span>学生管理</span>
        </router-link>
        <router-link to="/schools" class="quick-action-item">
          <el-icon :size="24"><School /></el-icon>
          <span>学校管理</span>
        </router-link>
        <router-link to="/coupons" class="quick-action-item">
          <el-icon :size="24"><Ticket /></el-icon>
          <span>优惠券管理</span>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { School, UserFilled, Document, Coin, Ticket } from '@element-plus/icons-vue'
import { getDashboardStats } from '@/api/dashboard'

const stats = ref({
  schoolCount: 0,
  userCount: 0,
  todayOrderCount: 0,
  todayIncome: 0
})

onMounted(async () => {
  try {
    const data = await getDashboardStats()
    stats.value = data
  } catch (e) {
    console.error('获取工作台数据失败:', e)
  }
})
</script>

<style scoped>
@import '@/assets/pages.css';

.dashboard {
  padding: 0;
}

/* 统计卡片网格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

@media (min-width: 900px) {
  .stats-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.stat-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 12px;
  flex-shrink: 0;
}

.stat-card--indigo .stat-card__icon {
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
}

.stat-card--blue .stat-card__icon {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.stat-card--emerald .stat-card__icon {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.stat-card--amber .stat-card__icon {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}

.stat-card__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-card__label {
  font-size: 13px;
  color: #64748b;
}

.stat-card__value {
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

/* 快捷操作区 */
.quick-actions {
  padding: 20px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.quick-actions__title {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.quick-actions__grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

@media (min-width: 600px) {
  .quick-actions__grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

.quick-action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 16px;
  border-radius: 10px;
  background: #f8fafc;
  color: #475569;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.quick-action-item:hover {
  background: rgba(99, 102, 241, 0.08);
  color: #6366f1;
}
</style>
