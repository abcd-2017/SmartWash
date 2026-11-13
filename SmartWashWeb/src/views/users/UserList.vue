<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container user-filter">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="用户ID">
          <el-input
            v-model.number="listQuery.userId"
            placeholder="输入用户ID"
            clearable
            style="width: 100px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="手机号">
          <el-input
            v-model="listQuery.phoneNumber"
            placeholder="输入手机号"
            clearable
            style="width: 140px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="学校">
          <el-select
            v-model="listQuery.schoolId"
            placeholder="选择学校"
            clearable
            style="width: 160px"
            @change="handleSearch"
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
            style="width: 120px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
        <el-form-item class="filter-end">
          <el-button type="primary" @click="handleCreate">新增用户</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="userList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
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
        <el-table-column prop="studentId" label="学号" min-width="120">
          <template #default="{ row }">{{ row.studentId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="campusCard" label="校园卡号" min-width="150">
          <template #default="{ row }">{{ row.campusCard || '-' }}</template>
        </el-table-column>
        <el-table-column prop="balance" label="余额" min-width="120" align="right">
          <template #default="{ row }">￥{{ formatAmount(row.balance) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" min-width="180">
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
      :title="dialogType === 'create' ? '新增用户' : '编辑用户'"
      v-model="dialogVisible"
      width="min(600px, 94vw)"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempUser" label-width="min(100px, 94vw)" :rules="rules">
        <el-form-item label="学校" prop="schoolId">
          <el-select v-model="tempUser.schoolId" placeholder="请选择学校" filterable>
            <el-option
              v-for="school in schoolOptions"
              :key="school.schoolId"
              :label="school.schoolName"
              :value="school.schoolId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="手机号" prop="phoneNumber">
          <el-input
            v-model="tempUser.phoneNumber"
            placeholder="请输入手机号"
            :disabled="dialogType === 'edit'"
          />
        </el-form-item>

        <el-form-item label="学号" prop="studentId">
          <el-input v-model="tempUser.studentId" placeholder="请输入学号" />
        </el-form-item>

        <el-form-item label="校园卡号">
          <el-input v-model="tempUser.campusCard" placeholder="请输入校园卡号" />
        </el-form-item>

        <el-form-item label="密码" prop="password" v-if="dialogType === 'create'">
          <el-input
            v-model="tempUser.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="修改密码" v-if="dialogType === 'edit'">
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
        <el-button type="primary" :loading="submitting" @click="submitForm">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { getUserList, addUser, updateUser, deleteUser } from '@/api/user';
import { schoolOptionsCache } from '@/utils/optionCache';
import { formatAmount, formatTime } from '@/utils/format';
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
    // 与 LockerList 同场景对齐：下拉拉取失败弹 toast（此前仅 logger 静默，筛选不可用无从归因）
    logger.error('获取学校列表失败:', error);
    ElMessage.error(error.message || '获取学校列表失败');
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
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  try {
    if (dialogType.value === 'create') {
      await addUser(tempUser);
      ElMessage.success('新增成功');
      // 写操作留痕对齐其他页（成功 info / 失败 error）；手机号等敏感字段不入日志
      logger.info(
        '新增学生成功',
        `userId=${tempUser.userId ?? '-'}`,
        `schoolId=${tempUser.schoolId}`
      );
    } else {
      await updateUser(tempUser);
      ElMessage.success('修改成功');
      logger.info('修改学生成功', `userId=${tempUser.userId}`);
    }
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    logger.error(
      '学生提交失败:',
      `type=${dialogType.value}`,
      `userId=${tempUser.userId ?? '-'}`,
      error
    );
    ElMessage.error(error.message || '操作失败');
  } finally {
    submitting.value = false;
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
    logger.info('删除学生成功', `userId=${row.userId}`);
    fetchData();
  } catch (error) {
    logger.error('删除学生失败:', `userId=${row.userId}`, error);
    ElMessage.error(error.message || '删除失败');
  }
};
</script>

<style scoped>
/* 学生筛选字段收窄后全条单行，筛选卡高度减半 */
.user-filter :deep(.el-form-item) {
  margin-right: 10px;
}

.user-table-avatar {
  background: var(--color-primary);
  /* 主色底上的白字 → on-primary token */
  color: var(--color-on-primary);
  font-size: 12px;
  font-weight: 600;
}
</style>
