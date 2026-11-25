<template>
  <el-dialog
    title="目录导入菜谱"
    v-model="visible"
    width="min(920px, 94vw)"
    :close-on-click-modal="false"
    :close-on-press-escape="phase !== 'running'"
    :show-close="phase !== 'running'"
    @closed="handleClosed"
  >
    <div v-if="phase === 'choose'" class="dir-choose">
      <div class="dir-guide">
        <p class="dir-guide-title">选择包含菜谱 CSV 与封面图的父目录，按 CSV 逐行上传：</p>
        <p>
          · 目录规范：根层放 *.csv 数据文件（可多个，列与导入模板一致）+ 封面目录（image/ 或
          covers/）；.DS_Store 等隐藏文件自动忽略，其他文件给出忽略提示
        </p>
        <p>
          · CSV 列：分类名 / 菜名 / 唯一标识(可选) / 食材名列表 / 食材明细列表 / 步骤 / 封面对象键 /
          封面链接 / 热量每100g / 整份热量 / 蛋白质g / 脂肪g / 碳水g / 钠mg
        </p>
        <p>
          · 封面：按「封面对象键」列的文件名在封面目录中查找（如 dining/covers/番茄炒蛋.png →
          image/番茄炒蛋.png），jpg/jpeg/png/webp、单张 ≤5MB；上传后自动回填对象键，无封面行直接入库
        </p>
        <p>
          · 判重：唯一标识优先 →（菜名,分类）兜底 →
          同名且库内唯一兜底；命中已有菜谱标记「覆盖更新」，确认后才会执行；分类不存在时自动创建
        </p>
        <p>
          · 与文件导入入口互不影响：本入口逐条上传（单行失败不阻断），需要整批事务请用「批量导入」
        </p>
      </div>
      <div class="dir-choose-action">
        <el-button type="primary" :loading="checking" @click="handlePickDir">
          {{ checking ? '预检中…' : '选择父目录' }}
        </el-button>
        <input
          ref="dirInputRef"
          type="file"
          webkitdirectory
          multiple
          hidden
          aria-label="选择包含菜谱 CSV 与封面图的父目录"
          @change="handleDirChosen"
        />
      </div>
      <!-- 预检含多文件解码与网络比对，期间无界面切换，必须给出阶段文案否则形似卡死 -->
      <p v-if="checking" class="dir-checking">{{ checkingText }}</p>
    </div>

    <template v-else>
      <div class="dir-summary">
        <template v-if="phase === 'preview'">
          共 {{ queue.length }} 行：将上传
          <span class="dir-num">{{ uploadTargets.length }}</span>
          行（新增 {{ freshCount }} / 覆盖 {{ overwriteCount }}），跳过 {{ skippedCount }} 行
          <template v-if="missingCategories.length">
            · 将自动创建分类：<span class="dir-num">{{ missingCategories.join('、') }}</span>
          </template>
          <el-tooltip v-if="ignoredFiles.length" :content="ignoredFiles.join('；')" placement="top">
            <span class="dir-ignored">（已忽略 {{ ignoredFiles.length }} 个无关文件）</span>
          </el-tooltip>
        </template>
        <template v-else-if="phase === 'running'">
          {{ stepText }}
        </template>
        <template v-else>
          完成：成功 {{ successCount }} 行（新增 {{ freshCount }} / 覆盖更新
          {{ overwriteCount }}）， 失败 {{ failedCount }} 行，跳过 {{ skippedCount }} 行
        </template>
      </div>

      <el-progress
        v-if="phase === 'running'"
        :percentage="runPercent"
        :stroke-width="8"
        class="dir-progress"
      />

      <el-table
        :data="queue"
        size="small"
        max-height="380"
        :row-class-name="rowClassName"
        aria-label="菜谱目录导入预检列表"
        empty-text="选择父目录后，此处展示预检到的菜谱行"
        :aria-describedby="captionId"
      >
        <el-table-column label="CSV 文件" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.csvName }}</template>
        </el-table-column>
        <el-table-column label="行号" width="60" align="center">
          <template #default="{ row }">{{ row.rowNo ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="菜名" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name || '—' }}</template>
        </el-table-column>
        <el-table-column label="分类" min-width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.categoryName || '未分类' }}</template>
        </el-table-column>
        <el-table-column label="唯一标识" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sourceKey || '—' }}</template>
        </el-table-column>
        <el-table-column label="封面" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.coverFileName || '无' }}</template>
        </el-table-column>
        <el-table-column label="处理" min-width="150">
          <template #default="{ row }">
            <!-- 状态优先级：跳过 > 上传结果 > 预检标记（覆盖/新增） > 待上传 -->
            <el-tag v-if="row.skipReason" type="danger" effect="plain">跳过</el-tag>
            <el-tag
              v-else-if="row.result?.ok && row.result.kind === 'update'"
              type="warning"
              effect="plain"
            >
              已更新
            </el-tag>
            <el-tag v-else-if="row.result?.ok" type="success" effect="plain">成功</el-tag>
            <el-tooltip
              v-else-if="row.result && !row.result.ok"
              :content="row.result.message"
              placement="top"
            >
              <el-tag type="danger" effect="plain" class="dir-error-tag">失败</el-tag>
            </el-tooltip>
            <el-tag v-else-if="row.willOverwrite" type="warning" effect="plain">覆盖更新</el-tag>
            <el-tag v-else type="success" effect="plain">新增</el-tag>
            <el-tooltip v-if="row.skipReason" :content="row.skipReason" placement="top">
              <span class="dir-reason">{{ row.skipReason }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <template #append>
          <span :id="captionId" class="sr-only">菜谱目录导入预检列表</span>
        </template>
      </el-table>

      <div class="dir-actions">
        <template v-if="phase === 'preview'">
          <el-button @click="phase = 'choose'">重新选择</el-button>
          <el-button type="primary" :disabled="!uploadTargets.length" @click="startUpload">
            开始上传（{{ uploadTargets.length }} 行）
          </el-button>
        </template>
        <template v-else-if="phase === 'running'">
          <el-button type="danger" plain @click="abortFlag = true">中止上传</el-button>
        </template>
        <template v-else>
          <el-button v-if="failedCount > 0" type="warning" plain :loading="retrying" @click="retryFailed">
            重试失败行（{{ failedCount }}）
          </el-button>
          <el-button @click="phase = 'choose'">重新导入</el-button>
          <el-button type="primary" @click="visible = false">完成</el-button>
        </template>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
// 目录导入菜谱：选父目录 → 根层 CSV + 封面目录（image/covers）预检分组（问题行标红跳过）→ 覆盖预检确认
// → 逐条上传（并发 3：封面先行拿对象键 → add/update）→ 逐行结果明细。
// 目录规范 / CSV 解析 / 判重映射复用 ../diningDirectory 纯逻辑模块（单测覆盖），
// 与 RecipeImportDialog（文件两阶段导入，整批事务）为并存入口，互不影响。
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  addDiningCategory,
  addDiningRecipe,
  getDiningCategoryList,
  getDiningRecipeList,
  updateDiningRecipe,
  uploadDiningRecipeCover,
} from '@/api/dining';
import { diningCategoryOptionsCache } from '@/utils/optionCache';
import { useConfirm } from '@/composables/useConfirm';
import logger from '@/utils/logger';
import {
  buildRecipeMappings,
  buildRecipePayload,
  buildRecipeRow,
  createMissingCategories,
  decodeCsvText,
  fetchAllPaged,
  inspectDiningDir,
  markBatchDuplicates,
  markOverwrite,
  parseRecipeCsv,
  RECIPE_PAGE_SIZE,
  runPool,
  splitDiningDir,
} from '../diningDirectory';
import { useTableA11y } from '@/composables/useTableA11y';

