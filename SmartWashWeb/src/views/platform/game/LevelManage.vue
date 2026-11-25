<template>
  <div class="page-container">
    <div class="filter-container">
      <el-form :inline="true">
        <el-form-item label="游戏">
          <el-select
            v-model="levelGameKey"
            placeholder="选择游戏"
            filterable
            style="width: 220px"
            @change="fetchLevels"
          >
            <el-option
              v-for="g in gameOptions"
              :key="g.gameKey"
              :label="`${g.title}（${g.gameKey}）`"
              :value="g.gameKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!levelGameKey" @click="handleCreateLevel">
            新建关卡
          </el-button>
          <el-button :disabled="!levelGameKey" @click="importDialogVisible = true">
            批量导入
          </el-button>
          <el-button :disabled="!levelGameKey" @click="fetchLevels">刷新</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert
      v-if="!levelGameKey"
      type="info"
      :closable="false"
      title="请先选择游戏；仅关卡定义型游戏（推箱子 / 数织 / 魔塔 / 弹弓 / 记忆翻牌 / 填字 / 猜成语 / 节拍）需要维护关卡。"
      style="margin-bottom: 12px"
    />

    <div class="table-card">
      <!-- 加载失败态：与「暂无关卡」空态区分，给出重试入口 -->
      <div v-if="levelLoadError" class="load-error">
        <span>关卡列表加载失败：{{ levelLoadError }}</span>
        <el-button size="small" @click="fetchLevels">重试</el-button>
      </div>
      <el-table
        ref="tableRef"
        v-else
        v-loading="levelLoading"
        :data="levelList"
        border
        stripe
        aria-label="关卡列表"
        :empty-text="levelGameKey ? '暂无关卡' : '请先选择游戏'"
        :aria-describedby="captionId"
        @sort-change="onSortChange"
      >
        <el-table-column prop="levelNo" label="序号" width="70" align="center" sortable />
        <el-table-column prop="name" label="关卡名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="difficulty" label="难度" width="80" align="center" sortable>
          <template #default="{ row }">{{ row.difficulty ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="90" align="center" sortable>
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '下发中' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" align="center" sortable />
        <el-table-column label="关卡数据" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.dataJson }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEditLevel(row)"
              >编辑</el-button
            >
            <el-button
              link
              type="danger"
              size="small"
              :loading="deletingLevelNo === row.levelNo"
              @click="handleDeleteLevel(row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
        <template #append>
          <span :id="captionId" class="sr-only">关卡列表</span>
        </template>
      </el-table>
    </div>

    <el-dialog
      v-model="levelDialogVisible"
      :title="levelForm.levelNo ? `编辑关卡 #${levelForm.levelNo}` : '新建关卡'"
      width="min(640px, 94vw)"
      :close-on-click-modal="false"
      @closed="resetLevelForm"
    >
      <el-form ref="levelFormRef" :model="levelForm" :rules="levelRules" label-width="90px">
        <el-form-item label="游戏">
          <span>{{ levelGameKey }}</span>
        </el-form-item>
        <el-form-item label="关卡序号" prop="levelNo">
          <el-input-number v-model="levelForm.levelNo" :min="1" :max="9999" />
          <span class="form-tip">同一游戏内序号唯一；已存在则更新</span>
        </el-form-item>
        <el-form-item label="关卡名" prop="name">
          <el-input v-model="levelForm.name" placeholder="如：第一关 / 热身靶场" maxlength="32" />
        </el-form-item>
        <el-form-item label="难度">
          <el-input-number v-model="levelForm.difficulty" :min="1" :max="99" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="levelForm.sort" :min="0" :max="9999" />
          <span class="form-tip">越小越靠前，默认 序号×10</span>
        </el-form-item>
        <el-form-item label="下发">
          <el-switch
            v-model="levelForm.enabled"
            :active-value="1"
            :inactive-value="0"
            active-text="下发"
            inactive-text="下架"
          />
        </el-form-item>
        <el-form-item label="关卡数据" prop="dataJson">
          <el-input
            v-model="levelForm.dataJson"
            type="textarea"
            :rows="8"
            placeholder='JSON 字符串，结构由游戏自定，如 ["######","#    #"]'
          />
          <span class="form-tip"
            >仅校验 JSON 合法性，语义由游戏解析（错误结构会导致游戏无法加载该关）</span
          >
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="levelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="levelSubmitting" @click="submitLevel">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗（双兼容 JSON 文件 → 解析预览 → 整批事务提交） -->
    <LevelImportDialog
      v-model="importDialogVisible"
      :game-key="levelGameKey"
      :game-title="currentGameTitle"
      :level-list="levelList"
      @success="fetchLevels"
    />
  </div>
</template>

<script setup>
import { friendlyErrorMessage } from '@/utils/errors';
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getGameList } from '@/api/game';
import { getGameLevels, saveGameLevel, deleteGameLevel } from '@/api/gameData';
import { useConfirm } from '@/composables/useConfirm';
import logger from '@/utils/logger';
import LevelImportDialog from './components/LevelImportDialog.vue';
import { useTableA11y } from '@/composables/useTableA11y';

const tableRef = ref(null);

const { captionId, onSortChange } = useTableA11y('level-manage', tableRef);

defineOptions({ name: 'LevelManage' });

const importDialogVisible = ref(false);
// 弹窗标题展示用：取当前选中游戏的标题（未选中时兜底 gameKey）
const currentGameTitle = computed(
  () => gameOptions.value.find((g) => g.gameKey === levelGameKey.value)?.title || levelGameKey.value
);

const gameOptions = ref([]);

