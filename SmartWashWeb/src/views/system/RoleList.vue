<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="角色名称">
          <el-input
            v-model="listQuery.roleName"
            placeholder="输入角色名称"
            clearable
            style="width: 200px"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="handleCreate">新增角色</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table v-loading="listLoading" :data="roleList" fit highlight-current-row>
        <el-table-column prop="roleId" label="ID" min-width="80" />
        <el-table-column prop="roleName" label="角色名称" min-width="150" />
        <el-table-column prop="description" label="角色描述" min-width="200" />
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
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
      :title="dialogType === 'create' ? '新增角色' : '编辑角色'"
      v-model="dialogVisible"
      width="600px"
    >
      <el-form ref="formRef" :model="tempRole" label-width="100px" :rules="rules">
        <el-form-item label="角色名称" prop="roleName" style="margin: 20px">
          <el-input v-model="tempRole.roleName" placeholder="请输入角色名称" />
        </el-form-item>

        <el-form-item label="角色描述" style="margin: 20px">
          <el-input
            v-model="tempRole.description"
            type="textarea"
            :rows="3"
            placeholder="请输入角色描述"
          />
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
import { getRoleList, addRole, updateRole, deleteRole } from '@/api/role';
import { roleOptionsCache } from '@/utils/optionCache';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';

const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref('create');

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: roleList,
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
  fetchApi: getRoleList,
  baseQuery: {
    roleName: '',
  },
  errorMsg: '获取数据失败',
});

// 表单数据
const tempRole = reactive({
  roleId: null,
  roleName: '',
  description: '',
});

// 验证规则
const rules = reactive({
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 20, message: '名称不超过20字', trigger: 'blur' },
  ],
});

// 初始化数据
onMounted(() => {
  fetchData();
});

// 打开新增弹窗
const handleCreate = () => {
  dialogType.value = 'create';
  dialogVisible.value = true;
  Object.assign(tempRole, {
    roleId: null,
    roleName: '',
    description: '',
  });
};

// 打开编辑弹窗
const handleEdit = (row) => {
  dialogType.value = 'edit';
  dialogVisible.value = true;
  Object.assign(tempRole, { ...row });
};

// 提交表单
const submitForm = async () => {
  try {
    await formRef.value.validate();

    if (dialogType.value === 'create') {
      await addRole(tempRole);
      ElMessage.success('新增成功');
    } else {
      await updateRole(tempRole);
      ElMessage.success('修改成功');
    }
    // 角色数据有变，失效角色下拉的全量缓存（评审 #15 的失效入口）
    roleOptionsCache.invalidate();
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '操作失败');
  }
};

// 删除角色
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除角色 ${row.roleName} 吗？`);
  if (!confirmed) return;
  try {
    await deleteRole(row.roleId);
    ElMessage.success('删除成功');
    // 角色下拉缓存同步失效（评审 #15 的失效入口）
    roleOptionsCache.invalidate();
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '删除失败');
  }
};
</script>

<style scoped>
@import '@/assets/pages.css';
</style>
