<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="标题">
          <el-input
            v-model="listQuery.title"
            placeholder="输入标题模糊搜索"
            clearable
            style="width: 200px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select
            v-model="listQuery.type"
            placeholder="全部类型"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in NOTICE_TYPE_OPTIONS"
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
        <el-form-item class="filter-end">
          <el-button type="primary" @click="handleCreate">发送站内信</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="noticeList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column prop="id" label="ID" min-width="80" align="center">
          <template #default="{ row }">{{ row.id ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.title || '-' }}</template>
        </el-table-column>
        <el-table-column label="类型" min-width="110">
          <template #default="{ row }">
            <!-- 类型标签(live 打磨 v2):圆点标记 + 体量微调,圆点继承标签语义色 -->
            <div class="notice-type-cell">
              <el-tag size="small" :type="noticeTypeTagType(row.type)">
                {{ noticeTypeText(row.type) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="深链" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="deep-link-cell">
              <!-- 深链是机器标识符：有链接时走 mono（DESIGN.md），占位句保持 sans -->
              <span class="deep-link-text" :class="{ 'deep-link-mono': row.deepLink }">
                {{ row.deepLink || '纯文案' }}
              </span>
              <el-button
                v-if="row.deepLink"
                link
                type="primary"
                size="small"
                @click="handleCopyDeepLink(row.deepLink)"
              >
                复制
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="发送时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="送达统计" min-width="180" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              :loading="statLoadingId === row.id"
              @click="handleShowStat(row)"
            >
              已读率
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

    <!-- 发送公告弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      title="发送站内信"
      width="min(640px, 94vw)"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempNotice" label-width="90px" :rules="rules">
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="tempNotice.title"
            placeholder="公告标题"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="类型" prop="type">
          <el-select v-model="tempNotice.type" placeholder="请选择类型" style="width: 100%">
            <el-option
              v-for="opt in NOTICE_TYPE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <div class="form-tip">
            系统通知（维护/版本/安全）走警示样式；运营公告（活动/规则）走绿色样式
          </div>
        </el-form-item>

        <el-form-item label="正文" prop="content">
          <el-input
            v-model="tempNotice.content"
            type="textarea"
            :rows="6"
            placeholder="公告正文，支持换行"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="深链">
          <el-input
            v-model="tempNotice.deepLink"
            placeholder="选填，如 smartwash://notice/1 或 https://..."
            maxlength="512"
            show-word-limit
          />
          <div class="form-tip">留空则为纯文案公告，端上不提供跳转入口</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">发送</el-button>
      </template>
    </el-dialog>

    <!-- 送达统计弹窗 -->
    <el-dialog v-model="statVisible" title="送达统计" width="min(420px, 94vw)">
      <div v-loading="statLoading" class="stat-panel">
        <div class="stat-panel__item">
          <span class="stat-panel__label">发送总数（全平台用户）</span>
          <span class="stat-panel__value">{{ formatThousands(stat.totalUsers) }}</span>
        </div>
        <div class="stat-panel__item">
          <span class="stat-panel__label">已读人数</span>
          <span class="stat-panel__value stat-panel__value--primary">
            {{ formatThousands(stat.readCount) }}
          </span>
        </div>
        <div class="stat-panel__item">
          <span class="stat-panel__label">已读率</span>
          <el-tag :type="noticeReadRateTagType(stat.readRateText)" size="large">
            {{ stat.readRateText || '-' }}
          </el-tag>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="statVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getNoticeList, publishNotice, getNoticeReadStat } from '@/api/notice';
import { formatTime, formatThousands } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import {
  NOTICE_TYPE_OPTIONS,
  noticeTypeTagType,
  noticeTypeText,
  noticeReadRateTagType,
} from '@/constants/dict';
import logger from '@/utils/logger';
import { isValidationError } from '@/utils/errors';

const formRef = ref(null);
const dialogVisible = ref(false);
const submitting = ref(false);

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: noticeList,
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
  fetchApi: getNoticeList,
  baseQuery: {
    title: '',
    type: null,
  },
  buildParams: (q) => ({
    ...q,
    title: q.title || undefined,
    type: q.type || undefined,
  }),
  errorMsg: '获取站内信列表失败',
});

// 发送表单（字段名与后端 AddNoticeFrom 对齐；creatorAdminId 由后端从登录上下文写入，前端不传）
const tempNotice = reactive({
  title: '',
  type: 'SYSTEM',
  content: '',
  deepLink: '',
});