const loadGameOptions = async () => {
  try {
    // 管理端游戏列表不分页（size 上限 100，足够关卡维护用）
    const res = await getGameList({ page: 1, size: 100 });
    gameOptions.value = res?.records || [];
  } catch (error) {
    // 下拉加载失败不能静默：不提示则下拉永远为空，用户无从区分「没有游戏」与「加载失败」
    logger.error('游戏下拉加载失败:', error);
    gameOptions.value = [];
    ElMessage.error(friendlyErrorMessage(error, '游戏列表加载失败'));
  }
};

const levelGameKey = ref('');
const levelList = ref([]);
const levelLoading = ref(false);
const levelLoadError = ref(''); // 关卡列表加载失败文案：与「暂无关卡」空态区分，供重试占位
// 竞态守卫：切换游戏时旧响应后到会覆盖新游戏的关卡（同 useTableList fetchSeq 模式）
let levelFetchSeq = 0;

const fetchLevels = async () => {
  levelLoadError.value = '';
  if (!levelGameKey.value) {
    levelList.value = [];
    return;
  }
  const mySeq = ++levelFetchSeq;
  levelLoading.value = true;
  try {
    const res = await getGameLevels(levelGameKey.value);
    if (mySeq !== levelFetchSeq) return;
    levelList.value = res || [];
  } catch (error) {
    if (mySeq !== levelFetchSeq) return;
    // 失败不得伪装成「暂无关卡」：清列表并给出错误占位（模板据此切重试态）
    levelList.value = [];
    levelLoadError.value = error.message || '关卡列表加载失败';
  } finally {
    // 被丢弃的旧响应不得关掉最新请求的 loading
    if (mySeq === levelFetchSeq) {
      levelLoading.value = false;
    }
  }
};

const levelDialogVisible = ref(false);
const levelSubmitting = ref(false);
const levelFormRef = ref(null);

const levelForm = reactive({
  levelNo: 1,
  name: '',
  difficulty: 1,
  enabled: 1,
  sort: 10,
  dataJson: '',
});

const levelRules = {
  levelNo: [{ required: true, message: '请输入关卡序号', trigger: 'blur' }],
  dataJson: [
    { required: true, message: '请输入关卡数据', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        try {
          JSON.parse(value);
          callback();
        } catch {
          callback(new Error('关卡数据不是合法 JSON'));
        }
      },
      trigger: 'blur',
    },
  ],
};

const handleCreateLevel = () => {
  const maxNo = levelList.value.reduce((m, it) => Math.max(m, it.levelNo || 0), 0);
  Object.assign(levelForm, {
    levelNo: maxNo + 1,
    name: '',
    difficulty: 1,
    enabled: 1,
    sort: (maxNo + 1) * 10,
    dataJson: '',
  });
  levelDialogVisible.value = true;
};

const handleEditLevel = (row) => {
  Object.assign(levelForm, {
    levelNo: row.levelNo,
    name: row.name || '',
    difficulty: row.difficulty ?? 1,
    enabled: row.enabled ?? 1,
    sort: row.sort ?? row.levelNo * 10,
    dataJson: row.dataJson || '',
  });
  levelDialogVisible.value = true;
};

const resetLevelForm = () => {
  levelFormRef.value?.clearValidate();
};

const submitLevel = async () => {
  try {
    await levelFormRef.value.validate();
  } catch {
    return;
  }
  levelSubmitting.value = true;
  try {
    await saveGameLevel({
      gameKey: levelGameKey.value,
      levelNo: levelForm.levelNo,
      name: levelForm.name || null,
      dataJson: levelForm.dataJson,
      difficulty: levelForm.difficulty ?? null,
      enabled: levelForm.enabled,
      sort: levelForm.sort,
      remark: null,
    });
    ElMessage.success('保存成功，关卡版本号已递增（用户下次启动游戏生效）');
    levelDialogVisible.value = false;
    await fetchLevels();
  } catch (error) {
    // 业务失败必须提示：http 拦截器只对 HTTP 层错误弹 toast，业务码拒绝到此为静默
    logger.error(
      '保存关卡失败:',
      `gameKey=${levelGameKey.value}`,
      `levelNo=${levelForm.levelNo}`,
      error
    );
    ElMessage.error(friendlyErrorMessage(error, '保存失败'));
  } finally {
    levelSubmitting.value = false;
  }
};

const deletingLevelNo = ref(null); // 行内「删除」按钮 loading，防连点

const handleDeleteLevel = async (row) => {
  // 入口守卫：确认/请求在途时忽略再次点击，防双击把多个确认弹窗排进队列
  if (deletingLevelNo.value) return;
  deletingLevelNo.value = row.levelNo;
  try {
    // 确认弹窗：取消/关闭静默返回 false，统一走 useConfirm（评审 #23）
    const confirmed = await useConfirm(
      `确认删除「${levelGameKey.value}」第 ${row.levelNo} 关？删除后版本号递增，用户下次启动生效。`
    );
    if (!confirmed) return;
    await deleteGameLevel(levelGameKey.value, row.levelNo);
    ElMessage.success('已删除');
    await fetchLevels();
  } catch (error) {
    // 同保存：业务拒绝（如后端占用校验）必须可见
    logger.error('删除关卡失败:', `gameKey=${levelGameKey.value}`, `levelNo=${row.levelNo}`, error);
    ElMessage.error(friendlyErrorMessage(error, '删除失败'));
  } finally {
    deletingLevelNo.value = null;
  }
};

onMounted(() => {
  loadGameOptions();
});
</script>

<style scoped>
.form-tip {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

/* .load-error 失败占位样式全局化在 src/assets/pages.css，此处不再重复定义 */
</style>
