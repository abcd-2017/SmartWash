<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="模型名称">
          <el-input
            v-model="listQuery.name"
            placeholder="输入模型名称"
            clearable
            style="width: 200px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="listQuery.enabled"
            placeholder="全部"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleCreate">新增模型</el-button>
          <el-button @click="importVisible = true">批量导入</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="modelList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column prop="vendor" label="厂商" min-width="120" />
        <el-table-column prop="name" label="显示名" min-width="160" />
        <el-table-column prop="baseUrl" label="Base URL" min-width="220" show-overflow-tooltip />
        <el-table-column prop="modelId" label="模型标识" min-width="160" />
        <el-table-column label="API Key" min-width="140">
          <template #default="{ row }">{{ row.apiKeyMask || '用户自带' }}</template>
        </el-table-column>
        <el-table-column label="优先级" min-width="100" align="right">
          <template #default="{ row }">{{ row.priority }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="连通性" min-width="120">
          <template #default="{ row }">
            <el-tag v-if="row.lastTestOk === 1" type="success">通过</el-tag>
            <el-tag v-else-if="row.lastTestOk === 0" type="danger">失败</el-tag>
            <el-tag v-else type="info">未测试</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-tooltip
              :content="row.apiKeyMask ? '' : '该条目未录入 API Key（用户自带密钥模式），无法测试'"
              :disabled="!!row.apiKeyMask"
              placement="top"
            >
              <span>
                <el-button
                  link
                  size="small"
                  type="primary"
                  @click="handleTest(row)"
                  :loading="testingId === row.id"
                  :disabled="!row.apiKeyMask"
                >
                  测试
                </el-button>
              </span>
            </el-tooltip>
            <el-button
              link
              size="small"
              :type="row.enabled === 1 ? 'warning' : 'success'"
              :loading="togglingId === row.id"
              @click="handleToggle(row)"
            >
              {{ row.enabled === 1 ? '停用' : '启用' }}
            </el-button>
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

    <!-- 批量导入弹窗 -->
    <el-dialog title="批量导入模型目录" v-model="importVisible" width="640px">
      <p class="import-tip">
        粘贴 JSON 数组（每条含 <code>vendor / name / baseUrl / modelId</code>，可选
        <code>priority</code>）， 非法条目自动跳过。目录密钥由用户自带，此处无需填 API Key。
      </p>
      <el-input
        v-model="importJson"
        type="textarea"
        :rows="12"
        placeholder='[{"vendor":"DeepSeek","name":"DeepSeek Flash","baseUrl":"https://api.deepseek.com","modelId":"deepseek-flash","priority":10}]'
      />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">导入</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      :title="dialogType === 'create' ? '新增模型' : '编辑模型'"
      v-model="dialogVisible"
      width="600px"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempModel" label-width="120px" :rules="rules">
        <el-form-item label="厂商" prop="vendor">
          <el-input v-model="tempModel.vendor" placeholder="如 DeepSeek（客户端一级分组）" />
        </el-form-item>
        <el-form-item label="显示名" prop="name">
          <el-input v-model="tempModel.name" placeholder="如 GLM-5.3" />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input
            v-model="tempModel.baseUrl"
            placeholder="https://api.example.com/v1（OpenAI 兼容端点）"
          />
        </el-form-item>
        <el-form-item label="模型标识" prop="modelId">
          <el-input v-model="tempModel.modelId" placeholder="如 glm-5.3" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="tempModel.apiKey"
            type="password"
            placeholder="留空（推荐）：密钥由用户自带，仅录入后可对该条目做连通性测试"
            show-password
          />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number
            v-model="tempModel.priority"
            :min="1"
            :max="999"
            controls-position="right"
          />
          <span class="form-tip">越小越优先</span>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="tempModel.enabled" :active-value="1" :inactive-value="0" />
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
  getModelList,
  createModel,
  updateModel,
  deleteModel,
  importModels,
  testModelConnectivity,
} from '@/api/divination';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';
import logger from '@/utils/logger';

const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref('create');
const testingId = ref(null);
const submitting = ref(false); // 弹窗确认按钮 loading，防连点
const togglingId = ref(null); // 行内「停用/启用」按钮 loading
const deletingId = ref(null); // 行内「删除」按钮 loading
const importVisible = ref(false); // 批量导入弹窗
const importing = ref(false); // 批量导入提交 loading
const importJson = ref(''); // 粘贴的目录 JSON 原文

const {
  list: modelList,
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
  fetchApi: getModelList,
  baseQuery: {
    name: '',
    enabled: null,
  },
  buildParams: (q) => ({
    ...q,
    name: q.name || undefined,
    enabled: q.enabled ?? undefined,
  }),
  errorMsg: '获取模型列表失败',
});

const tempModel = reactive({
  id: null,
  vendor: '',
  name: '',
  provider: 'openai_compat',
  baseUrl: '',
  modelId: '',
  apiKey: '',
  priority: 100,
  enabled: 1,
});

const rules = reactive({
  vendor: [{ required: true, message: '请输入厂商', trigger: 'blur' }],
  name: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  baseUrl: [
    { required: true, message: '请输入 Base URL', trigger: 'blur' },
    { pattern: /^https?:\/\/.+/, message: 'URL 需以 http(s):// 开头', trigger: 'blur' },
  ],
  modelId: [{ required: true, message: '请输入模型标识', trigger: 'blur' }],
  // API Key 可留空（BYOK 模式密钥由用户自带）；录入后仅用于该条目的连通性测试
});

onMounted(() => {
  fetchData();
});

