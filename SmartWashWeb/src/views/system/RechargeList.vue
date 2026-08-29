<template>
  <div class="page-container">
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

        <el-form-item label="手机号">
          <el-input
            v-model="listQuery.phoneNumber"
            placeholder="输入用户手机号"
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
    <div class="table-card">
    <el-table
      v-loading="listLoading"
      :data="rechargeList"
      fit
      highlight-current-row
    >
      <el-table-column prop="recordId" label="记录ID" min-width="100" />
      <el-table-column label="手机号" min-width="120">
        <template #default="{ row }">{{ row.users?.phoneNumber || '-' }}</template>
      </el-table-column>
      <el-table-column label="充值金额" min-width="150">
        <template #default="{ row }">￥{{ row.amount?.toFixed(2) || '0.00' }}</template>
      </el-table-column>
      <el-table-column label="充值类型" min-width="120">
        <template #default="{ row }">
          <el-tag :type="rechargeTypeTagType(row.rechargeType)">
            {{ rechargeTypeText(row.rechargeType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="充值时间" min-width="180">
        <template #default="{ row }">{{
          formatTime(row.rechargeTime)
        }}</template>
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
import { onMounted } from "vue";
import { getRechargeList } from "@/api/recharge";
import { formatTime } from "@/utils/format";
import {
  RECHARGE_TYPE_OPTIONS,
  rechargeTypeText,
  rechargeTypeTagType,
} from "@/constants/dict";
import { useTableList } from "@/composables/useTableList";
import { useTimeRange } from "@/composables/useTimeRange";

// 充值类型选项（枚举字典统一维护，评审 #16）
const rechargeTypeOptions = RECHARGE_TYPE_OPTIONS;

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: rechargeList,
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
  fetchApi: getRechargeList,
  baseQuery: {
    recordId: null,
    phoneNumber: null,
    amount: null,
    rechargeType: null,
    startTime: null,
    endTime: null,
  },
  buildParams: (q) => ({
    ...q,
    recordId: q.recordId || undefined,
    amount: q.amount || undefined,
    rechargeType: q.rechargeType || undefined,
  }),
  errorMsg: "获取数据失败",
});

// 监听时间范围选择（评审 #14：抽离为公共 composable）
const timeRange = useTimeRange(listQuery);

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
@import '@/assets/pages.css';
</style>