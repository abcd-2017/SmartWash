<template>
  <div class="layout-container">
    <!-- 窄屏抽屉遮罩：桌面 display:none 不参与布局，窄屏淡入；点击关闭抽屉 -->
    <div
      class="sidebar-mask"
      :class="{ 'is-visible': sidebarOpen }"
      aria-hidden="true"
      @click="closeSidebar"
    ></div>
    <Sidebar :open="sidebarOpen" />

    <div class="main-container">
      <Navbar :sidebar-open="sidebarOpen" @toggle-sidebar="toggleSidebar" />

      <!-- 子页面：路由切换淡入滑动（进出场方向性一致，mode=out-in 先离后进避免重叠） -->
      <!-- role=main：全站缺失主内容 landmark，读屏/键盘用户无法一键跳到主区 -->
      <div ref="mainRef" class="app-main" role="main" id="main-content">
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
import { ref, watch, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import Sidebar from './Sidebar.vue';
import Navbar from './Navbar.vue';

// 用 fullPath 作为转换 key：查询参数/路径变化均触发过渡，保证同页内跳转不“硬切”
const route = useRoute();

// 窄屏抽屉开关：由 Navbar 左侧菜单按钮切换。桌面端该状态无任何样式效果
// （抽屉/遮罩规则都写在 ≤768px 媒体查询内），故桌面布局与宽度不受影响
const sidebarOpen = ref(false);
const toggleSidebar = () => {
  sidebarOpen.value = !sidebarOpen.value;
};
const closeSidebar = () => {
  sidebarOpen.value = false;
};

// Esc 收起抽屉：键盘可达性兜底（侧栏搜索框自身的 Esc 清词互不影响）
const handleKeydown = (event) => {
  if (event.key === 'Escape' && sidebarOpen.value) closeSidebar();
};
onMounted(() => window.addEventListener('keydown', handleKeydown));
onUnmounted(() => window.removeEventListener('keydown', handleKeydown));

// 路由跳转（含搜索直达、菜单点击）后自动收起抽屉
watch(() => route.path, closeSidebar);

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
  overflow-y: auto;
  background-color: var(--bg-page);
}

/* 窄屏（≤ --bp-mobile 768px）：内容区内边距收敛，避免卡片贴边 */
@media (max-width: 768px) {
  .app-main {
    padding: 12px;
  }
}

/* 抽屉遮罩：桌面不显示；窄屏淡入淡出，时长/缓动走全局 token，
   reduced-motion 由 base.css 全局规则统一降级 */
.sidebar-mask {
  display: none;
}

@media (max-width: 768px) {
  .sidebar-mask {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 1000;
    background-color: rgba(15, 23, 42, 0.45);
    opacity: 0;
    pointer-events: none;
    transition: opacity var(--motion-base) var(--ease-standard);
  }

  .sidebar-mask.is-visible {
    opacity: 1;
    pointer-events: auto;
  }
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
