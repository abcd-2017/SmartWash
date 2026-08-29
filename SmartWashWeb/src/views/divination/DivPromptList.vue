<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="术数方法">
          <el-select
            v-model="listQuery.method"
            placeholder="全部"
            clearable
            style="width: 140px"
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
            placeholder="输入版本号"
            clearable
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="listQuery.status"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option label="草稿" :value="0" />
            <el-option label="激活" :value="1" />
            <el-option label="退役" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="handleCreate">新增 Prompt</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table v-loading="listLoading" :data="promptList" fit highlight-current-row>
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column label="术数方法" min-width="120">
          <template #default="{ row }">{{ methodText(row.method) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本号" min-width="140" />
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="System Prompt" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.systemPrompt }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 1"
              size="small"
              type="success"
              @click="handleActivate(row)"
            >
              激活
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
      :title="dialogType === 'create' ? '新增 Prompt' : '编辑 Prompt'"
      v-model="dialogVisible"
      width="700px"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempPrompt" label-width="120px" :rules="rules">
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
            placeholder="系统提示词"
          />
        </el-form-item>
        <el-form-item label="领域方法文本" prop="methodText">
          <el-input
            v-model="tempPrompt.methodText"
            type="textarea"
            :rows="4"
            placeholder="领域分析方法文本"
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
        <el-button type="primary" @click="submitForm">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import {
  getPromptList,
  createPrompt,
  updatePrompt,
  activatePrompt,
} from '@/api/divination';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';

const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref('create');

// 术数方法选项（与后端 method 枚举对齐）
const methodOptions = [
  { value: 'liuyao', label: '六爻' },
  { value: 'meihua', label: '梅花易数' },
  { value: 'qimen', label: '奇门遁甲' },
  { value: 'liuren', label: '大六壬' },
];

const methodText = (method) => {
  return methodOptions.find((m) => m.value === method)?.label || method;
};

// 状态映射
const statusText = (status) => {
  return { 0: '草稿', 1: '激活', 2: '退役' }[status] || '未知';
};

const statusTagType = (status) => {
  return { 0: 'info', 1: 'success', 2: 'warning' }[status] || 'info';
};

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
  try {
    await formRef.value.validate();
    const submitData = { ...tempPrompt };
    // outputConfig 从字符串解析为 JSON
    if (submitData.outputConfig) {
      try {
        submitData.outputConfig = JSON.parse(submitData.outputConfig);
      } catch {
        ElMessage.warning('输出配置 JSON 格式不正确，已忽略');
        delete submitData.outputConfig;
      }
    }
    if (dialogType.value === 'create') {
      await createPrompt(submitData);
      ElMessage.success('新增成功');
    } else {
      await updatePrompt(submitData.id, submitData);
      ElMessage.success('修改成功');
    }
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '操作失败');
  }
};

const handleActivate = async (row) => {
  const confirmed = await useConfirm(
    `确认激活 Prompt「${methodText(row.method)} ${row.version}」吗？同方法的其他激活版本将自动退役。`,
    '提示'
  );
  if (!confirmed) return;
  try {
    await activatePrompt(row.id);
    ElMessage.success('激活成功');
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '激活失败');
  }
};

const handleDelete = async (row) => {
  const confirmed = await useConfirm(
    `确认删除 Prompt「${methodText(row.method)} ${row.version}」吗？`,
    '警告'
  );
  if (!confirmed) return;
  try {
    // 后端未提供删除接口，此处通过标记退役实现（或扩展 delete 接口）
    await updatePrompt(row.id, { status: 2 });
    ElMessage.success('已标记为退役');
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '操作失败');
  }
};
</script>

<style scoped>
@import '@/assets/pages.css';
</style>
