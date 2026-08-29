<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="手机号">
          <el-input
            v-model="listQuery.phoneNumber"
            placeholder="输入用户手机号"
            clearable
            style="width: 160px"
          />
        </el-form-item>

        <el-form-item label="学校">
          <el-select
            v-model="listQuery.schoolName"
            placeholder="选择学校"
            filterable
            clearable
            style="width: 180px"
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
            placeholder="输入订单号"
            clearable
            style="width: 200px"
          />
        </el-form-item>

        <el-form-item label="洗护套餐">
          <el-select
            v-model="listQuery.laundryItemsId"
            placeholder="选择套餐"
            filterable
            clearable
            style="width: 180px"
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
    >
      <el-table-column prop="orderNo" label="订单号" min-width="200" />
      <el-table-column label="用户信息" min-width="150">
        <template #default="{ row }">
          {{ row.userVo?.phoneNumber || "-" }}
        </template>
      </el-table-column>
      <el-table-column label="学校" min-width="180">
        <template #default="{ row }">
          {{ row.schoolsVo?.schoolName || "-" }}
        </template>
      </el-table-column>
      <el-table-column label="洗护套餐" min-width="180">
        <template #default="{ row }">
          {{ row.laundryPackageVo?.itemName || "-" }}
        </template>
      </el-table-column>
      <el-table-column label="总价" min-width="120">
        <template #default="{ row }"
          >￥{{ row.totalPrice?.toFixed(2) || '0.00' }}</template
        >
      </el-table-column>
      <el-table-column label="状态" min-width="120">
        <template #default="{ row }">
          <el-tag :type="orderStatusTagType(row.status)">
            {{ formatStatus(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="pickupCode" label="取件码" min-width="120" />
      <el-table-column label="创建时间" min-width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            size="small"
            type="primary"
            @click="handleUpdateStatus(row)"
            >修改状态</el-button
          >
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

    <!-- 修改状态对话框 -->
    <el-dialog v-model="statusDialogVisible" title="修改订单状态" width="400px">
      <el-form :model="statusForm" label-width="100px">
        <el-form-item label="订单号">
          <span>{{ statusForm.orderNo }}</span>
        </el-form-item>
        <el-form-item label="当前状态">
          <el-tag :type="orderStatusTagType(statusForm.currentStatus)">
            {{ formatStatus(statusForm.currentStatus) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="新状态">
          <el-select v-model="statusForm.newStatus" placeholder="请选择新状态">
            <el-option
              v-for="(desc, code) in statusOptions"
              :key="code"
              :label="desc"
              :value="code"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="statusDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmUpdateStatus">
            确认
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import { ElMessage } from "element-plus";
import {
  getOrderStatus,
  getOrderList,
  deleteOrder,
  updateOrderStatus,
} from "@/api/order";
import { schoolOptionsCache, laundryOptionsCache } from "@/utils/optionCache";
import { formatTime } from "@/utils/format";
import { orderStatusTagType } from "@/constants/dict";
import { useTableList } from "@/composables/useTableList";
import { useTimeRange } from "@/composables/useTimeRange";
import { useConfirm } from "@/composables/useConfirm";

// 数据
const statusOptions = ref({}); // 状态选项
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
  handleSearch,
  resetSearch,
  handlePageChange,
  handleSizeChange,
} = useTableList({
  fetchApi: getOrderList,
  baseQuery: {
    phoneNumber: "",
    schoolName: "",
    orderNo: "",
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
  errorMsg: "获取数据失败",
});

// 时间范围处理（评审 #14：抽离为公共 composable）
const timeRange = useTimeRange(listQuery);

// 状态更新相关
const statusDialogVisible = ref(false);
const statusForm = reactive({
  orderId: null,
  orderNo: "",
  currentStatus: "",
  newStatus: "",
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
    ElMessage.error("获取状态失败");
  }
};

// 获取学校列表（模块级缓存：多页面共用一份全量数据，评审 #15）
const fetchSchools = async () => {
  try {
    schoolOptions.value = await schoolOptionsCache.load();
  } catch (error) {
    ElMessage.error("获取学校列表失败");
  }
};

// 获取洗护套餐列表（模块级缓存，评审 #15）
const fetchLaundry = async () => {
  try {
    laundryOptions.value = await laundryOptionsCache.load();
  } catch (error) {
    ElMessage.error("获取洗护套餐失败");
  }
};

// 删除订单
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除订单 ${row.orderNo} 吗？`);
  if (!confirmed) return;
  try {
    await deleteOrder(row.orderId);
    ElMessage.success("删除成功");
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || "删除失败");
  }
};

// 打开修改状态对话框
const handleUpdateStatus = (row) => {
  statusForm.orderId = row.orderId;
  statusForm.orderNo = row.orderNo;
  statusForm.currentStatus = row.status;
  statusForm.newStatus = "";
  statusDialogVisible.value = true;
};

// 确认更新状态
const confirmUpdateStatus = async () => {
  if (!statusForm.newStatus) {
    ElMessage.warning("请选择新状态");
    return;
  }

  try {
    await updateOrderStatus({
      orderId: statusForm.orderId,
      status: statusForm.newStatus,
    });
    ElMessage.success("状态更新成功");
    statusDialogVisible.value = false;
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || "状态更新失败");
  }
};

// 状态文本转换
const formatStatus = (status) => {
  return statusOptions.value[status] || "未知状态";
};
</script>

<style scoped>
@import '@/assets/pages.css';
</style>