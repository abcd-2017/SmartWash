<template>
  <el-dialog
    v-model="visible"
    title="批量导入菜谱"
    width="860px"
    :close-on-click-modal="false"
    @closed="resetState"
  >
    <!-- ==================== 输入态：下载模板 + 上传文件 ==================== -->
    <template v-if="stage === 'input'">
      <el-alert type="info" :closable="false" class="rule-tip">
        <template #title>
          列：分类名 / 菜名 / 食材名列表 / 步骤 / 封面链接；食材之间用<b>顿号、逗号或分号</b>分隔；
          分类名需先在「分类管理」页创建（留空为未分类）；封面不支持批量导入，导入后请单独上传；
          单次最多 500 行。解析预览前<b>不会写入任何数据</b>。
        </template>
      </el-alert>

      <div class="template-row">
        <span class="template-row-label">第一次使用</span>
        <el-button size="small" :loading="templateDownloading" @click="handleDownloadTemplate">
          下载 Excel 模板（含填写说明与示例行）
        </el-button>
      </div>

      <el-upload
        drag
        accept=".xlsx,.csv"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="handleFileChange"
        class="upload-box"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽 .xlsx / .csv 文件到此处，或 <em>点击选择</em></div>
        <template #tip>
          <div v-if="fileName" class="file-name">已选文件：{{ fileName }}</div>
          <div class="el-upload__tip">≤ 5MB、最多 500 行；上传后先解析预览，确认后才落库</div>
        </template>
      </el-upload>

      <div class="step-actions">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedFile" :loading="parsing" @click="handleParse">
          解析预览
        </el-button>
      </div>
    </template>

    <!-- ==================== 预览态：逐行校验结果 + 确认 ==================== -->
    <template v-else-if="stage === 'preview'">
      <div class="preview-summary">
        <span>
          共 <b>{{ preview.total }}</b> 行：可导入 <b class="num-valid">{{ preview.validCount }}</b
          >，错误
          <b class="num-error">{{ preview.errorCount }}</b>
        </span>
        <el-tag v-if="preview.errorCount > 0" type="danger" effect="plain">
          存在错误行，修正文件后重新上传解析
        </el-tag>
      </div>

      <el-table :data="preview.rows" border stripe max-height="380" size="small">
        <el-table-column prop="rowNo" label="行号" width="64" align="center" />
        <el-table-column prop="name" label="菜名" min-width="120" show-overflow-tooltip />
        <el-table-column label="分类" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.categoryName">{{ row.categoryName }}</span>
            <span v-else class="text-muted">未分类</span>
          </template>
        </el-table-column>
        <el-table-column label="食材" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row.ingredients || []).join('、') || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="校验结果" width="140">
          <template #default="{ row }">
            <el-tooltip v-if="row.errors.length" :content="row.errors.join('；')" placement="top">
              <el-tag size="small" type="danger">校验失败</el-tag>
            </el-tooltip>
            <el-tag v-else size="small" type="success">通过</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="step-actions">
        <el-button @click="stage = 'input'">返回修改</el-button>
        <el-button
          type="primary"
          :disabled="preview.validCount === 0"
          :loading="submitting"
          @click="handleSubmit"
        >
          确认导入 {{ preview.validCount }} 行
        </el-button>
      </div>
    </template>

    <!-- ==================== 结果态：逐行成功/失败报告 ==================== -->
    <template v-else>
      <el-alert
        v-if="result.rolledBack"
        type="error"
        :closable="false"
        class="result-alert"
        :title="`导入未执行：${result.failedCount} 行校验失败，已整批回滚（0 行写入），修正后请重新上传解析`"
      />
      <el-alert
        v-else
        type="success"
        :closable="false"
        class="result-alert"
        :title="`导入成功：共 ${result.total} 行，全部写入（已默认上架，封面请在列表中单独上传）`"
      />

      <el-table :data="result.rows" border stripe max-height="380" size="small">
        <el-table-column prop="rowNo" label="行号" width="64" align="center" />
        <el-table-column prop="name" label="菜名" min-width="140" show-overflow-tooltip />
        <el-table-column label="结果" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.success" size="small" type="success">成功</el-tag>
            <el-tooltip v-else :content="row.error || '同批存在校验失败行'" placement="top">
              <el-tag size="small" type="danger">失败</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="菜谱 ID" width="100" align="center">
          <template #default="{ row }">{{ row.recipeId ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.error || '-' }}</template>
        </el-table-column>
      </el-table>

      <div class="step-actions">
        <el-button @click="stage = 'input'">继续导入</el-button>
        <el-button type="primary" @click="visible = false">完成</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import {
  confirmDiningRecipeImport,
  downloadDiningRecipeImportTemplate,
  previewDiningRecipeImport,
} from '@/api/dining';
import { saveBlobAs } from '@/utils/download';
import logger from '@/utils/logger';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
});

