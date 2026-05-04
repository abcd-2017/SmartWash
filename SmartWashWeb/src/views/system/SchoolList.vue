<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="学校ID">
          <el-input
            v-model.number="listQuery.schoolId"
            placeholder="输入学校ID"
            clearable
            style="width: 120px"
          />
        </el-form-item>

        <el-form-item label="学校名称">
          <el-input
            v-model="listQuery.schoolName"
            placeholder="输入学校名称"
            clearable
            style="width: 200px"
          />
        </el-form-item>

        <el-form-item label="学校编码">
          <el-input
            v-model="listQuery.schoolCode"
            placeholder="输入学校编码"
            clearable
            style="width: 160px"
          />
        </el-form-item>

        <el-form-item label="省份">
          <el-input
            v-model="listQuery.province"
            placeholder="输入省份"
            clearable
            style="width: 120px"
          />
        </el-form-item>

        <el-form-item label="城市">
          <el-input
            v-model="listQuery.city"
            placeholder="输入城市"
            clearable
            style="width: 120px"
          />
        </el-form-item>

        <el-form-item label="储物柜数量">
          <el-select
            v-model="listQuery.lockerCount"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="count in lockerCountOptions"
              :key="count"
              :label="`${count}个`"
              :value="count"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="handleCreate">新增学校</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
    <el-table
      v-loading="listLoading"
      :data="schoolList"
      fit
      highlight-current-row
    >
      <el-table-column prop="schoolId" label="ID" min-width="80" />
      <el-table-column prop="schoolName" label="学校名称" min-width="180" />
      <el-table-column prop="schoolCode" label="学校编码" min-width="120" />
      <el-table-column prop="province" label="省份" min-width="100" />
      <el-table-column prop="city" label="城市" min-width="100" />
      <el-table-column prop="district" label="区县" min-width="100" />
      <el-table-column prop="contactName" label="联系人" min-width="100" />
      <el-table-column prop="contactPhone" label="联系电话" min-width="130" />
      <el-table-column prop="location" label="位置" min-width="150" />
      <el-table-column label="经纬度" min-width="160">
        <template #default="{ row }">
          <span v-if="row.longitude && row.latitude">
            {{ row.longitude }}, {{ row.latitude }}
          </span>
          <span v-else style="color: #94a3b8">未定位</span>
        </template>
      </el-table-column>
      <el-table-column prop="lockerCount" label="储物柜数量" min-width="120">
        <template #default="{ row }">{{ row.lockerCount }}个</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
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
      :title="dialogType === 'create' ? '新增学校' : '编辑学校'"
      v-model="dialogVisible"
      width="750px"
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="tempSchool"
        label-width="100px"
        :rules="rules"
      >
        <!-- 基本信息 -->
        <el-divider content-position="left">基本信息</el-divider>

        <el-form-item label="学校名称" prop="schoolName" style="margin: 20px">
          <el-input v-model="tempSchool.schoolName" placeholder="请输入学校名称" />
        </el-form-item>

        <el-form-item label="学校编码" prop="schoolCode" style="margin: 20px">
          <el-input v-model="tempSchool.schoolCode" placeholder="请输入学校编码" />
        </el-form-item>

        <el-form-item label="储物柜数量" prop="lockerCount" style="margin: 20px">
          <el-select v-model="tempSchool.lockerCount" placeholder="请选择" style="width: 100%">
            <el-option v-for="count in lockerCountOptions" :key="count" :label="`${count}个`" :value="count" />
          </el-select>
        </el-form-item>

        <!-- 地理位置 -->
        <el-divider content-position="left">地理位置</el-divider>

        <el-form-item label="省/市/区" style="margin: 20px">
          <RegionCascader v-model="regionValue" @change="handleRegionChange" />
        </el-form-item>

        <el-form-item label="详细地址" prop="location" style="margin: 20px">
          <el-input v-model="tempSchool.location" placeholder="请输入详细地址" />
        </el-form-item>

        <el-form-item label="地图定位" style="margin: 20px">
          <AmapPicker
            :model-value="tempSchool.longitude ? { longitude: tempSchool.longitude, latitude: tempSchool.latitude } : null"
            :address="tempSchool.location"
            :school-name="tempSchool.schoolName"
            :city="tempSchool.city"
            @change="handleMapPick"
          />
        </el-form-item>

        <el-form-item label="经度" style="margin: 20px">
          <el-input-number v-model="tempSchool.longitude" :precision="6" :step="0.000001" controls-position="right" style="width: 100%" />
        </el-form-item>

        <el-form-item label="纬度" style="margin: 20px">
          <el-input-number v-model="tempSchool.latitude" :precision="6" :step="0.000001" controls-position="right" style="width: 100%" />
        </el-form-item>

        <!-- 联系信息 -->
        <el-divider content-position="left">联系信息</el-divider>

        <el-form-item label="联系人" style="margin: 20px">
          <el-input v-model="tempSchool.contactName" placeholder="请输入联系人姓名" />
        </el-form-item>

        <el-form-item label="联系电话" style="margin: 20px">
          <el-input v-model="tempSchool.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>

        <el-form-item label="Logo URL" style="margin: 20px">
          <el-input v-model="tempSchool.logoUrl" placeholder="请输入 Logo 图片地址" />
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
import {
  getSchoolList,
  addSchool,
  updateSchool,
  deleteSchool,
} from "@/api/school";
import RegionCascader from '@/components/RegionCascader.vue'
import AmapPicker from '@/components/AmapPicker.vue'

