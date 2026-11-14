<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="审计状态">
          <el-select
            v-model="listQuery.status"
            placeholder="不一致(默认)"
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
        <el-table-column prop="recordId" label="卦例ID" min-width="100">
          <template #default="{ row }">
            <span class="mono-text">{{ row.recordId ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="interpretationId" label="解读ID" min-width="100">
          <template #default="{ row }">
            <span class="mono-text">{{ row.interpretationId ?? '-' }}</span>
          </template>
        </el-table-column>
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
    <el-dialog title="审计详情" v-model="detailDialogVisible" width="min(760px, 94vw)">
      <div v-if="currentAudit" class="audit-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="解读ID">{{
            currentAudit.interpretationId
          }}</el-descriptions-item>
          <el-descriptions-item label="卦例ID">{{
            currentAudit.recordId || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ currentAudit.model || '-' }}</el-descriptions-item>
          <el-descriptions-item label="服务商">{{
            currentAudit.provider || '-'
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

        <h4 class="diff-title">问事</h4>
        <div class="audit-text">{{ currentAudit.question || '-' }}</div>

        <h4 class="diff-title">
          AI 原文
          <el-button
            link
            size="small"
            class="collapse-toggle"
            @click="contentCollapsed = !contentCollapsed"
          >
            {{ contentCollapsed ? '展开' : '折叠' }}
          </el-button>
        </h4>
        <pre
          v-if="currentAudit.contentMd"
          class="audit-content"
          :class="{ 'is-collapsed': contentCollapsed }"
          >{{ currentAudit.contentMd }}</pre>
        <span v-else class="audit-empty">无 AI 原文</span>

        <h4 class="diff-title">审计明细</h4>
        <div v-if="auditDiffs.length" class="diff-chips">
          <el-tag v-for="(diff, index) in auditDiffs" :key="index" type="danger" effect="light">
            {{ diffChipText(diff) }}
          </el-tag>
        </div>
        <div class="diff-content">
          <template v-if="currentAudit.auditJson">
            <el-button
              v-if="auditDiffs.length"
              link
              size="small"
              class="collapse-toggle"
              @click="rawJsonCollapsed = !rawJsonCollapsed"
            >
              {{ rawJsonCollapsed ? '展开原始 JSON' : '折叠原始 JSON' }}
            </el-button>
            <pre v-if="!auditDiffs.length || !rawJsonCollapsed">{{
              formatJson(currentAudit.auditJson)
            }}</pre>
          </template>
          <span v-else class="audit-empty">无审计明细数据</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
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
// AI 原文默认展开、原始审计 JSON 默认折叠（有 diffs 时以 chip 行替代raw JSON 倾倒）
const contentCollapsed = ref(false);
const rawJsonCollapsed = ref(true);

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

const parseAuditJson = (json) => {
  try {
    return typeof json === 'string' ? JSON.parse(json) : json;
  } catch {
    return null;
  }
};

const formatJson = (json) => {
  const parsed = parseAuditJson(json);
  if (parsed === null) {
    // 降级为原文展示；审计明细可能含用户问事内容，日志只记异常事实不打内容
    logger.warn('审计明细 JSON 解析失败，已按原文兜底展示');
    return String(json);
  }
  return JSON.stringify(parsed, null, 2);
};

// diffs 数组存在时以 chip 行呈现（field + cited + inChart），避免 raw JSON 倾倒
const auditDiffs = computed(() => {
  const parsed = parseAuditJson(currentAudit.value?.auditJson);
  return Array.isArray(parsed?.diffs) ? parsed.diffs : [];
});

const diffChipText = (diff) =>
  `${diff.field ?? '-'}：引用 ${diff.cited ?? '-'}，盘面 ${
    String(diff.inChart) === 'true' ? '命中' : '缺失'
  }`;

const handleViewDetail = (row) => {
  currentAudit.value = row;
  // 每次打开重置折叠态，避免上一条记录的状态串到下一条
  contentCollapsed.value = false;
  rawJsonCollapsed.value = true;
  detailDialogVisible.value = true;
};
</script>

<style scoped>
/* 卦例/解读 ID 是机器标识符：走 mono（DESIGN.md Mono 13px/400） */
.mono-text {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--text-body);
}

.audit-detail {
  padding: 0 8px;
}

.diff-title {
  margin: 20px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.collapse-toggle {
  margin-left: 8px;
  font-weight: 400;
}

/* 问事 / AI 原文：长文 pre-wrap，弱底块承载（对齐输入框弱底语言） */
.audit-text,
.audit-content {
  margin: 0;
  padding: 12px 16px;
  background: var(--bg-subtle);
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-body);
  white-space: pre-wrap;
  word-break: break-word;
}

.audit-content {
  max-height: 320px;
  overflow-y: auto;
}

.audit-content.is-collapsed {
  max-height: 96px;
  overflow: hidden;
}

.audit-empty {
  font-size: 14px;
  color: var(--text-secondary);
}

/* diffs chip 行：field + cited + inChart 一屏可读，替代 raw JSON 倾倒 */
.diff-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
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
