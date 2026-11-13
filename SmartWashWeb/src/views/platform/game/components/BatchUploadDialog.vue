<template>
  <el-dialog
    title="批量导入游戏"
    v-model="visible"
    width="880px"
    :close-on-click-modal="false"
    :close-on-press-escape="phase !== 'running'"
    :show-close="phase !== 'running'"
    @closed="handleClosed"
  >
    <!-- 步骤 1：选择父目录 -->
    <div v-if="phase === 'choose'" class="batch-choose">
      <div class="batch-guide">
        <p class="batch-guide-title">选择包含多个游戏目录的父目录，按规范逐个上传：</p>
        <p>
          · 每个游戏目录 = index.html（必须入口）+ README.md（可选，自动解析标题 / gameKey / 分类 /
          玩法）+ 封面图（可选）
        </p>
        <p>
          · 目录名 = gameKey（小写字母开头的 3-64 位字母数字-_）；不符合规范的目录自动跳过并给出原因
        </p>
        <p>· 与已有游戏同 gameKey 时会先标记「覆盖更新」，确认后才会执行</p>
      </div>
      <div class="batch-choose-action">
        <el-button type="primary" @click="handlePickDir">选择父目录</el-button>
        <input
          ref="dirInputRef"
          type="file"
          webkitdirectory
          multiple
          hidden
          @change="handleDirChosen"
        />
      </div>
    </div>

    <!-- 步骤 2/3/4：预检队列 / 上传中 / 结果 -->
    <template v-else>
      <div class="batch-summary">
        <template v-if="phase === 'preview'">
          共 {{ queue.length }} 个目录：将上传
          <span class="batch-num">{{ uploadTargets.length }}</span> 个（新增 {{ freshCount }} / 覆盖
          {{ overwriteCount }}），跳过 {{ skippedCount }} 个
        </template>
        <template v-else-if="phase === 'running'">
          正在上传 {{ doneCount + 1 }} / {{ uploadTargets.length }}：{{ current?.title }}
        </template>
        <template v-else>
          完成：成功 {{ successCount }} 个（新增 {{ freshCount }} / 覆盖 {{ overwriteCount }}），
          失败 {{ failedCount }} 个，跳过 {{ skippedCount }} 个
        </template>
      </div>

      <el-progress
        v-if="phase === 'running'"
        :percentage="runPercent"
        :stroke-width="8"
        class="batch-progress"
      />

      <el-table
        :data="queue"
        size="small"
        max-height="380"
        empty-text="选择父目录后，此处展示预检到的游戏目录"
      >
        <el-table-column label="目录" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.dirName }}</template>
        </el-table-column>
        <el-table-column label="gameKey" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.gameKey || '-' }}</template>
        </el-table-column>
        <el-table-column label="标题" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.title || '-' }}</template>
        </el-table-column>
        <el-table-column label="大小" width="80">
          <template #default="{ row }">{{
            row.sizeBytes ? formatFileSize(row.sizeBytes) : '-'
          }}</template>
        </el-table-column>
        <el-table-column label="处理" min-width="200">
          <template #default="{ row }">
            <!-- 状态优先级：跳过 > 上传结果 > 预检标记（新增/覆盖） -->
            <el-tag v-if="row.skipReason" type="info" effect="plain">跳过</el-tag>
            <el-tag v-else-if="row.result?.ok" type="success" effect="plain">成功</el-tag>
            <el-tag v-else-if="row.result && !row.result.ok" type="danger" effect="plain">
              {{ row.result.message }}
            </el-tag>
            <el-tag v-else-if="row.willOverwrite" type="warning" effect="plain">覆盖更新</el-tag>
            <el-tag v-else type="success" effect="plain">新增</el-tag>
            <span v-if="row.skipReason" class="batch-reason">{{ row.skipReason }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="batch-actions">
        <template v-if="phase === 'preview'">
          <el-button @click="phase = 'choose'">重新选择</el-button>
          <el-button
            type="primary"
            :disabled="!uploadTargets.length"
            :loading="checking"
            @click="startUpload"
          >
            开始上传（{{ uploadTargets.length }} 个）
          </el-button>
        </template>
        <template v-else-if="phase === 'running'">
          <el-button type="danger" plain @click="abortFlag = true">中止上传</el-button>
        </template>
        <template v-else>
          <el-button @click="phase = 'choose'">重新导入</el-button>
          <el-button type="primary" @click="visible = false">完成</el-button>
        </template>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
// 批量导入游戏：选父目录 → 按规范预检分组（不合规跳过）→ 覆盖预检确认 → 串行上传 → 结果明细。
// 目录规范与 README 解析复用 ./gameDirectory 共享模块（与单目录导入同源，避免规范漂移）。
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getGameList, uploadGame, uploadGameCover } from '@/api/game';
import { useConfirm } from '@/composables/useConfirm';
import { formatFileSize } from '@/utils/format';
import logger from '@/utils/logger';
import { splitGameDirs, inspectGameDir, parseGameReadme, GAME_KEY_PATTERN } from '../gameDirectory';

