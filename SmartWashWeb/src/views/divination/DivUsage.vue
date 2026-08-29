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
          <el-select
            v-model="methodFilter"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="m in methodOptions"
              :key="m.value"
              :label="m.label"
              :value="m.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchStats">查询</el-button>
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

    <!-- 图表区域 -->
    <div class="charts-row" v-loading="loading">
      <div class="chart-card">
        <h4 class="chart-title">每日用量趋势</h4>
        <div ref="trendChartRef" class="chart-container"></div>
      </div>
      <div class="chart-card">
        <h4 class="chart-title">术数方法分布</h4>
        <div ref="methodChartRef" class="chart-container"></div>
      </div>
    </div>
    <div class="charts-row" v-loading="loading">
      <div class="chart-card">
        <h4 class="chart-title">Token 消耗</h4>
        <div ref="tokenChartRef" class="chart-container"></div>
      </div>
      <div class="chart-card">
        <h4 class="chart-title">缓存命中率</h4>
        <div ref="cacheChartRef" class="chart-container"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';
import { ElMessage } from 'element-plus';
import { getUsageStats } from '@/api/divination';

const loading = ref(false);
const dateRange = ref(null);
const methodFilter = ref('');

const methodOptions = [
  { value: 'liuyao', label: '六爻' },
  { value: 'meihua', label: '梅花易数' },
  { value: 'qimen', label: '奇门遁甲' },
  { value: 'liuren', label: '大六壬' },
];

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

  // 每日用量趋势
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['卦例数', '解读数', '拦截数'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value' },
    series: [
      { name: '卦例数', type: 'line', smooth: true, data: records.map((r) => r.recordCount || 0) },
      { name: '解读数', type: 'line', smooth: true, data: records.map((r) => r.interpretCount || 0) },
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
@import '@/assets/pages.css';

.stats-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 16px;
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
  margin-bottom: 16px;
}

.chart-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.chart-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.chart-container {
  width: 100%;
  height: 300px;
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
