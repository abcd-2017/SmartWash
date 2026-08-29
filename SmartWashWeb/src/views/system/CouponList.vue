<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="优惠券ID">
          <el-input
            v-model.number="listQuery.couponId"
            placeholder="输入ID"
            clearable
            style="width: 120px"
          />
        </el-form-item>

        <el-form-item label="标题">
          <el-input
            v-model="listQuery.title"
            placeholder="输入标题"
            clearable
            style="width: 200px"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="listQuery.status"
            placeholder="全部状态"
            clearable
            style="width: 120px"
          >
            <el-option label="可用" value="ACTIVE" />
            <el-option label="已失效" value="INACTIVE" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="handleCreate">新增优惠券</el-button>
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
        :data="couponList"
        fit
        highlight-current-row
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="couponId" label="ID" min-width="80" />
        <el-table-column prop="title" label="标题" min-width="150" />
        <el-table-column label="折扣金额" min-width="120">
          <template #default="{ row }">￥{{ row.discount?.toFixed(2) || '0.00' }}</template>
        </el-table-column>
        <el-table-column label="使用门槛" min-width="120">
          <template #default="{ row }">￥{{ row.threshold?.toFixed(2) || '0.00' }}</template>
        </el-table-column>
        <el-table-column label="有效期" min-width="220">
          <template #default="{ row }">
            {{ formatTime(row.startTime, 'YYYY-MM-DD HH:mm') }} ~
            {{ formatTime(row.endTime, 'YYYY-MM-DD HH:mm') }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
              {{ row.status === 'ACTIVE' ? '可用' : '已失效' }}
            </el-tag>
          </template>
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
      :title="dialogType === 'create' ? '新增优惠券' : '编辑优惠券'"
      v-model="dialogVisible"
      width="800px"
    >
      <el-form ref="formRef" :model="tempCoupon" label-width="120px" :rules="rules">
        <el-form-item label="优惠券标题" prop="title" style="margin: 20px">
          <el-input v-model="tempCoupon.title" placeholder="请输入标题" />
        </el-form-item>

        <el-form-item label="优惠券描述" style="margin: 20px">
          <el-input
            v-model="tempCoupon.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>

        <el-row :gutter="20" style="margin: 20px">
          <el-col :span="12">
            <el-form-item label="折扣金额" prop="discount">
              <el-input-number
                v-model="tempCoupon.discount"
                :min="0.01"
                :precision="2"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="使用门槛" prop="threshold">
              <el-input-number
                v-model="tempCoupon.threshold"
                :min="0"
                :precision="2"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" style="margin: 20px">
          <el-col :span="12">
            <el-form-item label="有效期开始" prop="startTime">
              <el-date-picker
                v-model="tempCoupon.startTime"
                type="datetime"
                placeholder="选择开始时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期结束" prop="endTime">
              <el-date-picker
                v-model="tempCoupon.endTime"
                type="datetime"
                placeholder="选择结束时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" style="margin: 20px">
          <el-col :span="12">
            <el-form-item label="有效天数" prop="validDays">
              <el-input-number
                v-model="tempCoupon.validDays"
                :min="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="新用户专享">
              <el-switch v-model="tempCoupon.isNewUser" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="状态" prop="status" style="margin: 20px">
          <el-select v-model="tempCoupon.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="可用" value="ACTIVE" />
            <el-option label="已失效" value="INACTIVE" />
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
import dayjs from 'dayjs';
import { getCouponList, addCoupon, updateCoupon, deleteCoupon } from '@/api/coupon';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';

const tableRef = ref(null);
const formRef = ref(null);
const dialogVisible = ref(false);
const dialogType = ref('create');
const multipleSelection = ref([]);

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: couponList,
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
  fetchApi: getCouponList,
  baseQuery: {
    couponId: null,
    title: '',
    status: '',
  },
  buildParams: (q) => ({
    ...q,
    couponId: q.couponId || undefined,
  }),
  errorMsg: '获取数据失败',
});

// 表单数据
const tempCoupon = reactive({
  couponId: null,
  title: '',
  description: '',
  discount: 0.01,
  threshold: 0,
  startTime: null,
  endTime: null,
  validDays: 1,
  isNewUser: false,
  status: 'ACTIVE',
});

// 验证规则
const rules = reactive({
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 50, message: '标题不超过50字', trigger: 'blur' },
  ],
  discount: [{ required: true, message: '请输入折扣金额', trigger: 'change' }],
  threshold: [{ required: true, message: '请输入使用门槛', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  validDays: [{ required: true, message: '请输入有效天数', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
});

// 初始化数据
onMounted(() => {
  fetchData();
});

// 处理多选
const handleSelectionChange = (val) => {
  multipleSelection.value = val;
};

// 批量删除
const handleBatchDelete = async () => {
  const count = multipleSelection.value.length;
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除选中的 ${count} 个优惠券吗？`);
  if (!confirmed) return;
  try {
    const ids = multipleSelection.value.map((item) => item.couponId).join(',');
    await deleteCoupon(ids);
    ElMessage.success('删除成功');
    fetchData();
    tableRef.value.clearSelection();
  } catch (error) {
    ElMessage.error(error.message || '删除失败');
  }
};

// 打开新增弹窗
const handleCreate = () => {
  dialogType.value = 'create';
  dialogVisible.value = true;
  Object.assign(tempCoupon, {
    couponId: null,
    title: '',
    description: '',
    discount: 0.01,
    threshold: 0,
    startTime: null,
    endTime: null,
    validDays: 1,
    isNewUser: false,
    status: 'ACTIVE',
  });
};

// 打开编辑弹窗
const handleEdit = (row) => {
  dialogType.value = 'edit';
  dialogVisible.value = true;
  Object.assign(tempCoupon, {
    ...row,
    startTime: dayjs(row.startTime).format('YYYY-MM-DD HH:mm:ss'),
    endTime: dayjs(row.endTime).format('YYYY-MM-DD HH:mm:ss'),
  });
};

// 提交表单
const submitForm = async () => {
  try {
    await formRef.value.validate();

    // 处理时间格式
    const submitData = {
      ...tempCoupon,
      startTime: dayjs(tempCoupon.startTime).format('YYYY-MM-DD HH:mm:ss'),
      endTime: dayjs(tempCoupon.endTime).format('YYYY-MM-DD HH:mm:ss'),
    };

    if (dialogType.value === 'create') {
      await addCoupon(submitData);
      ElMessage.success('新增成功');
    } else {
      await updateCoupon(submitData);
      ElMessage.success('修改成功');
    }

    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '操作失败');
  }
};

// 删除优惠券
const handleDelete = async (row) => {
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除优惠券 "${row.title}" 吗？`);
  if (!confirmed) return;
  try {
    await deleteCoupon(row.couponId);
    ElMessage.success('删除成功');
    fetchData();
  } catch (error) {
    ElMessage.error(error.message || '删除失败');
  }
};
</script>

<style scoped>
@import '@/assets/pages.css';
</style>
