<template>
  <div class="page-container">
    <el-tabs v-model="activeTab" @tab-click="handleTabClick">
      <!-- ==================== 存档记录 ==================== -->
      <el-tab-pane label="存档记录" name="saves" lazy>
        <div class="filter-container">
          <el-form :inline="true" @keyup.enter="handleSaveSearch">
            <el-form-item label="游戏">
              <el-select
                v-model="saveListQuery.gameKey"
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
                v-model.number="saveListQuery.userId"
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

        <div class="table-card">
          <el-table
            v-loading="saveListLoading"
            :data="saveList"
            border
            stripe
            empty-text="暂无数据，试试调整筛选条件"
          >
            <el-table-column prop="userId" label="用户ID" width="100" />
            <el-table-column prop="maskedPhone" label="手机号" width="130" />
            <el-table-column prop="gameKey" label="gameKey" min-width="130" show-overflow-tooltip />
            <el-table-column
              prop="gameTitle"
              label="游戏名"
              min-width="120"
              show-overflow-tooltip
            />
            <el-table-column prop="schemaVer" label="版本" width="70" align="center" />
            <el-table-column label="存档内容" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">{{ row.stateJson }}</template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="saveListQuery.page"
            v-model:page-size="saveListQuery.size"
            :total="saveTotal"
            :page-sizes="pageSizes"
            layout="total, sizes, prev, pager, next"
            style="margin-top: 12px"
            @size-change="handleSaveSizeChange"
            @current-change="handleSavePageChange"
          />
        </div>
      </el-tab-pane>

      <!-- ==================== 成绩榜 ==================== -->
      <el-tab-pane label="成绩榜" name="scores" lazy>
        <div class="filter-container">
          <el-form :inline="true" @keyup.enter="handleScoreSearch">
            <el-form-item label="游戏">
              <el-select
                v-model="scoreListQuery.gameKey"
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

        <div class="table-card">
          <el-table
            v-loading="scoreListLoading"
            :data="scoreList"
            border
            stripe
            empty-text="暂无数据，试试调整筛选条件"
          >
            <el-table-column prop="userId" label="用户ID" width="100" />
            <el-table-column prop="maskedPhone" label="手机号" width="130" />
            <el-table-column prop="gameKey" label="gameKey" min-width="130" show-overflow-tooltip />
            <el-table-column
              prop="gameTitle"
              label="游戏名"
              min-width="120"
              show-overflow-tooltip
            />
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
            v-model:current-page="scoreListQuery.page"
            v-model:page-size="scoreListQuery.size"
            :total="scoreTotal"
            :page-sizes="pageSizes"
            layout="total, sizes, prev, pager, next"
            style="margin-top: 12px"
            @size-change="handleScoreSizeChange"
            @current-change="handleScorePageChange"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { getGameList } from '@/api/game';
import { getGameSaves, getGameScores } from '@/api/gameData';
import { useTableList } from '@/composables/useTableList';
import { formatTime } from '@/utils/format';

defineOptions({ name: 'PlayerData' });

const activeTab = ref('saves');
// 成绩榜首次激活时才拉取（el-tab-pane lazy 仅控制渲染，不触发请求）
const scoresLoaded = ref(false);
const handleTabClick = (tab) => {
  if (tab.props.name === 'scores' && !scoresLoaded.value) {
    scoresLoaded.value = true;
    fetchScores();
  }
};

// ==================== 游戏选项 ====================

const gameOptions = ref([]);

const loadGameOptions = async () => {
  try {
    const res = await getGameList({ page: 1, size: 100 });
    gameOptions.value = res?.records || [];
  } catch {
    gameOptions.value = [];
  }
};

// ==================== 存档记录 ====================

const {
  list: saveList,
  total: saveTotal,
  listLoading: saveListLoading,
  listQuery: saveListQuery,
  pageSizes,
  fetchData: fetchSaves,
  handleSearch: handleSaveSearch,
  resetSearch: resetSaveSearch,
  handlePageChange: handleSavePageChange,
  handleSizeChange: handleSaveSizeChange,
} = useTableList({
  fetchApi: getGameSaves,
  baseQuery: { gameKey: '', userId: '' },
  buildParams: (q) => ({
    page: q.page,
    size: q.size,
    gameKey: q.gameKey || undefined,
    userId: q.userId || undefined,
  }),
});

// ==================== 成绩榜 ====================

const {
  list: scoreList,
  total: scoreTotal,
  listLoading: scoreListLoading,
  listQuery: scoreListQuery,
  fetchData: fetchScores,
  handleSearch: handleScoreSearch,
  resetSearch: resetScoreSearch,
  handlePageChange: handleScorePageChange,
  handleSizeChange: handleScoreSizeChange,
} = useTableList({
  fetchApi: getGameScores,
  baseQuery: { gameKey: '' },
  buildParams: (q) => ({
    page: q.page,
    size: q.size,
    gameKey: q.gameKey || undefined,
  }),
});

onMounted(() => {
  loadGameOptions();
  // 默认 tab 为存档记录：进入页面即拉取，避免筛选下拉加载后列表恒空
  fetchSaves();
});
</script>
