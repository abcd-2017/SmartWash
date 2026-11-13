<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="角色名称">
          <el-input
            v-model="listQuery.roleName"
            placeholder="请输入角色名称"
            clearable
            style="width: 200px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
        <el-form-item class="filter-end">
          <el-button type="primary" @click="handleCreate">新增角色</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="roleList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column prop="roleId" label="ID" min-width="80" />
        <el-table-column label="角色名称" min-width="150">
          <template #default="{ row }">
            <!-- 角色码机器标识符(live 打磨 v2):mono + 弱底 chip,与订单号/短码列同一语言 -->
            <div class="role-name-cell">{{ row.roleName }}</div>
          </template>
        </el-table-column>
        <el-table-column
          prop="description"
          label="角色描述"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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
      width="min(600px, 94vw)"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempRole" label-width="min(100px, 94vw)" :rules="rules">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="tempRole.roleName" placeholder="请输入角色名称" />
        </el-form-item>

        <el-form-item label="角色描述">
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
        <el-button type="primary" :loading="submitting" @click="submitForm">确认</el-button>
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
import logger from '@/utils/logger';

const formRef = ref(null);

// 弹窗关闭后清理：resetFields 归位 + clearValidate 清除校验红字，
// 避免下次打开时上次未通过的校验残留（评审：弹窗关闭清理不一致）
const handleDialogClosed = () => {
  formRef.value?.resetFields();
  formRef.value?.clearValidate();
};
const dialogVisible = ref(false);
const dialogType = ref('create');
const submitting = ref(false); // 确认按钮 loading，防连点

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
  submitting.value = true;
  try {
    await formRef.value.validate();

    if (dialogType.value === 'create') {
      await addRole(tempRole);
      ElMessage.success('新增成功');
      // 写操作成功留痕：业务键仅角色名称（新增无 roleId）
      logger.info('新增角色成功', `roleName=${tempRole.roleName}`);
    } else {
      await updateRole(tempRole);
      ElMessage.success('修改成功');
      logger.info('修改角色成功', `roleId=${tempRole.roleId}`, `roleName=${tempRole.roleName}`);
    }
    // 角色数据有变，失效角色下拉的全量缓存（评审 #15 的失效入口）
    roleOptionsCache.invalidate();
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    // validate 校验失败 reject 的是字段对象（fieldValue 含输入明文），非 Error 时仅记文本
    logger.error('保存角色失败:', error instanceof Error ? error : '表单校验未通过');
    ElMessage.error(error.message || '操作失败');
  } finally {
    submitting.value = false;
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
    // 写操作成功留痕：业务键仅 roleId/roleName
    logger.info('删除角色成功', `roleId=${row.roleId}`, `roleName=${row.roleName}`);
    // 角色下拉缓存同步失效（评审 #15 的失效入口）
    roleOptionsCache.invalidate();
    fetchData();
  } catch (error) {
    logger.error('删除角色失败:', `roleId=${row.roleId}`, error);
    ElMessage.error(error.message || '删除失败');
  }
};
</script>

<style scoped>
/* 角色码机器标识符(live 打磨 v2):mono + 弱底 chip,与订单号/短码列同一语言 */
.role-name-cell {
  display: inline-block;
  align-self: flex-start;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--border-subtle);
  font-family: var(--font-mono);
  font-size: 12.5px;
  font-weight: 500;
  letter-spacing: 0.02em;
  color: var(--text-input);
}
</style>
