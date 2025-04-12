<template>
  <div class="coupon-container">
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
    <el-table
      v-loading="listLoading"
      :data="couponList"
      border
      fit
      highlight-current-row
    >
      <el-table-column prop="userCouponId" label="记录ID" width="100" />
      <el-table-column prop="phoneNumber" label="手机号" width="140" />
      <el-table-column prop="couponTitle" label="优惠券名称" min-width="180">
        <template #default="{ row }">{{ row.couponVo.title }}</template>
      </el-table-column>
      <el-table-column label="使用状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isUsed ? 'success' : 'info'">
            {{ row.isUsed ? "已使用" : "未使用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="领取时间" width="160">
        <template #default="{ row }">{{ formatTime(row.receivedAt) }}</template>
      </el-table-column>
      <el-table-column label="过期时间" width="160">
        <template #default="{ row }">{{ formatTime(row.expiredAt) }}</template>
      </el-table-column>
      <el-table-column label="使用时间" width="160">
        <template #default="{ row }">{{ formatTime(row.usedAt) }}</template>
      </el-table-column>
      <el-table-column prop="orderNo" label="订单号" width="200" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="danger" @click="handleDelete(row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      background
      :current-page="listQuery.page"
      layout="prev, pager, next"
      :total="total"
      :page-size="listQuery.size"
      @current-change="handlePageChange"
    />
  </div>
</template>
  
  <script setup>
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import dayjs from "dayjs";
import { getUserCouponList, deleteUserCoupon } from "@/api/userCoupon";
import { getCouponList } from "@/api/coupon";

const couponOptions = ref([]); // 优惠券选项
const couponList = ref([]); // 数据列表
const total = ref(0); // 总条数
const listLoading = ref(false); // 加载状态

// 查询参数
const listQuery = reactive({
  page: 1,
  size: 10,
  phoneNumber: "",
  couponId: null,
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

// 获取数据
const fetchData = async () => {
  listLoading.value = true;
  try {
    const params = {
      ...listQuery,
      couponId: listQuery.couponId || undefined,
    };
    const res = await getUserCouponList(params);
    couponList.value = res.records;
    total.value = res.total;
  } catch (error) {
    ElMessage.error(error.message || "获取数据失败");
  } finally {
    listLoading.value = false;
  }
};

// 搜索
const handleSearch = () => {
  listQuery.page = 1;
  fetchData();
};

// 重置搜索
const resetSearch = () => {
  listQuery.phoneNumber = "";
  listQuery.couponId = null;
  handleSearch();
};

// 分页
const handlePageChange = (val) => {
  listQuery.page = val;
  fetchData();
};

// 删除记录
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除该领取记录吗？`, "警告", {
      confirmButtonText: "确认",
      cancelButtonText: "取消",
      type: "warning",
    });
    await deleteUserCoupon(row.userCouponId);
    ElMessage.success("删除成功");
    fetchData();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error.message || "删除失败");
    }
  }
};

// 时间格式化
const formatTime = (time) => {
  return time ? dayjs(time).format("YYYY-MM-DD HH:mm:ss") : "-";
};
</script>
  
  <style scoped>
.coupon-container {
  padding: 20px;
  background-color: #fff;
  height: 100%;
  box-sizing: border-box;
}

.filter-container {
  margin-bottom: 20px;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;
}

.el-form-item {
  margin-bottom: 0;
  margin-right: 15px;
}
</style>