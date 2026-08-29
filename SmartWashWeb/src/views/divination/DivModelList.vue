<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="模型名称">
          <el-input
            v-model="listQuery.name"
            placeholder="输入模型名称"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="listQuery.enabled"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="handleCreate">新增模型</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table v-loading="listLoading" :data="modelList" fit highlight-current-row>
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column prop="name" label="显示名" min-width="160" />
        <el-table-column prop="provider" label="供应商" min-width="120" />
        <el-table-column prop="baseUrl" label="Base URL" min-width="220" />
        <el-table-column prop="modelId" label="模型标识" min-width="160" />
        <el-table-column label="API Key" min-width="160">
          <template #default="{ row }">{{ row.apiKeyMask || '***' }}</template>
        </el-table-column>
        <el-table-column label="优先级" min-width="100">
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
            <span v-else style="color: #94a3b8">未测试</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="primary" @click="handleTest(row)" :loading="testingId === row.id">
              测试
            </el-button>
            <el-button
              size="small"
              :type="row.enabled === 1 ? 'warning' : 'success'"
              @click="handleToggle(row)"
            >
              {{ row.enabled === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
      :title="dialogType === 'create' ? '新增模型' : '编辑模型'"
      v-model="dialogVisible"
      width="600px"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempModel" label-width="120px" :rules="rules">
        <el-form-item label="显示名" prop="name">
          <el-input v-model="tempModel.name" placeholder="如 GLM-4.7" />
        </el-form-item>
        <el-form-item label="供应商" prop="provider">
          <el-input v-model="tempModel.provider" placeholder="如 openai_compat" />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="tempModel.baseUrl" placeholder="https://api.example.com/v1" />
        </el-form-item>
        <el-form-item label="模型标识" prop="modelId">
          <el-input v-model="tempModel.modelId" placeholder="如 glm-4.7" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="tempModel.apiKey"
            type="password"
            placeholder="粘贴 API Key（列表仅显示掩码，永不回显明文）"
            show-password
          />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="tempModel.priority" :min="1" :max="999" controls-position="right" />
          <span class="form-tip">越小越优先</span>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="tempModel.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确认</el-button>
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
  testModelConnectivity,
} from '@/api/divination';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';

const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref('create');
const testingId = ref(null);

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
  name: '',
  provider: 'openai_compat',
  baseUrl: '',
  modelId: '',
  apiKey: '',
  priority: 100,
  enabled: 1,
});

const rules = reactive({
  name: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  provider: [{ required: true, message: '请输入供应商', trigger: 'blur' }],
  baseUrl: [
    { required: true, message: '请输入 Base URL', trigger: 'blur' },
    { pattern: /^https?:\/\/.+/, message: 'URL 需以 http(s):// 开头', trigger: 'blur' },
  ],
  modelId: [{ required: true, message: '请输入模型标识', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
});

onMounted(() => {
  fetchData();
});

const handleCreate = () => {
  dialogType.value = 'create';
  dialogVisible.value = true;
  Object.assign(tempModel, {
    id: null,
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
    name: row.name,
    provider: row.provider,
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

const submitForm = async () => {
  try {
    await formRef.value.validate();
    // 编辑时若未填写 apiKey，则不传该字段（后端不更新密钥）
    const submitData = { ...tempModel };
    if (dialogType.value === 'edit' && !submitData.apiKey) {
      delete submitData.apiKey;
    }
    if (dialogType.value === 'create') {
      await createModel(submitData);
      ElMessage.success('新增成功');
    } else {
      await updateModel(submitData.id, submitData);
      ElMessage.success('修改成功');
    }
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '操作失败');
  }
};

const handleDelete = async (row) => {
  const confirmed = await useConfirm(`确认删除模型「${row.name}」吗？`, '警告');
  if (!confirmed) return;
  try {
    await deleteModel(row.id);
    ElMessage.success('删除成功');
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '删除失败');
  }
};

const handleTest = async (row) => {
  testingId.value = row.id;
  try {
    await testModelConnectivity(row.id);
    ElMessage.success('连通性测试通过');
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '连通性测试失败');
  } finally {
    testingId.value = null;
  }
};

const handleToggle = async (row) => {
  const action = row.enabled === 1 ? '停用' : '启用';
  const confirmed = await useConfirm(`确认${action}模型「${row.name}」吗？`, '提示');
  if (!confirmed) return;
  try {
    await updateModel(row.id, { enabled: row.enabled === 1 ? 0 : 1 });
    ElMessage.success(`${action}成功`);
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || `${action}失败`);
  }
};
</script>

<style scoped>
@import '@/assets/pages.css';

.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #94a3b8;
}
</style>
