<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="术数方法">
          <el-select
            v-model="listQuery.method"
            placeholder="全部"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option
              v-for="m in methodOptions"
              :key="m.value"
              :label="m.label"
              :value="m.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号">
          <el-input
            v-model="listQuery.version"
            placeholder="请输入版本号"
            clearable
            style="width: 140px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="listQuery.status"
            placeholder="全部"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option label="草稿" :value="0" />
            <el-option label="激活" :value="1" />
            <el-option label="退役" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleCreate">新增 Prompt</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="promptList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column label="术数方法" min-width="120">
          <template #default="{ row }">{{ methodText(row.method) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本号" min-width="140" />
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="divPromptStatusTagType(row.status)">
              {{ divPromptStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="System Prompt" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.systemPrompt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 1"
              link
              size="small"
              type="success"
              :loading="activatingId === row.id"
              @click="handleActivate(row)"
            >
              激活
            </el-button>
            <el-button
              link
              size="small"
              :loading="deletingId === row.id"
              @click="handleDelete(row)"
            >
              退役
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      :title="dialogType === 'create' ? '新增 Prompt' : '编辑 Prompt'"
      v-model="dialogVisible"
      width="min(700px, 94vw)"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempPrompt" label-width="min(120px, 94vw)" :rules="rules">
        <el-form-item label="术数方法" prop="method">
          <el-select v-model="tempPrompt.method" placeholder="选择术数方法" style="width: 100%">
            <el-option
              v-for="m in methodOptions"
              :key="m.value"
              :label="m.label"
              :value="m.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号" prop="version">
          <el-input v-model="tempPrompt.version" placeholder="如 liuyao-v1.2" />
        </el-form-item>
        <el-form-item label="System Prompt" prop="systemPrompt">
          <el-input
            v-model="tempPrompt.systemPrompt"
            type="textarea"
            :rows="6"
            placeholder="请输入系统提示词"
          />
        </el-form-item>
        <el-form-item label="领域方法文本" prop="methodText">
          <el-input
            v-model="tempPrompt.methodText"
            type="textarea"
            :rows="4"
            placeholder="请输入领域分析方法"
          />
        </el-form-item>
        <el-form-item label="输出配置" prop="outputConfig">
          <el-input
            v-model="tempPrompt.outputConfig"
            type="textarea"
            :rows="3"
            placeholder='JSON 格式，如 {"temperature": 0.7, "max_tokens": 2000}'
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="tempPrompt.remark" placeholder="版本说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import {
  DIV_METHOD_OPTIONS as methodOptions,
  divinationMethodText as methodText,
} from '@/constants/dict';
import { getPromptList, createPrompt, updatePrompt, activatePrompt } from '@/api/divination';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';
import { divPromptStatusTagType, divPromptStatusText } from '@/constants/dict';
import logger from '@/utils/logger';
import { isValidationError } from '@/utils/errors';

const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref('create');
const submitting = ref(false); // 弹窗确认按钮 loading，防连点
const activatingId = ref(null); // 行内「激活」按钮 loading
const deletingId = ref(null); // 行内「删除」按钮 loading

// 术数方法选项（与后端 method 枚举对齐）

// 状态映射统一引 constants/dict.js（DIV_PROMPT_STATUS_*），页面不再自写 switch
const {
  list: promptList,
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
  fetchApi: getPromptList,
  baseQuery: {
    method: '',
    version: '',
    status: null,
  },
  buildParams: (q) => ({
    ...q,
    method: q.method || undefined,
    version: q.version || undefined,
    status: q.status ?? undefined,
  }),
  errorMsg: '获取 Prompt 列表失败',
});

const tempPrompt = reactive({
  id: null,
  method: '',
  version: '',
  systemPrompt: '',
  methodText: '',
  outputConfig: '',
  remark: '',
});

const rules = reactive({
  method: [{ required: true, message: '请选择术数方法', trigger: 'change' }],
  version: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: '请输入 System Prompt', trigger: 'blur' }],
  methodText: [{ required: true, message: '请输入领域方法文本', trigger: 'blur' }],
  // 非法 JSON 直接阻止提交，避免用户填写的配置被静默丢弃
  outputConfig: [
    {
      validator: (rule, value, callback) => {
        if (!value) return callback();
        try {
          JSON.parse(value);
          callback();
        } catch {
          callback(new Error('输出配置需为合法 JSON'));
        }
      },
      trigger: 'blur',
    },
  ],
});

