<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="标题">
          <el-input
            v-model="listQuery.title"
            placeholder="输入标题模糊搜索"
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
          <el-button type="primary" @click="handleCreate">新增入口</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table v-loading="listLoading" :data="entryList" fit highlight-current-row>
        <el-table-column prop="sort" label="排序" min-width="70" align="center">
          <template #default="{ row }">{{ row.sort ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="标题" min-width="180">
          <template #default="{ row }">
            <div class="title-cell">
              <span class="title-main">{{ row.title || '-' }}</span>
              <span v-if="row.description" class="title-sub">{{ row.description }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="图标 / 色组" min-width="160">
          <template #default="{ row }">
            <div class="icon-color-cell">
              <!-- 端上按 icon/color key 白名单映射，这里直接展示 key 便于两端对齐 -->
              <el-tag size="small">{{ row.icon || 'default' }}</el-tag>
              <span
                class="color-dot"
                :style="{ backgroundColor: workbenchEntryColorHex(row.color) }"
              />
              <el-tag size="small" type="info">{{ row.color || '-' }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="target" label="跳转 target" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="target-cell">
              <span class="target-text">{{ row.target || '-' }}</span>
              <el-button
                v-if="row.target"
                link
                type="primary"
                size="small"
                @click="handleCopyTarget(row.target)"
              >
                复制
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角标" min-width="90">
          <template #default="{ row }">{{ row.badge || '-' }}</template>
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
        <!-- 更新时间（AdminWorkbenchEntryVo 含 createdAt/updatedAt，兜底 createdAt 防 后端裁剪字段） -->
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) }}</template>
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
      :title="dialogType === 'create' ? '新增工作台入口' : '编辑工作台入口'"
      v-model="dialogVisible"
      width="750px"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempEntry" label-width="100px" :rules="rules">
        <!-- 基本内容 -->
        <el-divider content-position="left">基本内容</el-divider>

        <el-form-item label="入口标识" prop="entryKey">
          <el-input
            v-model="tempEntry.entryKey"
            placeholder="如 laundry_calendar"
            :disabled="dialogType === 'edit'"
            maxlength="64"
          />
          <div class="form-tip">
            业务唯一标识，小写字母开头，仅含小写字母/数字/_/-{{
              dialogType === 'edit' ? '；编辑时不可修改' : ''
            }}
          </div>
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input
            v-model="tempEntry.title"
            placeholder="入口显示标题"
            maxlength="32"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="副标题">
          <el-input
            v-model="tempEntry.description"
            placeholder="选填"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="角标">
          <el-input
            v-model="tempEntry.badge"
            placeholder="选填，如 HOT / 新"
            maxlength="16"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="排序" prop="sort">
          <el-input-number
            v-model="tempEntry.sort"
            :min="0"
            :max="9999"
            controls-position="right"
          />
          <span class="form-tip">数字越小越靠前</span>
        </el-form-item>

        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="tempEntry.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="tempEntry.remark"
            type="textarea"
            :rows="2"
            placeholder="选填，管理备注"
            maxlength="255"
          />
        </el-form-item>

        <!-- 跳转与样式 -->
        <el-divider content-position="left">跳转与样式</el-divider>

        <el-form-item label="图标" prop="icon">
          <el-select v-model="tempEntry.icon" placeholder="请选择图标" style="width: 100%">
            <el-option
              v-for="opt in WORKBENCH_ENTRY_ICON_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <div class="form-tip">端上为白名单映射，未知值显示默认图标</div>
        </el-form-item>

        <el-form-item label="色组" prop="color">
          <el-select v-model="tempEntry.color" placeholder="请选择色组" style="width: 100%">
            <el-option
              v-for="opt in WORKBENCH_ENTRY_COLOR_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            >
              <span class="color-dot" :style="{ backgroundColor: opt.hex }" />
              <span>{{ opt.label }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="跳转 target" prop="target">
          <el-input
            v-model="tempEntry.target"
            placeholder="smartwash://... 或 https://..."
            maxlength="512"
            show-word-limit
          />
          <div class="form-tip">支持 smartwash://... 或 https://...；可选值参见「跳转路由」页</div>
          <el-select
            v-model="routePick"
            class="route-select"
            allow-create
            filterable
            clearable
            :loading="routeOptionsLoading"
            placeholder="从路由字典选择（自动填充 target，也可手输）"
            @change="handleRoutePick"
          >
            <el-option
              v-for="item in routeOptions"
              :key="item.id"
              :label="`${item.name}（${item.host}）`"
              :value="`smartwash://${item.host}`"
            />
          </el-select>
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
  getWorkbenchEntryList,
  addWorkbenchEntry,
  updateWorkbenchEntry,
  deleteWorkbenchEntry,
} from '@/api/workbench';
import { getDeeplinkRouteList } from '@/api/deeplink';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';
import {
  WORKBENCH_ENTRY_ICON_OPTIONS,
  WORKBENCH_ENTRY_COLOR_OPTIONS,
  workbenchEntryColorHex,
} from '@/constants/dict';

