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
        <div class="stat-card__head">
          <div class="stat-card__icon">
            <el-icon :size="16"><School /></el-icon>
          </div>
          <span class="stat-card__label">学校总数</span>
        </div>
        <span class="stat-card__value">
          <el-skeleton v-if="statsLoading" animated>
            <template #template>
              <el-skeleton-item variant="text" class="stat-card__skeleton-bar" />
            </template>
          </el-skeleton>
          <template v-else-if="statsError">--</template>
          <template v-else>{{ schoolCount }}</template>
        </span>
      </div>
      <div class="stat-card stat-card--blue">
        <div class="stat-card__head">
          <div class="stat-card__icon">
            <el-icon :size="16"><UserFilled /></el-icon>
          </div>
          <span class="stat-card__label">用户总数</span>
        </div>
        <span class="stat-card__value">
          <el-skeleton v-if="statsLoading" animated>
            <template #template>
              <el-skeleton-item variant="text" class="stat-card__skeleton-bar" />
            </template>
          </el-skeleton>
          <template v-else-if="statsError">--</template>
          <template v-else>{{ userCount }}</template>
        </span>
      </div>
      <div class="stat-card stat-card--emerald">
        <div class="stat-card__head">
          <div class="stat-card__icon">
            <el-icon :size="16"><Document /></el-icon>
          </div>
          <span class="stat-card__label">今日订单</span>
        </div>
        <span class="stat-card__value">
          <el-skeleton v-if="statsLoading" animated>
            <template #template>
              <el-skeleton-item variant="text" class="stat-card__skeleton-bar" />
            </template>
          </el-skeleton>
          <template v-else-if="statsError">--</template>
          <template v-else>{{ todayOrderCount }}</template>
        </span>
      </div>
      <div class="stat-card stat-card--amber">
        <div class="stat-card__head">
          <div class="stat-card__icon">
            <el-icon :size="16"><Coin /></el-icon>
          </div>
          <span class="stat-card__label">今日收入</span>
        </div>
        <!-- 金额：整数元做滚动（小数补 .00 固定），避免 rAF 对小数漏帧抖动 -->
        <span class="stat-card__value">
          <el-skeleton v-if="statsLoading" animated>
            <template #template>
              <el-skeleton-item variant="text" class="stat-card__skeleton-bar" />
            </template>
          </el-skeleton>
          <template v-else-if="statsError">--</template>
          <template v-else>￥{{ incomeDisplay }}</template>
        </span>
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
      <!-- 加载失败态：面板内显式失败 + 重试，不再误显 el-empty「暂无数据」 -->
      <div v-if="trendError" class="trend-card__error">
        <span>加载失败</span>
        <el-button size="small" @click="fetchTrend">重试</el-button>
      </div>
      <div v-else class="trend-card__grid">
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
      <div class="quick-actions__chips">
        <router-link to="/orders" class="quick-action-chip">
          <span class="quick-action-chip__icon"
            ><el-icon :size="16"><Document /></el-icon
          ></span>
          <span>订单管理</span>
        </router-link>
        <router-link to="/users" class="quick-action-chip">
          <span class="quick-action-chip__icon"
            ><el-icon :size="16"><UserFilled /></el-icon
          ></span>
          <span>学生管理</span>
        </router-link>
        <router-link to="/schools" class="quick-action-chip">
          <span class="quick-action-chip__icon"
            ><el-icon :size="16"><School /></el-icon
          ></span>
          <span>学校管理</span>
        </router-link>
        <router-link to="/coupon" class="quick-action-chip">
          <span class="quick-action-chip__icon"
            ><el-icon :size="16"><Ticket /></el-icon
          ></span>
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

// 统计卡加载态：进行中渲染骨架，失败落定「--」，避免「0→真值」闪变与误导
const statsLoading = ref(true);
const statsError = ref(false);

// 统计卡数值滚动：数据来自 getDashboardStats（真实数据触发），reduced-motion 下直接落定
const { value: schoolCount } = useAnimatedNumber(() => stats.value.schoolCount);
const { value: userCount } = useAnimatedNumber(() => stats.value.userCount);
const { value: todayOrderCount } = useAnimatedNumber(() => stats.value.todayOrderCount);
const { value: todayIncome } = useAnimatedNumber(() => stats.value.todayIncome);