const handleCreate = () => {
  dialogType.value = 'create';
  dialogVisible.value = true;
  Object.assign(tempModel, {
    id: null,
    vendor: '',
    name: '',
    provider: 'openai_compat',
    baseUrl: '',
    modelId: '',
    apiKey: '',
    priority: 100,
    enabled: 1,
  });
};

const handleEdit = (row) => {
  dialogType.value = 'edit';
  dialogVisible.value = true;
  // 编辑时 API Key 不回显明文，留空表示不更换
  Object.assign(tempModel, {
    id: row.id,
    vendor: row.vendor,
    name: row.name,
    provider: row.provider || 'openai_compat',
    baseUrl: row.baseUrl,
    modelId: row.modelId,
    apiKey: '',
    priority: row.priority,
    enabled: row.enabled,
  });
};

const handleDialogClosed = () => {
  formRef.value?.resetFields();
  formRef.value?.clearValidate();
};

// 批量导入：前端只做 JSON 解析与基础结构校验，字段级校验与去重由后端处理
const submitImport = async () => {
  let items;
  try {
    items = JSON.parse(importJson.value);
  } catch {
    ElMessage.error('JSON 格式不正确，请检查后重试');
    return;
  }
  if (!Array.isArray(items) || items.length === 0) {
    ElMessage.error('需要为非空 JSON 数组');
    return;
  }
  importing.value = true;
  try {
    const res = await importModels(items);
    logger.info('模型目录批量导入完成', `total=${items.length}`, `imported=${res}`);
    ElMessage.success(`导入完成：成功 ${res} 条 / 共 ${items.length} 条`);
    importVisible.value = false;
    importJson.value = '';
    fetchData();
  } catch (error) {
    logger.error('模型目录批量导入失败:', error);
    ElMessage.error(error.message || '导入失败');
  } finally {
    importing.value = false;
  }
};

const submitForm = async () => {
  submitting.value = true;
  try {
    await formRef.value.validate();
    // 编辑时若未填写 apiKey，则不传该字段（后端不更新密钥）
    const submitData = { ...tempModel };
    if (dialogType.value === 'edit' && !submitData.apiKey) {
      delete submitData.apiKey;
    }
    if (dialogType.value === 'create') {
      await createModel(submitData);
      // 密钥红线：日志只记业务键，绝不打印 apiKey/submitData
      logger.info('模型新增成功', `name=${tempModel.name}`, `modelId=${tempModel.modelId}`);
      ElMessage.success('新增成功');
    } else {
      await updateModel(submitData.id, submitData);
      logger.info(
        '模型编辑成功',
        `id=${submitData.id}`,
        `name=${tempModel.name}`,
        `modelId=${tempModel.modelId}`
      );
      ElMessage.success('修改成功');
    }
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    // validate() 校验失败的特征是错误对象带 fields；与接口失败区分级别（校验失败不属系统错误）
    if (error?.fields) {
      logger.debug('模型表单校验未通过，已拦截提交');
    } else {
      logger.error(
        '模型提交失败:',
        `type=${dialogType.value}`,
        `id=${tempModel.id ?? '-'}`,
        `name=${tempModel.name}`,
        error
      );
    }
    ElMessage.error(error.message || '操作失败');
  } finally {
    submitting.value = false;
  }
};

const handleDelete = async (row) => {
  const confirmed = await useConfirm(`确认删除模型「${row.name}」吗？`, '警告');
  if (!confirmed) return;
  deletingId.value = row.id;
  try {
    await deleteModel(row.id);
    logger.info('模型删除成功', `id=${row.id}`, `name=${row.name}`);
    ElMessage.success('删除成功');
    fetchData();
  } catch (error) {
    logger.error('模型删除失败:', `id=${row.id}`, `name=${row.name}`, error);
    ElMessage.error(error.message || '删除失败');
  } finally {
    deletingId.value = null;
  }
};

const handleTest = async (row) => {
  testingId.value = row.id;
  try {
    await testModelConnectivity(row.id);
    logger.info('模型连通性测试通过', `id=${row.id}`, `name=${row.name}`);
    ElMessage.success('连通性测试通过');
    fetchData();
  } catch (error) {
    logger.error('模型连通性测试失败:', `id=${row.id}`, `name=${row.name}`, error);
    ElMessage.error(error.message || '连通性测试失败');
  } finally {
    testingId.value = null;
  }
};

const handleToggle = async (row) => {
  const action = row.enabled === 1 ? '停用' : '启用';
  const confirmed = await useConfirm(`确认${action}模型「${row.name}」吗？`, '提示');
  if (!confirmed) return;
  togglingId.value = row.id;
  try {
    const targetEnabled = row.enabled === 1 ? 0 : 1;
    await updateModel(row.id, { enabled: targetEnabled });
    logger.info(
      `模型${action}成功`,
      `id=${row.id}`,
      `name=${row.name}`,
      `enabled=${targetEnabled}`
    );
    ElMessage.success(`${action}成功`);
    fetchData();
  } catch (error) {
    logger.error(`模型${action}失败:`, `id=${row.id}`, `name=${row.name}`, error);
    ElMessage.error(error.message || `${action}失败`);
  } finally {
    togglingId.value = null;
  }
};
</script>

<style scoped>
.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.import-tip {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
}

.import-tip code {
  padding: 1px 4px;
  background: var(--bg-subtle);
  border-radius: 6px; /* 对齐 rounded.sm（原 3px 低于阶梯最小档） */
  font-size: 12px;
}
</style>
