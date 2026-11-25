<template>
  <div class="page-container">
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

    <div class="table-card">
      <!-- 加载失败态：与空态区分，给出重试入口 -->
      <div v-if="listError" class="list-error">
        <span>充值记录列表加载失败，请检查网络后重试</span>
        <el-button size="small" @click="fetchData">重试</el-button>
      </div>
      <el-table
        v-else
        v-loading="listLoading"
        :data="rechargeList"
        fit
        highlight-current-row
        :empty-text="
          hasRechargeFilter ? '试试调整筛选条件' : '还没有充值记录，用户充值后将在此展示'
        "
      >
        <el-table-column prop="recordId" label="记录ID" min-width="100" />
        <el-table-column label="手机号" min-width="120">
          <template #default="{ row }">{{ row.users?.phoneNumber || '-' }}</template>
        </el-table-column>
        <el-table-column label="充值金额" min-width="150" align="right">
          <template #default="{ row }">{{ formatPayAmount(row.amount) }}</template>
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
import { friendlyErrorMessage } from '@/utils/errors';
import { onMounted, ref, reactive, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { Download } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import { getRechargeList, getRechargeSummary, exportRechargeRecords } from '@/api/recharge';
import { formatTime, formatThousands, formatAmount, formatPayAmount } from '@/utils/format';
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

const {
  list: rechargeList,
  total,
  listLoading,
  listError,
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

// 空态区分：有筛选且库内确有命中时提示调整条件；库空（total=0）即使用户带了筛选也一律展示记录来源说明。
// 日期范围默认近 30 天（常驻筛选），仅当用户改动过才计入「有筛选」，否则空库时会被常驻范围误判成筛选态
const hasRechargeFilter = computed(() => {
  const defaultRange = DEFAULT_RANGE();
  const rangeChanged =
    dateRange.value?.[0] !== defaultRange[0] || dateRange.value?.[1] !== defaultRange[1];
  return (
    (Boolean(listQuery.recordId) ||
      Boolean((listQuery.phoneNumber || '').trim()) ||
      Boolean(listQuery.amount) ||
      Boolean(listQuery.rechargeType) ||
      rangeChanged) &&
    total.value > 0
  );
});

// 列表接口沿用 startTime/endTime：日期换算成全天时间边界
const syncRangeToQuery = () => {
  listQuery.startTime = dateRange.value?.[0] ? `${dateRange.value[0]} 00:00:00` : null;
  listQuery.endTime = dateRange.value?.[1] ? `${dateRange.value[1]} 23:59:59` : null;
};

// 汇总/导出共用的日期范围参数（dateFrom/dateTo，yyyy-MM-dd HH:mm:ss；清空则不限定）
// 后端 RechargeDateRangeFrom 按 yyyy-MM-dd HH:mm:ss 解析，此前只传日期导致 400；
// 补全时间与列表筛选 syncRangeToQuery 同口径（含当天：起 00:00:00 / 止 23:59:59）
const buildRangeParams = () => {
  const params = {};
  if (dateRange.value?.[0]) {
    params.dateFrom = `${dateRange.value[0]} 00:00:00`;
  }
  if (dateRange.value?.[1]) {
    params.dateTo = `${dateRange.value[1]} 23:59:59`;
  }
  return params;
};

const summary = reactive({
  totalCount: 0,
  successCount: 0,
  totalAmount: 0,
  successAmount: 0,
});
const summaryLoading = ref(false);
// 竞态守卫：快速连改日期范围时旧区间响应后到会覆盖新汇总（列表侧 useTableList 已有同款）
let summarySeq = 0;

const fetchSummary = async () => {
  const mySeq = ++summarySeq;
  summaryLoading.value = true;
  try {
    const data = await getRechargeSummary(buildRangeParams());
    if (mySeq !== summarySeq) return;
    Object.assign(summary, data);
  } catch (error) {
    if (mySeq !== summarySeq) return;
    logger.error('充值汇总加载失败:', error);
    ElMessage.error(friendlyErrorMessage(error, '获取充值汇总失败'));
  } finally {
    // 被丢弃的旧响应不得关掉最新请求的 loading
    if (mySeq === summarySeq) {
      summaryLoading.value = false;
    }
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

const exporting = ref(false);

const handleExport = async () => {
  exporting.value = true;
  try {
    // 导出与列表所见一致：日期范围 + 当前业务筛选（记录ID/手机号/金额/充值类型）
    const params = {
      ...buildRangeParams(),
      recordId: listQuery.recordId ?? undefined,
      phoneNumber: listQuery.phoneNumber ?? undefined,
      amount: listQuery.amount ?? undefined,
      rechargeType: listQuery.rechargeType ?? undefined,
    };
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
    ElMessage.error(friendlyErrorMessage(error, '导出失败，请稍后重试'));
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

/* KPI 项之间 1px 分隔线：从松散均布改为利落分栏（窄屏两列布局下取消） */
@media (min-width: 900px) {
  .summary-card__item + .summary-card__item {
    padding-left: 16px;
    border-left: 1px solid var(--border-subtle);
  }
}

.summary-card__label {
  font-size: 13px;
  color: var(--text-secondary);
}

.summary-card__value {
  /* 对齐 DESIGN.md Stat 规格 24px：与首页统计卡同类数值卡同语言 */
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

/* 成功语义走翡翠绿：淡彩底规则不适用于裸数值，仅同色系深字点缀 */
.summary-card__value--success {
  color: var(--color-success);
}
</style>