const { captionId } = useTableA11y('dining-directory-import');

const UPLOAD_CONCURRENCY = 3;

const visible = defineModel({ type: Boolean, default: false });
const emit = defineEmits(['success']);

const dirInputRef = ref(null);
const phase = ref('choose'); // choose → preview → running → done
const queue = ref([]); // 预检后的上传队列（含跳过行，用于结果展示）
const ignoredFiles = ref([]); // 目录内被忽略的非规范文件清单（预览汇总提示）
const checking = ref(false);
const checkingText = ref('');
const resetChecking = () => {
  checking.value = false;
  checkingText.value = '';
};
const abortFlag = ref(false);
const current = ref(null);
const doneCount = ref(0);
// 重试失败行进行中（D23 加固）；runDenominator = 本轮上传行数（首轮全量 / 重试轮仅失败行），进度条按本轮算
const retrying = ref(false);
const runDenominator = ref(0);
// 覆盖更新预检拉到的既有分类（上传阶段补建缺失分类的差集依据）
const knownCategories = ref([]);

const uploadTargets = computed(() => queue.value.filter((it) => !it.skipReason));
const skippedCount = computed(() => queue.value.length - uploadTargets.value.length);
// 新增/覆盖口径分阶段：preview 按预检标记（将执行的处置），running/done 按上传成功结果
const overwriteCount = computed(() =>
  phase.value === 'preview'
    ? uploadTargets.value.filter((it) => it.willOverwrite).length
    : uploadTargets.value.filter((it) => it.result?.ok && it.result.kind === 'update').length
);
const freshCount = computed(() =>
  phase.value === 'preview'
    ? uploadTargets.value.filter((it) => !it.willOverwrite).length
    : uploadTargets.value.filter((it) => it.result?.ok && it.result.kind === 'create').length
);
const successCount = computed(() => uploadTargets.value.filter((it) => it.result?.ok).length);
const failedCount = computed(
  () => uploadTargets.value.filter((it) => it.result && !it.result.ok).length
);
const runPercent = computed(() =>
  runDenominator.value ? Math.round((doneCount.value / runDenominator.value) * 100) : 0
);
// 预检发现的不存在分类（预览汇总提示；上传时逐个补建）
const missingCategories = computed(() => {
  const known = new Set(knownCategories.value.map((c) => c.name));
  return [
    ...new Set(
      uploadTargets.value
        .filter((it) => it.categoryName && !known.has(it.categoryName))
        .map((it) => it.categoryName)
    ),
  ];
});