const emit = defineEmits(['update:modelValue', 'success']);

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
});

const MAX_FILE_BYTES = 5 * 1024 * 1024;

const stage = ref('input');
const fileName = ref('');
const selectedFile = ref(null);
const templateDownloading = ref(false);
const parsing = ref(false);
const submitting = ref(false);
// 预览结果（后端归一后的逐行结构）：{ total, validCount, errorCount, rows }
const preview = ref({ total: 0, validCount: 0, errorCount: 0, rows: [] });
// 确认导入结果报告：{ total, successCount, failedCount, rolledBack, rows }
const result = ref({ total: 0, successCount: 0, failedCount: 0, rolledBack: false, rows: [] });

const resetState = () => {
  stage.value = 'input';
  fileName.value = '';
  selectedFile.value = null;
  preview.value = { total: 0, validCount: 0, errorCount: 0, rows: [] };
  result.value = { total: 0, successCount: 0, failedCount: 0, rolledBack: false, rows: [] };
};

// 弹窗重开时复位（closed 已触发 resetState，这里兜底）
watch(visible, (v) => {
  if (v) resetState();
});

// ---------- 下载模板 ----------

const handleDownloadTemplate = async () => {
  templateDownloading.value = true;
  try {
    const blob = await downloadDiningRecipeImportTemplate();
    saveBlobAs(blob, '菜谱导入模板.xlsx');
  } catch (error) {
    logger.error('菜谱导入模板下载失败:', error);
    ElMessage.error(error.message || '模板下载失败');
  } finally {
    templateDownloading.value = false;
  }
};

// ---------- 上传与解析 ----------

const handleFileChange = (uploadFile) => {
  const raw = uploadFile?.raw;
  if (!raw) return;
  const name = raw.name.toLowerCase();
  if (!name.endsWith('.xlsx') && !name.endsWith('.csv')) {
    ElMessage.error('仅支持 .xlsx 或 .csv 文件');
    return;
  }
  if (raw.size > MAX_FILE_BYTES) {
    ElMessage.error('文件大小不能超过 5MB');
    return;
  }
  selectedFile.value = raw;
  fileName.value = raw.name;
};

const handleParse = async () => {
  if (!selectedFile.value) return;
  parsing.value = true;
  try {
    const formData = new FormData();
    formData.append('file', selectedFile.value);
    const data = await previewDiningRecipeImport(formData);
    preview.value = data;
    stage.value = 'preview';
  } catch (error) {
    // 整体性错误（缺列/超限/格式损坏）由后端信封 message 给出，拦截器统一提示后这里兜底
    logger.warn('菜谱导入解析失败:', error);
    ElMessage.error(error.message || '解析失败，请下载最新模板对照填写');
  } finally {
    parsing.value = false;
  }
};

// ---------- 确认导入（只提交通过校验的行） ----------

const handleSubmit = async () => {
  submitting.value = true;
  try {
    const validRows = preview.value.rows
      .filter((row) => row.errors.length === 0)
      .map((row) => ({
        rowNo: row.rowNo,
        categoryName: row.categoryName,
        name: row.name,
        ingredients: row.ingredients,
        steps: row.steps,
      }));
    const data = await confirmDiningRecipeImport({ rows: validRows });
    result.value = data;
    stage.value = 'result';
    if (!data.rolledBack && data.successCount > 0) {
      emit('success'); // 刷新菜谱列表
    }
  } catch (error) {
    logger.error('菜谱批量导入提交失败:', error);
    ElMessage.error(error.message || '导入失败');
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.rule-tip {
  margin-bottom: 12px;
}
.template-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.template-row-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.upload-box {
  width: 100%;
}
.file-name {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.preview-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 14px;
}
.num-valid {
  color: var(--el-color-success);
}
.num-error {
  color: var(--el-color-danger);
}
.result-alert {
  margin-bottom: 12px;
}
.text-muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.step-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}
</style>
