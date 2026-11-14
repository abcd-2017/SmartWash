<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="学校ID">
          <el-input
            v-model.number="listQuery.schoolId"
            placeholder="请输入学校ID"
            clearable
            style="width: 120px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="学校名称">
          <el-input
            v-model="listQuery.schoolName"
            placeholder="输入学校名称"
            clearable
            style="width: 200px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="学校编码">
          <el-input
            v-model="listQuery.schoolCode"
            placeholder="输入学校编码"
            clearable
            style="width: 160px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="省份">
          <el-input
            v-model="listQuery.province"
            placeholder="输入省份"
            clearable
            style="width: 120px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="城市">
          <el-input
            v-model="listQuery.city"
            placeholder="输入城市"
            clearable
            style="width: 120px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="储物柜数量">
          <el-select
            v-model="listQuery.lockerCount"
            placeholder="请选择"
            clearable
            style="width: 120px"
            @change="handleSearch"
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
        </el-form-item>
        <el-form-item class="filter-end">
          <el-button type="primary" @click="handleCreate">新增学校</el-button>
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
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column prop="schoolId" label="ID" min-width="70" />
        <el-table-column prop="schoolName" label="学校名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="schoolCode" label="学校编码" min-width="100" />
        <el-table-column label="地址" min-width="190">
          <template #default="{ row }">
            <!-- 地址两行单元格（对齐 CouponList 有效期列先例）：主行详细地址，次行 12px 弱化省市区 + 经纬度，替代原省/市/区/位置/经纬度 5 列 -->
            <div class="addr-cell">
              <span class="addr-cell__main">{{ row.location || '-' }}</span>
              <span class="addr-cell__sub">{{ addressSubLine(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="contactName" label="联系人" min-width="90">
          <template #default="{ row }">{{ row.contactName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="contactPhone" label="联系电话" min-width="120">
          <template #default="{ row }">{{ row.contactPhone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="lockerCount" label="储物柜数量" min-width="90">
          <template #default="{ row }">{{ row.lockerCount }}个</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180">
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
      :title="dialogType === 'create' ? '新增学校' : '编辑学校'"
      v-model="dialogVisible"
      width="min(750px, 94vw)"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempSchool" label-width="min(100px, 94vw)" :rules="rules">
        <!-- 基本信息 -->
        <el-divider content-position="left">基本信息</el-divider>

        <el-form-item label="学校名称" prop="schoolName">
          <el-input v-model="tempSchool.schoolName" placeholder="请输入学校名称" />
        </el-form-item>

        <el-form-item label="学校编码" prop="schoolCode">
          <el-input v-model="tempSchool.schoolCode" placeholder="请输入学校编码" />
        </el-form-item>

        <el-form-item label="储物柜数量" prop="lockerCount">
          <el-select v-model="tempSchool.lockerCount" placeholder="请选择" style="width: 100%">
            <el-option
              v-for="count in lockerCountOptions"
              :key="count"
              :label="`${count}个`"
              :value="count"
            />
          </el-select>
        </el-form-item>

        <!-- 地理位置 -->
        <el-divider content-position="left">地理位置</el-divider>

        <el-form-item label="省/市/区">
          <RegionCascader v-model="regionValue" @change="handleRegionChange" />
        </el-form-item>

        <el-form-item label="详细地址" prop="location">
          <el-input v-model="tempSchool.location" placeholder="请输入详细地址" />
        </el-form-item>

        <el-form-item label="地图定位">
          <AmapPicker
            :model-value="
              tempSchool.longitude
                ? { longitude: tempSchool.longitude, latitude: tempSchool.latitude }
                : null
            "
            :address="tempSchool.location"
            :school-name="tempSchool.schoolName"
            :city="tempSchool.city"
            @change="handleMapPick"
          />
        </el-form-item>

        <el-form-item label="经度">
          <el-input-number
            v-model="tempSchool.longitude"
            :precision="6"
            :step="0.000001"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="纬度">
          <el-input-number
            v-model="tempSchool.latitude"
            :precision="6"
            :step="0.000001"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>

        <!-- 联系信息 -->
        <el-divider content-position="left">联系信息</el-divider>

        <el-form-item label="联系人">
          <el-input v-model="tempSchool.contactName" placeholder="请输入联系人姓名" />
        </el-form-item>

        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="tempSchool.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>

        <el-form-item label="Logo URL">
          <el-input v-model="tempSchool.logoUrl" placeholder="请输入 Logo 图片地址" />
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
import { getSchoolList, addSchool, updateSchool, deleteSchool } from '@/api/school';
import { schoolOptionsCache } from '@/utils/optionCache';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';
import RegionCascader from '@/components/RegionCascader.vue';
import AmapPicker from '@/components/AmapPicker.vue';
import logger from '@/utils/logger';

const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref('create');
const submitting = ref(false); // 确认按钮 loading，防连点

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: schoolList,
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
  fetchApi: getSchoolList,
  baseQuery: {
    schoolId: null,
    schoolName: '',
    schoolCode: '',
    province: '',
    city: '',
    lockerCount: null,
  },
  buildParams: (q) => ({
    ...q,
    schoolId: q.schoolId || undefined,
    schoolCode: q.schoolCode || undefined,
    province: q.province || undefined,
    city: q.city || undefined,
  }),
  errorMsg: '获取学校列表失败',
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
  lockerCount: 50,
});

const regionValue = ref([]);

// 地图选点回调 — 自动填充省市区和地址
function handleMapPick(locationData) {
  if (!locationData) return;
  tempSchool.longitude = locationData.longitude;
  tempSchool.latitude = locationData.latitude;
  if (locationData.province) tempSchool.province = locationData.province;
  if (locationData.city) tempSchool.city = locationData.city;
  if (locationData.district) tempSchool.district = locationData.district;
  if (locationData.address) tempSchool.location = locationData.address;
  // 同步省市县联动选择器
  regionValue.value = [locationData.province, locationData.city, locationData.district].filter(
    Boolean
  );
}

// 省市县联动变化回调
function handleRegionChange(val) {
  tempSchool.province = val[0] || '';
  tempSchool.city = val[1] || '';
  tempSchool.district = val[2] || '';
}

// 地址两行单元格次行：省市区 + 经纬度弱化展示（均缺省时提示未定位）
function addressSubLine(row) {
  const region = [row.province, row.city, row.district].filter(Boolean).join(' ');
  const coord = row.longitude && row.latitude ? `${row.longitude}, ${row.latitude}` : '';
  return [region, coord].filter(Boolean).join(' · ') || '未定位';
}

// 储物柜数量选项
const lockerCountOptions = [50, 100, 200];

// 验证规则
const rules = reactive({
  // 联系电话可选填，填了必须合法（口径与 UserList 手机号校验一致）；可空字段 async-validator 自动跳过
  contactPhone: [
    { pattern: /^(?:\+86)?1[3-9]\d{9}$/, message: '联系电话格式不正确', trigger: 'blur' },
  ],

  schoolName: [{ required: true, message: '请输入学校名称', trigger: 'blur' }],
  schoolCode: [{ required: true, message: '请输入学校编码', trigger: 'blur' }],
  location: [{ required: true, message: '请输入位置', trigger: 'blur' }],
  lockerCount: [{ required: true, message: '请选择储物柜数量', trigger: 'change' }],
});

// 初始化数据
onMounted(() => {
  fetchData();
});

// 打开新增弹窗
const handleCreate = () => {
  dialogType.value = 'create';
  dialogVisible.value = true;
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
    lockerCount: 50,
  });
  regionValue.value = [];
};