const formRef = ref(null);
const schoolList = ref([]);
const total = ref(0);
const listLoading = ref(false);
const dialogVisible = ref(false);
const dialogType = ref("create");

// 查询参数
const listQuery = reactive({
  page: 1,
  size: 10,
  schoolId: null,
  schoolName: "",
  schoolCode: "",
  province: "",
  city: "",
  lockerCount: null,
});

// 表单数据
const tempSchool = reactive({
  schoolId: null,
  schoolName: '',
  schoolCode: '',
  location: '',
  province: '',
  city: '',
  district: '',
  longitude: null,
  latitude: null,
  logoUrl: '',
  contactName: '',
  contactPhone: '',
  lockerCount: 50
})

const regionValue = ref([])

// 地图选点回调 — 自动填充省市区和地址
function handleMapPick(locationData) {
  if (!locationData) return
  tempSchool.longitude = locationData.longitude
  tempSchool.latitude = locationData.latitude
  if (locationData.province) tempSchool.province = locationData.province
  if (locationData.city) tempSchool.city = locationData.city
  if (locationData.district) tempSchool.district = locationData.district
  if (locationData.address) tempSchool.location = locationData.address
  // 同步省市县联动选择器
  regionValue.value = [locationData.province, locationData.city, locationData.district].filter(Boolean)
}

// 省市县联动变化回调
function handleRegionChange(val) {
  tempSchool.province = val[0] || ''
  tempSchool.city = val[1] || ''
  tempSchool.district = val[2] || ''
}

// 储物柜数量选项
const lockerCountOptions = [50, 100, 200];

// 验证规则
const rules = reactive({
  schoolName: [{ required: true, message: '请输入学校名称', trigger: 'blur' }],
  schoolCode: [{ required: true, message: '请输入学校编码', trigger: 'blur' }],
  location: [{ required: true, message: '请输入位置', trigger: 'blur' }],
  lockerCount: [{ required: true, message: '请选择储物柜数量', trigger: 'change' }]
})

// 初始化数据
onMounted(() => {
  fetchData();
});

// 获取学校列表
const fetchData = async () => {
  listLoading.value = true;
  try {
    const params = {
      ...listQuery,
      schoolId: listQuery.schoolId || undefined,
      schoolCode: listQuery.schoolCode || undefined,
      province: listQuery.province || undefined,
      city: listQuery.city || undefined,
    };
    const res = await getSchoolList(params);
    schoolList.value = res.records;
    total.value = res.total;
  } catch (error) {
    ElMessage.error(error.message || "获取学校列表失败");
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
  listQuery.schoolId = null;
  listQuery.schoolName = "";
  listQuery.schoolCode = "";
  listQuery.province = "";
  listQuery.city = "";
  listQuery.lockerCount = null;
  handleSearch();
};

// 分页
const handlePageChange = (val) => {
  listQuery.page = val;
  fetchData();
};

// 打开新增弹窗
const handleCreate = () => {
  dialogType.value = 'create'
  dialogVisible.value = true
  Object.assign(tempSchool, {
    schoolId: null,
    schoolName: '',
    schoolCode: '',
    location: '',
    province: '',
    city: '',
    district: '',
    longitude: null,
    latitude: null,
    logoUrl: '',
    contactName: '',
    contactPhone: '',
    lockerCount: 50
  })
  regionValue.value = []
}

// 打开编辑弹窗
const handleEdit = (row) => {
  dialogType.value = 'edit'
  dialogVisible.value = true
  Object.assign(tempSchool, { ...row })
  regionValue.value = [row.province, row.city, row.district].filter(Boolean)
}

// 弹窗关闭 — 清除验证状态
const handleDialogClosed = () => {
  formRef.value?.resetFields()
  formRef.value?.clearValidate()
}

// 提交表单
const submitForm = async () => {
  try {
    await formRef.value.validate();

    if (dialogType.value === "create") {
      await addSchool(tempSchool);
      ElMessage.success("新增成功");
    } else {
      await updateSchool(tempSchool);
      ElMessage.success("修改成功");
    }
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || "操作失败");
  }
};

// 删除学校
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除学校 ${row.schoolName} 吗？`, "警告", {
      confirmButtonText: "确认",
      cancelButtonText: "取消",
      type: "warning",
    });
    await deleteSchool(row.schoolId);
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