const categoryPreparing = ref(false);

const stepText = computed(() => {
  if (categoryPreparing.value) return '正在创建缺失分类…';
  const currentName = current.value?.name ? `：${current.value.name}` : '';
  return `正在上传 ${Math.min(doneCount.value + 1, uploadTargets.value.length)} / ${uploadTargets.value.length} 行${currentName}`;
});

// 问题行（跳过）整行标红，给原因可跳过
const rowClassName = ({ row }) =>
  row.skipReason && phase.value === 'preview' ? 'dir-row-error' : '';

const handlePickDir = () => {
  dirInputRef.value?.click();
};

const handleDirChosen = async (event) => {
  const files = Array.from(event.target.files || []);
  event.target.value = ''; // 允许重复选择同一目录
  if (!files.length) return;

  const split = splitDiningDir(files);
  const inspect = inspectDiningDir(split);
  if (!inspect.ok) {
    ElMessage.error(inspect.reason);
    return;
  }

  checking.value = true;
  checkingText.value = '正在解析 CSV 文件…';
  try {
    // 逐文件解码 + 解析 + 行归一（文件互不依赖，可并行；单文件失败不拖垮其他文件）
    const perFile = await Promise.all(
      split.csvFiles.map(async (csvFile) => {
        try {
          const text = await decodeCsvText(csvFile);
          const parsed = parseRecipeCsv(text);
          if (!parsed.ok) {
            return { items: [], fileError: { csvName: csvFile.name, reason: parsed.reason } };
          }
          const items = parsed.rows.map((row, idx) =>
            buildRecipeRow(row, {
              rowNo: idx + 1,
              csvName: csvFile.name,
              imageIndex: split.imageIndex,
            })
          );
          return { items, fileError: null };
        } catch (err) {
          logger.error('目录导入 CSV 读取失败:', csvFile.name, err);
          return { items: [], fileError: { csvName: csvFile.name, reason: '文件读取失败' } };
        }
      })
    );

    const items = perFile.flatMap((r) => r.items);
    const fileErrors = perFile
      .filter((r) => r.fileError)
      .map((r) => ({
        csvName: r.fileError.csvName,
        rowNo: null,
        name: null,
        categoryName: null,
        sourceKey: null,
        coverFileName: null,
        errors: [],
        skipReason: `${r.fileError.csvName}：${r.fileError.reason}`,
        willOverwrite: false,
        existingId: null,
        result: null,
      }));

    // 批内判重（同唯一标识 /（菜名,分类)重复 → 全部重复行标错）
    markBatchDuplicates(items);
    items.forEach((it) => {
      it.skipReason = it.errors.join('；');
    });

    // 覆盖预检：拉全量菜谱构建两级映射（sourceKey → id；（菜名,分类）→ id），命中标「覆盖更新」
    checkingText.value = '正在比对库内已有菜谱…';
    const [recipes, categories] = await Promise.all([
      fetchAllPaged(getDiningRecipeList, RECIPE_PAGE_SIZE),
      fetchAllPaged(getDiningCategoryList, RECIPE_PAGE_SIZE),
    ]);
    markOverwrite(items, buildRecipeMappings(recipes));
    knownCategories.value = categories;

    queue.value = [...fileErrors, ...items];
    ignoredFiles.value = split.ignoredFiles;
    phase.value = 'preview';
    if (!uploadTargets.value.length) {
      ElMessage.warning('所选目录中没有可上传的菜谱行，请检查 CSV 内容');
    }
  } catch (err) {
    // 预检网络失败（比对/分类拉取）兜底：不提示会停在 choose 形似卡死
    logger.error('目录导入预检失败:', err);
    ElMessage.error(err.message || '预检失败，请重新选择目录');
  } finally {
    resetChecking();
  }
};

