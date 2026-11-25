<template>
  <div class="page-container">
    <!-- 筛选区(live 打磨 v1):关键词输入定宽成组、动作右置,消除原 705px 行尾空洞 -->
    <div class="filter-container blocked-filter">
      <el-form
        :inline="true"
        :model="listQuery"
        @keyup.enter="handleSearch"
        class="blocked-filter__form"
      >
        <el-form-item label="关键词" class="blocked-filter__field">
          <el-input
            v-model="listQuery.keyword"
            placeholder="请输入问题内容"
            clearable
            style="width: 100%"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item class="blocked-filter__actions">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <!-- 加载失败态：与空态区分，给出重试入口 -->
      <div v-if="listError" class="list-error">
        <span>黑名单列表加载失败，请检查网络后重试</span>
        <el-button size="small" @click="fetchData">重试</el-button>
      </div>
      <el-table
        ref="tableRef"
        v-else
        v-loading="listLoading"
        :data="blockedList"
        fit
        highlight-current-row
        aria-label="黑名单列表"
        empty-text="暂无数据，试试调整筛选条件"
        :aria-describedby="captionId"
        @sort-change="onSortChange"
      >
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column prop="userId" label="用户ID" min-width="100" />
        <el-table-column prop="question" label="拦截问题" min-width="280" show-overflow-tooltip />
        <el-table-column prop="method" label="术数方法" min-width="120" sortable>
          <template #default="{ row }">{{ methodText(row.method) }}</template>
        </el-table-column>
        <el-table-column label="拦截原因" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ blockedReasonText(row.reason) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="拦截时间" min-width="180" sortable>
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <template #append>
          <span :id="captionId" class="sr-only">黑名单列表</span>
        </template>
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
import { onMounted, ref } from 'vue';
import { blockedReasonText, divinationMethodText } from '@/constants/dict';
import { getBlockedList } from '@/api/divination';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useTableA11y } from '@/composables/useTableA11y';

const tableRef = ref(null);

const { captionId, onSortChange } = useTableA11y('div-blocked-list', tableRef);

// 未知值兜底 '-'（列表展示语义）
const methodText = (method) => divinationMethodText(method) || '-';

const {
  list: blockedList,
  total,
  listLoading,
  listError,
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

<style scoped>
/* 筛选区(live 打磨 v1):关键词输入定宽成组、动作右置,消除原 705px 行尾空洞 */
.blocked-filter .blocked-filter__form {
  gap: 12px 0;
}

.blocked-filter .blocked-filter__field {
  flex: 0 1 480px;
  min-width: 0;
}

.blocked-filter .blocked-filter__actions {
  margin-left: auto;
  flex-shrink: 0;
}
</style>
