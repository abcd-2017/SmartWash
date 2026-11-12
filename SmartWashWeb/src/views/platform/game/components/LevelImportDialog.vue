<template>
  <el-dialog
    v-model="visible"
    title="批量导入关卡"
    width="860px"
    :close-on-click-modal="false"
    @closed="resetState"
  >
    <!-- ==================== 输入态 ==================== -->
    <template v-if="stage === 'input'">
      <el-alert type="info" :closable="false" class="rule-tip">
        <template #title>
          顶层数组，每项一关，两种写法可混用：<b>裸数据</b>（元素即关卡数据，游戏包内
          DEFAULT_LEVELS 直接拷出）或<b>信封</b>
          <code>{ data, levelNo?, name?, difficulty?, enabled?, sort? }</code>；序号缺省自动顺延。
        </template>
      </el-alert>

      <div class="game-row">
        <span class="game-row-label">导入到游戏</span>
        <el-tag size="large">{{ gameTitle }}（{{ gameKey }}）</el-tag>
      </div>

      <el-upload
        drag
        accept=".json"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="handleFileChange"
        class="upload-box"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          拖拽 .json 文件到此处，或 <em>点击选择</em>
        </div>
        <template #tip>
          <div v-if="fileName" class="file-name">已选文件：{{ fileName }}</div>
          <div class="el-upload__tip">≤ {{ MAX_IMPORT_FILE_SIZE / 1024 / 1024 }}MB、最多 {{ MAX_IMPORT_COUNT }} 关；导入前会解析预览，不会直接落库</div>
        </template>
      </el-upload>

      <el-divider>或粘贴 JSON 文本</el-divider>
      <el-input
        v-model="pasteText"
        type="textarea"
        :rows="6"
        placeholder='["######","#    #"] 或 [{"name":"第一关","data":["######"]}, ...]'
      />

      <div class="step-actions">
        <el-button @click="visible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!parseInput"
          :loading="parsing"
          @click="handleParse"
        >
          解析预览
        </el-button>
      </div>
    </template>

    <!-- ==================== 预览态 ==================== -->
    <template v-else>
      <div class="preview-summary">
        <span>
          共 <b>{{ plan.rows.length }}</b> 关：新增
          <b class="num-create">{{ plan.created }}</b>，覆盖
          <b class="num-update">{{ plan.updated }}</b>
        </span>
        <el-tag v-if="plan.blocked" type="danger" effect="plain">存在错误行，修正后重新解析</el-tag>
      </div>

      <el-table :data="plan.rows" border stripe max-height="360" size="small">
        <el-table-column prop="levelNo" label="序号" width="64" align="center" />
        <el-table-column label="关卡名" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name || '—' }}</template>
        </el-table-column>
        <el-table-column label="难度" width="64" align="center">
          <template #default="{ row }">{{ row.difficulty ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="下发" width="70" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="(row.enabled ?? 1) === 1 ? 'success' : 'info'"
            >
              {{ (row.enabled ?? 1) === 1 ? '下发' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处置" width="150">
          <template #default="{ row }">
            <el-tooltip v-if="row.error" :content="row.error" placement="top">
              <el-tag size="small" type="danger">格式错误</el-tag>
            </el-tooltip>
            <el-tag v-else-if="row.fate === 'update'" size="small" type="warning">
              覆盖第 {{ row.levelNo }} 关
            </el-tag>
            <el-tag v-else size="small" type="success">新增</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关卡数据" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.dataJson.slice(0, 80) }}</template>
        </el-table-column>
      </el-table>

      <el-checkbox
        v-model="replaceAll"
        class="replace-check"
        :disabled="plan.blocked"
        @change="handleReplaceChange"
      >
        <span class="replace-text">先清空该游戏现有关卡（{{ levelList.length }} 关）再导入</span>
      </el-checkbox>

      <div class="step-actions">
        <el-button @click="stage = 'input'">返回修改</el-button>
        <el-button
          type="primary"
          :disabled="plan.blocked"
          :loading="submitting"
          @click="handleSubmit"
        >
          确认导入 {{ plan.rows.length }} 关
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import { batchImportGameLevels } from '@/api/gameData';
import {
  MAX_IMPORT_COUNT,
  MAX_IMPORT_FILE_SIZE,
  parseLevelImportText,
  planLevelImport,
  buildImportPayload,
} from '../levelImportParser';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  gameKey: { type: String, default: '' },
  gameTitle: { type: String, default: '' },
  /** 现有关卡列表：序号顺延起点与覆盖判定依据 */
  levelList: { type: Array, default: () => [] },
});