const startUpload = async () => {
  const targets = uploadTargets.value;
  const overwrites = targets.filter((it) => it.willOverwrite);
  if (overwrites.length) {
    const preview = overwrites
      .slice(0, 5)
      .map((it) => `${it.name}（${it.categoryName || '未分类'}）`)
      .join('、');
    const more = overwrites.length > 5 ? ` 等 ${overwrites.length} 行` : '';
    const ok = await useConfirm(
      `以下 ${overwrites.length} 行命中已有菜谱，上传将覆盖更新：${preview}${more}。确定继续？`,
      '覆盖确认'
    );
    if (!ok) return;
  }

  phase.value = 'running';
  abortFlag.value = false;
  doneCount.value = 0;
  runDenominator.value = targets.length;
  current.value = null;
  try {
    await uploadAll(targets);
  } finally {
    // 中止后未开始的行统一标记（在途行各自落结果）
    targets.forEach((it) => {
      if (!it.result) it.result = { ok: false, message: '未上传（已中止）' };
    });
    phase.value = 'done';
    emit('success');
  }
};

const uploadAll = async (targets) => {
  // 分类先补建（串行，addDiningCategory 全局唯一不回传 id）→ 重拉列表映射 name→id
  categoryPreparing.value = true;
  let idByName = new Map(knownCategories.value.map((c) => [c.name, c.id]));
  try {
    const names = [...new Set(targets.map((it) => it.categoryName).filter(Boolean))];
    if (missingCategories.value.length && !abortFlag.value) {
      const { failed } = await createMissingCategories(
        names,
        knownCategories.value,
        addDiningCategory
      );
      failed.forEach((err, name) => {
        logger.error('目录导入自动建分类失败:', name, err);
      });
      knownCategories.value = await fetchAllPaged(getDiningCategoryList, RECIPE_PAGE_SIZE);
      idByName = new Map(knownCategories.value.map((c) => [c.name, c.id]));
      // 分类页下拉走共享缓存，增删后失效下次重新拉取
      diningCategoryOptionsCache.invalidate();
    }
  } catch (err) {
    // 重拉失败：所有带分类的行在 uploadItem 内以「分类不可用」失败，无分类行照常上传
    logger.error('目录导入分类列表重拉失败:', err);
    idByName = new Map();
  } finally {
    categoryPreparing.value = false;
  }

  await runPool(
    targets,
    UPLOAD_CONCURRENCY,
    async (item) => {
      current.value = item;
      await uploadItem(item, idByName);
      doneCount.value += 1;
    },
    () => abortFlag.value
  );
};

