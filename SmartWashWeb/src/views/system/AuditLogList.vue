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
              v-for="item in actionOptions"
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
              v-for="item in targetTypeOptions"
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
            style="width: 380px"
            @change="handleSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>

        <el-form-item label="抽查条数">
          <el-select v-model="verifyLimit" style="width: 100px">
            <el-option
              v-for="limit in VERIFY_LIMIT_OPTIONS"
              :key="limit"
              :label="String(limit)"
              :value="limit"
            />
          </el-select>
          <el-button
            type="warning"
            plain
            :loading="verifyLoading"
            style="margin-left: 12px"
            @click="handleVerify"
          >
            链完整性校验
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格（只读：审计日志仅查询，不提供新增/编辑/删除） -->
    <div class="table-card">
      <el-table v-loading="listLoading" :data="auditList" fit highlight-current-row>
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column prop="adminUser" label="操作人" min-width="120" show-overflow-tooltip />
        <el-table-column label="动作" min-width="150">
          <template #default="{ row }">
            <el-tag :type="actionTagType(row.action)">{{ actionText(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标类型" min-width="110">
          <template #default="{ row }">{{ targetTypeText(row.targetType) }}</template>
        </el-table-column>
        <el-table-column label="目标ID" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.targetId || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="IP" min-width="130">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作前后摘要" min-width="260">
          <template #default="{ row }">
            <el-tooltip v-if="row.beforeJson || row.afterJson" placement="top" effect="light">
              <template #content>
                <div class="payload-tip">
                  <p v-if="row.beforeJson">操作前：{{ row.beforeJson }}</p>
                  <p v-if="row.afterJson">操作后：{{ row.afterJson }}</p>
                </div>
              </template>
              <span class="payload-cell">{{ payloadSummary(row) }}</span>
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
    <el-dialog v-model="verifyVisible" title="链完整性校验结果" width="560px">
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
import { getAuditLogs, verifyAuditLogs } from '@/api/audit';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useTimeRange } from '@/composables/useTimeRange';
import logger from '@/utils/logger';

// ===== 动作/目标类型字典（与后端 @Audited 注解全集对齐） =====
// 来源：SmartWash 后端 controller/background/ 各 Controller 的 @Audited(action, targetType)。
// 后端新增注解动作时需同步维护 ACTION_TEXT；未知值在表格中回显原始编码，不猜文案。
const ACTION_TEXT = {
  ORDER_DELETE: '删除订单',
  ORDER_STATUS_UPDATE: '更新订单状态',
  ROLE_ADD: '新增角色',
  ROLE_UPDATE: '更新角色',
  ROLE_DELETE: '删除角色',
  LOCKER_ADD: '新增寄存柜',
  LOCKER_BATCH_ADD: '批量新增寄存柜',
  LOCKER_UPDATE: '更新寄存柜',
  LOCKER_DELETE: '删除寄存柜',
  USER_ADD: '新增学生',
  USER_UPDATE: '更新学生',
  USER_DELETE: '删除学生',
  PAYMENT_MANUAL_ADD: '人工补录支付',
  PAYMENT_MANUAL_UPDATE: '更新支付记录',
  USER_COUPON_DELETE: '删除用户优惠券',
  COUPON_ADD: '新增优惠券',
  COUPON_UPDATE: '更新优惠券',
  COUPON_DELETE: '删除优惠券',
  LAUNDRY_ITEM_ADD: '新增洗护套餐',
  LAUNDRY_ITEM_UPDATE: '更新洗护套餐',
  LAUNDRY_ITEM_DELETE: '删除洗护套餐',
  SCHOOL_ADD: '新增学校',
  SCHOOL_UPDATE: '更新学校',
  SCHOOL_DELETE: '删除学校',
  ADMIN_USER_ADD: '新增管理员',
  ADMIN_USER_UPDATE: '更新管理员',
  ADMIN_USER_DELETE: '删除管理员',
};

const TARGET_TYPE_TEXT = {
  order: '订单',
  role: '角色',
  locker: '寄存柜',
  user: '学生',
  payment: '支付记录',
  user_coupon: '用户优惠券',
  coupon: '优惠券',
  laundry_item: '洗护套餐',
  school: '学校',
  admin_user: '管理员',
};

// 筛选下拉由字典派生，保证下拉与表格文案同源
const actionOptions = Object.entries(ACTION_TEXT).map(([value, label]) => ({ value, label }));
const targetTypeOptions = Object.entries(TARGET_TYPE_TEXT).map(([value, label]) => ({
  value,
  label,
}));

// 未知动作/类型回显原始编码（后端后续新增动作时页面不致显示错误文案）
const actionText = (action) => ACTION_TEXT[action] || action || '-';
const targetTypeText = (targetType) => TARGET_TYPE_TEXT[targetType] || targetType || '-';

// 动作标签配色按动词后缀派生：新增绿 / 更新蓝 / 删除红 / 其余灰
const actionTagType = (action) => {
  if (action?.endsWith('_ADD')) return 'success';
  if (action?.endsWith('_UPDATE')) return 'primary';
  if (action?.endsWith('_DELETE')) return 'danger';
  return 'info';
};

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
    adminUser: q.adminUser || undefined,
    action: q.action || undefined,
    targetType: q.targetType || undefined,
  }),
  errorMsg: '获取审计日志失败',
});

// 时间范围：startTime/endTime 字段名与后端 SearchAuditLogFrom 同名，
// 格式 yyyy-MM-dd HH:mm:ss 与后端 @DateTimeFormat 口径一致
const timeRange = useTimeRange(listQuery);

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
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
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
</style>
