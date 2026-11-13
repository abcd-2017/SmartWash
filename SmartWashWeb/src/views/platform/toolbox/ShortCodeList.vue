<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="短码">
          <el-input
            v-model="listQuery.code"
            placeholder="请输入短码"
            clearable
            style="width: 180px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="归属人手机号">
          <el-input
            v-model="listQuery.ownerPhone"
            placeholder="请输入归属人手机号"
            clearable
            style="width: 200px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select
            v-model="listQuery.contentType"
            placeholder="全部类型"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in SHORT_CODE_CONTENT_TYPE_OPTIONS"
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
      <el-table v-loading="listLoading" :data="shortCodeList" fit highlight-current-row>
        <el-table-column label="短码" min-width="110">
          <template #default="{ row }">
            <span class="code-cell">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" min-width="100">
          <template #default="{ row }">
            <el-tag :type="shortCodeContentTypeTagType(row.contentType)">
              {{ shortCodeContentTypeText(row.contentType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标链接" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <!-- 私密文本无目标链接（内容不对管理端展示） -->
            <span v-if="row.contentType === 7">—</span>
            <span v-else>{{ row.target }}</span>
          </template>
        </el-table-column>
        <el-table-column label="秘密状态" min-width="100">
          <template #default="{ row }">
            <el-tag v-if="row.contentType === 7" :type="secretStateTagType(row.secretState)">
              {{ secretStateText(row.secretState) }}
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="归属人手机号" min-width="130">
          <template #default="{ row }">{{ row.ownerPhone || '-' }}</template>
        </el-table-column>
        <el-table-column label="可见性" min-width="90">
          <template #default="{ row }">
            <el-tag :type="shortCodeVisibilityTagType(row.isPublic)">
              {{ shortCodeVisibilityText(row.isPublic) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" min-width="170">
          <template #default="{ row }">
            <el-tag v-if="isExpired(row.expireAt)" type="danger">已过期</el-tag>
            <span v-else>{{ formatTime(row.expireAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="点击数" min-width="90" align="right">
          <template #default="{ row }">{{ row.clickCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="handleDetail(row)">详情</el-button>
            <el-button
              link
              size="small"
              type="danger"
              :loading="deletingId === row.id"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
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

    <!-- 详情抽屉：生命周期字段全量 + 最近访问明细 -->
    <el-drawer v-model="detailVisible" title="短码详情" size="560px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="短码">{{ detail.code }}</el-descriptions-item>
            <el-descriptions-item label="类型">
              <el-tag :type="shortCodeContentTypeTagType(detail.contentType)">
                {{ shortCodeContentTypeText(detail.contentType) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="目标链接">
              <span v-if="detail.contentType === 7">—</span>
              <span v-else class="break-all">{{ detail.target }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.contentType === 7" label="秘密状态">
              <el-tag :type="secretStateTagType(detail.secretState)">
                {{ secretStateText(detail.secretState) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.contentType === 7" label="焚毁时间">
              {{ formatTime(detail.burnedAt) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.contentType === 7" label="内容">
              <span class="privacy-note">不对管理端展示（隐私红线）</span>
            </el-descriptions-item>
            <el-descriptions-item label="归属人ID">
              {{ detail.ownerUserId ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="归属人手机号">
              {{ detail.ownerPhone || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="可见性">
              <el-tag :type="shortCodeVisibilityTagType(detail.isPublic)">
                {{ shortCodeVisibilityText(detail.isPublic) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="解锁时间">
              {{ formatTime(detail.unlockAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="过期时间">
              <el-tag v-if="isExpired(detail.expireAt)" type="danger">已过期</el-tag>
              <span v-else>{{ formatTime(detail.expireAt) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="访问上限">
              {{ detail.maxVisits ?? '不限' }}
            </el-descriptions-item>
            <el-descriptions-item label="点击数">{{
              detail.clickCount ?? '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatTime(detail.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">
              {{ formatTime(detail.updatedAt) }}
            </el-descriptions-item>
          </el-descriptions>

          <h4 class="visit-title">最近访问（{{ detail.recentVisits?.length || 0 }} 条）</h4>
          <el-table v-if="detail.recentVisits?.length" :data="detail.recentVisits" size="small">
            <el-table-column prop="ipHash" label="IP 摘要" min-width="90" />
            <el-table-column
              prop="userAgent"
              label="UserAgent"
              min-width="150"
              show-overflow-tooltip
            />
            <el-table-column prop="referer" label="Referer" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.referer || '-' }}</template>
            </el-table-column>
            <el-table-column label="访问时间" min-width="160">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无访问记录" :image-size="60" />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';
import { getShortCodeList, getShortCodeDetail, deleteShortCode } from '@/api/toolbox';
import { formatTime } from '@/utils/format';
import logger from '@/utils/logger';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';
import {
  SHORT_CODE_CONTENT_TYPE_OPTIONS,
  shortCodeContentTypeText,
  shortCodeContentTypeTagType,
  shortCodeVisibilityText,
  shortCodeVisibilityTagType,
  secretStateText,
  secretStateTagType,
} from '@/constants/dict';

// 过期判断：expireAt 为空表示永不过期
const isExpired = (expireAt) => !!expireAt && dayjs(expireAt).isBefore(dayjs());

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: shortCodeList,
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
  fetchApi: getShortCodeList,
  baseQuery: {
    code: '',
    ownerPhone: '',
    contentType: null,
  },
  buildParams: (q) => ({
    ...q,
    code: q.code || undefined,
    ownerPhone: q.ownerPhone || undefined,
    contentType: q.contentType || undefined,
  }),
  errorMsg: '获取短码列表失败',
});

// 行内「删除」按钮 loading，防连点
const deletingId = ref(null);

// 详情抽屉
const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref(null);

const handleDetail = async (row) => {
  detailVisible.value = true;
  detailLoading.value = true;
  detail.value = null;
  try {
    detail.value = await getShortCodeDetail(row.id);
  } catch (error) {
    // 页面自有 catch（列表拉取失败已由 useTableList/拦截器留痕，这里只兜底详情请求）
    logger.error('短码详情获取失败:', `id=${row.id}`, error);
    ElMessage.error(error.message || '获取短码详情失败');
    detailVisible.value = false;
  } finally {
    detailLoading.value = false;
  }
};

// 删除短码
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除短码「${row.code}」吗？删除后立即失效且不可恢复。`);
  if (!confirmed) return;
  deletingId.value = row.id;
  try {
    await deleteShortCode(row.id);
    // 低频写操作成功（短链码与目标 URL 非凭证，可入日志；ownerPhone 不主动传参）
    logger.info('短码删除成功', `id=${row.id}`, `code=${row.code}`);
    ElMessage.success('删除成功');
    fetchData(); // 刷新当前页
  } catch (error) {
    logger.error('短码删除失败:', `id=${row.id}`, `code=${row.code}`, error);
    ElMessage.error(error.message || '删除失败');
  } finally {
    deletingId.value = null;
  }
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
/* 短码是机器标识符:按 DESIGN.md mono 规则用等宽字体,弱底块与输入控件同语言 */
.code-cell {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--border-subtle);
  font-family: var(--font-mono);
  font-size: 12.5px;
  font-weight: 500;
  letter-spacing: 0.02em;
  color: var(--text-input);
}

.visit-title {
  margin: 20px 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.break-all {
  word-break: break-all;
}

/* 隐私红线提示：管理端永不展示私密文本内容（含密文） */
.privacy-note {
  color: var(--text-muted);
  font-size: 12px;
}
</style>
