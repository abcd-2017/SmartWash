<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="管理员ID">
          <el-input
            v-model.number="listQuery.adminId"
            placeholder="输入管理员ID"
            clearable
            style="width: 120px"
          />
        </el-form-item>

        <el-form-item label="用户名">
          <el-input
            v-model="listQuery.username"
            placeholder="输入用户名"
            clearable
            style="width: 150px"
          />
        </el-form-item>

        <!-- 修改角色ID输入为下拉选择 -->
        <el-form-item label="角色">
          <el-select
            v-model="listQuery.roleId"
            placeholder="选择角色"
            filterable
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.roleId"
              :label="role.roleName"
              :value="role.roleId"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="handleCreate">新增管理员</el-button>
          <el-button
            type="danger"
            :disabled="multipleSelection.length === 0"
            @click="handleBatchDelete"
            >批量删除</el-button
          >
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
    <el-table
      ref="tableRef"
      v-loading="listLoading"
      :data="adminList"
      fit
      highlight-current-row
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column prop="adminId" label="ID" min-width="80" />
      <el-table-column prop="username" label="用户名" min-width="150" />
      <el-table-column prop="roles.roleName" label="角色" min-width="180" />
      <el-table-column label="创建时间" min-width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)"
            >删除</el-button
          >
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
      :title="dialogType === 'create' ? '新增管理员' : '编辑管理员'"
      v-model="dialogVisible"
      width="600px"
    >
      <el-form
        ref="formRef"
        :model="tempAdmin"
        label-width="100px"
        :rules="rules"
      >
        <el-form-item label="用户名" prop="username" style="margin: 20px">
          <el-input v-model="tempAdmin.username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item
          style="margin: 20px"
          label="密码"
          prop="password"
          v-if="dialogType === 'create'"
        >
          <el-input
            v-model="tempAdmin.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>

        <el-form-item
          label="修改密码"
          v-if="dialogType === 'edit'"
          style="margin: 20px"
        >
          <el-input
            v-model="tempAdmin.password"
            type="password"
            placeholder="留空则不修改密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="角色" prop="roleId" style="margin: 20px">
          <el-select
            v-model="tempAdmin.roleId"
            placeholder="请选择角色"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.roleId"
              :label="role.roleName"
              :value="role.roleId"
            />
          </el-select>
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
import { ref, reactive, onMounted } from "vue";
import { ElMessage } from "element-plus";
import {
  getAdminUserList,
  addAdminUser,
  updateAdminUser,
  deleteAdminUser,
} from "@/api/adminUser";
import { roleOptionsCache } from "@/utils/optionCache";
import { formatTime } from "@/utils/format";
import { useTableList } from "@/composables/useTableList";
import { useConfirm } from "@/composables/useConfirm";

const tableRef = ref(null);
const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref("create");
const multipleSelection = ref([]);
const roleOptions = ref([]); // 角色选项列表

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: adminList,
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
  fetchApi: getAdminUserList,
  baseQuery: {
    adminId: null,
    username: "",
    roleId: null,
  },
  buildParams: (q) => ({
    ...q,
    adminId: q.adminId || undefined,
    roleId: q.roleId || undefined,
  }),
  errorMsg: "获取数据失败",
});

// 表单数据
const tempAdmin = reactive({
  adminId: null,
  username: "",
  password: "",
  roleId: null,
});

// 验证规则
const rules = reactive({
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 20, message: "长度在 3 到 20 个字符", trigger: "blur" },
  ],
  password: [
    {
      required: true,
      message: "请输入密码",
      trigger: "blur",
      validator: (rule, value, callback) => {
        if (dialogType.value === "create" && !value) {
          callback(new Error("请输入密码"));
        } else {
          callback();
        }
      },
    },
  ],
  roleId: [
    { required: true, message: "请选择角色", trigger: "change" },
    { type: "number", min: 1, message: "角色ID必须大于0" },
  ],
});

// 初始化数据
onMounted(() => {
  fetchData();
  fetchRoles(); // 加载角色选项
});

// 获取角色列表（模块级缓存：首次拉取后复用，评审 #15）
const fetchRoles = async () => {
  try {
    roleOptions.value = await roleOptionsCache.load();
  } catch (error) {
    ElMessage.error("获取角色列表失败");
  }
};

// 处理多选
const handleSelectionChange = (val) => {
  multipleSelection.value = val;
};

// 批量删除
const handleBatchDelete = async () => {
  const count = multipleSelection.value.length;
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除选中的 ${count} 个管理员吗？`);
  if (!confirmed) return;
  try {
    const ids = multipleSelection.value.map((item) => item.adminId).join(",");
    await deleteAdminUser(ids);
    ElMessage.success("删除成功");
    fetchData();
    tableRef.value.clearSelection();
  } catch (error) {
    ElMessage.error(error.message || "删除失败");
  }
};

// 打开创建弹窗
const handleCreate = () => {
  dialogType.value = "create";
  dialogVisible.value = true;
  Object.assign(tempAdmin, {
    adminId: null,
    username: "",
    password: "",
    roleId: null,
  });
};

// 打开编辑弹窗
const handleEdit = (row) => {
  dialogType.value = "edit";
  dialogVisible.value = true;
  Object.assign(tempAdmin, {
    adminId: row.adminId,
    username: row.username,
    password: "", // 清空密码字段
    roleId: row.roles?.roleId || null, // 正确设置角色ID
  });
};

// 提交表单
const submitForm = async () => {
  try {
    await formRef.value.validate();

    // 构造提交数据（过滤空密码）
    const submitData = {
      ...tempAdmin,
      password: tempAdmin.password || undefined,
    };

    if (dialogType.value === "create") {
      await addAdminUser(submitData);
      ElMessage.success("新增成功");
    } else {
      await updateAdminUser(submitData);
      ElMessage.success("修改成功");
    }

    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    if (error.message) {
      ElMessage.error(error.message);
    } else {
      ElMessage.error("操作失败，请检查输入");
    }
  }
};

// 删除单个管理员
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除管理员 ${row.username} 吗？`);
  if (!confirmed) return;
  try {
    await deleteAdminUser(row.adminId);
    ElMessage.success("删除成功");
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || "删除失败");
  }
};
</script>
  
<style scoped>
@import '@/assets/pages.css';
</style>