/** 单游戏包上限，与后端 GamePackageValidator.MAX_ORIGINAL_BYTES 对齐 */
const MAX_PACKAGE_BYTES = 10 * 1024 * 1024;

const visible = defineModel({ type: Boolean, default: false });
const emit = defineEmits(['success']);

const dirInputRef = ref(null);
const phase = ref('choose'); // choose → preview → running → done
const queue = ref([]); // 预检后的上传队列（含跳过项，用于结果展示）
const checking = ref(false); // 覆盖预检请求中
const abortFlag = ref(false);
const current = ref(null); // 当前上传项
const currentPercent = ref(0);
const doneCount = ref(0);

const uploadTargets = computed(() => queue.value.filter((it) => !it.skipReason));
const skippedCount = computed(() => queue.value.length - uploadTargets.value.length);
const overwriteCount = computed(
  () =>
    queue.value.filter((it) => it.willOverwrite && it.result?.ok).length ||
    uploadTargets.value.filter((it) => it.willOverwrite).length
);
// 结果页的新增/覆盖口径：以上传成功项为准（未开始的仍按预检标记展示在表格里）
const freshCount = computed(
  () => uploadTargets.value.filter((it) => it.result?.ok && !it.willOverwrite).length
);
const successCount = computed(() => uploadTargets.value.filter((it) => it.result?.ok).length);
const failedCount = computed(
  () => uploadTargets.value.filter((it) => it.result && !it.result.ok).length
);
const runPercent = computed(() =>
  uploadTargets.value.length ? Math.round((doneCount.value / uploadTargets.value.length) * 100) : 0
);

const handlePickDir = () => {
  dirInputRef.value?.click();
};

// 拉全量已入库 gameKey（分页循环，size 上限 100；用于覆盖预检）
const fetchAllGameKeys = async () => {
  const keys = new Set();
  let page = 1;
  let total = Infinity;
  while ((page - 1) * 100 < total && page <= 100) {
    const res = await getGameList({ page, size: 100 });
    total = res.total || 0;
    const records = res.records || [];
    if (!records.length) break; // 防御：空页直接结束，避免死循环
    records.forEach((r) => keys.add(r.gameKey));
    page += 1;
  }
  return keys;
};

const handleDirChosen = async (event) => {
  const files = Array.from(event.target.files || []);
  event.target.value = ''; // 允许重复选择同一目录
  if (!files.length) return;
  const groups = splitGameDirs(files);
  if (!groups.length) {
    ElMessage.error('未识别到游戏目录——请选择「包含多个游戏目录」的父目录');
    return;
  }

  checking.value = true;
  try {
    // 逐目录校验 + README 解析（读文件是异步的，统一 Promise.all）
    const items = await Promise.all(
      groups.map(async ({ dirName, files: groupFiles }) => {
        const inspect = inspectGameDir(dirName, groupFiles, 2);
        const item = {
          dirName,
          skipReason: inspect.ok ? '' : inspect.reason,
          indexHtml: inspect.indexHtml,
          coverFile: inspect.coverFile,
          title: '',
          gameKey: '',
          category: '',
          description: '',
          sizeBytes: 0,
          willOverwrite: false,
          result: null, // { ok, message }：上传结果（done 页展示）
        };
        if (!inspect.ok) return item;
        if (inspect.indexHtml.size > MAX_PACKAGE_BYTES) {
          item.skipReason = `index.html 超过 10MB 上限（${formatFileSize(inspect.indexHtml.size)}）`;
          return item;
        }
        item.sizeBytes = inspect.indexHtml.size;
        let meta = { title: '', gameKey: dirName, category: '', description: '' };
        if (inspect.readme) {
          try {
            meta = parseGameReadme(await inspect.readme.text(), dirName);
          } catch (err) {
            logger.error('批量导入 README 读取失败:', dirName, err);
            // 读取失败不跳过：按目录名兜底继续（与无 README 同路径）
          }
        }
        item.gameKey = meta.gameKey;
        // 规范约定目录名 = gameKey；README 指定的 key 或中文目录名不合规即跳过
        if (!GAME_KEY_PATTERN.test(item.gameKey)) {
          item.skipReason = `gameKey「${item.gameKey || dirName}」不符合规范（小写字母开头的 3-64 位字母数字-_）`;
          return item;
        }
        // 标题/分类兜底：README 缺失或解析不出时用目录名 / other（批量场景无人手填）
        item.title = meta.title || dirName;
        item.category = meta.category || 'other';
        item.description = meta.description;
        return item;
      })
    );

    // 队列内 gameKey 去重：重复的后来者跳过（同名目录内容只会传一份）
    const seen = new Set();
    for (const it of items) {
      if (it.skipReason) continue;
      if (seen.has(it.gameKey)) {
        it.skipReason = 'gameKey 与队列内其他目录重复';
      } else {
        seen.add(it.gameKey);
      }
    }

    // 库内覆盖预检：已有同 key 标记「覆盖更新」，开始上传前统一确认
    const existing = await fetchAllGameKeys();
    for (const it of items) {
      if (!it.skipReason) it.willOverwrite = existing.has(it.gameKey);
    }

    queue.value = items;
    phase.value = 'preview';
    if (!uploadTargets.value.length) {
      ElMessage.warning('所选目录中没有符合规范的游戏目录，请检查目录结构');
    }
  } finally {
    checking.value = false;
  }
};