const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref('create');
const submitting = ref(false); // 确认按钮 loading，防连点
const togglingId = ref(null); // 行内启停开关 loading，防连点
const deletingId = ref(null); // 行内删除按钮 loading，防连点

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: entryList,
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
  fetchApi: getWorkbenchEntryList,
  baseQuery: {
    title: '',
    enabled: null,
  },
  buildParams: (q) => ({
    ...q,
    title: q.title || undefined,
    enabled: q.enabled ?? undefined,
  }),
  errorMsg: '获取工作台入口列表失败',
});

// 表单数据（字段名已与后端 AdminWorkbenchEntryVo / Add·UpdateWorkbenchEntryFrom 对齐）
const tempEntry = reactive({
  id: null,
  entryKey: '',
  title: '',
  description: '',
  icon: 'default',
  color: 'green',
  target: '',
  badge: '',
  enabled: 1,
  sort: 0,
  remark: '',
});

// 与后端 From 校验对齐的正则：entryKey 小写字母开头 2-63 位；target 须为 smartwash:// 或 http(s):// 开头
const ENTRY_KEY_PATTERN = /^[a-z][a-z0-9_-]{2,63}$/;
const TARGET_PATTERN = /^(smartwash:\/\/|https?:\/\/)\S+$/;

// 验证规则
const rules = reactive({
  entryKey: [
    { required: true, message: '请输入业务唯一标识', trigger: 'blur' },
    {
      pattern: ENTRY_KEY_PATTERN,
      message: '须为小写字母开头、3-64 位小写字母/数字/_/-',
      trigger: 'blur',
    },
  ],
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 32, message: '标题不能超过 32 个字符', trigger: 'blur' },
  ],
  target: [
    { required: true, message: '请输入跳转 target', trigger: 'blur' },
    {
      pattern: TARGET_PATTERN,
      message: '须以 smartwash:// 或 http(s):// 开头且不含空白',
      trigger: 'blur',
    },
    { max: 512, message: 'target 不能超过 512 个字符', trigger: 'blur' },
  ],
});

// ---------- 路由字典下拉（target 快捷填充） ----------

const routeOptions = ref([]);
const routeOptionsLoading = ref(false);
const routePick = ref('');

// 拉取启用中的路由拼成 smartwash://host 候选项；字典量级小，仅取启用中的前 100 条
const loadRouteOptions = async () => {
  routeOptionsLoading.value = true;
  try {
    const res = await getDeeplinkRouteList({ page: 1, size: 100, enabled: 1 });
    routeOptions.value = res.records || [];
  } catch {
    // 字典加载失败不阻塞主流程，target 仍可手输
    routeOptions.value = [];
  } finally {
    routeOptionsLoading.value = false;
  }
};

// 选中/手输候选项后回填 target
const handleRoutePick = (val) => {
  if (val) {
    tempEntry.target = val;
  }
};