const emit = defineEmits(['update:modelValue', 'success']);

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
});

const stage = ref('input');
const fileName = ref('');
const fileText = ref('');
const pasteText = ref('');
const parsing = ref(false);
const submitting = ref(false);
const replaceAll = ref(false);
const plan = ref({ rows: [], created: 0, updated: 0, blocked: true });

// 输入源：文件优先，其次粘贴文本
const parseInput = computed(() => fileText.value || pasteText.value.trim());

const resetState = () => {
  stage.value = 'input';
  fileName.value = '';
  fileText.value = '';
  pasteText.value = '';
  replaceAll.value = false;
  plan.value = { rows: [], created: 0, updated: 0, blocked: true };
};

// 弹窗重开时复位（closed 已触发 resetState，这里兜底 gameKey 切换后遗留的预览数据）
watch(visible, (v) => {
  if (v) resetState();
});

const handleFileChange = (uploadFile) => {
  const raw = uploadFile?.raw;
  if (!raw) return;
  if (raw.size > MAX_IMPORT_FILE_SIZE) {
    ElMessage.error(`文件超过 ${MAX_IMPORT_FILE_SIZE / 1024 / 1024}MB 上限`);
    return;
  }
  const reader = new FileReader();
  reader.onload = () => {
    fileName.value = raw.name;
    fileText.value = String(reader.result || '');
    // 文件与粘贴互斥，避免解析结果与所见不符
    pasteText.value = '';
  };
  reader.onerror = () => ElMessage.error('文件读取失败，请重试');
  reader.readAsText(raw);
};

const handleParse = () => {
  parsing.value = true;
  try {
    const parsed = parseLevelImportText(parseInput.value);
    if (!parsed.ok) {
      ElMessage.error(parsed.reason);
      return;
    }
    plan.value = planLevelImport(parsed.items, props.levelList);
    stage.value = 'preview';
  } finally {
    parsing.value = false;
  }
};

// 危险选项在勾选瞬间确认，防止误勾后直接点导入
const handleReplaceChange = async (val) => {
  if (!val) return;
  try {
    await ElMessageBox.confirm(
      `将删除「${props.gameTitle}」现有 ${props.levelList.length} 关后再导入本次 ${plan.value.rows.length} 关，删除不可恢复。`,
      '清空现有关卡',
      { type: 'warning', confirmButtonText: '仍要清空', cancelButtonText: '取消' }
    );
  } catch {
    replaceAll.value = false;
  }
};

const handleSubmit = async () => {
  submitting.value = true;
  try {
    const res = await batchImportGameLevels(
      buildImportPayload(plan.value.rows, { gameKey: props.gameKey, replaceAll: replaceAll.value })
    );
    ElMessage.success(
      `导入成功：新增 ${res.created} 关、覆盖 ${res.updated} 关，版本号已递增（用户下次启动游戏生效）`
    );
    visible.value = false;
    emit('success');
  } catch {
    // 错误提示由 http 拦截器统一处理（失败原因含「第 N 关」定位）
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.rule-tip {
  margin-bottom: 12px;
}
.rule-tip code {
  padding: 1px 4px;
  background: var(--el-fill-color);
  border-radius: 4px;
}
.game-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.game-row-label {
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
.num-create {
  color: var(--el-color-success);
}
.num-update {
  color: var(--el-color-warning);
}
.replace-check {
  margin-top: 10px;
}
.replace-text {
  color: var(--el-color-danger);
}
.step-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}
</style>
