<template>
  <div class="page-container">
    <!-- 筛选区域 -->
    <div class="filter-container">
      <el-form :inline="true" @keyup.enter="fetchReport">
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            :clearable="false"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="fetchReport">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 汇总卡：记录数 / 参与用户数 -->
    <div class="summary-card" v-loading="loading">
      <div class="summary-card__item">
        <span class="summary-card__label">记录数</span>
        <span class="summary-card__value">{{ formatThousands(summary.recordCount) }}</span>
      </div>
      <div class="summary-card__item">
        <span class="summary-card__label">参与用户数</span>
        <span class="summary-card__value">{{ formatThousands(summary.userCount) }}</span>
      </div>
    </div>

    <!-- 图表区：情绪分布（柱图）+ 按日记录数（折线，后端已补零） -->
    <div class="charts-grid" v-loading="loading">
      <div class="chart-card">
        <h4 class="chart-card__title">情绪分布</h4>
        <TrendChart type="bar" :data="emotionData" />
      </div>
      <div class="chart-card">
        <h4 class="chart-card__title">按日记录数</h4>
        <TrendChart type="line" :data="trendData" color="var(--color-info)" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';
import TrendChart from '@/components/TrendChart.vue';
import { getMoodSummary } from '@/api/mood';
import { formatThousands } from '@/utils/format';
import { moodLevelText } from '@/constants/dict';
import logger from '@/utils/logger';

// 默认近 30 天（含今天），与充值记录页口径一致
const DEFAULT_RANGE = () => [
  dayjs().subtract(29, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
];
const dateRange = ref(DEFAULT_RANGE());

const loading = ref(false);
const summary = reactive({
  recordCount: 0,
  userCount: 0,
});
const emotionData = ref([]);
const trendData = ref([]);

const buildRangeParams = () => ({
  dateFrom: dateRange.value?.[0],
  dateTo: dateRange.value?.[1],
});

const fetchReport = async () => {
  loading.value = true;
  try {
    const data = (await getMoodSummary(buildRangeParams())) || {};
    summary.recordCount = Number(data.recordCount) || 0;
    summary.userCount = Number(data.userCount) || 0;
    // 防御性渲染：情绪维度情绪值兼容 emotion/moodLevel/level 命名差异，未知值回显原值
    const emotionRows = Array.isArray(data.byEmotion) ? data.byEmotion : [];
    emotionData.value = emotionRows.map((it) => ({
      date: moodLevelText(it.emotion ?? it.moodLevel ?? it.level),
      value: Number(it.count ?? it.recordCount) || 0,
    }));
    // 按日序列：契约 byDate 连续补零，直接映射
    const dateRows = Array.isArray(data.byDate) ? data.byDate : [];
    trendData.value = dateRows.map((it) => ({
      date: it.date,
      value: Number(it.count ?? it.recordCount) || 0,
    }));
  } catch (error) {
    logger.error('心情报表加载失败:', error);
    ElMessage.error(error.message || '获取心情报表失败');
  } finally {
    loading.value = false;
  }
};

// 重置：恢复默认近 30 天后重新拉取
const resetFilter = () => {
  dateRange.value = DEFAULT_RANGE();
  fetchReport();
};

onMounted(fetchReport);
</script>

<style scoped>
/* 汇总卡：复用统计卡视觉语言（白卡 12px 圆角 + 着地阴影 + 等宽数字），同 RechargeList */
.summary-card {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  padding: 20px 24px;
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-ground);
}

.summary-card__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.summary-card__label {
  font-size: 13px;
  color: var(--text-secondary);
}

.summary-card__value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

/* 图表区：窄屏单列，≥900px 双列（与全局断点一致） */
.charts-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

@media (min-width: 900px) {
  .charts-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.chart-card {
  padding: 20px 24px;
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-ground);
}

.chart-card__title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
</style>