const buildFormData = (item, coverUrl) => {
  const fd = new FormData();
  fd.append('file', item.indexHtml);
  fd.append('title', item.title);
  fd.append('gameKey', item.gameKey);
  fd.append('category', item.category);
  fd.append('description', item.description);
  // 批量导入不指定个性化外观，默认值与单个上传表单初值一致
  fd.append('icon', 'gamepad');
  fd.append('color', 'green');
  fd.append('badge', '');
  fd.append('sort', '100');
  fd.append('remark', '');
  fd.append('coverUrl', coverUrl || '');
  return fd;
};

const startUpload = async () => {
  const targets = uploadTargets.value;
  const overwrites = targets.filter((it) => it.willOverwrite);
  if (overwrites.length) {
    const preview = overwrites
      .slice(0, 5)
      .map((it) => it.gameKey)
      .join('、');
    const more = overwrites.length > 5 ? ` 等 ${overwrites.length} 个` : '';
    const ok = await useConfirm(
      `以下 gameKey 已存在，上传将覆盖更新：${preview}${more}。确定继续？`,
      '覆盖确认'
    );
    if (!ok) return;
  }

  phase.value = 'running';
  abortFlag.value = false;
  doneCount.value = 0;
  for (const item of targets) {
    if (abortFlag.value) {
      item.result = { ok: false, message: '未上传（已中止）' };
      continue;
    }
    current.value = item;
    currentPercent.value = 0;
    try {
      // 目录内封面（可选）：先传封面拿直链再回填，失败不中断该游戏上传
      let coverUrl = '';
      if (item.coverFile) {
        try {
          coverUrl = await uploadGameCover(item.coverFile);
        } catch (err) {
          logger.error('批量导入封面上传失败（忽略继续）:', item.dirName, err);
        }
      }
      const res = await uploadGame(buildFormData(item, coverUrl), (e) => {
        if (e.total) currentPercent.value = Math.round((e.loaded / e.total) * 100);
      });
      item.result = { ok: true };
      logger.info('批量导入游戏成功', `gameKey=${item.gameKey}`, `id=${res?.id ?? '-'}`);
    } catch (err) {
      item.result = { ok: false, message: err.message || '上传失败' };
      logger.error('批量导入游戏失败:', `gameKey=${item.gameKey}`, err);
    }
    doneCount.value += 1;
  }
  phase.value = 'done';
  emit('success'); // 通知列表刷新（新增与覆盖都会改变列表内容）
};

// 关闭即重置：下次打开从选目录开始，避免上一次的队列残留
const handleClosed = () => {
  phase.value = 'choose';
  queue.value = [];
  current.value = null;
  doneCount.value = 0;
  abortFlag.value = false;
};
</script>

<style scoped>
.batch-choose {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.batch-guide {
  padding: 16px 20px;
  background: var(--bg-subtle);
  border-radius: 10px;
  color: var(--text-regular);
  font-size: 13px;
  line-height: 1.8;
}

.batch-guide-title {
  font-weight: 600;
  color: var(--text-body);
}

.batch-guide p {
  margin: 0;
}

.batch-choose-action {
  display: flex;
  justify-content: center;
  padding-bottom: 8px;
}

.batch-summary {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-regular);
}

.batch-num {
  font-weight: 700;
  color: var(--color-primary);
}

.batch-progress {
  margin-bottom: 12px;
}

.batch-reason {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.batch-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
</style>
