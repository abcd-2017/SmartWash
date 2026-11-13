<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="审计状态">
          <el-select
            v-model="listQuery.status"
            placeholder="全部"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in DIV_AUDIT_STATUS_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
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
        :data="auditList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column prop="recordId" label="卦例ID" min-width="100" />
        <el-table-column prop="interpretationId" label="解读ID" min-width="100" />
        <el-table-column label="审计状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="divAuditStatusTagType(row.auditStatus)">
              {{ divAuditStatusText(row.auditStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="审计时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="handleViewDetail(row)">查看详情</el-button>
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

    <!-- 审计详情弹窗 -->
    <el-dialog title="审计详情" v-model="detailDialogVisible" width="min(700px, 94vw)">
      <div v-if="currentAudit" class="audit-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="解读ID">{{
            currentAudit.interpretationId
          }}</el-descriptions-item>
          <el-descriptions-item label="卦例ID">{{
            currentAudit.recordId || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="审计状态">
            <el-tag :type="divAuditStatusTagType(currentAudit.auditStatus)">
              {{ divAuditStatusText(currentAudit.auditStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="审计时间">{{
            formatTime(currentAudit.createdAt)
          }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="diff-title">审计明细</h4>
        <div class="diff-content">
          <pre v-if="currentAudit.auditJson">{{ formatJson(currentAudit.auditJson) }}</pre>
          <span v-else style="color: var(--text-secondary)">无审计明细数据</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { getAuditList } from '@/api/divination';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import {
  DIV_AUDIT_STATUS_OPTIONS,
  divAuditStatusTagType,
  divAuditStatusText,
} from '@/constants/dict';
import logger from '@/utils/logger';

const detailDialogVisible = ref(false);
const currentAudit = ref(null);

const {
  list: auditList,
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
  fetchApi: getAuditList,
  baseQuery: {
    status: 2, // 默认展示失败（不一致）
  },
  buildParams: (q) => ({
    ...q,
    status: q.status ?? undefined,
  }),
  errorMsg: '获取审计列表失败',
});

onMounted(() => {
  fetchData();
});

const formatJson = (json) => {
  try {
    if (typeof json === 'string') return JSON.stringify(JSON.parse(json), null, 2);
    return JSON.stringify(json, null, 2);
  } catch {
    // 降级为原文展示；审计明细可能含用户问事内容，日志只记异常事实不打内容
    logger.warn('审计明细 JSON 解析失败，已按原文兜底展示');
    return String(json);
  }
};

const handleViewDetail = (row) => {
  currentAudit.value = row;
  detailDialogVisible.value = true;
};
</script>

<style scoped>
.audit-detail {
  padding: 0 8px;
}

.diff-title {
  margin: 20px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.diff-content {
  background: var(--bg-subtle);
  border-radius: 8px;
  padding: 16px;
  max-height: 400px;
  overflow-y: auto;
}

.diff-content pre {
  margin: 0;
  font-family: var(--font-mono); /* 审计 JSON 属机器数据，走 mono 规则 */
  font-size: 12px;
  color: var(--text-body);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