onMounted(() => {
  fetchData();
});

const handleCreate = () => {
  dialogType.value = 'create';
  dialogVisible.value = true;
  Object.assign(tempPrompt, {
    id: null,
    method: '',
    version: '',
    systemPrompt: '',
    methodText: '',
    outputConfig: '',
    remark: '',
  });
};

const handleEdit = (row) => {
  dialogType.value = 'edit';
  dialogVisible.value = true;
  Object.assign(tempPrompt, {
    id: row.id,
    method: row.method,
    version: row.version,
    systemPrompt: row.systemPrompt || '',
    methodText: row.methodText || '',
    outputConfig: row.outputConfig ? JSON.stringify(row.outputConfig) : '',
    remark: row.remark || '',
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
    const submitData = { ...tempPrompt };
    // outputConfig 从字符串解析为 JSON（合法性已由表单规则前置校验）
    if (submitData.outputConfig) {
      submitData.outputConfig = JSON.parse(submitData.outputConfig);
    }
    if (dialogType.value === 'create') {
      await createPrompt(submitData);
      logger.info(
        'Prompt 新增成功',
        `method=${tempPrompt.method}`,
        `version=${tempPrompt.version}`
      );
      ElMessage.success('新增成功');
    } else {
      await updatePrompt(submitData.id, submitData);
      logger.info(
        'Prompt 编辑成功',
        `id=${submitData.id}`,
        `method=${tempPrompt.method}`,
        `version=${tempPrompt.version}`
      );
      ElMessage.success('修改成功');
    }
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    // validate() 校验失败的特征是错误对象带 fields；与接口失败区分级别（校验失败不属系统错误）
    if (isValidationError(error)) {
      logger.debug('Prompt 表单校验未通过，已拦截提交（错误示于字段红字，不弹全局 toast）');
    } else {
      logger.error(
        'Prompt 提交失败:',
        `type=${dialogType.value}`,
        `id=${tempPrompt.id ?? '-'}`,
        `method=${tempPrompt.method}`,
        `version=${tempPrompt.version}`,
        error
      );
      ElMessage.error(error.message || '操作失败');
    }
  } finally {
    submitting.value = false;
  }
};

const handleActivate = async (row) => {
  const confirmed = await useConfirm(
    `确认激活 Prompt「${methodText(row.method)} ${row.version}」吗？同方法的其他激活版本将自动退役。`,
    '提示'
  );
  if (!confirmed) return;
  activatingId.value = row.id;
  try {
    await activatePrompt(row.id);
    logger.info(
      'Prompt 激活成功',
      `id=${row.id}`,
      `method=${row.method}`,
      `version=${row.version}`
    );
    ElMessage.success('激活成功');
    fetchData();
  } catch (error) {
    logger.error(
      'Prompt 激活失败:',
      `id=${row.id}`,
      `method=${row.method}`,
      `version=${row.version}`,
      error
    );
    ElMessage.error(error.message || '激活失败');
  } finally {
    activatingId.value = null;
  }
};

const handleDelete = async (row) => {
  const confirmed = await useConfirm(
    `确认将 Prompt「${methodText(row.method)} ${row.version}」退役吗？退役后不再参与调用，可重新编辑激活。`,
    '提示'
  );
  if (!confirmed) return;
  deletingId.value = row.id;
  try {
    // 退役为软删除语义（status=2）；后端另有 DELETE /admin/div/prompts/{id} 硬删接口未接，
    // 是否放开硬删待产品确认，勿在未对齐前混用两种删除语义
    await updatePrompt(row.id, { status: 2 });
    logger.info(
      'Prompt 退役成功',
      `id=${row.id}`,
      `method=${row.method}`,
      `version=${row.version}`
    );
    ElMessage.success('Prompt 已退役');
    fetchData();
  } catch (error) {
    logger.error(
      'Prompt 退役失败:',
      `id=${row.id}`,
      `method=${row.method}`,
      `version=${row.version}`,
      error
    );
    ElMessage.error(error.message || '操作失败');
  } finally {
    deletingId.value = null;
  }
};
</script>
