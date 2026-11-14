<template>
  <div class="page-container">
    <!-- 筛选区域 -->
    <div class="filter-container">
      <el-form :inline="true">
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item label="术数方法">
          <el-select v-model="methodFilter" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="m in methodOptions"
              :key="m.value"
              :label="m.label"
              :value="m.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="fetchStats">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row" v-loading="loading">
      <!-- 查询失败占位：替代统计卡区域，避免失败后残留旧数据伪装正常 -->
      <div v-if="queryError" class="stats-error">
        <el-icon class="stats-error__icon"><WarningFilled /></el-icon>
        <p class="stats-error__text">数据加载失败</p>
        <el-button type="primary" size="small" @click="fetchStats">重试</el-button>
      </div>
      <template v-else>
        <div class="stat-card">
          <div class="stat-head">
            <div class="stat-icon stat-icon--indigo">
              <el-icon><Document /></el-icon>
            </div>
            <span class="stat-label">总卦例数</span>
          </div>
          <div class="stat-value">{{ formatThousands(summary.recordCount) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-head">
            <div class="stat-icon stat-icon--blue">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <span class="stat-label">总解读数</span>
          </div>
          <div class="stat-value">{{ formatThousands(summary.interpretCount) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-head">
            <div class="stat-icon stat-icon--emerald">
              <el-icon><CircleCheck /></el-icon>
            </div>
            <span class="stat-label">缓存命中</span>
          </div>
          <div class="stat-value">{{ formatThousands(summary.cacheHitCount) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-head">
            <div class="stat-icon stat-icon--red">
              <el-icon><CircleClose /></el-icon>
            </div>
            <span class="stat-label">拦截数</span>
          </div>
          <div class="stat-value">{{ formatThousands(summary.blockedCount) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-head">
            <div class="stat-icon stat-icon--amber">
              <el-icon><User /></el-icon>
            </div>
            <!-- 口径说明：activeUsers 为每日每方法明细求和，非去重用户数 -->
            <el-tooltip
              content="按日按方法累计：同一用户跨方法/跨天会重复计入，非去重用户数"
              placement="top"
            >
              <span class="stat-label stat-label--help">活跃人次</span>
            </el-tooltip>
          </div>
          <div class="stat-value">{{ formatThousands(summary.activeUsers) }}</div>
        </div>
      </template>
    </div>

    <div class="charts-row" v-loading="loading">
      <div class="chart-card">
        <h4 class="chart-title">每日用量趋势</h4>
        <div class="chart-wrap">
          <div v-if="queryError" class="chart-empty">数据加载失败</div>
          <div v-else-if="!hasData" class="chart-empty">暂无数据</div>
          <div
            ref="trendChartRef"
            class="chart-container"
            :class="{ 'is-empty': !hasData }"
            role="img"
            :aria-label="`每日用量趋势图：共 ${recordsCount} 天数据，含卦例数、解读数、拦截数三条序列`"
          ></div>
        </div>
      </div>
      <div class="chart-card">
        <h4 class="chart-title">术数方法分布</h4>
        <div class="chart-wrap">
          <div v-if="queryError" class="chart-empty">数据加载失败</div>
          <div v-else-if="!hasData" class="chart-empty">暂无数据</div>
          <div
            ref="methodChartRef"
            class="chart-container"
            :class="{ 'is-empty': !hasData }"
            role="img"
            aria-label="术数方法分布饼图：各术数解读量占比"
          ></div>
        </div>
      </div>
    </div>
    <div class="charts-row" v-loading="loading">
      <div class="chart-card">
        <h4 class="chart-title">Token 消耗</h4>
        <div class="chart-wrap">
          <div v-if="queryError" class="chart-empty">数据加载失败</div>
          <div v-else-if="!hasData" class="chart-empty">暂无数据</div>
          <div
            ref="tokenChartRef"
            class="chart-container"
            :class="{ 'is-empty': !hasData }"
            role="img"
            aria-label="Token 消耗柱状图：输入与输出 Token 对比"
          ></div>
        </div>
      </div>
      <div class="chart-card">
        <h4 class="chart-title">缓存命中率</h4>
        <div class="chart-wrap">
          <div v-if="queryError" class="chart-empty">数据加载失败</div>
          <div v-else-if="!hasData" class="chart-empty">暂无数据</div>
          <div
            ref="cacheChartRef"
            class="chart-container"
            :class="{ 'is-empty': !hasData }"
            role="img"
            aria-label="缓存命中率趋势图：百分比折线"
          ></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue';
// echarts/core 按需注册（树摇，收编 TrendChart.vue 方案）：折线/柱/饼 + 网格/悬浮提示/图例 + Canvas，
// 避免整包 echarts 进入本路由 chunk
import * as echarts from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { ElMessage } from 'element-plus';
import {
  ChatDotRound,
  CircleCheck,
  CircleClose,
  Document,
  User,
  WarningFilled,
} from '@element-plus/icons-vue';
import { DIV_METHOD_OPTIONS as methodOptions } from '@/constants/dict';
import { getUsageStats } from '@/api/divination';
import { formatThousands } from '@/utils/format';
import logger from '@/utils/logger';

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  CanvasRenderer,
]);

const loading = ref(false);
// 查询失败态：失败时统计卡与图表显示错误占位而非残留旧数据/「暂无数据」（成功查询清位）
const queryError = ref(false);
const dateRange = ref(null);
const methodFilter = ref('');

const summary = reactive({
  recordCount: 0,
  interpretCount: 0,
  cacheHitCount: 0,
  blockedCount: 0,
  activeUsers: 0,
});

const trendChartRef = ref(null);
const methodChartRef = ref(null);
const tokenChartRef = ref(null);
const cacheChartRef = ref(null);

let trendChart = null;
let methodChart = null;
let tokenChart = null;
let cacheChart = null;
let resizeTimer = null;

// 是否有可渲染数据：决定图表是否挂空状态占位（评审遗留：图表空状态补齐）
const hasData = computed(() => recordsCount.value > 0);
const recordsCount = ref(0);

// 减少动态效果：canvas 动画直接关闭（DESIGN.md 全局降级要求，与 TrendChart 同法）
const reducedMotion =
  typeof window !== 'undefined' &&
  !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

// canvas 无法引用 CSS var：构建 option 时运行时解析设计令牌一次（TrendChart.vue 同款方案，
// 取不到时回退令牌字面值），时长/色板与全局 token 不再手工复制
const readCssVar = (name, fallback) => {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
};

const resolveColor = (name, fallback) => readCssVar(name, fallback);

// 图表入场/更新动画：时长运行时读全局 token（--motion-slow/--motion-base）
const buildAnimation = () => ({
  animation: !reducedMotion,
  animationDuration: Number(readCssVar('--motion-slow', '320ms').replace(/ms$/, '')) || 320,
  animationDurationUpdate: Number(readCssVar('--motion-base', '220ms').replace(/ms$/, '')) || 220,
  animationEasing: 'cubicOut',
});

// 图表色板：与 base.css token 同源（靛蓝/翡翠绿/警琥珀/警示红/浅靛），四张图共用一份
const buildChartColors = () => [
  resolveColor('--color-primary', '#6366f1'),
  resolveColor('--color-success', '#10b981'),
  resolveColor('--color-warning', '#f59e0b'),
  resolveColor('--color-danger', '#ef4444'),
  resolveColor('--primary-soft', '#a5b4fc'),
];

onMounted(() => {
  initCharts();
  fetchStats();
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  if (resizeTimer) clearTimeout(resizeTimer);
  trendChart?.dispose();
  methodChart?.dispose();
  tokenChart?.dispose();
  cacheChart?.dispose();
});

const initCharts = () => {
  trendChart = echarts.init(trendChartRef.value);
  methodChart = echarts.init(methodChartRef.value);
  tokenChart = echarts.init(tokenChartRef.value);
  cacheChart = echarts.init(cacheChartRef.value);
};

// resize 防抖：窗口拖拽时避免每帧重建布局
const handleResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    trendChart?.resize();
    methodChart?.resize();
    tokenChart?.resize();
    cacheChart?.resize();
  }, 150);
};

const fetchStats = async () => {
  loading.value = true;
  try {
    const params = {};
    if (dateRange.value?.length === 2) {
      params.startDate = dateRange.value[0];
      params.endDate = dateRange.value[1];
    }
    if (methodFilter.value) {
      params.method = methodFilter.value;
    }
    queryError.value = false;
    const res = await getUsageStats(params);

    // 汇总数据
    const records = res.records || [];
    summary.recordCount = records.reduce((s, r) => s + (r.recordCount || 0), 0);
    summary.interpretCount = records.reduce((s, r) => s + (r.interpretCount || 0), 0);
    summary.cacheHitCount = records.reduce((s, r) => s + (r.cacheHitCount || 0), 0);
    summary.blockedCount = records.reduce((s, r) => s + (r.blockedCount || 0), 0);
    summary.activeUsers = records.reduce((s, r) => s + (r.activeUsers || 0), 0);

    await nextTick();
    renderCharts(records);
    // 统计查询属用户手动触发的查询路径，成功最多 debug（不污染 info/warn 通道）
    logger.debug(
      '用量统计查询成功',
      `startDate=${params.startDate ?? '-'}`,
      `endDate=${params.endDate ?? '-'}`,
      `method=${params.method ?? '-'}`,
      `records=${records.length}`
    );
  } catch (error) {
    // 失败置位：统计卡显示错误占位+重试按钮，图表区显示错误态而非「暂无数据」
    queryError.value = true;
    logger.error('用量统计查询失败:', error);
    ElMessage.error(error.message || '获取用量数据失败');
  } finally {
    loading.value = false;
  }
};

const resetFilter = () => {
  dateRange.value = null;
  methodFilter.value = '';
  fetchStats();
};

const renderCharts = (rawRecords) => {
  recordsCount.value = rawRecords.length;
  // 先按日期统一排序再派生全部序列：此前只排 x 轴标签不排 records，
  // 后端一旦乱序返回，标签与三条序列会整体错位且无告警（评审 P1）
  const records = [...rawRecords].sort((a, b) =>
    String(a.statDate ?? '').localeCompare(String(b.statDate ?? ''))
  );
  const dates = records.map((r) => r.statDate);
  const animation = buildAnimation();
  const chartColors = buildChartColors();

  // 每日用量趋势
  trendChart.setOption({
    ...animation,
    color: chartColors,
    tooltip: { trigger: 'axis' },
    legend: { data: ['卦例数', '解读数', '拦截数'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value' },
    series: [
      { name: '卦例数', type: 'line', smooth: true, data: records.map((r) => r.recordCount || 0) },
      {
        name: '解读数',
        type: 'line',
        smooth: true,
        data: records.map((r) => r.interpretCount || 0),
      },
      { name: '拦截数', type: 'line', smooth: true, data: records.map((r) => r.blockedCount || 0) },
    ],
  });

  // 术数方法分布（按 method 聚合）
  const methodMap = {};
  records.forEach((r) => {
    if (!methodMap[r.method]) methodMap[r.method] = 0;
    methodMap[r.method] += r.interpretCount || 0;
  });
  const methodData = Object.entries(methodMap).map(([name, value]) => ({
    name: methodOptions.find((m) => m.value === name)?.label || name,
    value,
  }));
  methodChart.setOption({
    ...animation,
    color: chartColors,
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    series: [
      {
        name: '术数分布',
        type: 'pie',
        radius: '60%',
        data: methodData,
        emphasis: {
          itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' },
        },
      },
    ],
  });

  // Token 消耗
  tokenChart.setOption({
    ...animation,
    color: chartColors,
    tooltip: { trigger: 'axis' },
    legend: { data: ['输入 Token', '输出 Token'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value' },
    series: [
      { name: '输入 Token', type: 'bar', data: records.map((r) => r.tokensIn || 0) },
      { name: '输出 Token', type: 'bar', data: records.map((r) => r.tokensOut || 0) },
    ],
  });

  // 缓存命中率
  const cacheRates = records.map((r) => {
    if (!r.interpretCount) return 0;
    return ((r.cacheHitCount / r.interpretCount) * 100).toFixed(1);
  });
  cacheChart.setOption({
    ...animation,
    color: chartColors,
    tooltip: { trigger: 'axis', formatter: '{b}: {c}%' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    series: [
      {
        name: '缓存命中率',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.3 },
        data: cacheRates,
      },
    ],
  });
};
</script>

<style scoped>
.stats-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

/* 头行:小淡底图标与标签同行,26px 数值独占一行——对齐 Home 纵向堆叠统计卡 */
.stat-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--bg-card);
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: var(--shadow-ground);
}

.stat-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 语义分配:卦例=靛蓝、解读=信息蓝、缓存命中=翡翠绿、拦截=警示红、活跃用户=警琥珀 */
.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  flex-shrink: 0;
  font-size: 16px;
}

.stat-icon--indigo {
  background: var(--primary-soft-bg);
  color: var(--color-primary);
}

.stat-icon--blue {
  background: var(--info-soft-bg);
  color: var(--color-info);
}

.stat-icon--emerald {
  background: var(--success-soft-bg);
  color: var(--color-success);
}

.stat-icon--red {
  background: var(--danger-soft-bg);
  color: var(--color-danger);
}

.stat-icon--amber {
  background: var(--warning-soft-bg);
  color: var(--color-warning);
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

/* 口径说明标签：hover 光标 + 可交互暗示，提示可悬停查看说明 */
.stat-label--help {
  cursor: help;
  border-bottom: 1px dashed var(--border-subtle);
}

.stat-value {
  font-size: 26px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--text-primary);
  /* 数字等宽规则：汇总数值随查询变化，避免逐帧变宽抖动 */
  font-variant-numeric: tabular-nums;
}

.charts-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

/* 查询失败占位：独占统计卡整行，图标+文案+重试按钮纵向居中 */
.stats-error {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 32px 0;
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-ground);
}

.stats-error__icon {
  font-size: 28px;
  color: var(--color-danger);
}

.stats-error__text {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.chart-card {
  background: var(--bg-card);
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: var(--shadow-ground);
}

.chart-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.chart-container {
  width: 100%;
  height: 300px;
}

/* 图表空状态占位：无数据时覆盖在图表容器上，保证高度稳定（避免布局跳动） */
.chart-wrap {
  position: relative;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  font-size: 14px;
  background: var(--bg-card);
  border-radius: 8px;
  z-index: 1;
}

/* 断点收敛回全局词汇表（600/880/900），移除越轨的 1200px 第四断点 */
@media (max-width: 900px) {
  .stats-row {
    grid-template-columns: repeat(3, 1fr);
  }
  .charts-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
