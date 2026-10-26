<template>
  <div class="layout-container">
    <!-- 左侧菜单栏 -->
    <Sidebar />

    <!-- 右侧内容区域 -->
    <div class="main-container">
      <!-- 顶部导航栏 -->
      <Navbar />

      <!-- 子页面：路由切换淡入滑动（进出场方向性一致，mode=out-in 先离后进避免重叠） -->
      <div ref="mainRef" class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import Sidebar from './Sidebar.vue';
import Navbar from './Navbar.vue';

// 用 fullPath 作为转换 key：查询参数/路径变化均触发过渡，保证同页内跳转不“硬切”
const route = useRoute();

// 滚动容器是常驻节点，跨页面切换时新页面会继承上一个页面的滚动位置，这里显式回到顶部；
// 按 path 判断，同页 query 变化（筛选预填/跳转联动）不打断当前浏览位置
const mainRef = ref(null);
watch(
  () => route.path,
  () => {
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    mainRef.value?.scrollTo({ top: 0, behavior: reduced ? 'auto' : 'smooth' });
  }
);
</script>

<style scoped>
.layout-container {
  display: flex;
  height: 100vh;
  width: 100%;
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

/* 路由切换过渡：进场减速（轻微上移 + 淡入）、离场快速淡出（退出快于进入，sub-60% 时长） */
.fade-slide-enter-active {
  transition:
    opacity var(--motion-base) var(--ease-decelerate),
    transform var(--motion-base) var(--ease-decelerate);
}

.fade-slide-leave-active {
  transition:
    opacity var(--motion-fast) var(--ease-accelerate),
    transform var(--motion-fast) var(--ease-accelerate);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
