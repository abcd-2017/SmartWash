<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="优惠券ID">
          <el-input
            v-model.number="listQuery.couponId"
            placeholder="请输入ID"
            clearable
            style="width: 120px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="标题">
          <el-input
            v-model="listQuery.title"
            placeholder="请输入标题"
            clearable
            style="width: 200px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="listQuery.status"
            placeholder="全部状态"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in COUPON_STATUS_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
        <el-form-item class="filter-end">
          <el-button type="primary" @click="handleCreate">新增优惠券</el-button>
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
        empty-text="暂无数据，试试调整筛选条件"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="couponId" label="ID" min-width="80" />
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column label="折扣金额" min-width="120" align="right">
          <template #default="{ row }">
            <!-- 金额列(live 打磨 v3):600 字重 + 墨色,数字成为扫读锚点 -->
            <div class="amount-cell">￥{{ formatAmount(row.discount) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="使用门槛" min-width="120" align="right">
          <template #default="{ row }">
            <div class="amount-cell">￥{{ formatAmount(row.threshold) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="有效期" min-width="220">
          <template #default="{ row }">
            <!-- 有效期两行单元格:起始/截止上下对齐,「至」前缀弱化承载区间语义(live 打磨 v2) -->
            <div class="validity-cell">
              <span class="validity-line">{{ formatTime(row.startTime, 'YYYY-MM-DD HH:mm') }}</span>
              <span class="validity-line">
                <span class="validity-prefix">至</span
                >{{ formatTime(row.endTime, 'YYYY-MM-DD HH:mm') }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="couponStatusTagType(row.status)">
              {{ couponStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 已发放数量：删除前需评估影响面（券在用户手中时删除会使其失效） -->
        <el-table-column label="已发放" min-width="90" align="right">
          <template #default="{ row }">{{ row.issuedCount || 0 }}</template>
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
      :title="dialogType === 'create' ? '新增优惠券' : '编辑优惠券'"
      v-model="dialogVisible"
      width="min(800px, 94vw)"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="tempCoupon" label-width="min(120px, 94vw)" :rules="rules">
        <el-form-item label="优惠券标题" prop="title">
          <el-input v-model="tempCoupon.title" placeholder="请输入标题" />
        </el-form-item>

        <el-form-item label="优惠券描述">
          <el-input
            v-model="tempCoupon.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>

        <el-row :gutter="20">
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

        <el-row :gutter="20">
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

        <el-row :gutter="20">
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

        <el-form-item label="状态" prop="status">
          <el-select v-model="tempCoupon.status" placeholder="请选择状态" style="width: 100%">
            <el-option
              v-for="opt in COUPON_STATUS_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
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
import dayjs from 'dayjs';
import { getCouponList, addCoupon, updateCoupon, deleteCoupon } from '@/api/coupon';
import { formatAmount, formatTime } from '@/utils/format';
import { COUPON_STATUS_OPTIONS, couponStatusTagType, couponStatusText } from '@/constants/dict';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';
import logger from '@/utils/logger';

const tableRef = ref(null);
const formRef = ref(null);

// 弹窗关闭后清理：resetFields 归位 + clearValidate 清除校验红字，
// 避免下次打开时上次未通过的校验残留（评审：弹窗关闭清理不一致）
const handleDialogClosed = () => {
  formRef.value?.resetFields();
  formRef.value?.clearValidate();
};
const dialogVisible = ref(false);
const dialogType = ref('create');
const multipleSelection = ref([]);
const submitting = ref(false); // 确认按钮 loading，防连点

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
  discount: [
    { required: true, message: '请输入折扣金额', trigger: 'change' },
    {
      // 交叉校验：折扣金额不得大于使用门槛，否则出现「用券反而倒赚」的资金漏洞
      validator: (rule, value, callback) => {
        if (
          value != null &&
          tempCoupon.threshold != null &&
          Number(value) > Number(tempCoupon.threshold)
        ) {
          callback(new Error('折扣金额不能大于使用门槛'));
          return;
        }
        callback();
      },
      trigger: 'change',
    },
  ],
  threshold: [{ required: true, message: '请输入使用门槛', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    {
      // 交叉校验：结束时间必须晚于开始时间
      validator: (rule, value, callback) => {
        if (value && tempCoupon.startTime && !dayjs(value).isAfter(dayjs(tempCoupon.startTime))) {
          callback(new Error('结束时间必须晚于开始时间'));
          return;
        }
        callback();
      },
      trigger: 'change',
    },
  ],
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
  // 已发放数量提示：券在用户手中时删除会使其失效，需让运营明确影响面
  const issuedCount = multipleSelection.value.reduce(
    (sum, item) => sum + (Number(item.issuedCount) || 0),
    0
  );
  const issuedTip =
    issuedCount > 0 ? `已有 ${issuedCount} 张券在用户手中，删除后这些券将失效。` : '';
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除选中的 ${count} 个优惠券吗？${issuedTip}`);
  if (!confirmed) return;
  try {
    const ids = multipleSelection.value.map((item) => item.couponId).join(',');
    await deleteCoupon(ids);
    ElMessage.success('删除成功');
    // 写操作成功留痕：业务键仅优惠券 ID 串与条数
    logger.info('批量删除优惠券成功', `count=${count}`, `couponIds=${ids}`);
    fetchData();
    tableRef.value.clearSelection();
  } catch (error) {
    logger.error('批量删除优惠券失败:', `count=${count}`, error);
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
  // 时间判空：老数据/后端空值时 dayjs(null).format() 会产出 "Invalid Date"
  // 回填日期选择器并在提交时携带脏数据；空值保持 null 原样回填
  const fmtOrNull = (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : null);
  Object.assign(tempCoupon, {
    ...row,
    startTime: fmtOrNull(row.startTime),
    endTime: fmtOrNull(row.endTime),
  });
};

// 提交表单
const submitForm = async () => {
  submitting.value = true;
  try {
    await formRef.value.validate();

    // 处理时间格式（与 handleEdit 同口径判空，空值提交 null 而非 Invalid Date）
    const fmtOrNull = (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : null);
    const submitData = {
      ...tempCoupon,
      startTime: fmtOrNull(tempCoupon.startTime),
      endTime: fmtOrNull(tempCoupon.endTime),
    };

    if (dialogType.value === 'create') {
      await addCoupon(submitData);
      ElMessage.success('新增成功');
      // 写操作成功留痕：业务键仅标题（新增无 couponId）
      logger.info('新增优惠券成功', `title=${tempCoupon.title}`);
    } else {
      await updateCoupon(submitData);
      ElMessage.success('修改成功');
      logger.info('修改优惠券成功', `couponId=${tempCoupon.couponId}`, `title=${tempCoupon.title}`);
    }

    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    // validate 校验失败 reject 的是字段对象（fieldValue 含输入明文），非 Error 时仅记文本
    logger.error('保存优惠券失败:', error instanceof Error ? error : '表单校验未通过');
    ElMessage.error(error.message || '操作失败');
  } finally {
    submitting.value = false;
  }
};

// 删除优惠券
const handleDelete = async (row) => {
  // 已发放数量提示：券在用户手中时删除会使其失效，需让运营明确影响面
  const issuedCount = Number(row.issuedCount) || 0;
  const issuedTip =
    issuedCount > 0 ? `已有 ${issuedCount} 张券在用户手中，删除后这些券将失效。` : '';
  // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
  const confirmed = await useConfirm(`确认删除优惠券 "${row.title}" 吗？${issuedTip}`);
  if (!confirmed) return;
  try {
    await deleteCoupon(row.couponId);
    ElMessage.success('删除成功');
    // 写操作成功留痕：业务键仅 couponId/title
    logger.info('删除优惠券成功', `couponId=${row.couponId}`, `title=${row.title}`);
    fetchData();
  } catch (error) {
    logger.error('删除优惠券失败:', `couponId=${row.couponId}`, error);
    ElMessage.error(error.message || '删除失败');
  }
};
</script>

<style scoped>
/* 金额列(live 打磨 v3):600 字重 + 墨色文字,与正文灰阶拉开锚点层级 */
.amount-cell {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
}

/* 有效期两行单元格(live 打磨 v2):上下对齐 + tabular-nums,窄列宽下不截断 */
.validity-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  color: var(--text-body);
}

.validity-line {
  white-space: nowrap;
  line-height: 1.5;
}

.validity-prefix {
  display: inline-block;
  margin-right: 6px;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
