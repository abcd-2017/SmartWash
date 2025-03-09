<template>
  <div class="navbar">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>{{ currentRouteName }}</el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>
  
<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();
const currentRouteName = computed(() => route.meta.title || "");
</script>
  
  <style scoped>
.navbar {
  padding: 15px;
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  flex-shrink: 0; /* 防止导航栏被压缩 */
}
</style>