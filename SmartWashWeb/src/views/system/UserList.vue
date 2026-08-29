<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="用户ID">
          <el-input
            v-model.number="listQuery.userId"
            placeholder="输入用户ID"
            clearable
            style="width: 120px"
          />
        </el-form-item>

        <el-form-item label="手机号">
          <el-input
            v-model="listQuery.phoneNumber"
            placeholder="输入手机号"
            clearable
            style="width: 160px"
          />
        </el-form-item>

        <el-form-item label="学校">
          <el-select
            v-model="listQuery.schoolId"
            placeholder="选择学校"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="school in schoolOptions"
              :key="school.schoolId"
              :label="school.schoolName"
              :value="school.schoolId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="学号">
          <el-input
            v-model="listQuery.studentId"
            placeholder="输入学号"
            clearable
            style="width: 160px"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="handleCreate">新增用户</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table v-loading="listLoading" :data="userList" fit highlight-current-row>
        <el-table-column prop="userId" label="ID" min-width="80" />
        <el-table-column label="头像" width="80">
          <template #default="{ row }">
            <el-avatar :size="36" :src="row.avatar" class="user-table-avatar">
              <span>{{ (row.phoneNumber || '?').slice(-2) }}</span>
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="phoneNumber" label="手机号" min-width="150" />
        <el-table-column label="学校" min-width="180">
          <template #default="{ row }">
            {{ row.schools?.schoolName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="studentId" label="学号" min-width="120" />
        <el-table-column prop="campusCard" label="校园卡号" min-width="150" />
        <el-table-column prop="balance" label="余额" min-width="120">
          <template #default="{ row }">￥{{ row.balance?.toFixed(2) || '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
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
      :title="dialogType === 'create' ? '新增用户' : '编辑用户'"
      v-model="dialogVisible"
      width="600px"
    >
      <el-form ref="formRef" :model="tempUser" label-width="100px" :rules="rules">
        <el-form-item label="学校" prop="schoolId" style="margin: 20px">
          <el-select v-model="tempUser.schoolId" placeholder="请选择学校" filterable>
            <el-option
              v-for="school in schoolOptions"
              :key="school.schoolId"
              :label="school.schoolName"
              :value="school.schoolId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="手机号" prop="phoneNumber" style="margin: 20px">
          <el-input
            v-model="tempUser.phoneNumber"
            placeholder="请输入手机号"
            :disabled="dialogType === 'edit'"
          />
        </el-form-item>

        <el-form-item label="学号" prop="studentId" style="margin: 20px">
          <el-input v-model="tempUser.studentId" placeholder="请输入学号" />
        </el-form-item>

        <el-form-item label="校园卡号" style="margin: 20px">
          <el-input v-model="tempUser.campusCard" placeholder="请输入校园卡号" />
        </el-form-item>

        <el-form-item
          label="密码"
          style="margin: 20px"
          prop="password"
          v-if="dialogType === 'create'"
        >
          <el-input
            v-model="tempUser.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="修改密码" v-if="dialogType === 'edit'" style="margin: 20px">
          <el-input
            v-model="tempUser.password"
            type="password"
            placeholder="留空则不修改密码"
            show-password
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
import { getUserList, addUser, updateUser, deleteUser } from '@/api/user';
import { schoolOptionsCache } from '@/utils/optionCache';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';

const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref('create');

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: userList,
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
  fetchApi: getUserList,
  baseQuery: {
    userId: null,
    schoolId: null,
    phoneNumber: '',
    studentId: '',
    campusCard: '',
  },
  buildParams: (q) => ({
    ...q,
    userId: q.userId || undefined,
    schoolId: q.schoolId || undefined,
  }),
  errorMsg: '获取用户列表失败',
});

// 学校下拉选项（模块级缓存：首次拉取后复用，不再每次进页面 size:1000 全量拉取）
const schoolOptions = ref([]);

// 表单数据
const tempUser = reactive({
  userId: null,
  schoolId: null,
  phoneNumber: '',
  studentId: '',
  campusCard: '',
  password: '',
  balance: 0,
});

// 验证规则
const rules = reactive({
  schoolId: [{ required: true, message: '请选择学校', trigger: 'change' }],
  phoneNumber: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    {
      pattern: /^(?:\+86)?1[3-9]\d{9}$/,
      message: '手机号格式不正确',
      trigger: 'blur',
    },
  ],
  studentId: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' },
  ],
});

// 初始化数据
onMounted(() => {
  fetchSchools();
  fetchData();
});

// 获取学校列表
const fetchSchools = async () => {
  try {
    schoolOptions.value = await schoolOptionsCache.load();
  } catch (error) {
    // HTTP 层错误已由拦截器统一提示，这里仅记录日志避免未处理 rejection
    console.error('获取学校列表失败:', error);
  }
};

// 打开新增弹窗
const handleCreate = () => {
  dialogType.value = 'create';
  dialogVisible.value = true;
  Object.assign(tempUser, {
    userId: null,
    schoolId: null,
    phoneNumber: '',
    studentId: '',
    campusCard: '',
    password: '',
    balance: 0,
  });
};

// 打开编辑弹窗
const handleEdit = (row) => {
  dialogType.value = 'edit';
  dialogVisible.value = true;
  Object.assign(tempUser, {
    ...row,
    schoolId: row.schools?.schoolId,
    password: '',
  });
};

// 提交表单
const submitForm = async () => {
  await formRef.value.validate();

  try {
    if (dialogType.value === 'create') {
      await addUser(tempUser);
      ElMessage.success('新增成功');
    } else {
      await updateUser(tempUser);
      ElMessage.success('修改成功');
    }
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '操作失败');
  }
};

// 删除用户
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除用户 ${row.phoneNumber} 吗？`);
  if (!confirmed) return;
  try {
    await deleteUser(row.userId);
    ElMessage.success('删除成功');
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '删除失败');
  }
};
</script>

<style scoped>
@import '@/assets/pages.css';

.user-table-avatar {
  background: #6366f1;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
</style>
