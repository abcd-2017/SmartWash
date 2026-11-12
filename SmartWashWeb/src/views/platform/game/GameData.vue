<template>
  <div class="page-container">
    <!--
      兼容壳：路由 /game/data 暂挂本页，终态为「关卡管理」与「玩家数据」两个独立菜单页。
      组件即终态文件（LevelManage / PlayerData），路由拆分落地后本壳随 tab 结构一起删除。
      PlayerData 用 lazy：切到才挂载，保持拆分前「切 tab 才拉数据」的行为。
    -->
    <el-tabs v-model="activeTab">
      <el-tab-pane label="关卡管理" name="levels">
        <LevelManage />
      </el-tab-pane>
      <el-tab-pane label="玩家数据" name="player" lazy>
        <PlayerData />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import LevelManage from './LevelManage.vue';
import PlayerData from './PlayerData.vue';

defineOptions({ name: 'GameData' });

const activeTab = ref('levels');
</script>
