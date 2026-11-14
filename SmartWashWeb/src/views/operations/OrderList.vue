<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="手机号">
          <el-input
            v-model="listQuery.phoneNumber"
            placeholder="请输入手机号"
            clearable
            style="width: 160px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="学校">
          <el-select
            v-model="listQuery.schoolName"
            placeholder="全部学校"
            filterable
            clearable
            style="width: 180px"
            @change="handleSearch"
          >
            <el-option
              v-for="school in schoolOptions"
              :key="school.schoolId"
              :label="school.schoolName"
              :value="school.schoolName"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="订单号">
          <el-input
            v-model="listQuery.orderNo"
            placeholder="请输入订单号"
            clearable
            style="width: 200px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="洗护套餐">
          <el-select
            v-model="listQuery.laundryItemsId"
            placeholder="全部套餐"
            filterable
            clearable
            style="width: 180px"
            @change="handleSearch"
          >
            <el-option
              v-for="item in laundryOptions"
              :key="item.itemId"
              :label="item.itemName"
              :value="item.itemId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="listQuery.status"
            placeholder="全部状态"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option
              v-for="(desc, code) in statusOptions"
              :key="code"
              :label="desc"
              :value="code"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="创建时间">
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
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="orderList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column prop="orderNo" label="订单号" min-width="200" show-overflow-tooltip />
        <el-table-column label="用户信息" min-width="150">
          <template #default="{ row }">
            {{ row.userVo?.phoneNumber || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="学校" min-width="180">
          <template #default="{ row }">
            {{ row.schoolsVo?.schoolName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="洗护套餐" min-width="180">
          <template #default="{ row }">
            {{ row.laundryPackageVo?.itemName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="总价" min-width="120" align="right">
          <template #default="{ row }">￥{{ formatAmount(row.totalPrice, '-') }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="orderStatusTagType(row.status)">
              {{ formatStatus(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pickupCode" label="取件码" min-width="150" class-name="col-code" />
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleUpdateStatus(row)"
              >修改状态</el-button
            >
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
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

    <!-- 修改状态对话框（资金相关操作弹窗禁止点遮罩误关） -->
    <el-dialog
      v-model="statusDialogVisible"
      title="修改订单状态"
      width="min(400px, 94vw)"
      :close-on-click-modal="false"
    >
      <el-form :model="statusForm" label-width="min(100px, 94vw)">
        <el-form-item label="订单号">
          <span>{{ statusForm.orderNo }}</span>
        </el-form-item>
        <el-form-item label="当前状态">
          <el-tag :type="orderStatusTagType(statusForm.currentStatus)">
            {{ formatStatus(statusForm.currentStatus) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="新状态">
          <el-select
            v-model="statusForm.newStatus"
            placeholder="请选择新状态"
            :disabled="isCurrentTerminal"
          >
            <el-option
              v-for="(desc, code) in allowedTargetOptions"
              :key="code"
              :label="desc"
              :value="code"
            />
          </el-select>
        </el-form-item>
        <!-- 取消已支付订单的确认区：展示实付金额，退款以此为准（空值显示 '-'） -->
        <el-form-item v-if="showRefundAmount" label="实付金额">
          <span class="refund-amount">{{ refundAmountText }}</span>
        </el-form-item>
        <el-form-item v-if="isCurrentTerminal" label=" ">
          <span class="status-warning">当前状态为终态（已完成/已退款/已取消），不可再流转</span>
        </el-form-item>
        <el-form-item v-else-if="isIrreversibleTarget" label=" ">
          <span class="status-warning danger">{{ irreversibleWarningText }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="statusDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="confirmUpdateStatus">
            确认
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { getOrderStatus, getOrderList, deleteOrder, updateOrderStatus } from '@/api/order';
import { schoolOptionsCache, laundryOptionsCache } from '@/utils/optionCache';
import { formatTime, formatAmount } from '@/utils/format';
import { orderStatusTagType } from '@/constants/dict';
import { useTableList } from '@/composables/useTableList';
import { useTimeRange } from '@/composables/useTimeRange';
import { useConfirm } from '@/composables/useConfirm';
import logger from '@/utils/logger';

// 数据
const statusOptions = ref({}); // 状态选项

// 订单状态机合法转移表（与后端 OrdersServiceImpl.VALID_TRANSITIONS + adminCancelOrder 取消链路对齐）。
// -1 已退款为退款链路内部产物：只能经「取消已支付订单」触达，前端不提供直达项；
// 终态 7 已完成 / -1 已退款 / -2 已取消不再流转。
// 此表仅在前端拦截误操作与倒退转移（如 已完成→待支付），最终裁决仍以后端为准；
// 四端联动评审后应收敛为后端下发合法转移表，前端只渲染。
const ORDER_ALLOWED_TRANSITIONS = {
  0: [1, -2], // 待支付 → 待寄件 / 取消（未支付直接取消，不触发退款）
  1: [2, -2], // 待寄件 → 已收取 / 取消（已支付，取消走退款链路）
  2: [3, -2], // 已收取 → 清洗中 / 取消（取消走退款链路）
  3: [4, 6, -2], // 清洗中 → 已烘干 / 待取件 / 取消
  4: [5, -2], // 已烘干 → 配送中 / 取消
  5: [6, -2], // 配送中 → 待取件 / 取消
  6: [7, -2], // 待取件 → 已完成 / 取消
  7: [],
  '-1': [],
  '-2': [],
};

// 弹窗内：当前状态为终态时禁止流转
const isCurrentTerminal = computed(
  () => (ORDER_ALLOWED_TRANSITIONS[statusForm.currentStatus] || []).length === 0
);
// 合法目标选项（筛选下拉仍用全量 statusOptions，此处只约束修改动作）
const allowedTargetOptions = computed(() => {
  const allowed = ORDER_ALLOWED_TRANSITIONS[statusForm.currentStatus];
  if (!allowed?.length) return {};
  return Object.fromEntries(
    Object.entries(statusOptions.value).filter(([code]) => allowed.includes(Number(code)))
  );
});
// 不可逆转移提示：目标为退款/取消终态（-1 已退款已收敛为取消链路内部产物，不再提供直达）
const isIrreversibleTarget = computed(() => ['-1', '-2'].includes(String(statusForm.newStatus)));
// 取消已支付订单的确认区需展示实付金额；待支付（0）无资金动作，不展示
const showRefundAmount = computed(
  () => String(statusForm.newStatus) === '-2' && Number(statusForm.currentStatus) !== 0
);
// 实付金额展示：空值（老数据/无支付流水）输出 '-'，避免 ￥- 误导
const refundAmountText = computed(() =>
  statusForm.payPrice == null ? '-' : `￥${formatAmount(statusForm.payPrice)}`
);
// 不可逆提示文案（与后端 adminCancelOrder 语义对齐）：待支付订单取消无资金动作；
// 已支付订单取消走退款链路——按实付金额退款至用户余额并还原所用优惠券
const irreversibleWarningText = computed(() =>
  Number(statusForm.currentStatus) === 0
    ? '目标状态为取消（终态），操作不可逆，请确认后再执行'
    : `取消已支付订单将按实付金额 ${refundAmountText.value} 退款至用户余额并还原所用优惠券，操作不可逆，请核对资金状态后再执行`
);
const schoolOptions = ref([]); // 学校选项
const laundryOptions = ref([]); // 洗护套餐选项

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: orderList,
  total,
  listLoading,
  listQuery,
  pageSizes,
  fetchData,
  fetchDataAfterDelete,
  handleSearch,
  resetSearch,
  handlePageChange,
  handleSizeChange,
} = useTableList({
  fetchApi: getOrderList,
  baseQuery: {
    phoneNumber: '',
    schoolName: '',
    orderNo: '',
    laundryItemsId: null,
    status: null,
    startTime: null,
    endTime: null,
  },
  buildParams: (q) => ({
    ...q,
    laundryItemsId: q.laundryItemsId || undefined,
    status: q.status || undefined,
  }),
  errorMsg: '获取数据失败',
});

// 时间范围处理（评审 #14：抽离为公共 composable）
const timeRange = useTimeRange(listQuery);

// 状态更新相关
const statusDialogVisible = ref(false);
const submitting = ref(false); // 确认按钮 loading，防连点
const statusForm = reactive({
  orderId: null,
  orderNo: '',
  currentStatus: '',
  newStatus: '',
  payPrice: null,
});
// 初始化数据
onMounted(async () => {
  await fetchStatus();
  await fetchSchools();
  await fetchLaundry();
  fetchData();
});

// 获取状态枚举
const fetchStatus = async () => {
  try {
    const res = await getOrderStatus();
    statusOptions.value = res;
  } catch (error) {
    // 传输层失败已由 http.js 拦截器统一留痕，这里归因到页面动作，避免静默失败
    logger.error('获取状态失败:', error);
    ElMessage.error('获取状态失败');
  }
};

// 获取学校列表（模块级缓存：多页面共用一份全量数据，评审 #15）
const fetchSchools = async () => {
  try {
    schoolOptions.value = await schoolOptionsCache.load();
  } catch (error) {
    // 传输层失败已由 http.js 拦截器统一留痕，这里归因到页面动作，避免静默失败
    logger.error('获取学校列表失败:', error);
    ElMessage.error('获取学校列表失败');
  }
};

// 获取洗护套餐列表（模块级缓存，评审 #15）
const fetchLaundry = async () => {
  try {
    laundryOptions.value = await laundryOptionsCache.load();
  } catch (error) {
    // 传输层失败已由 http.js 拦截器统一留痕，这里归因到页面动作，避免静默失败
    logger.error('获取洗护套餐失败:', error);
    ElMessage.error('获取洗护套餐失败');
  }
};

// 删除订单
const handleDelete = async (row) => {
  // 确认弹窗：列明订单号与金额，明确不可恢复（订单含支付金额，删除即资金对账盲区；
  // 后端为最终裁决，四端联动评审后建议改为作废语义）
  const confirmed = await useConfirm(
    `确认删除订单 ${row.orderNo}（金额 ￥${formatAmount(row.totalPrice, '-')}）吗？删除后不可恢复，如需留痕请改用状态流转或联系开发确认。`,
    '删除订单'
  );
  if (!confirmed) return;
  try {
    await deleteOrder(row.orderId);
    ElMessage.success('删除成功');
    // 管理端写操作成功留痕（低频关键动作），业务键仅 orderId/orderNo，不含用户手机号等敏感字段
    logger.info('删除订单成功', `orderId=${row.orderId}`, `orderNo=${row.orderNo}`);
    fetchDataAfterDelete();
  } catch (error) {
    logger.error('删除订单失败:', `orderId=${row.orderId}`, `orderNo=${row.orderNo}`, error);
    ElMessage.error(error.message || '删除失败');
  }
};

// 打开修改状态对话框
const handleUpdateStatus = (row) => {
  statusForm.orderId = row.orderId;
  statusForm.orderNo = row.orderNo;
  statusForm.currentStatus = row.status;
  statusForm.newStatus = '';
  statusForm.payPrice = row.payPrice ?? null;
  statusDialogVisible.value = true;
};

// 确认更新状态
const confirmUpdateStatus = async () => {
  if (!statusForm.newStatus) {
    ElMessage.warning('请选择新状态');
    return;
  }
  // 兜底校验：目标不在合法转移表内（含终态再流转/倒退转移）直接拦截，不发起请求
  const allowed = ORDER_ALLOWED_TRANSITIONS[statusForm.currentStatus] || [];
  if (!allowed.includes(Number(statusForm.newStatus))) {
    ElMessage.error('目标状态与当前状态不符合法转移，请重新选择');
    return;
  }

  submitting.value = true;
  try {
    await updateOrderStatus({
      orderId: statusForm.orderId,
      status: statusForm.newStatus,
    });
    ElMessage.success('状态更新成功');
    // 管理端写操作成功留痕（低频关键动作），业务键仅 orderId/orderNo 与目标状态
    logger.info(
      '订单状态更新成功',
      `orderId=${statusForm.orderId}`,
      `orderNo=${statusForm.orderNo}`,
      `status=${statusForm.newStatus}`
    );
    statusDialogVisible.value = false;
    fetchData();
  } catch (error) {
    logger.error(
      '订单状态更新失败:',
      `orderId=${statusForm.orderId}`,
      `orderNo=${statusForm.orderNo}`,
      error
    );
    ElMessage.error(error.message || '状态更新失败');
  } finally {
    submitting.value = false;
  }
};

// 状态文本转换
const formatStatus = (status) => {
  return statusOptions.value[status] || '未知状态';
};
</script>

<style scoped>
/* 实付金额：金额列语言（600 字重 + 等宽数字），与退款文案形成资金锚点 */
.refund-amount {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
}

/* 状态弹窗警示文案：终态/不可逆提示走语义文字色，不做实心色块（淡彩底规则） */
.status-warning {
  font-size: 13px;
  color: var(--text-secondary);
}

.status-warning.danger {
  color: var(--color-danger);
}

/* impeccable generate 2ea823a7: accepted variant 1 */
/* v1 微细节：取件码为机器短码，mono + nowrap 防断码 */
div.table-card .el-table .col-code .cell {
  font-family: var(--font-mono);
  font-size: 13px;
  white-space: nowrap;
}
</style>