// 与后端 From 校验对齐：深链可空，非空须以 smartwash:// 或 http(s):// 开头且不含空白
const DEEP_LINK_PATTERN = /^(smartwash:\/\/|https?:\/\/)\S*$/;

const rules = reactive({
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过 100 个字符', trigger: 'blur' },
  ],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  content: [
    { required: true, message: '请输入正文', trigger: 'blur' },
    { max: 2000, message: '正文不能超过 2000 个字符', trigger: 'blur' },
  ],
  deepLink: [
    {
      validator: (rule, value, callback) => {
        if (value && !DEEP_LINK_PATTERN.test(value)) {
          callback(new Error('须以 smartwash:// 或 http(s):// 开头且不含空白'));
          return;
        }
        if (value && value.length > 512) {
          callback(new Error('深链不能超过 512 个字符'));
          return;
        }
        callback();
      },
      trigger: 'blur',
    },
  ],
});

// ---------- 送达统计 ----------

const statVisible = ref(false);
const statLoading = ref(false);
const statLoadingId = ref(null);
const stat = reactive({
  totalUsers: 0,
  readCount: 0,
  readRateText: '-',
});

const handleShowStat = async (row) => {
  statLoadingId.value = row.id;
  statVisible.value = true;
  statLoading.value = true;
  try {
    const data = (await getNoticeReadStat(row.id)) || {};
    stat.totalUsers = Number(data.totalUsers) || 0;
    stat.readCount = Number(data.readCount) || 0;
    stat.readRateText = data.readRateText || '-';
  } catch (error) {
    logger.error('公告送达统计加载失败:', `id=${row.id}`, error);
    ElMessage.error(error.message || '获取送达统计失败');
    statVisible.value = false;
  } finally {
    statLoading.value = false;
    statLoadingId.value = null;
  }
};

// ---------- 发送 ----------

const handleCreate = () => {
  dialogVisible.value = true;
  Object.assign(tempNotice, {
    title: '',
    type: 'SYSTEM',
    content: '',
    deepLink: '',
  });
};

const handleDialogClosed = () => {
  formRef.value?.resetFields();
  formRef.value?.clearValidate();
};

const submitForm = async () => {
  submitting.value = true;
  try {
    await formRef.value.validate();
    await publishNotice({
      title: tempNotice.title,
      type: tempNotice.type,
      content: tempNotice.content,
      deepLink: tempNotice.deepLink || undefined,
    });
    logger.info('站内信发送成功', `title=${tempNotice.title}`, `type=${tempNotice.type}`);
    ElMessage.success('发送成功');
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    // validate() 校验失败的特征是错误对象带 fields；与接口失败区分级别（校验失败不属系统错误）
    if (isValidationError(error)) {
      logger.debug('站内信表单校验未通过，已拦截提交（错误示于字段红字，不弹全局 toast）');
    } else {
      logger.error(
        '站内信发送失败:',
        `title=${tempNotice.title}`,
        `type=${tempNotice.type}`,
        error
      );
      ElMessage.error(error.message || '发送失败');
    }
  } finally {
    submitting.value = false;
  }
};

// ---------- 深链复制 ----------

const handleCopyDeepLink = async (text) => {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('已复制');
  } catch (error) {
    logger.error('公告深链复制失败:', error);
    ElMessage.error('复制失败，请手动选择复制');
  }
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
/* 类型标签(live 打磨 v2):圆点标记提升可扫读性,圆点继承标签语义色(currentColor) */
.notice-type-cell .el-tag {
  gap: 6px;
  padding: 4px 10px;
  font-size: 13px;
}

.notice-type-cell .el-tag::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

/* 深链列：文本 + 复制按钮 */
.deep-link-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.deep-link-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 深链值走 mono（DESIGN.md Mono 13px/400）：仅在有链接时应用，占位句保持 sans */
.deep-link-mono {
  font-family: var(--font-mono);
  font-size: 13px;
}

/* 表单项下的辅助说明 */
.form-tip {
  width: 100%;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
}

/* 送达统计面板：竖排三项，等宽数字 */
.stat-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 0;
}

.stat-panel__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-panel__label {
  font-size: 13px;
  color: var(--text-secondary);
}

.stat-panel__value {
  font-size: 20px; /* 对齐 headline 档（送达统计数值强调展示，原 18px off-ramp） */
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.stat-panel__value--primary {
  color: var(--color-primary);
}
</style>
