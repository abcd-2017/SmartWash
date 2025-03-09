<template>
  <div class="recharge-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="记录ID">
          <el-input
            v-model.number="listQuery.recordId"
            placeholder="输入记录ID"
            clearable
            style="width: 120px"
          />
        </el-form-item>

        <el-form-item label="用户ID">
          <el-input
            v-model.number="listQuery.userId"
            placeholder="输入用户ID"
            clearable
            style="width: 120px"
          />
        </el-form-item>

        <el-form-item label="金额">
          <el-input-number
            v-model="listQuery.amount"
            :precision="2"
            :min="0"
            controls-position="right"
            placeholder="输入金额"
            style="width: 150px"
          />
        </el-form-item>

        <el-form-item label="充值类型">
          <el-select
            v-model="listQuery.rechargeType"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="type in rechargeTypeOptions"
              :key="type.value"
              :label="type.label"
              :value="type.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="充值时间">
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
      :data="rechargeList"
      border
      fit
      highlight-current-row
    >
      <el-table-column prop="recordId" label="记录ID" width="100" />
      <el-table-column prop="userId" label="用户ID" width="120" />
      <el-table-column label="充值金额" width="150">
        <template #default="{ row }">￥{{ row.amount.toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="充值类型" width="120">
        <template #default="{ row }">
          <el-tag :type="rechargeTypeTag(row.rechargeType)">
            {{ formatRechargeType(row.rechargeType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="充值时间" width="180">
        <template #default="{ row }">{{
          formatTime(row.rechargeTime)
        }}</template>
      </el-table-column>
      <!-- <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="danger" @click="handleDelete(row)"
            >删除</el-button
          >
        </template>
      </el-table-column> -->
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
import { getRechargeList } from "@/api/recharge";

const listLoading = ref(false);
const rechargeList = ref([]);
const total = ref(0);
const timeRange1 = ref([]);

// 充值类型选项
const rechargeTypeOptions = [
  { value: "1", label: "微信支付" },
  { value: "2", label: "支付宝支付" },
];

// 查询参数
const listQuery = reactive({
  page: 1,
  size: 10,
  recordId: null,
  userId: null,
  amount: null,
  rechargeType: null,
  startTime: null,
  endTime: null,
});

// 监听时间范围选择
const timeRange = computed({
  get: () => [listQuery.startTime, listQuery.endTime],
  set: (val) => {
    listQuery.startTime = val?.[0] || null;
    listQuery.endTime = val?.[1] || null;
  },
});

// 获取数据
const fetchData = async () => {
  listLoading.value = true;
  try {
    const params = {
      ...listQuery,
      recordId: listQuery.recordId || undefined,
      userId: listQuery.userId || undefined,
      amount: listQuery.amount || undefined,
      rechargeType: listQuery.rechargeType || undefined,
    };
    const res = await getRechargeList(params);
    rechargeList.value = res.records;
    total.value = res.total;
  } catch (error) {
    ElMessage.error(error.message || "获取数据失败");
  } finally {
    listLoading.value = false;
  }
};

// 格式化充值类型
const formatRechargeType = (type) => {
  const map = {
    1: "微信支付",
    2: "支付宝支付",
  };
  return map[type] || "未知类型";
};

// 充值类型标签样式
const rechargeTypeTag = (type) => {
  const map = {
    1: "success", // 微信支付
    2: "primary", // 支付宝支付
  };
  return map[type] || "info";
};

// 搜索
const handleSearch = () => {
  listQuery.page = 1;
  fetchData();
};

// 重置搜索
const resetSearch = () => {
  listQuery.recordId = null;
  listQuery.userId = null;
  listQuery.amount = null;
  listQuery.rechargeType = null;
  listQuery.startTime = null;
  listQuery.endTime = null;
  handleSearch();
};

// 分页
const handlePageChange = (val) => {
  listQuery.page = val;
  fetchData();
};

// 删除记录
// const handleDelete = async (row) => {
//   try {
//     await ElMessageBox.confirm(`确认删除该充值记录吗？`, "警告", {
//       confirmButtonText: "确认",
//       cancelButtonText: "取消",
//       type: "warning",
//     });
//     await deleteRecharge(row.recordId);
//     ElMessage.success("删除成功");
//     fetchData();
//   } catch (error) {
//     if (error !== "cancel") {
//       ElMessage.error(error.message || "删除失败");
//     }
//   }
// };

// 时间格式化
const formatTime = (time) => {
  return dayjs(time).format("YYYY-MM-DD HH:mm:ss");
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.recharge-container {
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