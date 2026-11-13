<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="记录ID">
          <el-input
            v-model.number="listQuery.recordId"
            placeholder="输入记录ID"
            clearable
            style="width: 120px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="手机号">
          <el-input
            v-model="listQuery.phoneNumber"
            placeholder="输入用户手机号"
            clearable
            style="width: 120px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="金额">
          <el-input-number
            v-model="listQuery.amount"
            :precision="2"
            :min="0"
            controls-position="right"
            placeholder="输入金额"
            style="width: 150px"
          />
        </el-form-item>

        <el-form-item label="充值类型">
          <el-select
            v-model="listQuery.rechargeType"
            placeholder="全部类型"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option
              v-for="type in rechargeTypeOptions"
              :key="type.value"
              :label="type.label"
              :value="type.value"
            />
          </el-select>
        </el-form-item>

        <!-- 日期范围：汇总卡与列表共用（变更即联动刷新），列表按天边界换算成起止时间 -->
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
            @change="handleRangeChange"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button :loading="exporting" :icon="Download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 充值汇总（与列表共用同一日期范围） -->
    <div class="summary-card" v-loading="summaryLoading">
      <div class="summary-card__item">
        <span class="summary-card__label">总笔数</span>
        <span class="summary-card__value">{{ formatThousands(summary.totalCount) }}</span>
      </div>
      <div class="summary-card__item">
        <span class="summary-card__label">成功笔数</span>
        <span class="summary-card__value summary-card__value--success">
          {{ formatThousands(summary.successCount) }}
        </span>
      </div>
      <div class="summary-card__item">
        <span class="summary-card__label">总金额</span>
        <span class="summary-card__value">￥{{ formatAmount(summary.totalAmount) }}</span>
      </div>
      <div class="summary-card__item">
        <span class="summary-card__label">成功金额</span>
        <span class="summary-card__value summary-card__value--success">
          ￥{{ formatAmount(summary.successAmount) }}
        </span>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="rechargeList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column prop="recordId" label="记录ID" min-width="100" />
        <el-table-column label="手机号" min-width="120">
          <template #default="{ row }">{{ row.users?.phoneNumber || '-' }}</template>
        </el-table-column>
        <el-table-column label="充值金额" min-width="150" align="right">
          <template #default="{ row }">￥{{ row.amount?.toFixed(2) || '0.00' }}</template>
        </el-table-column>
        <el-table-column label="充值类型" min-width="120">
          <template #default="{ row }">
            <el-tag :type="rechargeTypeTagType(row.rechargeType)">
              {{ rechargeTypeText(row.rechargeType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="rechargeStatusTagType(row.status)">
              {{ rechargeStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="充值时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.rechargeTime) }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          background
          :current-page="listQuery.page"
          :page-size="listQuery.size"
          :page-sizes="pageSizes"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import { Download } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import { getRechargeList, getRechargeSummary, exportRechargeRecords } from '@/api/recharge';
import { formatTime, formatThousands, formatAmount } from '@/utils/format';
import { saveBlobAs } from '@/utils/download';
import {
  RECHARGE_TYPE_OPTIONS,
  rechargeTypeText,
  rechargeTypeTagType,
  rechargeStatusText,
  rechargeStatusTagType,
} from '@/constants/dict';
import { useTableList } from '@/composables/useTableList';
import logger from '@/utils/logger';

// 充值类型选项（枚举字典统一维护，评审 #16）
const rechargeTypeOptions = RECHARGE_TYPE_OPTIONS;

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: rechargeList,
  total,
  listLoading,
  listQuery,
  pageSizes,
  fetchData,
  handleSearch,
  handlePageChange,
  handleSizeChange,
} = useTableList({
  fetchApi: getRechargeList,
  baseQuery: {
    recordId: null,
    phoneNumber: null,
    amount: null,
    rechargeType: null,
    startTime: null,
    endTime: null,
  },
  buildParams: (q) => ({
    ...q,
    recordId: q.recordId || undefined,
    amount: q.amount || undefined,
    rechargeType: q.rechargeType || undefined,
  }),
  errorMsg: '获取数据失败',
});

// ---------- 共享日期范围（汇总 + 列表 + 导出三处同一数据源） ----------
// 默认近 30 天（含今天）
const DEFAULT_RANGE = () => [
  dayjs().subtract(29, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
];
const dateRange = ref(DEFAULT_RANGE());

// 列表接口沿用 startTime/endTime：日期换算成全天时间边界
const syncRangeToQuery = () => {
  listQuery.startTime = dateRange.value?.[0] ? `${dateRange.value[0]} 00:00:00` : null;
  listQuery.endTime = dateRange.value?.[1] ? `${dateRange.value[1]} 23:59:59` : null;
};

// 汇总/导出共用的日期范围参数（dateFrom/dateTo，yyyy-MM-dd；清空则不限定）
const buildRangeParams = () => {
  const params = {};
  if (dateRange.value?.[0]) {
    params.dateFrom = dateRange.value[0];
  }
  if (dateRange.value?.[1]) {
    params.dateTo = dateRange.value[1];
  }
  return params;
};

// ---------- 充值汇总 ----------
const summary = reactive({
  totalCount: 0,
  successCount: 0,
  totalAmount: 0,
  successAmount: 0,
});
const summaryLoading = ref(false);

const fetchSummary = async () => {
  summaryLoading.value = true;
  try {
    const data = await getRechargeSummary(buildRangeParams());
    Object.assign(summary, data);
  } catch (error) {
    logger.error('充值汇总加载失败:', error);
    ElMessage.error(error.message || '获取充值汇总失败');
  } finally {
    summaryLoading.value = false;
  }
};

// 日期范围变更：汇总与列表联动刷新，列表回到第一页
const handleRangeChange = () => {
  syncRangeToQuery();
  listQuery.page = 1;
  fetchSummary();
  fetchData();
};

// 重置：业务筛选字段恢复初值 + 日期范围恢复默认近 30 天，统一刷新
const resetSearch = () => {
  listQuery.recordId = null;
  listQuery.phoneNumber = null;
  listQuery.amount = null;
  listQuery.rechargeType = null;
  listQuery.page = 1;
  dateRange.value = DEFAULT_RANGE();
  syncRangeToQuery();
  fetchSummary();
  fetchData();
};

// ---------- 导出 ----------
const exporting = ref(false);

const handleExport = async () => {
  exporting.value = true;
  try {
    const params = buildRangeParams();
    const blob = await exportRechargeRecords(params);
    // 文件名含秒级时间戳，避免同名文件互相覆盖
    saveBlobAs(blob, `充值记录_${dayjs().format('YYYYMMDDHHmmss')}.xlsx`);
    ElMessage.success('导出成功');
    logger.info(
      '充值记录导出成功',
      `dateFrom=${params.dateFrom ?? '-'}`,
      `dateTo=${params.dateTo ?? '-'}`
    );
  } catch (error) {
    logger.error('充值记录导出失败:', error);
    ElMessage.error(error.message || '导出失败，请稍后重试');
  } finally {
    exporting.value = false;
  }
};

onMounted(() => {
  syncRangeToQuery();
  fetchData();
  fetchSummary();
});
</script>

<style scoped>
/* 汇总卡：复用统计卡视觉语言（白卡 12px 圆角 + 着地阴影 + 等宽数字），
   与列表共用日期范围，随范围变更联动刷新 */
.summary-card {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  padding: 20px 24px;
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-ground);
}

@media (min-width: 900px) {
  .summary-card {
    grid-template-columns: repeat(4, 1fr);
  }
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

/* 成功语义走翡翠绿：淡彩底规则不适用于裸数值，仅同色系深字点缀 */
.summary-card__value--success {
  color: var(--color-success);
}
</style>
