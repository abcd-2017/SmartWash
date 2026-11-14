<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="分类">
          <el-select
            v-model="listQuery.categoryId"
            placeholder="全部分类"
            clearable
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option
              v-for="cat in categoryOptions"
              :key="cat.id"
              :label="cat.name"
              :value="cat.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="listQuery.status"
            placeholder="全部状态"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in DINING_RECIPE_STATUS_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="菜名">
          <el-input
            v-model="listQuery.keyword"
            placeholder="输入菜名模糊搜索"
            clearable
            style="width: 200px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleCreate">新增菜谱</el-button>
          <el-button @click="importDialogVisible = true">批量导入</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="recipeList"
        fit
        highlight-current-row
        :empty-text="hasRecipeFilter ? undefined : '还没有菜谱：可逐个新增，或用 Excel 批量导入'"
      >
        <el-table-column label="封面" width="90">
          <template #default="{ row }">
            <el-image
              v-if="row.coverUrl"
              :src="row.coverUrl"
              :preview-src-list="[row.coverUrl]"
              preview-teleported
              fit="cover"
              class="cover-thumb"
            />
            <span v-else class="cover-missing">未上传</span>
          </template>
        </el-table-column>
        <el-table-column label="菜名" min-width="150">
          <template #default="{ row }">
            <span class="recipe-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" min-width="110">
          <template #default="{ row }">
            <el-tag v-if="row.categoryName" effect="plain">{{ row.categoryName }}</el-tag>
            <span v-else class="text-muted">未分类</span>
          </template>
        </el-table-column>
        <el-table-column label="食材" min-width="200">
          <template #default="{ row }">
            <template v-if="row.ingredients && row.ingredients.length">
              <el-tag
                v-for="ing in row.ingredients.slice(0, 3)"
                :key="ing"
                size="small"
                type="info"
                effect="plain"
                class="ingredient-tag"
              >
                {{ ing }}
              </el-tag>
              <span v-if="row.ingredients.length > 3" class="text-muted">
                +{{ row.ingredients.length - 3 }}
              </span>
            </template>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="diningRecipeSourceTagType(row.source)" effect="light">
              {{ diningRecipeSourceText(row.source) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上架" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :loading="statusSwitchingId === row.id"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
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
      :title="dialogType === 'create' ? '新增菜谱' : '编辑菜谱'"
      v-model="dialogVisible"
      width="640px"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempRecipe" label-width="90px" :rules="rules">
        <el-form-item label="菜名" prop="name">
          <el-input
            v-model="tempRecipe.name"
            placeholder="如 番茄炒蛋"
            maxlength="64"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select
            v-model="tempRecipe.categoryId"
            placeholder="未分类"
            clearable
            style="width: 220px"
          >
            <el-option label="未分类" :value="0" />
            <el-option
              v-for="cat in categoryOptions"
              :key="cat.id"
              :label="cat.name"
              :value="cat.id"
            />
          </el-select>
          <span class="form-tip-inline">分类在「分类管理」页维护</span>
          <span v-if="categoryOptionsLoadFailed" class="form-tip-inline form-tip-inline--error">
            分类选项加载失败，请刷新页面重试
          </span>
        </el-form-item>
        <el-form-item label="封面" prop="coverObjectKey">
          <div class="cover-field">
            <el-upload
              class="cover-uploader"
              :show-file-list="false"
              accept="image/png,image/jpeg,image/webp"
              :before-upload="handleCoverBeforeUpload"
              :http-request="handleCoverUpload"
            >
              <img
                v-if="coverPreviewUrl"
                :src="coverPreviewUrl"
                class="cover-preview"
                alt="封面预览"
              />
              <div v-else class="cover-empty" :class="{ uploading: coverUploading }">
                <el-icon><Plus /></el-icon>
                <span>{{ coverUploading ? '上传中…' : '上传封面' }}</span>
              </div>
            </el-upload>
            <el-button
              v-if="dialogType === 'create' && tempRecipe.coverObjectKey"
              size="small"
              :disabled="coverUploading"
              @click="handleRemoveCover"
            >
              移除封面
            </el-button>
          </div>
          <!-- 编辑态封面字段后端约定 null=不修改，故不提供「移除」动作，只能重新上传覆盖 -->
          <div v-if="dialogType === 'edit'" class="form-tip">如需更换封面请直接重新上传</div>
          <div v-else class="form-tip">选填：png / jpeg / webp，不超过 5MB；用户端菜谱卡展示</div>
        </el-form-item>
        <el-form-item label="食材" prop="ingredients">
          <div class="ingredient-editor">
            <el-tag
              v-for="(ing, idx) in tempRecipe.ingredients"
              :key="`${ing}-${idx}`"
              closable
              @close="tempRecipe.ingredients.splice(idx, 1)"
            >
              {{ ing }}
            </el-tag>
            <el-input
              v-model="ingredientInput"
              placeholder="输入食材名后回车添加（如 西红柿）"
              maxlength="32"
              style="width: 240px"
              @keydown.enter.prevent="addIngredient"
            />
          </div>
          <div class="form-tip">购买清单按所有被选菜谱的食材名并集去重生成</div>
        </el-form-item>
        <el-form-item label="步骤" prop="steps">
          <el-input
            v-model="tempRecipe.steps"
            type="textarea"
            :rows="5"
            placeholder="做法步骤，换行分段"
            maxlength="20000"
            show-word-limit
          />
          <!-- 后端 updateRecipe 对 steps 用 hasText 判空：留空不会清空原内容，需显式告知 -->
          <div class="form-tip">编辑时留空表示不修改原步骤内容</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确认</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗（下载模板 → 上传解析预览 → 确认 → 逐行结果报告） -->
    <RecipeImportDialog v-model="importDialogVisible" @success="fetchData" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import RecipeImportDialog from './components/RecipeImportDialog.vue';
import {
  getDiningRecipeList,
  addDiningRecipe,
  updateDiningRecipe,
  deleteDiningRecipe,
  changeDiningRecipeStatus,
  uploadDiningRecipeCover,
} from '@/api/dining';
import { diningCategoryOptionsCache } from '@/utils/optionCache';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';
import { formatTime } from '@/utils/format';
import {
  DINING_RECIPE_STATUS_OPTIONS,
  diningRecipeSourceText,
  diningRecipeSourceTagType,
} from '@/constants/dict';
import logger from '@/utils/logger';

const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref('create');
const submitting = ref(false); // 确认按钮 loading，防连点
const deletingId = ref(null); // 行内删除按钮 loading，防连点
const statusSwitchingId = ref(null); // 行内上下架开关 loading，防连点

// 批量导入弹窗可见性（导入成功后由对话框 emit success → fetchData 刷新列表）
const importDialogVisible = ref(false);

// 列表查询与分页：统一由 useTableList 承载
const {
  list: recipeList,
  total,
  listLoading,
  listQuery,
  pageSizes,
  fetchData,
  fetchDataAfterDelete,
  handleSearch,
  resetSearch,
  handlePageChange,
  handleSizeChange,
} = useTableList({
  fetchApi: getDiningRecipeList,
  baseQuery: {
    categoryId: null,
    status: null,
    keyword: '',
  },
  buildParams: (q) => ({
    ...q,
    categoryId: q.categoryId || undefined,
    status: q.status ?? undefined,
    keyword: q.keyword || undefined,
  }),
  errorMsg: '获取菜谱列表失败',
});

// 空态区分：无任何筛选条件视为「库空」引导新增/批量导入，有筛选时保留默认空态文案
const hasRecipeFilter = computed(
  () =>
    (listQuery.categoryId ?? '') !== '' ||
    (listQuery.status ?? '') !== '' ||
    Boolean((listQuery.keyword || '').trim())
);

// 分类下拉：走 optionCache 全量缓存（分类页增删改后自动失效）
const categoryOptions = ref([]);
// 失败状态位：为 true 时表单分类项下提示重试，避免用户对着空下拉无从判断
const categoryOptionsLoadFailed = ref(false);
const loadCategoryOptions = async () => {
  try {
    categoryOptions.value = await diningCategoryOptionsCache.load();
    categoryOptionsLoadFailed.value = false;
  } catch (error) {
    logger.warn('菜谱分类选项加载失败:', error);
    categoryOptions.value = [];
    categoryOptionsLoadFailed.value = true;
  }
};

// 表单数据（字段名已与后端 DiningRecipeVo / Add·UpdateDiningRecipeFrom 对齐；
// categoryId 用 0 表示未分类：后端约定 0 归一为 null 落库，null 则表示「不修改」）
const tempRecipe = reactive({
  id: null,
  name: '',
  categoryId: 0,
  coverObjectKey: null,
  ingredients: [],
  steps: '',
});

const rules = reactive({
  name: [
    { required: true, message: '请输入菜名', trigger: 'blur' },
    { max: 64, message: '菜名不能超过 64 个字符', trigger: 'blur' },
  ],
});

// ---------- 封面上传 ----------

const coverUploading = ref(false);
const coverPreviewUrl = ref(null); // 预览直链（新上传用 ObjectURL，编辑回显用 coverUrl）
const coverObjectUrl = ref(null); // 本地生成的 ObjectURL，换图/移除/关闭时统一回收

// 统一回收本地预览 ObjectURL：换图、移除、弹窗关闭都走这里，避免泄漏
const revokeCoverPreview = () => {
  if (coverObjectUrl.value) {
    URL.revokeObjectURL(coverObjectUrl.value);
    coverObjectUrl.value = null;
  }
};

const COVER_OK_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

// 上传前置校验：类型与大小拦截（与后端 RecipeImageValidator 校验同语义）
const handleCoverBeforeUpload = (file) => {
  if (!COVER_OK_TYPES.includes(file.type)) {
    ElMessage.error('仅支持 JPG / PNG / WebP 图片');
    return false;
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB');
    return false;
  }
  return true;
};

// 自定义上传：后端 /admin/dining/recipes/cover，成功回填对象键（coverUrl 由列表接口按对象键拼装）
const handleCoverUpload = async ({ file }) => {
  coverUploading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', file);
    const objectKey = await uploadDiningRecipeCover(formData);
    tempRecipe.coverObjectKey = objectKey;
    // 用本地 ObjectURL 立即预览（不依赖后端拼直链）；换图前回收旧 URL 防泄漏
    revokeCoverPreview();
    coverObjectUrl.value = URL.createObjectURL(file);
    coverPreviewUrl.value = coverObjectUrl.value;
    ElMessage.success('封面已上传');
  } catch (error) {
    ElMessage.error(error.message || '封面上传失败');
  } finally {
    coverUploading.value = false;
  }
};

// 移除封面（本次弹窗内生效，保存后随 update 提交——后端约定封面字段 null=不修改，
// 故「移除」仅在新增场景生效；编辑场景如需换图直接重新上传覆盖）
const handleRemoveCover = () => {
  tempRecipe.coverObjectKey = null;
  revokeCoverPreview();
  coverPreviewUrl.value = null;
};

// ---------- 食材编辑 ----------

const ingredientInput = ref('');

const addIngredient = () => {
  const value = ingredientInput.value.trim();
  if (!value) return;
  if (tempRecipe.ingredients.includes(value)) {
    ElMessage.warning(`食材「${value}」已存在`);
    return;
  }
  if (tempRecipe.ingredients.length >= 100) {
    ElMessage.warning('食材数量已达上限（100 项）');
    return;
  }
  tempRecipe.ingredients.push(value);
  ingredientInput.value = '';
};

// ---------- 初始化与弹窗 ----------

onMounted(() => {
  fetchData();
  loadCategoryOptions();
});

// 打开新增弹窗
const handleCreate = () => {
  dialogType.value = 'create';
  dialogVisible.value = true;
  Object.assign(tempRecipe, {
    id: null,
    name: '',
    categoryId: 0,
    coverObjectKey: null,
    ingredients: [],
    steps: '',
  });
  ingredientInput.value = '';
  revokeCoverPreview();
  coverPreviewUrl.value = null;
};

// 打开编辑弹窗（编辑为全量字段语义，直接携带行数据；未分类回显哨兵 0）
const handleEdit = (row) => {
  dialogType.value = 'edit';
  dialogVisible.value = true;
  Object.assign(tempRecipe, {
    id: row.id,
    name: row.name,
    categoryId: row.categoryId ?? 0,
    coverObjectKey: row.coverObjectKey,
    ingredients: [...(row.ingredients || [])],
    steps: row.steps || '',
  });
  ingredientInput.value = '';
  // 回显用后端直链（非 ObjectURL），先回收上次本地预览再切换
  revokeCoverPreview();
  coverPreviewUrl.value = row.coverUrl || null;
};

// 弹窗关闭 — 清除验证状态并回收本地预览 ObjectURL
const handleDialogClosed = () => {
  formRef.value?.resetFields();
  formRef.value?.clearValidate();
  revokeCoverPreview();
  coverPreviewUrl.value = null;
};

// 提交表单（编辑为全量字段语义，与后端 UpdateDiningRecipeFrom 对齐）
const submitForm = async () => {
  submitting.value = true;
  try {
    await formRef.value.validate();

    const payload = {
      name: tempRecipe.name,
      categoryId: tempRecipe.categoryId ?? 0,
      ingredients: tempRecipe.ingredients,
      steps: tempRecipe.steps,
      coverObjectKey: tempRecipe.coverObjectKey,
    };
    if (dialogType.value === 'create') {
      await addDiningRecipe(payload);
      logger.info('菜谱新增成功', `name=${tempRecipe.name}`);
      ElMessage.success('新增成功，菜谱已上架');
    } else {
      await updateDiningRecipe({ id: tempRecipe.id, ...payload });
      logger.info('菜谱编辑成功', `id=${tempRecipe.id}`, `name=${tempRecipe.name}`);
      ElMessage.success('修改成功');
    }
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    // validate() 校验失败的特征是错误对象带 fields；与接口失败区分级别
    if (error?.fields) {
      logger.debug('菜谱表单校验未通过，已拦截提交');
    } else {
      logger.error(
        '菜谱提交失败:',
        `type=${dialogType.value}`,
        `name=${tempRecipe.name}`,
        `id=${tempRecipe.id ?? '-'}`,
        error
      );
    }
    ElMessage.error(error.message || '操作失败');
  } finally {
    submitting.value = false;
  }
};

// ---------- 行内上下架 ----------

// 上下架：专用状态接口；失败时恢复开关原值
const handleStatusChange = async (row, val) => {
  const targetStatus = val ? 1 : 0;
  statusSwitchingId.value = row.id;
  try {
    await changeDiningRecipeStatus(row.id, targetStatus);
    row.status = targetStatus;
    logger.info('菜谱上下架成功', `id=${row.id}`, `status=${targetStatus}`);
    ElMessage.success(targetStatus === 1 ? '已上架' : '已下架');
  } catch (error) {
    logger.error('菜谱上下架失败:', `id=${row.id}`, error);
    ElMessage.error(error.message || '操作失败');
  } finally {
    statusSwitchingId.value = null;
  }
};

// 删除菜谱（物理删除；下架菜谱不再出现在用户端上架列表，历史点单订单不受影响）
const handleDelete = async (row) => {
  const confirmed = await useConfirm(
    `确认删除菜谱「${row.name}」吗？删除后用户端将不再展示该菜谱（历史点单记录不受影响）。`
  );
  if (!confirmed) return;
  deletingId.value = row.id;
  try {
    await deleteDiningRecipe(row.id);
    logger.info('菜谱删除成功', `id=${row.id}`, `name=${row.name}`);
    ElMessage.success('删除成功');
    // 删除后统一走页码钳制：当前页只剩 1 条且不在第一页时先回退一页再拉取
    fetchDataAfterDelete();
  } catch (error) {
    logger.error('菜谱删除失败:', `id=${row.id}`, `name=${row.name}`, error);
    ElMessage.error(error.message || '删除失败');
  } finally {
    deletingId.value = null;
  }
};
</script>

<style scoped>
.recipe-name {
  color: var(--text-primary);
  font-weight: 500;
}

.text-muted {
  color: var(--text-secondary);
  font-size: 12px;
}

.ingredient-tag {
  margin-right: 4px;
}

/* 封面缩略图：固定尺寸保持行高稳定 */
.cover-thumb {
  width: 56px;
  height: 42px;
  border-radius: 4px;
  display: block;
}

.cover-missing {
  font-size: 12px;
  color: var(--text-secondary);
}

/* 封面上传区：固定预览框尺寸，点击整块即触发文件选择（对齐工作台配图先例） */
.cover-uploader :deep(.el-upload) {
  border: 1px dashed var(--border-color, #d9d9d9);
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 148px;
  height: 110px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-uploader :deep(.el-upload:hover) {
  border-color: var(--el-color-primary);
}

.cover-preview {
  width: 148px;
  height: 110px;
  object-fit: cover;
  display: block;
}

.cover-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
  font-size: 12px;
}

.ingredient-editor {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

/* 开关同行的行内说明 */
.form-tip-inline {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

/* 加载失败等异常态行内说明 */
.form-tip-inline--error {
  color: var(--color-danger);
}

.form-tip {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
}
</style>
