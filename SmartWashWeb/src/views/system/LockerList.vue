<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="寄存柜ID">
          <el-input
            v-model.number="listQuery.lockerId"
            placeholder="输入ID"
            clearable
            style="width: 120px"
          />
        </el-form-item>

        <el-form-item label="学校">
          <el-select
            v-model="listQuery.schoolId"
            placeholder="选择学校"
            filterable
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="school in schoolOptions"
              :key="school.schoolId"
              :label="school.schoolName"
              :value="school.schoolId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="柜号">
          <el-input-number
            v-model="listQuery.lockerNumber"
            :min="1"
            controls-position="right"
            style="width: 120px"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="listQuery.status"
            placeholder="全部状态"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="(desc, code) in statusOptions"
              :key="code"
              :label="desc"
              :value="code"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="使用时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 380px"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="handleCreate">新增寄存柜</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table v-loading="listLoading" :data="lockerList" fit highlight-current-row>
        <el-table-column prop="lockerId" label="ID" min-width="80" />
        <el-table-column label="学校" min-width="180">
          <template #default="{ row }">
            {{ schoolMap[row.schoolId]?.schoolName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="lockerNumber" label="柜号" min-width="100" />
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="lockerStatusTagType(row.status)">
              {{ formatStatus(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastUsedAt" label="最后使用时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.lastUsedAt) }}</template>
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
      :title="dialogType === 'create' ? '新增寄存柜' : '编辑寄存柜'"
      v-model="dialogVisible"
      width="600px"
    >
      <el-form ref="formRef" :model="tempLocker" label-width="100px" :rules="rules">
        <el-form-item label="学校" prop="schoolId" style="margin: 20px">
          <el-select
            v-model="tempLocker.schoolId"
            placeholder="选择学校"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="school in schoolOptions"
              :key="school.schoolId"
              :label="school.schoolName"
              :value="school.schoolId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="柜号" prop="lockerNumber" style="margin: 20px">
          <el-input-number
            :disabled="dialogType !== 'create'"
            v-model="tempLocker.lockerNumber"
            :min="1"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="状态" prop="status" style="margin: 20px">
          <el-select v-model="tempLocker.status" placeholder="选择状态" style="width: 100%">
            <el-option
              v-for="(desc, code) in statusOptions"
              :key="code"
              :label="desc"
              :value="code"
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
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import {
  getLockerStatus,
  getLockerList,
  addLocker,
  updateLocker,
  deleteLocker,
} from '@/api/locker';
import { schoolOptionsCache } from '@/utils/optionCache';
import { formatTime } from '@/utils/format';
import { lockerStatusTagType } from '@/constants/dict';
import { useTableList } from '@/composables/useTableList';
import { useTimeRange } from '@/composables/useTimeRange';
import { useConfirm } from '@/composables/useConfirm';

// 数据
const statusOptions = ref({}); // 状态选项（键值对格式）
const schoolOptions = ref([]); // 学校选项
const schoolMap = ref({}); // 学校ID到名称的映射
const dialogVisible = ref(false); // 弹窗显示状态
const dialogType = ref('create'); // 弹窗类型（新增/编辑）
const formRef = ref(null); // 引用表单组件

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: lockerList,
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
  fetchApi: getLockerList,
  baseQuery: {
    lockerId: null,
    schoolId: null,
    lockerNumber: null,
    status: null,
    startTime: null,
    endTime: null,
  },
  buildParams: (q) => ({
    ...q,
    lockerId: q.lockerId || undefined,
    schoolId: q.schoolId || undefined,
    lockerNumber: q.lockerNumber || undefined,
    status: q.status || undefined,
  }),
  errorMsg: '获取数据失败',
});

// 时间范围处理（评审 #14：抽离为公共 composable）
const timeRange = useTimeRange(listQuery);

// 表单数据
const tempLocker = reactive({
  lockerId: null,
  schoolId: null,
  lockerNumber: null,
  status: '0', // 默认状态为“空闲”
});

// 验证规则
const rules = reactive({
  schoolId: [
    { required: true, message: '请选择学校', trigger: 'change' },
    { type: 'number', min: 1, message: '学校ID必须大于0' },
  ],
  lockerNumber: [
    { required: true, message: '请输入柜号', trigger: 'change' },
    { type: 'number', min: 1, message: '柜号必须大于0' },
  ],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
});

// 初始化数据
onMounted(async () => {
  await fetchStatus();
  await fetchSchools();
  fetchData();
});

// 获取状态枚举
const fetchStatus = async () => {
  try {
    const res = await getLockerStatus();
    statusOptions.value = res;
  } catch {
    ElMessage.error('获取状态失败');
  }
};

// 获取学校列表（模块级缓存：多页面共用一份全量数据，评审 #15）
const fetchSchools = async () => {
  try {
    const records = await schoolOptionsCache.load();
    schoolOptions.value = records;
    schoolMap.value = records.reduce((map, school) => {
      map[school.schoolId] = school;
      return map;
    }, {});
  } catch {
    ElMessage.error('获取学校列表失败');
  }
};

// 打开新增弹窗
const handleCreate = () => {
  dialogType.value = 'create';
  dialogVisible.value = true;
  Object.assign(tempLocker, {
    lockerId: null,
    schoolId: null,
    lockerNumber: null,
    status: '0', // 默认状态为“空闲”
  });
};

// 打开编辑弹窗
const handleEdit = (row) => {
  dialogType.value = 'edit';
  dialogVisible.value = true;
  Object.assign(tempLocker, { ...row });
};

// 提交表单
const submitForm = async () => {
  try {
    await formRef.value.validate();

    const submitData = {
      ...tempLocker,
      schoolId: tempLocker.schoolId || undefined,
      lockerNumber: tempLocker.lockerNumber || undefined,
    };

    if (dialogType.value === 'create') {
      await addLocker(submitData);
      ElMessage.success('新增成功');
    } else {
      await updateLocker(submitData);
      ElMessage.success('修改成功');
    }

    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '操作失败');
  }
};

// 删除寄存柜
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除寄存柜 ${row.lockerNumber} 吗？`);
  if (!confirmed) return;
  try {
    await deleteLocker(row.lockerId);
    ElMessage.success('删除成功');
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '删除失败');
  }
};

// 状态文本转换
const formatStatus = (status) => {
  return statusOptions.value[status] || '未知状态';
};
</script>

<style scoped>
@import '@/assets/pages.css';
</style>
