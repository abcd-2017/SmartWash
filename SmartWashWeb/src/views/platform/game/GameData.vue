<template>
  <div class="page-container">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- ==================== 关卡管理 ==================== -->
      <el-tab-pane label="关卡管理" name="levels">
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

        <el-table
          v-loading="levelLoading"
          :data="levelList"
          border
          stripe
          :empty-text="levelGameKey ? '暂无关卡' : '请选择游戏'"
        >
          <el-table-column prop="levelNo" label="序号" width="70" align="center" />
          <el-table-column prop="name" label="关卡名" min-width="140" show-overflow-tooltip />
          <el-table-column label="难度" width="80" align="center">
            <template #default="{ row }">{{ row.difficulty ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
                {{ row.enabled === 1 ? '下发中' : '已下架' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="70" align="center" />
          <el-table-column label="关卡数据" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.dataJson }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleEditLevel(row)"
                >编辑</el-button
              >
              <el-button link type="danger" size="small" @click="handleDeleteLevel(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 存档查看 ==================== -->
      <el-tab-pane label="存档查看" name="saves">
        <div class="filter-container">
          <el-form :inline="true" @keyup.enter="handleSaveSearch">
            <el-form-item label="游戏">
              <el-select
                v-model="saveQuery.gameKey"
                placeholder="全部游戏"
                clearable
                filterable
                style="width: 200px"
                @change="handleSaveSearch"
              >
                <el-option
                  v-for="g in gameOptions"
                  :key="g.gameKey"
                  :label="`${g.title}（${g.gameKey}）`"
                  :value="g.gameKey"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="用户ID">
              <el-input
                v-model.number="saveQuery.userId"
                placeholder="精确匹配"
                clearable
                style="width: 140px"
                @clear="handleSaveSearch"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSaveSearch">搜索</el-button>
              <el-button @click="resetSaveSearch">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <el-table v-loading="saveLoading" :data="saveList" border stripe>
          <el-table-column prop="userId" label="用户ID" width="100" />
          <el-table-column prop="maskedPhone" label="手机号" width="130" />
          <el-table-column prop="gameKey" label="gameKey" min-width="130" show-overflow-tooltip />
          <el-table-column prop="gameTitle" label="游戏名" min-width="120" show-overflow-tooltip />
          <el-table-column prop="schemaVer" label="版本" width="70" align="center" />
          <el-table-column label="存档内容" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ row.stateJson }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="saveQuery.page"
          v-model:page-size="saveQuery.size"
          :total="saveTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 12px"
          @size-change="fetchSaves"
          @current-change="fetchSaves"
        />
      </el-tab-pane>

      <!-- ==================== 成绩查看 ==================== -->
      <el-tab-pane label="成绩查看" name="scores">
        <div class="filter-container">
          <el-form :inline="true" @keyup.enter="handleScoreSearch">
            <el-form-item label="游戏">
              <el-select
                v-model="scoreQuery.gameKey"
                placeholder="全部游戏"
                clearable
                filterable
                style="width: 200px"
                @change="handleScoreSearch"
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
              <el-button type="primary" @click="handleScoreSearch">搜索</el-button>
              <el-button @click="resetScoreSearch">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <el-table v-loading="scoreLoading" :data="scoreList" border stripe>
          <el-table-column prop="userId" label="用户ID" width="100" />
          <el-table-column prop="maskedPhone" label="手机号" width="130" />
          <el-table-column prop="gameKey" label="gameKey" min-width="130" show-overflow-tooltip />
          <el-table-column prop="gameTitle" label="游戏名" min-width="120" show-overflow-tooltip />
          <el-table-column prop="metricValue" label="成绩值" width="110" align="right" />
          <el-table-column label="辅助值" width="100" align="right">
            <template #default="{ row }">{{ row.metricExtra ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="playedCount" label="上报次数" width="90" align="center" />
          <el-table-column label="最近上报" width="170">
            <template #default="{ row }">{{ formatTime(row.lastPlayedAt) }}</template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="scoreQuery.page"
          v-model:page-size="scoreQuery.size"
          :total="scoreTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 12px"
          @size-change="fetchScores"
          @current-change="fetchScores"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- ==================== 关卡编辑弹窗 ==================== -->
    <el-dialog
      v-model="levelDialogVisible"
      :title="levelForm.levelNo ? `编辑关卡 #${levelForm.levelNo}` : '新建关卡'"
      width="640px"
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import dayjs from 'dayjs';
import { getGameList } from '@/api/game';
import {
  getGameLevels,
  saveGameLevel,
  deleteGameLevel,
  getGameSaves,
  getGameScores,
} from '@/api/gameData';

