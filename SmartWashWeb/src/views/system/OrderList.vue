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
    <el-table
      v-loading="listLoading"
      :data="orderList"
      border
      fit
      highlight-current-row
    >
      <el-table-column prop="orderNo" label="订单号" width="200" />
      <el-table-column label="用户信息" width="150">
        <template #default="{ row }">
          {{ row.userVo?.phoneNumber || "-" }}
        </template>
      </el-table-column>
      <el-table-column label="学校" width="180">
        <template #default="{ row }">
          {{ row.schoolsVo?.schoolName || "-" }}
        </template>
      </el-table-column>
      <el-table-column label="洗护套餐" width="180">
        <template #default="{ row }">
          {{ row.laundryPackageVo?.itemName || "-" }}
        </template>
      </el-table-column>
      <el-table-column label="总价" width="120">
        <template #default="{ row }"
          >￥{{ row.totalPrice?.toFixed(2) }}</template
        >
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusTagType(row.status)">
            {{ formatStatus(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="pickupCode" label="取件码" width="120" />
      <el-table-column label="创建时间" width="180">
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

    <!-- 分页 -->
    <el-pagination
      background
      :current-page="listQuery.page"
      layout="prev, pager, next"
      :total="total"
      :page-size="listQuery.size"
      @current-change="handlePageChange"
    />

    <!-- 修改状态对话框 -->
    <el-dialog v-model="statusDialogVisible" title="修改订单状态" width="400px">
      <el-form :model="statusForm" label-width="100px">
        <el-form-item label="订单号">
          <span>{{ statusForm.orderNo }}</span>
        </el-form-item>
        <el-form-item label="当前状态">
          <el-tag :type="getStatusTagType(statusForm.currentStatus)">
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
import { ref, reactive, onMounted, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import dayjs from "dayjs";
import {
  getOrderStatus,
  getOrderList,
  deleteOrder,
  updateOrderStatus,
} from "@/api/order";
import { getSchoolList } from "@/api/school";
import { getLaundryList } from "@/api/laundry";

// 数据
const statusOptions = ref({}); // 状态选项
const schoolOptions = ref([]); // 学校选项
const laundryOptions = ref([]); // 洗护套餐选项
const orderList = ref([]); // 订单列表
const total = ref(0); // 总条数
const listLoading = ref(false); // 加载状态

// 查询参数
const listQuery = reactive({
  page: 1,
  size: 10,
  phoneNumber: "",
  schoolName: "",
  orderNo: "",
  laundryItemsId: null,
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

// 获取学校列表
const fetchSchools = async () => {
  try {
    const res = await getSchoolList({ page: 1, size: 1000 });
    schoolOptions.value = res.records;
  } catch (error) {
    ElMessage.error("获取学校列表失败");
  }
};

// 获取洗护套餐列表
const fetchLaundry = async () => {
  try {
    const res = await getLaundryList({ page: 1, size: 1000 });
    laundryOptions.value = res.records;
  } catch (error) {
    ElMessage.error("获取洗护套餐失败");
  }
};

// 获取数据
const fetchData = async () => {
  listLoading.value = true;
  try {
    const params = {
      ...listQuery,
      laundryItemsId: listQuery.laundryItemsId || undefined,
      status: listQuery.status || undefined,
    };
    const res = await getOrderList(params);
    orderList.value = res.records;
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
  listQuery.schoolName = "";
  listQuery.orderNo = "";
  listQuery.laundryItemsId = null;
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

// 删除订单
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除订单 ${row.orderNo} 吗？`, "警告", {
      confirmButtonText: "确认",
      cancelButtonText: "取消",
      type: "warning",
    });
    await deleteOrder(row.orderId);
    ElMessage.success("删除成功");
    fetchData();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error.message || "删除失败");
    }
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
// 状态标签颜色
const getStatusTagType = (status) => {
  switch (status) {
    case "-2":
      return "info"; // 已取消
    case "-1":
      return "warning"; // 已退款
    case "0":
      return "danger"; // 待支付
    case "1":
      return ""; // 待寄件（默认）
    case "2":
      return "success"; // 已收取
    case "3":
      return "primary"; // 清洗中
    case "4":
      return ""; // 已烘干（默认）
    case "5":
      return "warning"; // 配送中
    case "6":
      return "success"; // 待取件
    case "7":
      return "success"; // 已完成
    default:
      return "info";
  }
};

// 状态文本转换
const formatStatus = (status) => {
  return statusOptions.value[status] || "未知状态";
};

// 时间格式化
const formatTime = (time) => {
  return dayjs(time).format("YYYY-MM-DD HH:mm:ss");
};
</script>

<style scoped>
@import '@/assets/pages.css';
</style>