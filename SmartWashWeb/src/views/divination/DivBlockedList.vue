<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="关键词">
          <el-input
            v-model="listQuery.keyword"
            placeholder="请输入问题内容"
            clearable
            style="width: 240px"
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
      <el-table v-loading="listLoading" :data="blockedList" fit highlight-current-row>
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column prop="userId" label="用户ID" min-width="100" />
        <el-table-column prop="question" label="拦截问题" min-width="280" show-overflow-tooltip />
        <el-table-column label="术数方法" min-width="120">
          <template #default="{ row }">{{ methodText(row.method) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="拦截原因" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="拦截时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
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
import { onMounted } from 'vue';
import { DIV_METHOD_OPTIONS as methodOptions, divinationMethodText } from '@/constants/dict';
import { getBlockedList } from '@/api/divination';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';

// 未知值兜底 '-'（列表展示语义）
const methodText = (method) => divinationMethodText(method) || '-';

const {
  list: blockedList,
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
  fetchApi: getBlockedList,
  baseQuery: {
    keyword: '',
  },
  buildParams: (q) => ({
    ...q,
    keyword: q.keyword || undefined,
  }),
  errorMsg: '获取拦截日志失败',
});

onMounted(() => {
  fetchData();
});
</script>