// 金额展示：滚动中间帧保持整数元（无千分位，避免逗号抖动），落定帧走 formatAmount 千分位
const incomeDisplay = computed(() => {
  const current = Number(todayIncome.value) || 0;
  const settled = todayIncome.value === (Number(stats.value.todayIncome) || 0);
  return settled ? formatAmount(current) : current.toFixed(2);
});

onMounted(async () => {
  statsLoading.value = true;
  statsError.value = false;
  try {
    const data = await getDashboardStats();
    // 后端可能返回 null：空对象兜底，保留默认结构避免展开报错
    stats.value = { ...stats.value, ...(data || {}) };
  } catch (error) {
    statsError.value = true;
    // 传输层失败已由 http.js 拦截器统一留痕，这里归因到页面动作；与趋势卡失败反馈
    // 统一弹 toast——此前仅 logger 静默，页面保持 0 值展示，用户对空面板无从归因
    logger.error('工作台统计数据加载失败:', error);
    ElMessage.error(error.message || '获取统计数据失败');
  } finally {
    statsLoading.value = false;
  }
});

// ---------- 趋势分析 ----------
// 近 N 天逐日趋势：一次请求同时产出收入与订单量两条序列（后端已补零成连续日期）
const TREND_DAYS_OPTIONS = [7, 30, 90];
const trendDays = ref(30);
const trendData = ref([]);
const trendLoading = ref(false);
// 趋势卡失败态：失败面板内显式提示 + 重试，不再误显「暂无数据」空态
const trendError = ref(false);

// 竞态守卫：快速切换 7/30/90 天时旧响应后到会覆盖新窗数据，序号不一致即丢弃（对齐 useTableList）
let trendFetchSeq = 0;

const fetchTrend = async () => {
  const mySeq = ++trendFetchSeq;
  trendLoading.value = true;
  trendError.value = false;
  try {
    const data = (await getDashboardTrend(trendDays.value)) || [];
    if (mySeq !== trendFetchSeq) return;
    trendData.value = data;
  } catch (error) {
    if (mySeq !== trendFetchSeq) return;
    trendError.value = true;
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
  flex-direction: column;
  align-items: stretch;
  gap: 14px;
  padding: 18px 20px;
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-ground);
}

/* 头行：小淡底图标与标签同行，数值独占一行——四卡数值共享基线，扫读成列 */
.stat-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  flex-shrink: 0;
}

.stat-card--indigo .stat-card__icon {
  background: var(--primary-soft-bg);
  color: var(--color-primary);
}

.stat-card--blue .stat-card__icon {
  background: var(--info-soft-bg);
  color: var(--color-info);
}

.stat-card--emerald .stat-card__icon {
  background: var(--success-soft-bg);
  color: var(--color-success);
}

.stat-card--amber .stat-card__icon {
  background: var(--warning-soft-bg);
  color: var(--color-warning);
}

.stat-card__label {
  font-size: 13px;
  color: var(--text-secondary);
}

.stat-card__value {
  font-size: 26px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--text-primary);
  /* 数字等宽规则：数值滚动动画期间避免逐帧变宽抖动 */
  font-variant-numeric: tabular-nums;
}

/* 统计卡加载骨架：数值位等高占位，避免「0→真值」闪变与布局跳动 */
.stat-card__skeleton-bar {
  width: 96px;
  height: 26px;
  border-radius: 6px;
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

/* 趋势卡失败态：面板内居中提示 + 重试入口 */
.trend-card__error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 48px 0;
  font-size: 14px;
  color: var(--text-secondary);
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
  margin: 0 0 12px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

/* 高频入口收成一行 chip：弱底块 + 28px 图标容器，hover 转靛蓝淡底并上浮 */
.quick-actions__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.quick-action-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px 8px 8px;
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

.quick-action-chip__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.05);
  color: var(--text-regular);
  flex-shrink: 0;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.quick-action-chip:hover {
  background: rgba(99, 102, 241, 0.08);
  color: var(--color-primary);
  /* hover 上浮表达「可进入」，与统计卡层级语言一致 */
  transform: translateY(-2px);
  box-shadow: var(--shadow-lift);
}

.quick-action-chip:hover .quick-action-chip__icon {
  background: rgba(99, 102, 241, 0.12);
  color: var(--color-primary);
}
</style>
