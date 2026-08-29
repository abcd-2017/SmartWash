<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="手机号">
          <el-input
            v-model="listQuery.phoneNumber"
            placeholder="输入手机号"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="优惠券">
          <el-select
            v-model="listQuery.couponId"
            placeholder="选择优惠券"
            filterable
            clearable
            style="width: 220px"
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
    >
      <el-table-column prop="userCouponId" label="记录ID" min-width="100" />
      <el-table-column prop="phoneNumber" label="手机号" min-width="140" />
      <el-table-column prop="couponTitle" label="优惠券名称" min-width="180">
        <template #default="{ row }">{{ row.couponVo?.title || '-' }}</template>
      </el-table-column>
      <el-table-column label="使用状态" min-width="100">
        <template #default="{ row }">
          <el-tag :type="row.isUsed ? 'success' : 'info'">
            {{ row.isUsed ? "已使用" : "未使用" }}
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
      <el-table-column prop="orderNo" label="订单号" min-width="200" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="danger" @click="handleDelete(row)"
            >删除</el-button
          >
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
import { ref, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { getUserCouponList, deleteUserCoupon } from "@/api/userCoupon";
import { getCouponList } from "@/api/coupon";
import { formatTime } from "@/utils/format";
import { useTableList } from "@/composables/useTableList";
import { useConfirm } from "@/composables/useConfirm";

const couponOptions = ref([]); // 优惠券选项

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: couponList,
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
  fetchApi: getUserCouponList,
  baseQuery: {
    phoneNumber: "",
    couponId: null,
  },
  buildParams: (q) => ({
    ...q,
    couponId: q.couponId || undefined,
  }),
  errorMsg: "获取数据失败",
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
    ElMessage.error("获取优惠券列表失败");
  }
};

// 删除记录
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除该领取记录吗？`);
  if (!confirmed) return;
  try {
    await deleteUserCoupon(row.userCouponId);
    ElMessage.success("删除成功");
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || "删除失败");
  }
};
</script>
  
  <style scoped>
@import '@/assets/pages.css';
</style>