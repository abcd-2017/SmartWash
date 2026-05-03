<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="支付ID">
          <el-input
            v-model.number="listQuery.paymentId"
            placeholder="输入支付ID"
            clearable
            style="width: 120px"
          />
        </el-form-item>

        <el-form-item label="订单号">
          <el-input
            v-model="listQuery.orderNo"
            placeholder="输入订单号"
            clearable
            style="width: 180px"
          />
        </el-form-item>

        <el-form-item label="手机号">
          <el-input
            v-model="listQuery.phoneNumber"
            placeholder="输入手机号"
            clearable
            style="width: 160px"
            @blur="validatePhone"
          />
        </el-form-item>

        <el-form-item label="支付方式">
          <el-select
            v-model="listQuery.paymentMethod"
            placeholder="全部方式"
            clearable
            style="width: 120px"
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
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button
            type="danger"
            :disabled="multipleSelection.length === 0"
            @click="handleBatchDelete"
            >批量删除</el-button
          >
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <el-table
      ref="tableRef"
      v-loading="listLoading"
      :data="paymentList"
      border
      fit
      highlight-current-row
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column prop="paymentId" label="支付ID" width="90" />
      <el-table-column prop="orderNo" label="订单号" width="200" />
      <el-table-column label="用户" width="150">
        <template #default="{ row }">
          {{ row.user?.phoneNumber || "-" }}
        </template>
      </el-table-column>
      <el-table-column label="金额" width="120">
        <template #default="{ row }">￥{{ row.amount.toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="支付方式" width="120">
        <template #default="{ row }">
          {{ payTypeOptions[row.paymentMethod] || "-" }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusTagType(row.status)">
            {{ payStatusOptions[row.status] || "-" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="支付时间" width="180">
        <template #default="{ row }">{{ formatTime(row.paidAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
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
import { ref, reactive, onMounted, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import dayjs from "dayjs";
import {
  getPayTypes,
  getPayStatus,
  getPaymentList,
  deletePayment,
} from "@/api/payment";

const tableRef = ref(null);
const paymentList = ref([]);
const total = ref(0);
const listLoading = ref(false);
const multipleSelection = ref([]);
const payTypeOptions = ref({});
const payStatusOptions = ref({});

// 查询参数
const listQuery = reactive({
  page: 1,
  size: 10,
  paymentId: null,
  orderNo: "",
  phoneNumber: "",
  paymentMethod: null,
  status: null,
  startTime: null,
  endTime: null,
});

// 时间范围处理
const timeRange = computed({
  get: () => [listQuery.startTime, listQuery.endTime],
  set: (val) => {
    listQuery.startTime = val?.[0] || null;
    listQuery.endTime = val?.[1] || null;
  },
});

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
    ElMessage.error("获取支付类型失败");
  }
};

// 获取支付状态
const fetchPayStatus = async () => {
  try {
    const res = await getPayStatus();
    payStatusOptions.value = res;
  } catch (error) {
    ElMessage.error("获取支付状态失败");
  }
};

// 获取数据
const fetchData = async () => {
  listLoading.value = true;
  try {
    const params = {
      ...listQuery,
      paymentId: listQuery.paymentId || undefined,
      paymentMethod: listQuery.paymentMethod || undefined,
      status: listQuery.status || undefined,
    };
    const res = await getPaymentList(params);
    paymentList.value = res.records;
    total.value = res.total;
  } catch (error) {
    ElMessage.error(error.message || "获取数据失败");
  } finally {
    listLoading.value = false;
  }
};

// 处理多选
const handleSelectionChange = (val) => {
  multipleSelection.value = val;
};

// 手机号验证
const validatePhone = () => {
  const phone = listQuery.phoneNumber;
  if (phone && !/^(?:\+86)?1[3-9]\d{9}$/.test(phone)) {
    ElMessage.warning("手机号格式不正确");
    listQuery.phoneNumber = "";
  }
};

// 搜索
const handleSearch = () => {
  listQuery.page = 1;
  fetchData();
};

// 重置搜索
const resetSearch = () => {
  listQuery.paymentId = null;
  listQuery.orderNo = "";
  listQuery.phoneNumber = "";
  listQuery.paymentMethod = null;
  listQuery.status = null;
  listQuery.startTime = null;
  listQuery.endTime = null;
  handleSearch();
};

// 分页
const handlePageChange = (val) => {
  listQuery.page = val;
  fetchData();
};

// 删除单个
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除支付记录 ${row.paymentId} 吗？`,
      "警告",
      {
        confirmButtonText: "确认",
        cancelButtonText: "取消",
        type: "warning",
      }
    );
    await deletePayment(row.paymentId);
    ElMessage.success("删除成功");
    fetchData();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error.message || "删除失败");
    }
  }
};

// 批量删除
const handleBatchDelete = async () => {
  try {
    const ids = multipleSelection.value.map((item) => item.paymentId).join(",");
    await ElMessageBox.confirm(
      `确认删除选中的 ${multipleSelection.value.length} 条记录吗？`,
      "警告",
      { confirmButtonText: "确认", cancelButtonText: "取消", type: "warning" }
    );
    await deletePayment(ids);
    ElMessage.success("删除成功");
    fetchData();
    tableRef.value.clearSelection();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error.message || "删除失败");
    }
  }
};

// 状态标签颜色
const getStatusTagType = (status) => {
  switch (status) {
    case "0":
      return "warning"; // 待支付
    case "1":
      return "success"; // 已支付
    case "2":
      return "danger"; // 失败
    default:
      return "info";
  }
};

// 时间格式化
const formatTime = (time) => {
  return dayjs(time).format("YYYY-MM-DD HH:mm:ss");
};
</script>
  
  <style scoped>
@import '@/assets/pages.css';
</style>