defineOptions({ name: 'GameData' });

const activeTab = ref('levels');

// ==================== 游戏选项 ====================

const gameOptions = ref([]);

const loadGameOptions = async () => {
  try {
    // 管理端游戏列表不分页（size 上限 100，足够关卡维护用）
    const res = await getGameList({ page: 1, size: 100 });
    gameOptions.value = res?.records || [];
  } catch {
    gameOptions.value = [];
  }
};

// ==================== 关卡管理 ====================

const levelGameKey = ref('');
const levelList = ref([]);
const levelLoading = ref(false);
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

const fetchLevels = async () => {
  if (!levelGameKey.value) {
    levelList.value = [];
    return;
  }
  levelLoading.value = true;
  try {
    levelList.value = (await getGameLevels(levelGameKey.value)) || [];
  } catch {
    levelList.value = [];
  } finally {
    levelLoading.value = false;
  }
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
  } catch {
    // 错误提示由 http 拦截器统一处理
  } finally {
    levelSubmitting.value = false;
  }
};

const handleDeleteLevel = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除「${levelGameKey.value}」第 ${row.levelNo} 关？删除后版本号递增，用户下次启动生效。`,
      '删除关卡',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  try {
    await deleteGameLevel(levelGameKey.value, row.levelNo);
    ElMessage.success('已删除');
    await fetchLevels();
  } catch {
    // 同上
  }
};

// ==================== 存档查看 ====================

const saveQuery = reactive({ gameKey: '', userId: '', page: 1, size: 10 });
const saveList = ref([]);
const saveTotal = ref(0);
const saveLoading = ref(false);

const fetchSaves = async () => {
  saveLoading.value = true;
  try {
    const params = { page: saveQuery.page, size: saveQuery.size };
    if (saveQuery.gameKey) params.gameKey = saveQuery.gameKey;
    if (saveQuery.userId) params.userId = saveQuery.userId;
    const res = await getGameSaves(params);
    saveList.value = res?.records || [];
    saveTotal.value = res?.total || 0;
  } catch {
    saveList.value = [];
    saveTotal.value = 0;
  } finally {
    saveLoading.value = false;
  }
};

const handleSaveSearch = () => {
  saveQuery.page = 1;
  fetchSaves();
};

const resetSaveSearch = () => {
  saveQuery.gameKey = '';
  saveQuery.userId = '';
  saveQuery.page = 1;
  fetchSaves();
};

// ==================== 成绩查看 ====================

const scoreQuery = reactive({ gameKey: '', page: 1, size: 10 });
const scoreList = ref([]);
const scoreTotal = ref(0);
const scoreLoading = ref(false);

const fetchScores = async () => {
  scoreLoading.value = true;
  try {
    const params = { page: scoreQuery.page, size: scoreQuery.size };
    if (scoreQuery.gameKey) params.gameKey = scoreQuery.gameKey;
    const res = await getGameScores(params);
    scoreList.value = res?.records || [];
    scoreTotal.value = res?.total || 0;
  } catch {
    scoreList.value = [];
    scoreTotal.value = 0;
  } finally {
    scoreLoading.value = false;
  }
};

const handleScoreSearch = () => {
  scoreQuery.page = 1;
  fetchScores();
};

const resetScoreSearch = () => {
  scoreQuery.gameKey = '';
  scoreQuery.page = 1;
  fetchScores();
};

// ==================== 通用 ====================

const formatTime = (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '—');

const handleTabChange = (name) => {
  if (name === 'saves' && saveList.value.length === 0) fetchSaves();
  if (name === 'scores' && scoreList.value.length === 0) fetchScores();
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
</style>
