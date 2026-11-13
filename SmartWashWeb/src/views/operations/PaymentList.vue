<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="支付ID">
          <el-input
            v-model.number="listQuery.paymentId"
            placeholder="输入支付ID"
            clearable
            style="width: 120px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="订单号">
          <el-input
            v-model="listQuery.orderNo"
            placeholder="输入订单号"
            clearable
            style="width: 180px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="手机号" :error="phoneError">
          <el-input
            v-model="listQuery.phoneNumber"
            placeholder="输入手机号"
            clearable
            style="width: 160px"
            @blur="validatePhone"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="支付方式">
          <el-select
            v-model="listQuery.paymentMethod"
            placeholder="全部方式"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option
              v-for="(desc, type) in payTypeOptions"
              :key="type"
              :label="desc"
              :value="type"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="支付状态">
          <el-select
            v-model="listQuery.status"
            placeholder="全部状态"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option
              v-for="(desc, status) in payStatusOptions"
              :key="status"
              :label="desc"
              :value="status"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="支付时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 380px"
            @change="handleSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <!-- 后端已摘除支付凭证删除入口，批量删除按钮一并移除（四端联动契约同步） -->
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="paymentList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column prop="paymentId" label="支付ID" min-width="90" class-name="col-subtle" />
        <el-table-column label="订单号" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.order?.orderNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="用户" min-width="150">
          <template #default="{ row }">
            {{ row.user?.phoneNumber || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="金额" min-width="120" align="right">
          <template #default="{ row }">
            <span class="pay-amount">￥{{ formatAmount(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="支付方式" min-width="120">
          <template #default="{ row }">
            {{ payTypeOptions[row.paymentMethod] || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="payStatusTagType(row.status)">
              {{ payStatusOptions[row.status] || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付时间" min-width="180" class-name="col-subtle">
          <template #default="{ row }">{{ formatTime(row.paidAt) }}</template>
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
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { getPayTypes, getPayStatus, getPaymentList } from '@/api/payment';
import { formatAmount, formatTime } from '@/utils/format';
import { payStatusTagType } from '@/constants/dict';
import { useTableList } from '@/composables/useTableList';
import { useTimeRange } from '@/composables/useTimeRange';
import logger from '@/utils/logger';

const payTypeOptions = ref({});
const payStatusOptions = ref({});

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: paymentList,
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
  fetchApi: getPaymentList,
  baseQuery: {
    paymentId: null,
    orderNo: '',
    phoneNumber: '',
    paymentMethod: null,
    status: null,
    startTime: null,
    endTime: null,
  },
  buildParams: (q) => ({
    ...q,
    paymentId: q.paymentId || undefined,
    paymentMethod: q.paymentMethod || undefined,
    status: q.status || undefined,
  }),
  errorMsg: '获取数据失败',
});

// 时间范围处理（评审 #14：抽离为公共 composable）
const timeRange = useTimeRange(listQuery);

// 初始化数据
onMounted(async () => {
  await fetchPayTypes();
  await fetchPayStatus();
  fetchData();
});

// 获取支付类型
const fetchPayTypes = async () => {
  try {
    const res = await getPayTypes();
    payTypeOptions.value = res;
  } catch (error) {
    // 传输层失败已由 http.js 拦截器统一留痕，这里归因到页面动作，避免静默失败
    logger.error('获取支付类型失败:', error);
    ElMessage.error('获取支付类型失败');
  }
};

// 获取支付状态
const fetchPayStatus = async () => {
  try {
    const res = await getPayStatus();
    payStatusOptions.value = res;
  } catch (error) {
    // 传输层失败已由 http.js 拦截器统一留痕，这里归因到页面动作，避免静默失败
    logger.error('获取支付状态失败:', error);
    ElMessage.error('获取支付状态失败');
  }
};

// 手机号验证：失焦时内联提示并保留输入（清空属静默丢弃用户输入，评审 P2）
const phoneError = ref('');
const validatePhone = () => {
  const phone = listQuery.phoneNumber;
  if (phone && !/^(?:\+86)?1[3-9]\d{9}$/.test(phone)) {
    phoneError.value = '手机号格式不正确，仅支持 11 位大陆手机号';
    return;
  }
  phoneError.value = '';
};
</script>
