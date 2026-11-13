<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <!-- 搜索区域 -->
    <!-- 筛选区(live 打磨 v1):主操作右置成组 + 搜索/上传竖分隔,行尾不再空洞 -->
    <div class="filter-container rag-filter">
      <el-form
        :inline="true"
        :model="listQuery"
        @keyup.enter="handleSearch"
        class="rag-filter__form"
      >
        <el-form-item label="书名" class="rag-filter__field">
          <el-input
            v-model="listQuery.title"
            placeholder="请输入书名"
            clearable
            style="width: 200px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="术数方法" class="rag-filter__field">
          <el-select
            v-model="listQuery.method"
            placeholder="全部方法"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option
              v-for="m in methodOptions"
              :key="m.value"
              :label="m.label"
              :value="m.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" class="rag-filter__field">
          <el-select
            v-model="listQuery.status"
            placeholder="全部"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option label="导入中" :value="0" />
            <el-option label="可用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item class="rag-filter__actions">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
        <el-form-item class="rag-filter__primary">
          <el-button type="primary" @click="handleUpload">上传古籍</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="documentList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column prop="title" label="书名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="book" label="所属典籍" min-width="160" show-overflow-tooltip />
        <el-table-column label="术数方法" min-width="120">
          <template #default="{ row }">{{ methodText(row.method) }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'">
              {{ row.status === 1 ? '可用' : '导入中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
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

    <!-- 上传弹窗 -->
    <el-dialog
      title="上传古籍"
      v-model="dialogVisible"
      width="min(500px, 94vw)"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="uploadForm" label-width="min(100px, 94vw)" :rules="rules">
        <el-form-item label="书名" prop="title">
          <el-input v-model="uploadForm.title" placeholder="如 增删卜易" />
        </el-form-item>
        <el-form-item label="所属典籍" prop="book">
          <el-input v-model="uploadForm.book" placeholder="如 卜易典籍" />
        </el-form-item>
        <el-form-item label="术数方法" prop="method">
          <el-select v-model="uploadForm.method" placeholder="请选择术数方法" style="width: 100%">
            <el-option
              v-for="m in methodOptions"
              :key="m.value"
              :label="m.label"
              :value="m.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="文件" prop="file">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-exceed="handleExceed"
            accept=".txt,.md,.pdf,.docx"
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="upload-tip">
                支持 .txt / .md / .pdf / .docx，上传后自动触发切片与 Embedding
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitUpload" :loading="uploading">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import {
  DIV_METHOD_OPTIONS as methodOptions,
  divinationMethodText as methodText,
} from '@/constants/dict';
import { getRagDocumentList, uploadRagDocument } from '@/api/divination';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import logger from '@/utils/logger';
import { isValidationError } from '@/utils/errors';

const formRef = ref(null);
const uploadRef = ref(null);
const dialogVisible = ref(false);
const uploading = ref(false);
const selectedFile = ref(null);

const {
  list: documentList,
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
  fetchApi: getRagDocumentList,
  baseQuery: {
    title: '',
    method: '',
    status: null,
  },
  buildParams: (q) => ({
    ...q,
    title: q.title || undefined,
    method: q.method || undefined,
    status: q.status ?? undefined,
  }),
  errorMsg: '获取古籍列表失败',
});

const uploadForm = reactive({
  title: '',
  book: '',
  method: '',
});

const rules = reactive({
  title: [{ required: true, message: '请输入书名', trigger: 'blur' }],
  book: [{ required: true, message: '请输入所属典籍', trigger: 'blur' }],
  method: [{ required: true, message: '请选择术数方法', trigger: 'change' }],
  file: [{ required: true, message: '请选择文件', trigger: 'change' }],
});

onMounted(() => {
  fetchData();
});

const handleUpload = () => {
  dialogVisible.value = true;
  Object.assign(uploadForm, { title: '', book: '', method: '' });
  selectedFile.value = null;
  uploadRef.value?.clearFiles();
};

const handleDialogClosed = () => {
  formRef.value?.resetFields();
  formRef.value?.clearValidate();
  selectedFile.value = null;
  uploadRef.value?.clearFiles();
};

const handleFileChange = (file) => {
  selectedFile.value = file.raw;
};

const handleExceed = (files) => {
  // limit=1 时用新文件替换已选文件，与提示文案「已替换当前选择」保持一致
  uploadRef.value?.clearFiles();
  uploadRef.value?.handleStart(files[0]);
};

const submitUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请选择文件');
    return;
  }
  try {
    await formRef.value.validate();
    uploading.value = true;
    const formData = new FormData();
    formData.append('file', selectedFile.value);
    formData.append('title', uploadForm.title);
    formData.append('book', uploadForm.book);
    formData.append('method', uploadForm.method);
    await uploadRagDocument(formData);
    // 只记业务键与文件名，formData 含文件二进制绝不入日志
    logger.info(
      '古籍上传成功',
      `title=${uploadForm.title}`,
      `book=${uploadForm.book}`,
      `method=${uploadForm.method}`,
      `file=${selectedFile.value?.name ?? '-'}`
    );
    ElMessage.success('上传成功，正在后台切片与 Embedding');
    dialogVisible.value = false;
    fetchData();
  } catch (error) {
    // validate() 校验失败的特征是错误对象带 fields；与接口失败区分级别（校验失败不属系统错误）
    if (isValidationError(error)) {
      logger.debug('古籍上传表单校验未通过，已拦截提交（错误示于字段红字，不弹全局 toast）');
    } else {
      logger.error('古籍上传失败:', `title=${uploadForm.title}`, error);
      ElMessage.error(error.message || '上传失败');
    }
  } finally {
    uploading.value = false;
  }
};
</script>

<style scoped>
/* 筛选区(live 打磨 v1):主操作右置成组 + 搜索/上传竖分隔,行尾不再空洞 */
.rag-filter .rag-filter__form {
  gap: 12px 0;
}

.rag-filter .rag-filter__actions {
  margin-left: auto;
}

.rag-filter .rag-filter__primary {
  margin-left: 8px;
  padding-left: 16px;
  border-left: 1px solid var(--border-subtle);
}

.upload-tip {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}
</style>
