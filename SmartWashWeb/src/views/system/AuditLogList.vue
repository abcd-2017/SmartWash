<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="操作人">
          <el-input
            v-model="listQuery.adminUser"
            placeholder="输入管理员用户名"
            clearable
            style="width: 160px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="动作">
          <el-select
            v-model="listQuery.action"
            placeholder="全部动作"
            clearable
            filterable
            style="width: 180px"
            @change="handleSearch"
          >
            <el-option
              v-for="item in AUDIT_ACTION_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="目标类型">
          <el-select
            v-model="listQuery.targetType"
            placeholder="全部类型"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option
              v-for="item in AUDIT_TARGET_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="操作时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled-date="disabledDate"
            :shortcuts="timeShortcuts"
            style="width: 380px"
            @change="handleSearch"
          />
        </el-form-item>

        <el-form-item class="filter-end">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格（只读：审计日志仅查询，不提供新增/编辑/删除）；
         链完整性校验是动作不是筛选，独立成工具条避免误触 5000 条 HMAC 复算 -->
    <div class="table-card">
      <div class="card-toolbar">
        <!-- 工具条标签(live 打磨 v2):墨色 600 + 竖分隔线,划分标签区与控件区 -->
        <span class="card-toolbar__label">链完整性校验</span>
        <el-select v-model="verifyLimit" style="width: 100px" aria-label="抽查条数">
          <el-option
            v-for="limit in VERIFY_LIMIT_OPTIONS"
            :key="limit"
            :label="String(limit)"
            :value="limit"
          />
        </el-select>
        <el-button type="warning" plain :loading="verifyLoading" @click="handleVerify">
          校验最近 {{ verifyLimit }} 条
        </el-button>
      </div>
      <el-table
        v-loading="listLoading"
        :data="auditList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column prop="adminUser" label="操作人" min-width="120" show-overflow-tooltip />
        <el-table-column label="动作" min-width="150">
          <template #default="{ row }">
            <el-tag :type="auditActionTagType(row.action)">{{
              auditActionText(row.action)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标类型" min-width="110">
          <template #default="{ row }">{{ auditTargetTypeText(row.targetType) }}</template>
        </el-table-column>
        <el-table-column label="目标ID" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.targetId || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="IP" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作前后摘要" min-width="260">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.beforeJson || row.afterJson"
              placement="top"
              effect="light"
              trigger="click"
            >
              <template #content>
                <div class="payload-tip">
                  <p v-if="row.beforeJson">操作前：{{ row.beforeJson }}</p>
                  <p v-if="row.afterJson">操作后：{{ row.afterJson }}</p>
                </div>
              </template>
              <span class="payload-cell" tabindex="0" role="button" aria-label="查看操作前后数据">{{
                payloadSummary(row)
              }}</span>
            </el-tooltip>
            <span v-else>-</span>
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

    <!-- 链完整性校验结果弹窗 -->
    <el-dialog v-model="verifyVisible" title="链完整性校验结果" width="min(560px, 94vw)">
      <el-descriptions v-if="verifyResult" :column="1" border>
        <el-descriptions-item label="校验结果">
          <el-tag :type="verifyResult.valid ? 'success' : 'danger'">
            {{ verifyResult.valid ? '校验通过：链完整' : '校验不通过：链存在断裂' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="校验条数">
          最近 {{ verifyResult.checkedCount ?? 0 }} 条
        </el-descriptions-item>
        <el-descriptions-item label="断裂点 ID">
          {{ verifyResult.brokenId ?? '—（无断裂）' }}
        </el-descriptions-item>
        <el-descriptions-item label="校验说明">
          {{ verifyResult.message || '—' }}
        </el-descriptions-item>
      </el-descriptions>
      <div class="verify-tip">
        校验为抽查性质：仅对最近 N 条记录逐条复算 HMAC 并检查链衔接，返回首个断裂点；
        删除抽查窗口末尾条目的情况无法检出。
      </div>
      <template #footer>
        <el-button @click="verifyVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';
import { getAuditLogs, verifyAuditLogs } from '@/api/audit';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useTimeRange } from '@/composables/useTimeRange';
import {
  AUDIT_ACTION_OPTIONS,
  AUDIT_TARGET_TYPE_OPTIONS,
  auditActionTagType,
  auditActionText,
  auditTargetTypeText,
} from '@/constants/dict';
import logger from '@/utils/logger';

// 动作/目标类型字典已统一收敛到 constants/dict.js（AUDIT_*）：
// 与后端 @Audited 注解全集对齐，未知动作/类型回显原始编码，不猜文案。

// ===== 操作前后摘要 =====
// 单元格内前/后各截 40 字符防撑破布局，完整内容经 tooltip 展开（纯文本插值，无 v-html）
const PAYLOAD_TRUNCATE = 40;
const truncatePayload = (text) =>
  text.length > PAYLOAD_TRUNCATE ? `${text.slice(0, PAYLOAD_TRUNCATE)}…` : text;
const payloadSummary = (row) => {
  const parts = [];
  if (row.beforeJson) parts.push(`前 ${truncatePayload(row.beforeJson)}`);
  if (row.afterJson) parts.push(`后 ${truncatePayload(row.afterJson)}`);
  return parts.join(' → ');
};

// ===== 列表查询与分页：统一由 useTableList 承载 =====
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
  fetchApi: getAuditLogs,
  baseQuery: {
    adminUser: '',
    action: null,
    targetType: null,
    startTime: null,
    endTime: null,
  },
  buildParams: (q) => ({
    ...q,
    adminUser: (q.adminUser || '').trim() || undefined,
    action: q.action || undefined,
    targetType: q.targetType || undefined,
  }),
  errorMsg: '获取审计日志失败',
});

// 时间范围：startTime/endTime 字段名与后端 SearchAuditLogFrom 同名，
// 格式 yyyy-MM-dd HH:mm:ss 与后端 @DateTimeFormat 口径一致
const timeRange = useTimeRange(listQuery);

// 时间筛选禁选未来时间（审计日志只增不改，未来区间必为空）
const disabledDate = (date) => date.getTime() > Date.now();

// 快捷区间：对齐运营排查常用窗口（今天 / 近 7 天 / 近 30 天）
const timeShortcuts = [
  {
    text: '今天',
    value: () => [dayjs().startOf('day').toDate(), dayjs().endOf('day').toDate()],
  },
  {
    text: '近 7 天',
    value: () => [
      dayjs().subtract(6, 'day').startOf('day').toDate(),
      dayjs().endOf('day').toDate(),
    ],
  },
  {
    text: '近 30 天',
    value: () => [
      dayjs().subtract(29, 'day').startOf('day').toDate(),
      dayjs().endOf('day').toDate(),
    ],
  },
];

// ===== 链完整性校验 =====
// 后端默认 500、上限 5000，此处提供常用档位
const VERIFY_LIMIT_OPTIONS = [100, 500, 1000, 5000];
const verifyLimit = ref(500);
const verifyVisible = ref(false);
const verifyLoading = ref(false);
const verifyResult = ref(null);

const handleVerify = async () => {
  verifyLoading.value = true;
  try {
    verifyResult.value = await verifyAuditLogs(verifyLimit.value);
    verifyVisible.value = true;
  } catch (error) {
    // 业务失败在 http.js 拦截器已留痕，这里归因到页面动作并透出后端 message
    logger.error('审计链完整性校验失败:', error);
    ElMessage.error(error.message || '链完整性校验失败');
  } finally {
    verifyLoading.value = false;
  }
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
/* 摘要单元格：指针提示可悬浮查看全文 */
.payload-cell {
  cursor: help;
  color: var(--el-text-color-secondary);
}

/* tooltip 内 JSON 摘要：等宽字体 + 可换行，超长不撑破气泡 */
.payload-tip {
  max-width: 480px;
  margin: 0;
  font-family: var(--font-mono);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

.payload-tip p {
  margin: 0 0 4px;
}

.verify-tip {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

/* 链完整性校验工具条：表格卡头部，与分页条同语言（弱底 + 卡内分隔线） */
.card-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: var(--bg-subtle);
  border-bottom: 1px solid var(--border-subtle);
}

/* 工具条标签(live 打磨 v2):墨色 600 + 竖分隔线,划分标签区与控件区 */
.card-toolbar__label {
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.card-toolbar__label::after {
  content: '';
  display: inline-block;
  width: 1px;
  height: 14px;
  margin-left: 12px;
  background: var(--border);
}
</style>
