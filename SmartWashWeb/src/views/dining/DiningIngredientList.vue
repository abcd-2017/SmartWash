<template>
  <div class="page-container">
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="配料名称">
          <el-input
            v-model="listQuery.keyword"
            placeholder="输入配料名称模糊搜索"
            clearable
            style="width: 200px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select
            v-model="listQuery.category"
            placeholder="全部分类"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in DINING_INGREDIENT_CATEGORY_OPTIONS"
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
        <el-form-item>
          <el-button type="primary" @click="handleCreate">新增配料</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <!-- 加载失败态：与空态区分，给出重试入口 -->
      <div v-if="listLoadError" class="load-error">
        <span>{{ listLoadError }}</span>
        <el-button size="small" @click="fetchData">重试</el-button>
      </div>
      <el-table
        v-else
        v-loading="listLoading"
        :data="ingredientList"
        fit
        highlight-current-row
        :empty-text="
          hasIngredientFilter
            ? '试试调整筛选条件'
            : '还没有配料：批量导入菜谱时按食材名自动注册，也可在此手动维护'
        "
      >
        <el-table-column label="ID" prop="id" width="80" />
        <el-table-column label="配料名称" min-width="200">
          <template #default="{ row }">
            <span class="ingredient-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="diningIngredientCategoryTagType(row.category)" effect="light">
              {{ diningIngredientCategoryText(row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
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

    <el-dialog
      :title="dialogType === 'create' ? '新增配料' : '编辑配料'"
      v-model="dialogVisible"
      width="min(480px, 94vw)"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempIngredient" label-width="90px" :rules="rules">
        <el-form-item label="配料名称" prop="name">
          <el-input
            v-model="tempIngredient.name"
            placeholder="如 鸡蛋、生抽、西红柿"
            maxlength="32"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="tempIngredient.category" style="width: 200px">
            <el-option
              v-for="opt in DINING_INGREDIENT_CATEGORY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <span class="form-tip-inline">主料/辅料/调料为批量导入的启发式预判，可在此修正</span>
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
  getDiningIngredientList,
  addDiningIngredient,
  updateDiningIngredient,
  deleteDiningIngredient,
} from '@/api/dining';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';
import { formatTime } from '@/utils/format';
import { isValidationError } from '@/utils/errors';
import {
  DINING_INGREDIENT_CATEGORY_OPTIONS,
  diningIngredientCategoryText,
  diningIngredientCategoryTagType,
} from '@/constants/dict';
import logger from '@/utils/logger';

const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref('create');
const submitting = ref(false); // 确认按钮 loading，防连点
const deletingId = ref(null); // 行内删除按钮 loading，防连点

const {
  list: ingredientList,
  total,
  listLoading,
  listLoadError,
  listQuery,
  pageSizes,
  fetchData,
  fetchDataAfterDelete,
  handleSearch,
  resetSearch,
  handlePageChange,
  handleSizeChange,
} = useTableList({
  fetchApi: getDiningIngredientList,
  baseQuery: {
    keyword: '',
    category: null,
  },
  buildParams: (q) => ({
    ...q,
    keyword: q.keyword || undefined,
    category: q.category || undefined,
  }),
  errorMsg: '获取配料列表失败',
});

// 空态区分：有筛选且库非空时提示调整条件；库空一律引导新增/导入自动注册
const hasIngredientFilter = computed(
  () => Boolean((listQuery.keyword || '').trim()) || Boolean(listQuery.category)
);

// 表单数据（字段名已与后端 DiningIngredientVo / Add·UpdateDiningIngredientFrom 对齐）
const tempIngredient = reactive({
  id: null,
  name: '',
  category: 'OTHER',
});

// 验证规则（name 唯一性由后端 uk_name + 服务层查重兜底，重名走 409 业务码，前端只做展示层提示）
const rules = reactive({
  name: [
    { required: true, message: '请输入配料名称', trigger: 'blur' },
    { max: 32, message: '配料名称不能超过 32 个字符', trigger: 'blur' },
  ],
  category: [{ required: true, message: '请选择配料分类', trigger: 'change' }],
});

onMounted(() => {
  fetchData();
});

const handleCreate = () => {
  dialogType.value = 'create';
  dialogVisible.value = true;
  Object.assign(tempIngredient, {
    id: null,
    name: '',
    category: 'OTHER',
  });
};

const handleEdit = (row) => {
  dialogType.value = 'edit';
  dialogVisible.value = true;
  Object.assign(tempIngredient, {
    id: row.id,
    name: row.name,
    category: row.category || 'OTHER',
  });
};

const handleDialogClosed = () => {
  formRef.value?.resetFields();
  formRef.value?.clearValidate();
};

// 提交表单（更新接口为 RESTful PUT：id 走路径变量，body 只带可变字段）
const submitForm = async () => {
  submitting.value = true;
  try {
    await formRef.value.validate();

    const payload = {
      name: tempIngredient.name,
      category: tempIngredient.category,
    };
    if (dialogType.value === 'create') {
      await addDiningIngredient(payload);
      logger.info(
        '配料新增成功',
        `name=${tempIngredient.name}`,
        `category=${tempIngredient.category}`
      );
      ElMessage.success('新增成功');
    } else {
      await updateDiningIngredient(tempIngredient.id, payload);
      logger.info(
        '配料编辑成功',
        `id=${tempIngredient.id}`,
        `name=${tempIngredient.name}`,
        `category=${tempIngredient.category}`
      );
      ElMessage.success('修改成功');
    }
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    // validate() 校验失败走字段红字；error?.fields 判据在 EP 2.9.6 下恒为 false，统一换 isValidationError
    if (isValidationError(error)) {
      logger.debug('配料表单校验未通过，已拦截提交');
    } else {
      logger.error(
        '配料提交失败:',
        `type=${dialogType.value}`,
        `name=${tempIngredient.name}`,
        `id=${tempIngredient.id ?? '-'}`,
        error
      );
      ElMessage.error(error.message || '操作失败');
    }
  } finally {
    submitting.value = false;
  }
};

// 删除配料（物理删除；被菜谱配料关联引用时后端 409 拒绝，message 含引用数量，前端如实透出）
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm
  const confirmed = await useConfirm(
    `确认删除配料「${row.name}」吗？已被菜谱引用的配料将无法删除；删除不影响历史点单与购买清单快照。`
  );
  if (!confirmed) return;
  deletingId.value = row.id;
  try {
    await deleteDiningIngredient(row.id);
    logger.info('配料删除成功', `id=${row.id}`, `name=${row.name}`);
    ElMessage.success('删除成功');
    // 删除后统一走页码钳制：当前页只剩 1 条且不在第一页时先回退一页再拉取
    fetchDataAfterDelete();
  } catch (error) {
    logger.error('配料删除失败:', `id=${row.id}`, `name=${row.name}`, error);
    // 引用冲突（如「配料「鸡蛋」被 3 个菜谱引用，无法删除」）与普通失败统一透出后端 message
    ElMessage.error(error.message || '删除失败');
  } finally {
    deletingId.value = null;
  }
};
</script>

<style scoped>
.ingredient-name {
  color: var(--text-primary);
  font-weight: 500;
}

.form-tip-inline {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