const uploadItem = async (item, idByName) => {
  try {
    // 封面先行：有封面行必须先拿到对象键（封面是行数据的一部分，失败该行报错不静默丢图）
    let coverObjectKey = null;
    if (item.coverFile) {
      const formData = new FormData();
      formData.append('file', item.coverFile);
      coverObjectKey = await uploadDiningRecipeCover(formData);
    }
    let categoryId = 0;
    if (item.categoryName) {
      categoryId = idByName.get(item.categoryName);
      if (categoryId == null) throw new Error(`分类「${item.categoryName}」自动创建失败`);
    }
    if (item.existingId != null) {
      await updateDiningRecipe(
        buildRecipePayload(item, { coverObjectKey, categoryId, existingId: item.existingId })
      );
      item.result = { ok: true, kind: 'update' };
    } else {
      await addDiningRecipe(buildRecipePayload(item, { coverObjectKey, categoryId }));
      item.result = { ok: true, kind: 'create' };
    }
    logger.info(
      '目录导入菜谱行成功',
      `${item.csvName}#${item.rowNo}`,
      `name=${item.name}`,
      `kind=${item.result.kind}`,
      `sourceKey=${item.sourceKey ?? '-'}`
    );
  } catch (err) {
    item.result = { ok: false, message: err.message || '上传失败' };
    logger.error('目录导入菜谱行失败:', `${item.csvName}#${item.rowNo}`, `name=${item.name}`, err);
  }
};

/**
 * 重试失败行（D23 加固）：仅重跑 result 失败的行（分类补建在 uploadAll 内幂等复跑，
 * 封面重传会产生新对象键覆盖同名键）；中止标志复位，进度条按本轮失败行数计。
 */
const retryFailed = async () => {
  const failedTargets = uploadTargets.value.filter((it) => it.result && !it.result.ok);
  if (!failedTargets.length) return;
  retrying.value = true;
  phase.value = 'running';
  abortFlag.value = false;
  doneCount.value = 0;
  runDenominator.value = failedTargets.length;
  current.value = null;
  try {
    await uploadAll(failedTargets);
  } finally {
    failedTargets.forEach((it) => {
      if (!it.result) it.result = { ok: false, message: '未上传（已中止）' };
    });
    retrying.value = false;
    phase.value = 'done';
    emit('success');
  }
};

const handleClosed = () => {
  phase.value = 'choose';
  queue.value = [];
  ignoredFiles.value = [];
  knownCategories.value = [];
  current.value = null;
  doneCount.value = 0;
  runDenominator.value = 0;
  retrying.value = false;
  abortFlag.value = false;
  resetChecking();
};
</script>

<style scoped>
.dir-choose {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dir-guide {
  padding: 16px 20px;
  background: var(--bg-subtle);
  border-radius: 10px;
  color: var(--text-regular);
  font-size: 13px;
  line-height: 1.8;
}

.dir-guide-title {
  font-weight: 600;
  color: var(--text-body);
}

.dir-guide p {
  margin: 0;
}

.dir-choose-action {
  display: flex;
  justify-content: center;
  padding-bottom: 8px;
}

.dir-checking {
  margin: 0;
  text-align: center;
  font-size: 13px;
  color: var(--text-secondary);
}

.dir-summary {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-regular);
}

.dir-num {
  font-weight: 700;
  color: var(--color-primary);
}

.dir-ignored {
  color: var(--text-secondary);
  cursor: help;
}

.dir-progress {
  margin-bottom: 12px;
}

.dir-reason {
  display: inline-block;
  max-width: 100%;
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.dir-error-tag {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dir-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

/* 问题行整行标红（预检阶段），原因见行内「跳过」提示 */
:deep(.el-table .dir-row-error) {
  --el-table-tr-bg-color: var(--el-color-danger-light-9);
}
</style>
