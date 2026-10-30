<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="Host">
          <el-input
            v-model="listQuery.host"
            placeholder="输入 host 模糊搜索"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="listQuery.enabled" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="primary" @click="handleCreate">新增路由</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table v-loading="listLoading" :data="routeList" fit highlight-current-row>
        <el-table-column label="Host" min-width="200">
          <template #default="{ row }">
            <div class="host-cell">
              <span class="host-main">{{ row.host || '-' }}</span>
              <span v-if="row.name" class="host-sub">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="平台" min-width="120">
          <template #default="{ row }">
            <el-tag :type="deeplinkPlatformTagType(row.platform)">
              {{ deeplinkPlatformText(row.platform) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="登录要求" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.requireLogin === 1 ? 'warning' : 'info'">
              {{ row.requireLogin === 1 ? '需登录' : '免登录' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="启停" min-width="90">
          <template #default="{ row }">
            <!-- 行内切换：成功前乐观更新，失败回滚并提示 -->
            <el-switch
              v-model="row.enabled"
              :active-value="1"
              :inactive-value="0"
              :loading="togglingId === row.id"
              @change="handleToggleEnabled(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      :title="dialogType === 'create' ? '新增跳转路由' : '编辑跳转路由'"
      v-model="dialogVisible"
      width="600px"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempRoute" label-width="100px" :rules="rules">
        <el-form-item label="Host" prop="host">
          <el-input
            v-model="tempRoute.host"
            placeholder="如 login"
            :disabled="dialogType === 'edit'"
            maxlength="64"
          />
          <div class="form-tip">
            smartwash://host 的 host 段{{ dialogType === 'edit' ? '，编辑时不可修改' : '' }}
          </div>
        </el-form-item>

        <el-form-item label="名称" prop="name">
          <el-input
            v-model="tempRoute.name"
            placeholder="路由显示名称"
            maxlength="64"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="平台" prop="platform">
          <el-select v-model="tempRoute.platform" placeholder="请选择平台" style="width: 100%">
            <el-option
              v-for="opt in DEEPLINK_PLATFORM_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="需登录" prop="requireLogin">
          <el-switch v-model="tempRoute.requireLogin" :active-value="1" :inactive-value="0" />
          <span class="form-tip-inline">开启后端上跳转前会校验登录态</span>
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="tempRoute.description"
            type="textarea"
            :rows="2"
            placeholder="选填，说明该路由用途"
            maxlength="255"
          />
        </el-form-item>

        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="tempRoute.enabled" :active-value="1" :inactive-value="0" />
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
  getDeeplinkRouteList,
  addDeeplinkRoute,
  updateDeeplinkRoute,
  deleteDeeplinkRoute,
} from '@/api/deeplink';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';
import {
  DEEPLINK_PLATFORM_OPTIONS,
  deeplinkPlatformText,
  deeplinkPlatformTagType,
} from '@/constants/dict';

const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref('create');
const submitting = ref(false); // 确认按钮 loading，防连点
const togglingId = ref(null); // 行内启停开关 loading，防连点
const deletingId = ref(null); // 行内删除按钮 loading，防连点

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: routeList,
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
  fetchApi: getDeeplinkRouteList,
  baseQuery: {
    host: '',
    enabled: null,
  },
  buildParams: (q) => ({
    ...q,
    host: q.host || undefined,
    enabled: q.enabled ?? undefined,
  }),
  errorMsg: '获取跳转路由列表失败',
});

// 表单数据（字段名已与后端 DeeplinkRouteVo / Add·UpdateDeeplinkRouteFrom 对齐）
const tempRoute = reactive({
  id: null,
  host: '',
  name: '',
  platform: 3,
  requireLogin: 0,
  description: '',
  enabled: 1,
});

// host 段规则：小写字母开头，仅小写字母/数字/中划线，2-64 位（与后端 AddDeeplinkRouteFrom 对齐）
const HOST_PATTERN = /^[a-z][a-z0-9-]{1,63}$/;

// 验证规则
const rules = reactive({
  host: [
    { required: true, message: '请输入 host', trigger: 'blur' },
    {
      pattern: HOST_PATTERN,
      message: 'host 需为小写字母开头，仅含小写字母/数字/中划线，2-64 位',
      trigger: 'blur',
    },
  ],
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { max: 64, message: '名称不能超过 64 个字符', trigger: 'blur' },
  ],
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
});