// ---------- 行内启停 ----------

// 编辑场景按行数据组装完整更新体（update 为全量字段语义，避免只传 id+enabled 清空其余字段；
// entryKey 后端不可修改，更新体不携带）
const buildRowUpdatePayload = (row, overrides = {}) => ({
  id: row.id,
  title: row.title,
  description: row.description,
  icon: row.icon,
  color: row.color,
  target: row.target,
  badge: row.badge,
  enabled: row.enabled,
  sort: row.sort,
  remark: row.remark,
  ...overrides,
});

const handleToggleEnabled = async (row) => {
  // el-switch 触发 change 时 v-model 已同步更新为目标值（Element Plus 源码 UPDATE 先于 CHANGE emit），
  // row.enabled 即用户意图值；prev 仅用于失败回滚；成功后无需再写 row.enabled
  const prev = row.enabled === 1 ? 0 : 1;
  const next = row.enabled;
  togglingId.value = row.id;
  try {
    await updateWorkbenchEntry(buildRowUpdatePayload(row, { enabled: next }));
    ElMessage.success(next === 1 ? '已启用' : '已停用');
  } catch (error) {
    row.enabled = prev; // 失败回滚开关状态
    ElMessage.error(error.message || '状态切换失败');
  } finally {
    togglingId.value = null;
  }
};

// ---------- 复制 target ----------

const handleCopyTarget = async (text) => {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('已复制');
  } catch {
    ElMessage.error('复制失败，请手动选择复制');
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
  Object.assign(tempEntry, {
    id: null,
    entryKey: '',
    title: '',
    description: '',
    icon: 'default',
    color: 'green',
    target: '',
    badge: '',
    enabled: 1,
    sort: 0,
    remark: '',
  });
  routePick.value = '';
  loadRouteOptions();
};

// 打开编辑弹窗
const handleEdit = (row) => {
  dialogType.value = 'edit';
  dialogVisible.value = true;
  Object.assign(tempEntry, { ...row });
  routePick.value = '';
  loadRouteOptions();
};

// 弹窗关闭 — 清除验证状态
const handleDialogClosed = () => {
  formRef.value?.resetFields();
  formRef.value?.clearValidate();
};

// 按契约组装提交体（entryKey 仅新增时携带——后端 UpdateWorkbenchEntryFrom 无此字段且禁止修改）
const buildSubmitPayload = () => {
  const payload = {
    title: tempEntry.title,
    description: tempEntry.description,
    icon: tempEntry.icon,
    color: tempEntry.color,
    target: tempEntry.target,
    badge: tempEntry.badge,
    enabled: tempEntry.enabled,
    sort: tempEntry.sort,
    remark: tempEntry.remark,
  };
  if (dialogType.value === 'create') {
    return { entryKey: tempEntry.entryKey, ...payload };
  }
  return { id: tempEntry.id, ...payload };
};

// 提交表单
const submitForm = async () => {
  submitting.value = true;
  try {
    await formRef.value.validate();

    if (dialogType.value === 'create') {
      await addWorkbenchEntry(buildSubmitPayload());
      ElMessage.success('新增成功');
    } else {
      await updateWorkbenchEntry(buildSubmitPayload());
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

// 删除入口
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除入口「${row.title}」吗？删除后端上不再展示。`);
  if (!confirmed) return;
  deletingId.value = row.id;
  try {
    await deleteWorkbenchEntry(row.id);
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
/* 标题 + 副标题两行单元格 */
.title-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.title-main {
  color: #0f172a;
  font-weight: 500;
}

.title-sub {
  color: #94a3b8;
  font-size: 12px;
}

.icon-color-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 色块圆点（表格与下拉选项共用） */
.color-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
  vertical-align: -1px;
}

.target-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.target-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 表单项下的辅助说明 */
.form-tip {
  width: 100%;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

/* 路由字典快捷选择下拉 */
.route-select {
  width: 100%;
  margin-top: 4px;
}
</style>
