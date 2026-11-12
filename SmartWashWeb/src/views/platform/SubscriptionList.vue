<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="状态">
          <el-select
            v-model="listQuery.status"
            placeholder="全部状态"
            clearable
            style="width: 130px"
            @change="handleSearch"
          >
            <el-option
              v-for="s in statusOptions"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
            @change="handleSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 订阅汇总（无参数，仅首载拉取一次） -->
    <div class="summary-card" v-loading="summaryLoading">
      <div class="summary-card__item">
        <span class="summary-card__label">生效中</span>
        <span class="summary-card__value summary-card__value--success">
          {{ formatThousands(summary.activeCount) }}
        </span>
      </div>
      <div class="summary-card__item">
        <span class="summary-card__label">已过期</span>
        <span class="summary-card__value">{{ formatThousands(summary.expiredCount) }}</span>
      </div>
      <div class="summary-card__item">
        <span class="summary-card__label">7 天内到期</span>
        <span class="summary-card__value summary-card__value--warning">
          {{ formatThousands(summary.expiringIn7Days) }}
        </span>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="list"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column label="订阅ID" min-width="90">
          <template #default="{ row }">{{ row.subscriptionId ?? row.id ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="用户手机号" min-width="120">
          <template #default="{ row }">{{
            row.userPhone ?? row.users?.phoneNumber ?? '-'
          }}</template>
        </el-table-column>
        <el-table-column label="订阅名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="每期金额" min-width="110" align="right">
          <template #default="{ row }">￥{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="计费周期" min-width="90">
          <template #default="{ row }">
            {{ subscriptionCycleText(row.cycleType, row.cycleInterval) }}
          </template>
        </el-table-column>
        <el-table-column label="自动续费" min-width="90">
          <template #default="{ row }">
            <el-tag :type="Number(row.autoRenew) === 1 ? 'success' : 'info'" size="small">
              {{ subscriptionAutoRenewText(row.autoRenew) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <el-tag :type="subscriptionStatusTagType(row.status)">
              {{ subscriptionStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下次续费日" min-width="110">
          <template #default="{ row }">{{ formatTime(row.nextRenewDate, 'YYYY-MM-DD') }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
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
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getSubscriptionList, getSubscriptionSummary } from '@/api/subscription';
import { formatAmount, formatTime, formatThousands } from '@/utils/format';
import {
  SUBSCRIPTION_STATUS_OPTIONS,
  subscriptionAutoRenewText,
  subscriptionCycleText,
  subscriptionStatusTagType,
  subscriptionStatusText,
} from '@/constants/dict';
import { useTableList } from '@/composables/useTableList';
import { useTimeRange } from '@/composables/useTimeRange';
import logger from '@/utils/logger';

const statusOptions = SUBSCRIPTION_STATUS_OPTIONS;

// 列表查询：分页/搜索/翻页统一由 useTableList 承载。
// 后端 AdminSearchSubscriptionFrom 继承 BaseSearchFrom（page/size），与仓库其他列表分页参数一致
const {
  list,
  total,
  listLoading,
  listQuery,
  pageSizes,
  fetchData,
  handleSearch,
  resetSearch,
  handlePageChange,
  handleSizeChange,
} = useTableList({
  fetchApi: getSubscriptionList,
  baseQuery: {
    status: null,
    dateFrom: null,
    dateTo: null,
  },
  buildParams: (q) => ({
    ...q,
    status: q.status || undefined,
    dateFrom: q.dateFrom || undefined,
    dateTo: q.dateTo || undefined,
  }),
  errorMsg: '获取订阅列表失败',
});

// 日期范围双向绑定（dateFrom/dateTo 直传 yyyy-MM-dd，后端 DATE 字段无需时分秒边界）
const dateRange = useTimeRange(listQuery, 'dateFrom', 'dateTo');

// ---------- 订阅汇总 ----------
const summary = reactive({
  activeCount: 0,
  expiredCount: 0,
  expiringIn7Days: 0,
});
const summaryLoading = ref(false);

const fetchSummary = async () => {
  summaryLoading.value = true;
  try {
    const data = (await getSubscriptionSummary()) || {};
    summary.activeCount = Number(data.activeCount) || 0;
    summary.expiredCount = Number(data.expiredCount) || 0;
    summary.expiringIn7Days = Number(data.expiringIn7Days) || 0;
  } catch (error) {
    logger.error('订阅汇总加载失败:', error);
    ElMessage.error(error.message || '获取订阅汇总失败');
  } finally {
    summaryLoading.value = false;
  }
};

onMounted(() => {
  fetchSummary();
  fetchData();
});
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

@media (min-width: 900px) {
  .summary-card {
    grid-template-columns: repeat(3, 1fr);
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

/* 语义点缀：生效走翡翠绿、临期走警琥珀（裸数值同色系深字，符合淡彩底规则的外延） */
.summary-card__value--success {
  color: var(--color-success);
}

.summary-card__value--warning {
  color: var(--color-warning);
}
</style>
