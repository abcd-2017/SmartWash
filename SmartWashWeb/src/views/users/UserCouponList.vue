<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="手机号">
          <el-input
            v-model="listQuery.phoneNumber"
            placeholder="输入手机号"
            clearable
            style="width: 160px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="优惠券">
          <el-select
            v-model="listQuery.couponId"
            placeholder="选择优惠券"
            filterable
            clearable
            style="width: 220px"
            @change="handleSearch"
          >
            <el-option
              v-for="coupon in couponOptions"
              :key="coupon.couponId"
              :label="coupon.title"
              :value="coupon.couponId"
            />
          </el-select>
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
        :data="couponList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column prop="userCouponId" label="记录ID" min-width="100" />
        <el-table-column prop="phoneNumber" label="手机号" min-width="140" />
        <el-table-column prop="couponTitle" label="优惠券名称" min-width="180">
          <template #default="{ row }">{{ row.couponVo?.title || '-' }}</template>
        </el-table-column>
        <el-table-column label="使用状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="userCouponUsedTagType(row.isUsed)">
              {{ userCouponUsedText(row.isUsed) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="领取时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.receivedAt) }}</template>
        </el-table-column>
        <el-table-column label="过期时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.expiredAt) }}</template>
        </el-table-column>
        <el-table-column label="使用时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.usedAt) }}</template>
        </el-table-column>
        <el-table-column label="订单号" min-width="200">
          <template #default="{ row }">
            <!-- 订单号机器标识符(live 打磨 v2):mono + 弱底 chip,与短码/Host 列同一语言;
                 空值输出纯文本占位,不渲染空 chip -->
            <span v-if="row.orderNo" class="order-no-cell" :title="row.orderNo">
              {{ row.orderNo }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              size="small"
              type="danger"
              :loading="deletingId === row.userCouponId"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { getUserCouponList, deleteUserCoupon } from '@/api/userCoupon';
import { getCouponList } from '@/api/coupon';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';
import { userCouponUsedTagType, userCouponUsedText } from '@/constants/dict';
import logger from '@/utils/logger';

const couponOptions = ref([]); // 优惠券选项
const deletingId = ref(null); // 行内「删除」按钮 loading，防连点

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: couponList,
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
  fetchApi: getUserCouponList,
  baseQuery: {
    phoneNumber: '',
    couponId: null,
  },
  buildParams: (q) => ({
    ...q,
    couponId: q.couponId || undefined,
  }),
  errorMsg: '获取数据失败',
});

// 初始化数据
onMounted(async () => {
  await fetchCoupons();
  fetchData();
});

// 获取优惠券列表
const fetchCoupons = async () => {
  try {
    const res = await getCouponList();
    couponOptions.value = res.records;
  } catch (error) {
    // 传输层失败已由 http.js 拦截器统一留痕，这里归因到页面动作，避免静默失败
    logger.error('获取优惠券列表失败:', error);
    ElMessage.error('获取优惠券列表失败');
  }
};

// 删除记录
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）；
  // 文案带对象标识（券名 + 手机号），误删时无从核对的问题收敛
  const confirmed = await useConfirm(
    `确认删除领取记录吗？持有人 ${row.phoneNumber || '-'}，券「${row.couponVo?.title || '-'}」删除后不可恢复。`,
    '删除领取记录'
  );
  if (!confirmed) return;
  deletingId.value = row.userCouponId;
  try {
    await deleteUserCoupon(row.userCouponId);
    ElMessage.success('删除成功');
    // 写操作成功留痕：业务键仅 userCouponId，手机号不入日志（敏感红线）
    logger.info('删除领取记录成功', `userCouponId=${row.userCouponId}`);
    fetchDataAfterDelete();
  } catch (error) {
    logger.error('删除领取记录失败:', `userCouponId=${row.userCouponId}`, error);
    ElMessage.error(error.message || '删除失败');
  } finally {
    deletingId.value = null;
  }
};
</script>

<style scoped>
/* 订单号机器标识符(live 打磨 v2):mono + 弱底 chip,与短码/Host 列同一语言;
   对齐 DESIGN.md Mono 13px/400,长单号溢出省略并经 title 悬浮看全文 */
.order-no-cell {
  display: inline-block;
  max-width: 100%;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--border-subtle);
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 400;
  color: var(--text-input);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