// 打开编辑弹窗
const handleEdit = (row) => {
  dialogType.value = 'edit';
  dialogVisible.value = true;
  Object.assign(tempSchool, { ...row });
  regionValue.value = [row.province, row.city, row.district].filter(Boolean);
};

// 弹窗关闭 — 清除验证状态
const handleDialogClosed = () => {
  formRef.value?.resetFields();
  formRef.value?.clearValidate();
};

// 提交表单
const submitForm = async () => {
  submitting.value = true;
  try {
    await formRef.value.validate();

    if (dialogType.value === 'create') {
      await addSchool(tempSchool);
      ElMessage.success('新增成功');
      // 写操作成功留痕：业务键仅学校名称，联系电话等敏感字段不入日志（新增无 schoolId）
      logger.info('新增学校成功', `schoolName=${tempSchool.schoolName}`);
    } else {
      await updateSchool(tempSchool);
      ElMessage.success('修改成功');
      logger.info(
        '修改学校成功',
        `schoolId=${tempSchool.schoolId}`,
        `schoolName=${tempSchool.schoolName}`
      );
    }
    // 学校数据有变，失效学校下拉的全量缓存，其他页面下次进入时重新拉取
    schoolOptionsCache.invalidate();
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    // validate 校验失败 reject 的是字段对象（fieldValue 含输入明文），非 Error 时仅记文本
    logger.error('保存学校失败:', error instanceof Error ? error : '表单校验未通过');
    ElMessage.error(error.message || '操作失败');
  } finally {
    submitting.value = false;
  }
};

// 删除学校
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除学校 ${row.schoolName} 吗？`);
  if (!confirmed) return;
  try {
    await deleteSchool(row.schoolId);
    ElMessage.success('删除成功');
    // 写操作成功留痕：业务键仅 schoolId/schoolName，联系电话不入日志
    logger.info('删除学校成功', `schoolId=${row.schoolId}`, `schoolName=${row.schoolName}`);
    // 学校下拉缓存同步失效（评审 #15 的失效入口）
    schoolOptionsCache.invalidate();
    fetchData();
  } catch (error) {
    logger.error('删除学校失败:', `schoolId=${row.schoolId}`, error);
    ElMessage.error(error.message || '删除失败');
  }
};
</script>

<style scoped>
/* 地址两行单元格：主行详细地址、次行 12px 弱化省市区/经纬度，窄列宽下单行省略
   （对齐 pages.css .laundry-item 两行单元格语言，此处列语义不同故页内自带样式） */
.addr-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.addr-cell__main {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.addr-cell__sub {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
