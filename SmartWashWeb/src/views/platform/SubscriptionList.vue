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
        <div class="summary-card__head">
          <div class="summary-card__icon summary-card__icon--success">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <span class="summary-card__label">生效中</span>
        </div>
        <span class="summary-card__value">{{ formatThousands(summary.activeCount) }}</span>
      </div>
      <div class="summary-card__item">
        <div class="summary-card__head">
          <div class="summary-card__icon summary-card__icon--muted">
            <el-icon><Clock /></el-icon>
          </div>
          <span class="summary-card__label">已过期</span>
        </div>
        <span class="summary-card__value">{{ formatThousands(summary.expiredCount) }}</span>
      </div>
      <div class="summary-card__item">
        <div class="summary-card__head">
          <div class="summary-card__icon summary-card__icon--warning">
            <el-icon><AlarmClock /></el-icon>
          </div>
          <span class="summary-card__label">7 天内到期</span>
        </div>
        <span class="summary-card__value">{{ formatThousands(summary.expiringIn7Days) }}</span>
      </div>
      <p class="summary-card__note">统计范围为全部订阅，不随下方筛选变化</p>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="list"
        fit
        highlight-current-row
        empty-text="暂无订阅记录"
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
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <div class="status-cell">
              <el-tag :type="subscriptionStatusTagType(row.status)">
                {{ subscriptionStatusText(row.status) }}
              </el-tag>
              <!-- 生效中但下次续费日已过：弱化「已逾期」标识，与状态标签并存 -->
              <el-tag v-if="isOverdue(row)" type="warning" size="small" effect="plain">
                已逾期
              </el-tag>
            </div>
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
import dayjs from 'dayjs';
import { AlarmClock, CircleCheck, Clock } from '@element-plus/icons-vue';
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

// 已逾期：仍处生效中但下次续费日已早于今天（按「日」比较，与列表 YYYY-MM-DD 展示口径一致）
const isOverdue = (row) =>
  row.status === 'active' &&
  !!row.nextRenewDate &&
  dayjs(row.nextRenewDate).isBefore(dayjs(), 'day');

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
/* 汇总卡：对齐全站统计卡语言（白卡 12px 圆角 + 着地阴影 + 等宽数字），同 RechargeList */
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

/* 头行：小淡底图标与标签同行，26px 数值独占一行——对齐 Home 纵向堆叠统计卡 */
.summary-card__item {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.summary-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  flex-shrink: 0;
  font-size: 16px;
}

/* 语义收进图标淡底：生效=翡翠绿、已过期=中性、临期=警琥珀；数值保持全墨 */
.summary-card__icon--success {
  background: var(--success-soft-bg);
  color: var(--color-success);
}

.summary-card__icon--muted {
  background: var(--bg-subtle);
  color: var(--text-secondary);
}

.summary-card__icon--warning {
  background: var(--warning-soft-bg);
  color: var(--color-warning);
}

.summary-card__label {
  font-size: 13px;
  color: var(--text-secondary);
}

.summary-card__value {
  font-size: 26px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

/* 汇总口径说明：Micro 弱化文字，横跨整行置于卡内底部 */
.summary-card__note {
  grid-column: 1 / -1;
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary);
}

/* 状态列：状态标签 + 已逾期弱化标识换行排布 */
.status-cell {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
