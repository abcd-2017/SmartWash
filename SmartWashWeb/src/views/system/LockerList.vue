<template>
  <div class="locker-container">
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
    <el-table
      v-loading="listLoading"
      :data="lockerList"
      border
      fit
      highlight-current-row
    >
      <el-table-column prop="lockerId" label="ID" width="80" />
      <el-table-column label="学校" max-width="180">
        <template #default="{ row }">
          {{ schoolMap[row.schoolId]?.schoolName || "-" }}
        </template>
      </el-table-column>
      <el-table-column prop="lockerNumber" label="柜号" width="100" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusTagType(row.status)">
            {{ formatStatus(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastUsedAt" label="最后使用时间" width="180">
        <template #default="{ row }">{{ formatTime(row.lastUsedAt) }}</template>
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

    <!-- 分页 -->
    <el-pagination
      background
      :current-page="listQuery.page"
      layout="prev, pager, next"
      :total="total"
      :page-size="listQuery.size"
      @current-change="handlePageChange"
    />

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      :title="dialogType === 'create' ? '新增寄存柜' : '编辑寄存柜'"
      v-model="dialogVisible"
      width="600px"
    >
      <el-form
        ref="formRef"
        :model="tempLocker"
        label-width="100px"
        :rules="rules"
      >
        <el-form-item label="学校" prop="schoolId">
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

        <el-form-item label="柜号" prop="lockerNumber">
          <el-input-number
            :disabled="dialogType !== 'create'"
            v-model="tempLocker.lockerNumber"
            :min="1"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-select
            v-model="tempLocker.status"
            placeholder="选择状态"
            style="width: 100%"
          >
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
import { ref, reactive, onMounted, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import dayjs from "dayjs";
import {
  getLockerStatus,
  getLockerList,
  addLocker,
  updateLocker,
  deleteLocker,
} from "@/api/locker";
import { getSchoolList } from "@/api/school";

// 数据
const statusOptions = ref({}); // 状态选项（键值对格式）
const schoolOptions = ref([]); // 学校选项
const schoolMap = ref({}); // 学校ID到名称的映射
const lockerList = ref([]); // 寄存柜列表
const total = ref(0); // 总条数
const listLoading = ref(false); // 加载状态
const dialogVisible = ref(false); // 弹窗显示状态
const dialogType = ref("create"); // 弹窗类型（新增/编辑）
const formRef = ref(null); // 引用表单组件

// 查询参数
const listQuery = reactive({
  page: 1,
  size: 10,
  lockerId: null,
  schoolId: null,
  lockerNumber: null,
  status: null,
  startTime: null,
  endTime: null,
});

// 时间范围处理
const timeRange = computed({
  get: () => [listQuery.startTime, listQuery.endTime],
  set: (val) => {
    listQuery.startTime = val?.[0] || null;
    listQuery.endTime = val?.[1] || null;
  },
});

// 表单数据
const tempLocker = reactive({
  lockerId: null,
  schoolId: null,
  lockerNumber: null,
  status: "0", // 默认状态为“空闲”
});

// 验证规则
const rules = reactive({
  schoolId: [
    { required: true, message: "请选择学校", trigger: "blur" },
    { type: "number", min: 1, message: "学校ID必须大于0" },
  ],
  lockerNumber: [
    { required: true, message: "请输入柜号", trigger: "blur" },
    { type: "number", min: 1, message: "柜号必须大于0" },
  ],
  status: [{ required: true, message: "请选择状态", trigger: "change" }],
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
  } catch (error) {
    ElMessage.error("获取状态失败");
  }
};

// 获取学校列表
const fetchSchools = async () => {
  try {
    const res = await getSchoolList({ page: 1, size: 1000 });
    schoolOptions.value = res.records;
    schoolMap.value = res.records.reduce((map, school) => {
      map[school.schoolId] = school;
      return map;
    }, {});
  } catch (error) {
    ElMessage.error("获取学校列表失败");
  }
};

// 获取数据
const fetchData = async () => {
  listLoading.value = true;
  try {
    const params = {
      ...listQuery,
      lockerId: listQuery.lockerId || undefined,
      schoolId: listQuery.schoolId || undefined,
      lockerNumber: listQuery.lockerNumber || undefined,
      status: listQuery.status || undefined,
    };
    const res = await getLockerList(params);
    lockerList.value = res.records;
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
  listQuery.lockerId = null;
  listQuery.schoolId = null;
  listQuery.lockerNumber = null;
  listQuery.status = null;
  listQuery.startTime = null;
  listQuery.endTime = null;
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
  Object.assign(tempLocker, {
    lockerId: null,
    schoolId: null,
    lockerNumber: null,
    status: "0", // 默认状态为“空闲”
  });
};

// 打开编辑弹窗
const handleEdit = (row) => {
  dialogType.value = "edit";
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

    if (dialogType.value === "create") {
      await addLocker(submitData);
      ElMessage.success("新增成功");
    } else {
      await updateLocker(submitData);
      ElMessage.success("修改成功");
    }

    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || "操作失败");
  }
};

// 删除寄存柜
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除寄存柜 ${row.lockerNumber} 吗？`,
      "警告",
      {
        confirmButtonText: "确认",
        cancelButtonText: "取消",
        type: "warning",
      }
    );
    await deleteLocker(row.lockerId);
    ElMessage.success("删除成功");
    fetchData();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error.message || "删除失败");
    }
  }
};

// 状态标签颜色
const getStatusTagType = (status) => {
  switch (status) {
    case "0":
      return "success"; // 空闲
    case "1":
      return "danger"; // 使用中
    case "2":
      return "warning"; // 故障
    default:
      return "info"; // 未知状态
  }
};

// 状态文本转换
const formatStatus = (status) => {
  return statusOptions.value[status] || "未知状态";
};

// 时间格式化
const formatTime = (time) => {
  return dayjs(time).format("YYYY-MM-DD HH:mm:ss");
};
</script>
  
  <style scoped>
.locker-container {
  padding: 20px;
  background-color: #fff;
  height: 100%;
  box-sizing: border-box;
}

.filter-container {
  margin-bottom: 20px;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;
}

.el-form-item {
  margin-bottom: 0;
  margin-right: 15px;
}
</style>