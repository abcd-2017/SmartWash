<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="分类名称">
          <el-input
            v-model="listQuery.name"
            placeholder="输入分类名称模糊搜索"
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
          <el-button type="primary" @click="handleCreate">新增分类</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table v-loading="listLoading" :data="categoryList" fit highlight-current-row>
        <el-table-column label="ID" prop="id" width="80" />
        <el-table-column label="分类名称" min-width="200">
          <template #default="{ row }">
            <span class="category-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="排序" prop="sort" width="100">
          <template #default="{ row }">
            <el-tag type="info" effect="plain">{{ row.sort }}</el-tag>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      :title="dialogType === 'create' ? '新增菜谱分类' : '编辑菜谱分类'"
      v-model="dialogVisible"
      width="480px"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempCategory" label-width="90px" :rules="rules">
        <el-form-item label="分类名称" prop="name">
          <el-input
            v-model="tempCategory.name"
            placeholder="如 家常菜、汤羹、凉菜"
            maxlength="32"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="tempCategory.sort" :min="0" :max="9999" />
          <span class="form-tip-inline">越小越靠前，用户端按此顺序渲染分类栏</span>
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
  getDiningCategoryList,
  addDiningCategory,
  updateDiningCategory,
  deleteDiningCategory,
} from '@/api/dining';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';
import { diningCategoryOptionsCache } from '@/utils/optionCache';
import { formatTime } from '@/utils/format';
import logger from '@/utils/logger';

const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref('create');
const submitting = ref(false); // 确认按钮 loading，防连点
const deletingId = ref(null); // 行内删除按钮 loading，防连点

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: categoryList,
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
  fetchApi: getDiningCategoryList,
  baseQuery: {
    name: '',
  },
  buildParams: (q) => ({
    ...q,
    name: q.name || undefined,
  }),
  errorMsg: '获取菜谱分类列表失败',
});

// 表单数据（字段名已与后端 DiningCategoryVo / Add·UpdateDiningCategoryFrom 对齐）
const tempCategory = reactive({
  id: null,
  name: '',
  sort: 100,
});

// 验证规则（name 唯一性由后端 uk_name + 服务层查重兜底，前端只做展示层提示）
const rules = reactive({
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 32, message: '分类名称不能超过 32 个字符', trigger: 'blur' },
  ],
  sort: [{ required: true, message: '请输入排序值', trigger: 'blur' }],
});

// 分类选项缓存失效：分类被菜谱页下拉（optionCache 全量缓存）消费，增删改后统一失效，
// 保证「分类管理 → 菜谱管理」两个页面的分类选项一致
const invalidateCategoryCache = () => {
  diningCategoryOptionsCache.invalidate();
};

// ---------- 初始化与弹窗 ----------

onMounted(() => {
  fetchData();
});

// 打开新增弹窗
const handleCreate = () => {
  dialogType.value = 'create';
  dialogVisible.value = true;
  Object.assign(tempCategory, {
    id: null,
    name: '',
    sort: 100,
  });
};

// 打开编辑弹窗
const handleEdit = (row) => {
  dialogType.value = 'edit';
  dialogVisible.value = true;
  Object.assign(tempCategory, { ...row });
};

// 弹窗关闭 — 清除验证状态
const handleDialogClosed = () => {
  formRef.value?.resetFields();
  formRef.value?.clearValidate();
};

// 提交表单（更新为全量字段语义，与后端 UpdateDiningCategoryFrom 对齐）
const submitForm = async () => {
  submitting.value = true;
  try {
    await formRef.value.validate();

    const payload = {
      name: tempCategory.name,
      sort: tempCategory.sort,
    };
    if (dialogType.value === 'create') {
      await addDiningCategory(payload);
      logger.info('菜谱分类新增成功', `name=${tempCategory.name}`, `sort=${tempCategory.sort}`);
      ElMessage.success('新增成功');
    } else {
      await updateDiningCategory({ id: tempCategory.id, ...payload });
      logger.info('菜谱分类编辑成功', `id=${tempCategory.id}`, `name=${tempCategory.name}`);
      ElMessage.success('修改成功');
    }
    invalidateCategoryCache();
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    // validate() 校验失败的特征是错误对象带 fields；与接口失败区分级别（校验失败不属系统错误）
    if (error?.fields) {
      logger.debug('菜谱分类表单校验未通过，已拦截提交');
    } else {
      logger.error(
        '菜谱分类提交失败:',
        `type=${dialogType.value}`,
        `name=${tempCategory.name}`,
        `id=${tempCategory.id ?? '-'}`,
        error
      );
    }
    ElMessage.error(error.message || '操作失败');
  } finally {
    submitting.value = false;
  }
};

// 删除分类（物理删除；被菜谱引用时后端拒绝并提示引用数量，前端如实透出）
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm
  const confirmed = await useConfirm(
    `确认删除分类「${row.name}」吗？已被菜谱引用的分类将无法删除；删除后新增菜谱时将无法再选择该分类。`
  );
  if (!confirmed) return;
  deletingId.value = row.id;
  try {
    await deleteDiningCategory(row.id);
    logger.info('菜谱分类删除成功', `id=${row.id}`, `name=${row.name}`);
    ElMessage.success('删除成功');
    invalidateCategoryCache();
    fetchData(); // 刷新当前页
  } catch (error) {
    logger.error('菜谱分类删除失败:', `id=${row.id}`, `name=${row.name}`, error);
    // 引用冲突（如「分类「家常菜」已被 3 个菜谱引用，无法删除」）与普通失败统一透出后端 message
    ElMessage.error(error.message || '删除失败');
  } finally {
    deletingId.value = null;
  }
};
</script>

<style scoped>
.category-name {
  color: var(--text-primary);
  font-weight: 500;
}

/* 开关同行的行内说明 */
.form-tip-inline {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
