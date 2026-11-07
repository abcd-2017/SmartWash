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
          <span class="stat-card__value">{{ schoolCount }}</span>
        </div>
      </div>
      <div class="stat-card stat-card--blue">
        <div class="stat-card__icon">
          <el-icon :size="28"><UserFilled /></el-icon>
        </div>
        <div class="stat-card__info">
          <span class="stat-card__label">用户总数</span>
          <span class="stat-card__value">{{ userCount }}</span>
        </div>
      </div>
      <div class="stat-card stat-card--emerald">
        <div class="stat-card__icon">
          <el-icon :size="28"><Document /></el-icon>
        </div>
        <div class="stat-card__info">
          <span class="stat-card__label">今日订单</span>
          <span class="stat-card__value">{{ todayOrderCount }}</span>
        </div>
      </div>
      <div class="stat-card stat-card--amber">
        <div class="stat-card__icon">
          <el-icon :size="28"><Coin /></el-icon>
        </div>
        <div class="stat-card__info">
          <span class="stat-card__label">今日收入</span>
          <!-- 金额：整数元做滚动（小数补 .00 固定），避免 rAF 对小数漏帧抖动 -->
          <span class="stat-card__value">￥{{ incomeDisplay }}</span>
        </div>
      </div>
    </div>

    <!-- 趋势分析（收入折线 + 订单量柱图，7/30/90 天切换） -->
    <div class="trend-card" v-loading="trendLoading">
      <div class="trend-card__header">
        <h3 class="trend-card__title">趋势分析</h3>
        <el-radio-group v-model="trendDays" size="small">
          <el-radio-button v-for="days in TREND_DAYS_OPTIONS" :key="days" :value="days">
            近 {{ days }} 天
          </el-radio-button>
        </el-radio-group>
      </div>
      <div class="trend-card__grid">
        <div class="trend-card__panel">
          <span class="trend-card__panel-title">收入趋势</span>
          <TrendChart
            :data="incomeTrend"
            type="line"
            color="var(--color-primary)"
            :value-formatter="formatIncomeValue"
          />
        </div>
        <div class="trend-card__panel">
          <span class="trend-card__panel-title">订单量趋势</span>
          <TrendChart
            :data="orderTrend"
            type="bar"
            color="var(--color-info)"
            :value-formatter="formatOrderValue"
          />
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
        <router-link to="/coupon" class="quick-action-item">
          <el-icon :size="24"><Ticket /></el-icon>
          <span>优惠券管理</span>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { School, UserFilled, Document, Coin, Ticket } from '@element-plus/icons-vue';
import { getDashboardStats, getDashboardTrend } from '@/api/dashboard';
import { useAnimatedNumber } from '@/composables/useAnimatedNumber';
import TrendChart from '@/components/TrendChart.vue';
import { formatAmount, formatThousands } from '@/utils/format';
import logger from '@/utils/logger';

const stats = ref({
  schoolCount: 0,
  userCount: 0,
  todayOrderCount: 0,
  todayIncome: 0,
});

// 统计卡数值滚动：数据来自 getDashboardStats（真实数据触发），reduced-motion 下直接落定
const { value: schoolCount } = useAnimatedNumber(() => stats.value.schoolCount);
const { value: userCount } = useAnimatedNumber(() => stats.value.userCount);
const { value: todayOrderCount } = useAnimatedNumber(() => stats.value.todayOrderCount);
const { value: todayIncome } = useAnimatedNumber(() => stats.value.todayIncome);

// 金额展示：整数元滚动 + 固定 .00，保证视觉效果连贯
const incomeDisplay = computed(() => {
  const v = Number(todayIncome.value) || 0;
  return `${v.toFixed(2)}`;
});

onMounted(async () => {
  try {
    const data = await getDashboardStats();
    stats.value = data;
  } catch (error) {
    // 传输层失败已由 http.js 拦截器统一留痕，这里归因到页面动作，避免静默失败；页面保持默认 0 值展示
    logger.error('工作台统计数据加载失败:', error);
  }
});

// ---------- 趋势分析 ----------
// 近 N 天逐日趋势：一次请求同时产出收入与订单量两条序列（后端已补零成连续日期）
const TREND_DAYS_OPTIONS = [7, 30, 90];
const trendDays = ref(30);
const trendData = ref([]);
const trendLoading = ref(false);

// 竞态守卫：快速切换 7/30/90 天时旧响应后到会覆盖新窗数据，序号不一致即丢弃（对齐 useTableList）
let trendFetchSeq = 0;

const fetchTrend = async () => {
  const mySeq = ++trendFetchSeq;
  trendLoading.value = true;
  try {
    const data = (await getDashboardTrend(trendDays.value)) || [];
    if (mySeq !== trendFetchSeq) return;
    trendData.value = data;
  } catch (error) {
    if (mySeq !== trendFetchSeq) return;
    logger.error('趋势数据加载失败:', error);
    ElMessage.error(error.message || '获取趋势数据失败');
  } finally {
    if (mySeq === trendFetchSeq) {
      trendLoading.value = false;
    }
  }
};

// 切换时间窗回到第一手数据（后端按 days 重算并补零）
watch(trendDays, fetchTrend);

// 接口行 → 图表点映射；金额 tooltip 千分位两位小数，订单量千分位整数
const incomeTrend = computed(() =>
  trendData.value.map((row) => ({ date: row.date, value: Number(row.income) || 0 }))
);
const orderTrend = computed(() =>
  trendData.value.map((row) => ({ date: row.date, value: Number(row.orderCount) || 0 }))
);
const formatIncomeValue = (value) => `￥${formatAmount(value)}`;
const formatOrderValue = (value) => `${formatThousands(value)} 单`;

onMounted(fetchTrend);
</script>

<style scoped>
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
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-ground);
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
  color: var(--color-primary);
}

.stat-card--blue .stat-card__icon {
  background: rgba(59, 130, 246, 0.1);
  color: var(--color-info);
}

.stat-card--emerald .stat-card__icon {
  background: rgba(16, 185, 129, 0.1);
  color: var(--color-success);
}

.stat-card--amber .stat-card__icon {
  background: rgba(245, 158, 11, 0.1);
  color: var(--color-warning);
}

.stat-card__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-card__label {
  font-size: 13px;
  color: var(--text-secondary);
}

.stat-card__value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

/* 趋势分析卡：与统计卡同级白卡语言（12px 圆角 + 着地阴影），靛蓝/信息蓝只出现在图表主体 */
.trend-card {
  padding: 20px;
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-ground);
  margin-bottom: 24px;
}

.trend-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.trend-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.trend-card__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

@media (min-width: 900px) {
  .trend-card__grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* min-width: 0 防止图表画布在 grid 单元格内溢出 */
.trend-card__panel {
  min-width: 0;
}

.trend-card__panel-title {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

/* 快捷操作区 */
.quick-actions {
  padding: 20px;
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-ground);
}

.quick-actions__title {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
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
  background: var(--bg-subtle);
  color: var(--text-regular);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard),
    transform var(--motion-fast) var(--ease-standard),
    box-shadow var(--motion-fast) var(--ease-standard);
}

.quick-action-item:hover {
  background: rgba(99, 102, 241, 0.08);
  color: var(--color-primary);
  /* hover 上浮表达「可进入」，与统计卡层级语言一致 */
  transform: translateY(-2px);
  box-shadow: var(--shadow-lift);
}
</style>
