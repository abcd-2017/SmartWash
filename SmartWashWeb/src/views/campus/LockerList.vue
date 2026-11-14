<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="寄存柜ID">
          <el-input
            v-model.number="listQuery.lockerId"
            placeholder="请输入ID"
            clearable
            style="width: 120px"
            @clear="handleSearch"
          />
        </el-form-item>

        <el-form-item label="学校">
          <el-select
            v-model="listQuery.schoolId"
            placeholder="全部学校"
            filterable
            clearable
            style="width: 150px"
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
            @change="handleSearch"
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
            @change="handleSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
        <el-form-item class="filter-end">
          <el-button type="primary" @click="handleCreate">新增寄存柜</el-button>
          <el-button @click="handleBatchCreate">批量新增</el-button>
          <el-button @click="handleGenerateTagsForSchool">生成柜码标签</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="lockerList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
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
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link size="small" @click="handleOpenLockerTag(row)">柜码</el-button>
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
      :title="dialogType === 'create' ? '新增寄存柜' : '编辑寄存柜'"
      v-model="dialogVisible"
      width="min(600px, 94vw)"
      @closed="handleMainDialogClosed"
    >
      <el-form ref="formRef" :model="tempLocker" label-width="min(100px, 94vw)" :rules="rules">
        <el-form-item label="学校" prop="schoolId">
          <el-select
            v-model="tempLocker.schoolId"
            placeholder="请选择学校"
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
          <el-select v-model="tempLocker.status" placeholder="请选择状态" style="width: 100%">
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
        <el-button type="primary" :loading="submitting" @click="submitForm">确认</el-button>
      </template>
    </el-dialog>

    <!-- 批量新增弹窗 -->
    <el-dialog
      title="批量新增寄存柜"
      v-model="batchDialogVisible"
      width="min(600px, 94vw)"
      @closed="handleBatchDialogClosed"
    >
      <el-form
        ref="batchFormRef"
        :model="tempBatchLocker"
        label-width="min(120px, 94vw)"
        :rules="batchRules"
      >
        <el-form-item label="学校" prop="schoolId">
          <el-select
            v-model="tempBatchLocker.schoolId"
            placeholder="请选择学校"
            filterable
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

        <el-form-item label="起始柜号" prop="startNumber">
          <el-input-number
            v-model="tempBatchLocker.startNumber"
            :min="1"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="新增数量" prop="count">
          <el-input-number
            v-model="tempBatchLocker.count"
            :min="1"
            :max="1000"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>

        <div class="batch-preview">
          将新增 <span class="batch-preview__num">{{ tempBatchLocker.count }}</span> 个寄存柜 ·
          自编号 <span class="batch-preview__num">{{ tempBatchLocker.startNumber }}</span> 起 ·
          自动跳过已占用编号
        </div>
      </el-form>

      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="submitBatchForm"
          >确认</el-button
        >
      </template>
    </el-dialog>

    <!-- 柜码标签打印弹窗：A4 网格排版（学校/柜号 + 二维码 + 扫码地址），借 ATA「生成→排版→打印」玩法 -->
    <el-dialog title="柜码标签" v-model="tagDialogVisible" width="860px" class="locker-tag-dialog">
      <div id="locker-tag-print">
        <div class="tag-sheet-title">{{ tagSchoolName }} · 储物柜资产码标签</div>
        <div class="tag-grid">
          <div v-for="item in tagItems" :key="item.lockerId" class="tag-card">
            <div class="tag-school">{{ tagSchoolName }}</div>
            <div class="tag-number">{{ item.lockerNumber }} 号柜</div>
            <img v-if="item.qrDataUrl" :src="item.qrDataUrl" class="tag-qr" alt="柜码二维码" />
            <div class="tag-url">{{ item.shortUrl }}</div>
          </div>
        </div>
      </div>
      <template #footer>
        <span class="tag-count">共 {{ tagItems.length }} 张 · 建议用 A4 不干胶纸打印后裁剪贴柜</span>
        <el-button @click="tagDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="tagPrinting" @click="printTagSheet">打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import QRCode from 'qrcode';
import {
  getLockerStatus,
  getLockerList,
  addLocker,
  updateLocker,
  deleteLocker,
  batchAddLocker,
} from '@/api/locker';
import { generateLockerTag, generateLockerTagsForSchool } from '@/api/lockerTag';
import { schoolOptionsCache } from '@/utils/optionCache';
import { formatTime } from '@/utils/format';
import { lockerStatusTagType } from '@/constants/dict';
import { useTableList } from '@/composables/useTableList';
import { useTimeRange } from '@/composables/useTimeRange';
import { useConfirm } from '@/composables/useConfirm';
import logger from '@/utils/logger';

