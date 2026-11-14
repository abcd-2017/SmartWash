<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="厂商">
          <el-select
            v-model="listQuery.vendor"
            placeholder="全部"
            clearable
            filterable
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option v-for="v in vendorOptions" :key="v" :label="v" :value="v" />
          </el-select>
        </el-form-item>
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

    <!-- 目录摘要：与移动端「厂商一级分组 / 模型二级」目录树形态对应 -->
    <div class="tree-summary">共 {{ vendorCount }} 个厂商 / {{ total }} 个模型</div>

    <!-- 数据表格（树形：厂商为一级分组、模型为二级子行） -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="treeData"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        fit
        highlight-current-row
      >
        <el-table-column prop="vendor" label="厂商" min-width="200">
          <template #default="{ row }">
            <template v-if="row._isVendor">
              <span class="vendor-name">{{ row.vendor }}</span>
              <el-tag size="small" type="info" effect="plain" class="vendor-badge">
                {{ row._modelCount }} 个模型
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" min-width="80">
          <template #default="{ row }">{{ row._isVendor ? '-' : row.id }}</template>
        </el-table-column>
        <el-table-column prop="name" label="显示名" min-width="160">
          <template #default="{ row }">{{ row._isVendor ? '-' : row.name }}</template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="Base URL" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono-text">{{ row._isVendor ? row.baseUrl || '-' : row.baseUrl }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="modelId" label="模型标识" min-width="160">
          <template #default="{ row }">
            <span class="mono-text">{{ row._isVendor ? '-' : row.modelId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="API Key" min-width="140">
          <template #default="{ row }">
            {{ row._isVendor ? '-' : row.apiKeyMask || '用户自带' }}
          </template>
        </el-table-column>
        <el-table-column label="优先级" min-width="100" align="right">
          <template #default="{ row }">{{ row._isVendor ? '-' : row.priority }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <template v-if="row._isVendor">
              <el-tag v-if="row._enabledCount === row._modelCount" type="success">全部启用</el-tag>
              <el-tag v-else-if="row._enabledCount === 0" type="info">全部停用</el-tag>
              <el-tag v-else type="warning"
                >{{ row._enabledCount }}/{{ row._modelCount }} 启用</el-tag
              >
            </template>
            <el-tag v-else :type="row.enabled === 1 ? 'success' : 'info'">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="连通性" min-width="150">
          <template #default="{ row }">
            <template v-if="row._isVendor">
              <span class="vendor-aggregate">{{ vendorConnectivityText(row) }}</span>
            </template>
            <template v-else>
              <el-tag v-if="row.lastTestOk === 1" type="success">通过</el-tag>
              <el-tag v-else-if="row.lastTestOk === 0" type="danger">失败</el-tag>
              <el-tag v-else type="info">未测试</el-tag>
            </template>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="180">
          <template #default="{ row }">
            {{ formatTime(row._isVendor ? row._latestUpdatedAt : row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <!-- 厂商父节点为分组行，不提供行级操作 -->
            <template v-if="!row._isVendor">
              <el-button size="small" @click="handleEdit(row)">编辑</el-button>
              <el-tooltip
                :content="
                  row.apiKeyMask ? '' : '该条目未录入 API Key（用户自带密钥模式），无法测试'
                "
                :disabled="!!row.apiKeyMask"
                placement="top"
              >
                <span>
                  <el-button
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
                size="small"
                :type="row.enabled === 1 ? 'warning' : 'success'"
                :loading="togglingId === row.id"
                @click="handleToggle(row)"
              >
                {{ row.enabled === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button
                size="small"
                type="danger"
                :loading="deletingId === row.id"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <!-- 全量树形模式不分页；仅当目录总量超出一次拉取上限时才出现分页器兜底 -->
      <div v-if="total > listQuery.size" class="pagination-bar">
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
    <el-dialog title="批量导入模型目录" v-model="importVisible" width="min(640px, 94vw)">
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
      width="min(600px, 94vw)"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempModel" label-width="min(120px, 94vw)" :rules="rules">
        <el-form-item label="厂商" prop="vendor">
          <el-select
            v-model="tempModel.vendor"
            filterable
            allow-create
            default-first-option
            placeholder="选择已有厂商或输入新厂商（客户端按此分组）"
            style="width: 100%"
          >
            <el-option v-for="v in vendorOptions" :key="v" :label="v" :value="v" />
          </el-select>
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
import { ref, reactive, computed, onMounted } from 'vue';
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
import { isValidationError } from '@/utils/errors';

// 树形目录一次拉全量（目录当前仅 81 条，量级远低于该上限；超出时分页器出现兜底）
const FULL_LOAD_SIZE = 500;

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
    vendor: '',
  },
  buildParams: (q) => ({
    ...q,
    name: q.name || undefined,
    enabled: q.enabled ?? undefined,
    vendor: q.vendor || undefined,
  }),
  errorMsg: '获取模型列表失败',
});
// 页面内换用全量加载（useTableList 为多页共用，不改其本身）：一次拉满，前端按厂商分组构树
listQuery.size = FULL_LOAD_SIZE;

// 厂商下拉选项：取当前加载的数据去重 vendor（清空筛选 = 全部厂商）
const vendorOptions = computed(() => {
  const set = new Set();
  for (const model of modelList.value) {
    if (model.vendor) set.add(model.vendor);
  }
  return Array.from(set);
});

// 平铺记录按 vendor 分组构树：厂商父节点（id 用 vendor-<vendor>，带 _isVendor 标记）+ 模型子行。
// 后端按 priority 升序且种子数据同厂商连续编号，组内保持接口返回顺序；厂商组之间按首次出现顺序。
const treeData = computed(() => {
  const groups = new Map();
  for (const model of modelList.value) {
    const bucket = groups.get(model.vendor);
    if (bucket) {
      bucket.push(model);
    } else {
      groups.set(model.vendor, [model]);
    }
  }
  return Array.from(groups.entries()).map(([vendor, models]) => ({
    id: `vendor-${vendor}`,
    _isVendor: true,
    vendor,
    // 厂商接入点取组内首条（同厂商接入点一致）
    baseUrl: models[0]?.baseUrl || '',
    _modelCount: models.length,
    _enabledCount: models.filter((m) => m.enabled === 1).length,
    _testOkCount: models.filter((m) => m.lastTestOk === 1).length,
    _testFailCount: models.filter((m) => m.lastTestOk === 0).length,
    _latestUpdatedAt: models.reduce(
      (latest, m) => (m.updatedAt && m.updatedAt > latest ? m.updatedAt : latest),
      ''
    ),
    children: models,
  }));
});

const vendorCount = computed(() => treeData.value.length);

// 厂商行连通性聚合文案（仅显示非零分段）
const vendorConnectivityText = (row) => {
  const untested = row._modelCount - row._testOkCount - row._testFailCount;
  const parts = [];
  if (row._testOkCount > 0) parts.push(`通过 ${row._testOkCount}`);
  if (row._testFailCount > 0) parts.push(`失败 ${row._testFailCount}`);
  if (untested > 0) parts.push(`未测 ${untested}`);
  return parts.join(' / ') || '-';
};

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
  vendor: [{ required: true, message: '请选择或输入厂商', trigger: 'change' }],
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
    if (isValidationError(error)) {
      logger.debug('模型表单校验未通过，已拦截提交（错误示于字段红字，不弹全局 toast）');
    } else {
      logger.error(
        '模型提交失败:',
        `type=${dialogType.value}`,
        `id=${tempModel.id ?? '-'}`,
        `name=${tempModel.name}`,
        error
      );
      ElMessage.error(error.message || '操作失败');
    }
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
    // 启停走全量更新体（update 为全量字段语义）：后端 SaveModelFrom 对 vendor/name/baseUrl/modelId
    // 均 @NotBlank，只传 {enabled} 会恒 400 且报错指向无关字段；apiKey 不携带，避免误覆盖密钥
    await updateModel(row.id, {
      id: row.id,
      vendor: row.vendor,
      name: row.name,
      provider: row.provider || 'openai_compat',
      baseUrl: row.baseUrl,
      modelId: row.modelId,
      priority: row.priority,
      enabled: targetEnabled,
    });
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
/* 模型标识 / Base URL 是机器标识符：走 mono（DESIGN.md Mono 13px/400） */
.mono-text {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--text-body);
}

/* 状态列(live 打磨 v2):标签内加圆点,与相邻连通性列(纯标签)区分两套状态语言 */
.model-status-cell .model-status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.model-status-cell .model-status-tag__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  background: currentColor;
}

/* 厂商列(live 打磨 v1):13px 次级文字,与显示名(14px)形成主次层级,减少重复噪音 */
.model-vendor-cell {
  font-size: 13px;
  color: var(--text-secondary);
}

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

.tree-summary {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--text-secondary);
}

.vendor-name {
  font-weight: 600;
}

.vendor-badge {
  margin-left: 8px;
}

.vendor-aggregate {
  font-size: 13px;
  color: var(--text-secondary);
}
</style>
