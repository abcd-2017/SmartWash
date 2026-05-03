<template>
  <div class="layout-container">
    <!-- 左侧菜单栏 -->
    <Sidebar />

    <!-- 右侧内容区域 -->
    <div class="main-container">
      <!-- 顶部导航栏 -->
      <Navbar />

      <!-- 子页面 -->
      <div class="app-main">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import Sidebar from "./Sidebar.vue";
import Navbar from "./Navbar.vue";
</script>

<style scoped>
.layout-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0; /* 防止内容溢出 */
}

.app-main {
  flex: 1;
  padding: 20px;
  overflow-y: auto; /* 垂直滚动 */
  background-color: #f1f5f9;
}
</style>