// 数据
const statusOptions = ref({}); // 状态选项（键值对格式）
const schoolOptions = ref([]); // 学校选项
const schoolMap = ref({}); // 学校ID到名称的映射
const dialogVisible = ref(false); // 弹窗显示状态
const dialogType = ref('create'); // 弹窗类型（新增/编辑）
const formRef = ref(null); // 引用表单组件

// 主弹窗关闭后清理：resetFields 归位 + clearValidate 清除校验红字，
// 避免下次打开时上次未通过的校验残留（评审：弹窗关闭清理不一致）
const handleMainDialogClosed = () => {
  formRef.value?.resetFields();
  formRef.value?.clearValidate();
};
const submitting = ref(false); // 确认按钮 loading，防连点

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

// 批量新增弹窗状态
const batchDialogVisible = ref(false);
const batchFormRef = ref(null);
const batchSubmitting = ref(false);

// 批量弹窗关闭后清理：与主弹窗各自 handler，避免共用一个 handler 清错表单实例
const handleBatchDialogClosed = () => {
  batchFormRef.value?.resetFields();
  batchFormRef.value?.clearValidate();
};

const tempBatchLocker = reactive({
  schoolId: null,
  startNumber: 1,
  count: 100,
});

const batchRules = reactive({
  schoolId: [
    { required: true, message: '请选择学校', trigger: 'change' },
    { type: 'number', min: 1, message: '学校ID必须大于0' },
  ],
  startNumber: [
    { required: true, message: '请输入起始柜号', trigger: 'change' },
    { type: 'number', min: 1, message: '起始柜号必须大于0' },
  ],
  count: [
    { required: true, message: '请输入新增数量', trigger: 'change' },
    { type: 'number', min: 1, message: '新增数量必须大于0' },
  ],
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
    // 传输层失败已由 http.js 拦截器统一留痕，这里归因到页面动作，避免静默失败
    logger.error('获取状态失败:', error);
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
  } catch (error) {
    // 传输层失败已由 http.js 拦截器统一留痕，这里归因到页面动作，避免静默失败
    logger.error('获取学校列表失败:', error);
    ElMessage.error('获取学校列表失败');
  }
};

// 打开批量新增弹窗
const handleBatchCreate = () => {
  batchDialogVisible.value = true;
  Object.assign(tempBatchLocker, {
    schoolId: null,
    startNumber: 1,
    count: 100,
  });
};

// 提交批量新增
const submitBatchForm = async () => {
  batchSubmitting.value = true;
  try {
    await batchFormRef.value.validate();

    const res = await batchAddLocker({
      schoolId: tempBatchLocker.schoolId,
      startNumber: tempBatchLocker.startNumber,
      count: tempBatchLocker.count,
    });

    ElMessage.success(`批量新增成功：${res.addedCount} 个（${res.firstNumber}-${res.lastNumber}）`);
    // 写操作成功留痕：业务键仅 schoolId/编号范围（批量新增无 lockerId）
    logger.info(
      '批量新增寄存柜成功',
      `schoolId=${tempBatchLocker.schoolId}`,
      `addedCount=${res.addedCount}`,
      `range=${res.firstNumber}-${res.lastNumber}`
    );
    batchDialogVisible.value = false;
    fetchData();
  } catch (error) {
    // validate 校验失败 reject 的是字段对象（fieldValue 含输入明文），非 Error 时仅记文本
    logger.error('批量新增寄存柜失败:', error instanceof Error ? error : '表单校验未通过');
    ElMessage.error(error.message || '操作失败');
  } finally {
    batchSubmitting.value = false;
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
  submitting.value = true;
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
      // 写操作成功留痕：业务键仅 schoolId/lockerNumber（新增无 lockerId）
      logger.info(
        '新增寄存柜成功',
        `schoolId=${tempLocker.schoolId}`,
        `lockerNumber=${tempLocker.lockerNumber}`
      );
    } else {
      await updateLocker(submitData);
      ElMessage.success('修改成功');
      logger.info(
        '修改寄存柜成功',
        `lockerId=${tempLocker.lockerId}`,
        `lockerNumber=${tempLocker.lockerNumber}`
      );
    }

    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    // validate 校验失败 reject 的是字段对象（fieldValue 含输入明文），非 Error 时仅记文本
    logger.error('保存寄存柜失败:', error instanceof Error ? error : '表单校验未通过');
    ElMessage.error(error.message || '操作失败');
  } finally {
    submitting.value = false;
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
    // 写操作成功留痕：业务键仅 lockerId/lockerNumber
    logger.info('删除寄存柜成功', `lockerId=${row.lockerId}`, `lockerNumber=${row.lockerNumber}`);
    fetchData();
  } catch (error) {
    logger.error('删除寄存柜失败:', `lockerId=${row.lockerId}`, error);
    ElMessage.error(error.message || '删除失败');
  }
};