// ---------- 行内启停 ----------

// 编辑场景按行数据组装完整更新体（update 为全量字段语义，避免只传 id+enabled 清空其余字段；
// host 后端不可修改，更新体不携带）
const buildRowUpdatePayload = (row, overrides = {}) => ({
  id: row.id,
  name: row.name,
  platform: row.platform,
  requireLogin: row.requireLogin,
  description: row.description,
  enabled: row.enabled,
  ...overrides,
});

const handleToggleEnabled = async (row) => {
  const prev = row.enabled;
  const next = row.enabled === 1 ? 0 : 1;
  togglingId.value = row.id;
  try {
    await updateDeeplinkRoute(buildRowUpdatePayload(row, { enabled: next }));
    row.enabled = next;
    ElMessage.success(next === 1 ? '已启用' : '已停用');
  } catch (error) {
    row.enabled = prev; // 失败回滚开关状态
    ElMessage.error(error.message || '状态切换失败');
  } finally {
    togglingId.value = null;
  }
};

// ---------- 初始化与弹窗 ----------

onMounted(() => {
  fetchData();
});

// 打开新增弹窗
const handleCreate = () => {
  dialogType.value = 'create';
  dialogVisible.value = true;
  Object.assign(tempRoute, {
    id: null,
    host: '',
    name: '',
    platform: 3,
    requireLogin: 0,
    description: '',
    enabled: 1,
  });
};

// 打开编辑弹窗
const handleEdit = (row) => {
  dialogType.value = 'edit';
  dialogVisible.value = true;
  Object.assign(tempRoute, { ...row });
};

// 弹窗关闭 — 清除验证状态
const handleDialogClosed = () => {
  formRef.value?.resetFields();
  formRef.value?.clearValidate();
};

// 按契约组装提交体（host 仅新增时携带——后端 UpdateDeeplinkRouteFrom 无此字段且禁止修改）
const buildSubmitPayload = () => {
  const payload = {
    name: tempRoute.name,
    platform: tempRoute.platform,
    requireLogin: tempRoute.requireLogin,
    description: tempRoute.description,
    enabled: tempRoute.enabled,
  };
  if (dialogType.value === 'create') {
    return { host: tempRoute.host, ...payload };
  }
  return { id: tempRoute.id, ...payload };
};

// 提交表单
const submitForm = async () => {
  submitting.value = true;
  try {
    await formRef.value.validate();

    if (dialogType.value === 'create') {
      await addDeeplinkRoute(buildSubmitPayload());
      ElMessage.success('新增成功');
    } else {
      await updateDeeplinkRoute(buildSubmitPayload());
      ElMessage.success('修改成功');
    }
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '操作失败');
  } finally {
    submitting.value = false;
  }
};

// 删除路由
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(
    `确认删除路由「${row.host}」吗？删除后相关入口的字典快捷选择将不可用。`
  );
  if (!confirmed) return;
  deletingId.value = row.id;
  try {
    await deleteDeeplinkRoute(row.id);
    ElMessage.success('删除成功');
    fetchData(); // 刷新当前页
  } catch (error) {
    ElMessage.error(error.message || '删除失败');
  } finally {
    deletingId.value = null;
  }
};
</script>

<style scoped>
/* host + 名称两行单元格 */
.host-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.host-main {
  color: #0f172a;
  font-weight: 500;
  font-family: 'SF Mono', Menlo, Consolas, monospace;
}

.host-sub {
  color: #94a3b8;
  font-size: 12px;
}

/* 表单项下的辅助说明 */
.form-tip {
  width: 100%;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

/* 开关同行的行内说明 */
.form-tip-inline {
  margin-left: 8px;
  font-size: 12px;
  color: #64748b;
}
</style>
