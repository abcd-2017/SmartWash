<template>
  <div class="sidebar">
    <el-menu
      :default-active="activeMenu"
      class="el-menu-vertical-demo"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
      router
    >
      <template v-for="route in menuRoutes" :key="route.path">
        <el-menu-item :index="route.path">
          <span>{{ route.meta.title }}</span>
        </el-menu-item>
      </template>
    </el-menu>
  </div>
</template>
  
  <script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

// 获取需要展示的菜单路由
const menuRoutes = computed(() => {
  const layoutRoute = router.options.routes.find((r) => r.path === "/");
  return layoutRoute?.children?.filter((r) => r.meta?.showInMenu) || [];
});

// 计算当前激活菜单
const activeMenu = computed(() => {
  const { meta, path } = route;
  return meta.activeMenu || path;
});
</script>
  
  <style scoped>
.sidebar {
  width: 200px;
  background-color: #304156;
  height: 100vh;
  overflow-y: auto;
  flex-shrink: 0;
}

.el-menu {
  border-right: none;
  height: 100%;
}
</style>