// 状态文本转换
const formatStatus = (status) => {
  return statusOptions.value[status] || '未知状态';
};

// ==================== 柜码标签（一物一码 M3） ====================
// 柜码 = 短码内核活码（别名 L{lockerId}，幂等）：扫码 /web/t/{code} 302 → 公开落地页 /l/{lockerId}
const tagDialogVisible = ref(false);
const tagItems = ref([]); // [{lockerId, lockerNumber, code, shortUrl, qrDataUrl}]
const tagSchoolName = ref('');
const tagPrinting = ref(false);

// 单柜生成柜码并打开打印弹窗（幂等：重复生成复用既有活码，不重复建行）
const handleOpenLockerTag = async (row) => {
  try {
    const vo = await generateLockerTag(row.lockerId);
    await openTagDialog(
      [
        {
          lockerId: row.lockerId,
          lockerNumber: row.lockerNumber,
          code: vo.code,
          shortUrl: vo.shortUrl,
        },
      ],
      schoolMap.value[row.schoolId]?.schoolName || ''
    );
  } catch (error) {
    logger.error('柜码生成失败:', `id=${row.lockerId}`, error);
    ElMessage.error(error.message || '柜码生成失败');
  }
};

// 按校批量生成（需先按学校筛选；逐柜幂等含既有复用项，一次出整校标签）
const handleGenerateTagsForSchool = async () => {
  const schoolId = listQuery.schoolId;
  if (!schoolId) {
    ElMessage.warning('请先在搜索区选择学校，再批量生成柜码标签');
    return;
  }
  const confirmed = await useConfirm(
    `确认为「${schoolMap.value[schoolId]?.schoolName || schoolId}」的全部储物柜生成柜码标签吗？已生成的柜子将复用原有码。`
  );
  if (!confirmed) return;
  try {
    const items = await generateLockerTagsForSchool(schoolId);
    await openTagDialog(items, schoolMap.value[schoolId]?.schoolName || '');
    ElMessage.success(`已就绪 ${items.length} 张柜码标签`);
  } catch (error) {
    logger.error('批量生成柜码失败:', `schoolId=${schoolId}`, error);
    ElMessage.error(error.message || '批量生成柜码失败');
  }
};

// 打开弹窗并生成二维码 DataURL（qrcode 库浏览器端本地生成，不走后端）
const openTagDialog = async (items, schoolName) => {
  tagSchoolName.value = schoolName;
  tagItems.value = [];
  tagPrinting.value = false;
  tagDialogVisible.value = true;
  try {
    tagItems.value = await Promise.all(
      items.map(async (item) => ({
        ...item,
        qrDataUrl: item.shortUrl
          ? await QRCode.toDataURL(item.shortUrl, { width: 240, margin: 1 })
          : '',
      }))
    );
  } catch (error) {
    logger.error('二维码生成失败:', error);
    ElMessage.error('二维码生成失败，请重试');
  }
};

// 打印（@media print 仅保留 #locker-tag-print 排版区）
const printTagSheet = async () => {
  await nextTick();
  tagPrinting.value = true;
  window.print();
  tagPrinting.value = false;
};
</script>

<style scoped>
/* 批量新增结果预览条：提交前让操作者确认将产生的结果，数字等宽防跳动 */
.batch-preview {
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--bg-subtle);
  font-size: 13px;
  color: var(--text-regular);
  line-height: 1.6;
}

.batch-preview__num {
  color: var(--text-primary);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* 标签排版（屏显）：4 列网格，卡片即裁剪边界 */
.tag-sheet-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.tag-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  max-height: 460px;
  overflow: auto;
}

.tag-card {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px 8px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

/* 学校/柜号：柜号等宽 tabular nums（DESIGN.md mono 规则） */
.tag-school {
  font-size: 12px;
  color: var(--text-secondary);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag-number {
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  font-variant-numeric: tabular-nums;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.tag-qr {
  width: 110px;
  height: 110px;
}

.tag-url {
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  font-variant-numeric: tabular-nums;
  font-size: 10px;
  color: var(--text-secondary);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag-count {
  margin-right: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>

<style>
/* 打印（非 scoped：需作用到 body 层级）——仅保留标签排版区，A4 网格裁剪贴柜 */
@media print {
  body * {
    visibility: hidden !important;
  }
  #locker-tag-print,
  #locker-tag-print * {
    visibility: visible !important;
  }
  #locker-tag-print {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
  }
  .tag-grid {
    max-height: none;
    overflow: visible;
    display: flex;
    flex-wrap: wrap;
  }
  .tag-card {
    width: 25%;
    box-sizing: border-box;
    break-inside: avoid;
  }
}
</style>
