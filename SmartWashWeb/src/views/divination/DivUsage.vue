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
      <div class="stat-card">
        <div class="stat-label">总卦例数</div>
        <div class="stat-value">{{ summary.recordCount.toLocaleString() }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">总解读数</div>
        <div class="stat-value">{{ summary.interpretCount.toLocaleString() }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">缓存命中</div>
        <div class="stat-value">{{ summary.cacheHitCount.toLocaleString() }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">拦截数</div>
        <div class="stat-value">{{ summary.blockedCount.toLocaleString() }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">活跃用户</div>
        <div class="stat-value">{{ summary.activeUsers.toLocaleString() }}</div>
      </div>
    </div>

    <div class="charts-row" v-loading="loading">
      <div class="chart-card">
        <h4 class="chart-title">每日用量趋势</h4>
        <div class="chart-wrap">
          <div v-if="!hasData" class="chart-empty">暂无数据</div>
          <div ref="trendChartRef" class="chart-container" :class="{ 'is-empty': !hasData }"></div>
        </div>
      </div>
      <div class="chart-card">
        <h4 class="chart-title">术数方法分布</h4>
        <div class="chart-wrap">
          <div v-if="!hasData" class="chart-empty">暂无数据</div>
          <div ref="methodChartRef" class="chart-container" :class="{ 'is-empty': !hasData }"></div>
        </div>
      </div>
    </div>
    <div class="charts-row" v-loading="loading">
      <div class="chart-card">
        <h4 class="chart-title">Token 消耗</h4>
        <div class="chart-wrap">
          <div v-if="!hasData" class="chart-empty">暂无数据</div>
          <div ref="tokenChartRef" class="chart-container" :class="{ 'is-empty': !hasData }"></div>
        </div>
      </div>
      <div class="chart-card">
        <h4 class="chart-title">缓存命中率</h4>
        <div class="chart-wrap">
          <div v-if="!hasData" class="chart-empty">暂无数据</div>
          <div ref="cacheChartRef" class="chart-container" :class="{ 'is-empty': !hasData }"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';
import { ElMessage } from 'element-plus';
import { DIV_METHOD_OPTIONS as methodOptions } from '@/constants/dict';
import { getUsageStats } from '@/api/divination';

const loading = ref(false);
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

// 是否有可渲染数据：决定图表是否挂空状态占位（评审遗留：图表空状态补齐）
const hasData = computed(() => recordsCount.value > 0);
const recordsCount = ref(0);

// 图表入场/更新动画：数据登场与过滤变化有节奏，值取自 --motion-slow/--motion-base 档
const CHART_ANIMATION = {
  animation: true,
  animationDuration: 360, // 首次渲染（进场）
  animationDurationUpdate: 300, // 数据更新重绘
  animationEasing: 'cubicOut',
};

onMounted(() => {
  initCharts();
  fetchStats();
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
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

const handleResize = () => {
  trendChart?.resize();
  methodChart?.resize();
  tokenChart?.resize();
  cacheChart?.resize();
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
  } catch (error) {
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

const renderCharts = (records) => {
  const dates = records.map((r) => r.statDate).sort();
  recordsCount.value = records.length;

  // 每日用量趋势
  trendChart.setOption({
    ...CHART_ANIMATION,
    color: ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#818cf8'],
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
    ...CHART_ANIMATION,
    color: ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#818cf8'],
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
    ...CHART_ANIMATION,
    color: ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#818cf8'],
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
    ...CHART_ANIMATION,
    color: ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#818cf8'],
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
/* 统计卡与图表卡进场：淡入上移（首挂载一次；数值/图表内容动画已分别处理） */
.stats-row,
.charts-row {
  animation: usage-fade-in-up var(--motion-slow) var(--ease-decelerate) both;
}

.charts-row:nth-of-type(2) {
  animation-delay: 60ms;
}

@keyframes usage-fade-in-up {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

.stat-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
  margin-top: 8px;
}

.charts-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.chart-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.chart-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
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
  color: #64748b;
  font-size: 14px;
  background: #fff;
  border-radius: 8px;
  z-index: 1;
}

@media (max-width: 1200px) {
  .stats-row {
    grid-template-columns: repeat(3, 1fr);
  }
  .charts-row {
    grid-template-columns: 1fr;
  }
}
</style>
