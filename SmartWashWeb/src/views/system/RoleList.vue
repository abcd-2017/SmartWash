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
    <el-table
      v-loading="listLoading"
      :data="roleList"
      fit
      highlight-current-row
    >
      <el-table-column prop="roleId" label="ID" min-width="80" />
      <el-table-column prop="roleName" label="角色名称" min-width="150" />
      <el-table-column prop="description" label="角色描述" min-width="200" />
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
        layout="prev, pager, next"
        :total="total"
        :page-size="listQuery.size"
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
      <el-form
        ref="formRef"
        :model="tempRole"
        label-width="100px"
        :rules="rules"
      >
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
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import dayjs from "dayjs";
import { getRoleList, addRole, updateRole, deleteRole } from "@/api/role";

const formRef = ref(null);
const roleList = ref([]);
const total = ref(0);
const listLoading = ref(false);
const dialogVisible = ref(false);
const dialogType = ref("create");

// 查询参数
const listQuery = reactive({
  page: 1,
  size: 10,
  roleName: "",
});

// 表单数据
const tempRole = reactive({
  roleId: null,
  roleName: "",
  description: "",
});

// 验证规则
const rules = reactive({
  roleName: [
    { required: true, message: "请输入角色名称", trigger: "blur" },
    { max: 20, message: "名称不超过20字", trigger: "blur" },
  ],
});

// 初始化数据
onMounted(() => {
  fetchData();
});

// 获取数据
const fetchData = async () => {
  listLoading.value = true;
  try {
    const res = await getRoleList(listQuery);
    roleList.value = res.records;
    total.value = res.total;
  } catch (error) {
    ElMessage.error(error.message || "获取数据失败");
  } finally {
    listLoading.value = false;
  }
};

// 搜索
const handleSearch = () => {
  listQuery.page = 1;
  fetchData();
};

// 重置搜索
const resetSearch = () => {
  listQuery.roleName = "";
  handleSearch();
};

// 分页
const handlePageChange = (val) => {
  listQuery.page = val;
  fetchData();
};

// 打开新增弹窗
const handleCreate = () => {
  dialogType.value = "create";
  dialogVisible.value = true;
  Object.assign(tempRole, {
    roleId: null,
    roleName: "",
    description: "",
  });
};

// 打开编辑弹窗
const handleEdit = (row) => {
  dialogType.value = "edit";
  dialogVisible.value = true;
  Object.assign(tempRole, { ...row });
};

// 提交表单
const submitForm = async () => {
  try {
    await formRef.value.validate();

    if (dialogType.value === "create") {
      await addRole(tempRole);
      ElMessage.success("新增成功");
    } else {
      await updateRole(tempRole);
      ElMessage.success("修改成功");
    }
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || "操作失败");
  }
};

// 删除角色
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除角色 ${row.roleName} 吗？`, "警告", {
      confirmButtonText: "确认",
      cancelButtonText: "取消",
      type: "warning",
    });
    await deleteRole(row.roleId);
    ElMessage.success("删除成功");
    fetchData();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error.message || "删除失败");
    }
  }
};

// 时间格式化
const formatTime = (time) => {
  return dayjs(time).format("YYYY-MM-DD HH:mm:ss");
};
</script>
  
  <style scoped>
@import '@/assets/pages.css';
</style>