<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <div class="brand-name">SmartWash</div>
      <div class="brand-sub">管理系统</div>
    </div>
    <el-menu
      ref="menuRef"
      :default-active="activeMenu"
      :default-openeds="defaultOpeneds"
      background-color="#1e293b"
      text-color="#cbd5e1"
      active-text-color="#a5b4fc"
      router
    >
      <!-- 首页是唯一子项的「数据看板」，保持顶层不折叠，减少一次点击 -->
      <el-menu-item v-if="homeRoute" :index="homeRoute.path">
        <el-icon><component :is="resolveIcon(homeRoute.meta.icon)" /></el-icon>
        <span>{{ homeRoute.meta.title }}</span>
      </el-menu-item>

      <el-sub-menu v-for="group in menuGroups" :key="group.title" :index="group.title">
        <template #title>
          <el-icon><component :is="resolveIcon(group.icon)" /></el-icon>
          <span>{{ group.title }}</span>
        </template>
        <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
          <el-icon><component :is="resolveIcon(item.meta.icon)" /></el-icon>
          <span>{{ item.meta.title }}</span>
        </el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup>
import { computed, markRaw, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  HomeFilled,
  School,
  UserFilled,
  Coin,
  ShoppingBag,
  Key,
  Avatar,
  Box,
  CreditCard,
  Document,
  Ticket,
  CollectionTag,
  ChatLineRound,
  Reading,
  Warning,
  DataAnalysis,
  CircleClose,
  Cpu,
  Setting,
  Message,
  // 树形分组表头图标 + 短链管理
  Files,
  OfficeBuilding,
  User,
  Present,
  MagicStick,
  Suitcase,
  Link,
  Grid,
} from '@element-plus/icons-vue';

const route = useRoute();
const router = useRouter();

// 图标清单已收敛进路由 meta.icon（评审 #26），这里只负责「图标名 → 组件」的解析。
// 新增菜单图标：给路由 meta 加 icon 名（或分组 icon 字段），并在此登记同名图标组件。
const iconRegistry = {
  HomeFilled: markRaw(HomeFilled),
  School: markRaw(School),
  UserFilled: markRaw(UserFilled),
  Coin: markRaw(Coin),
  ShoppingBag: markRaw(ShoppingBag),
  Key: markRaw(Key),
  Avatar: markRaw(Avatar),
  Box: markRaw(Box),
  CreditCard: markRaw(CreditCard),
  Document: markRaw(Document),
  Ticket: markRaw(Ticket),
  CollectionTag: markRaw(CollectionTag),
  // 观象台（占卜模块）
  ChatLineRound: markRaw(ChatLineRound),
  Reading: markRaw(Reading),
  Warning: markRaw(Warning),
  DataAnalysis: markRaw(DataAnalysis),
  CircleClose: markRaw(CircleClose),
  Cpu: markRaw(Cpu),
  Setting: markRaw(Setting),
  Message: markRaw(Message),
  // 工具箱与分组表头
  Link: markRaw(Link),
  Files: markRaw(Files),
  OfficeBuilding: markRaw(OfficeBuilding),
  User: markRaw(User),
  Present: markRaw(Present),
  MagicStick: markRaw(MagicStick),
  Suitcase: markRaw(Suitcase),
  Grid: markRaw(Grid),
};

// 按图标名解析组件，未登记的图标回退为空（不渲染）
const resolveIcon = (name) => iconRegistry[name] || null;

const menuRoutes = computed(() => {
  const layoutRoute = router.options.routes.find((r) => r.path === '/');
  return layoutRoute?.children?.filter((r) => r.meta?.showInMenu) || [];
});

// 树形分组配置：paths 引用路由 path，item 的标题/图标仍取自路由 meta。
// 未被任何分组覆盖的 showInMenu 路由会兜底进「其他」组，
// 保证将来新增路由即使忘了登记分组，菜单也不会消失。
const MENU_GROUP_CONFIG = [
  { title: '业务管理', icon: 'Files', paths: ['/orders', '/payment', '/recharge'] },
  { title: '基础数据', icon: 'OfficeBuilding', paths: ['/schools', '/lockers', '/laundry'] },
  { title: '用户与权限', icon: 'User', paths: ['/users', '/adminUsers', '/roles'] },
  { title: '营销', icon: 'Present', paths: ['/coupon', '/userCoupon'] },
  {
    title: '观象台',
    icon: 'MagicStick',
    paths: [
      '/divination/prompts',
      '/divination/rag',
      '/divination/audits',
      '/divination/usage',
      '/divination/blocked',
      '/divination/models',
      '/divination/settings',
    ],
  },
  { title: '工具箱', icon: 'Suitcase', paths: ['/captcha', '/toolbox/short-codes'] },
];

const homeRoute = computed(() => menuRoutes.value.find((r) => r.path === '/') || null);

const menuGroups = computed(() => {
  const byPath = new Map(menuRoutes.value.map((r) => [r.path, r]));
  const consumed = new Set();
  const groups = MENU_GROUP_CONFIG.map((cfg) => {
    const items = cfg.paths.map((p) => byPath.get(p)).filter(Boolean);
    items.forEach((item) => consumed.add(item.path));
    return { title: cfg.title, icon: cfg.icon, items };
  });
  const rest = menuRoutes.value.filter((r) => !consumed.has(r.path));
  if (rest.length) {
    groups.push({ title: '其他', icon: 'Grid', items: rest });
  }
  return groups;
});

const activeMenu = computed(() => {
  const { meta, path } = route;
  return meta.activeMenu || path;
});

// 当前激活项所属分组的标题（sub-menu 以分组标题作为 index）
const activeGroupIndex = computed(() => {
  const active = activeMenu.value;
  return menuGroups.value.find((g) => g.items.some((item) => item.path === active))?.title || null;
});

// 初次渲染（含刷新/直链进入）即展开当前所在分组
const defaultOpeneds = computed(() => (activeGroupIndex.value ? [activeGroupIndex.value] : []));

const menuRef = ref(null);
// 已展开分组记录：el-menu 对重复 open 可能重复入栈，自己去重
const expandedGroups = new Set(defaultOpeneds.value);

// 编程式跳转（如首页卡片跳订单页）时目标分组可能未展开：路由变化后自动展开当前分组
watch(activeGroupIndex, (title) => {
  if (title && !expandedGroups.has(title)) {
    expandedGroups.add(title);
    menuRef.value?.open(title);
  }
});
</script>

<style scoped>
.sidebar {
  width: 220px;
  background-color: #1e293b;
  height: 100vh;
  overflow-y: auto;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 24px 20px 20px;
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  color: #f1f5f9;
  letter-spacing: -0.3px;
}

.brand-sub {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}

.el-menu {
  border-right: none;
  height: calc(100% - 80px);
  padding: 0 12px;
}

:deep(.el-menu-item) {
  border-radius: 8px;
  margin-bottom: 2px;
  height: 44px;
  line-height: 44px;
  color: #cbd5e1 !important;
  font-weight: 500;
  font-size: 14px;
}

:deep(.el-menu-item:hover) {
  background-color: rgba(99, 102, 241, 0.1) !important;
  color: #e2e8f0 !important;
}

:deep(.el-menu-item.is-active) {
  background-color: rgba(99, 102, 241, 0.2) !important;
  color: #a5b4fc !important;
}

:deep(.el-menu-item .el-icon) {
  margin-right: 10px;
  font-size: 18px;
}

/* 分组表头与顶层菜单项同视觉规格 */
:deep(.el-sub-menu__title) {
  border-radius: 8px;
  margin-bottom: 2px;
  height: 44px;
  line-height: 44px;
  color: #cbd5e1 !important;
  font-weight: 500;
  font-size: 14px;
}

:deep(.el-sub-menu__title:hover) {
  background-color: rgba(99, 102, 241, 0.1) !important;
  color: #e2e8f0 !important;
}

:deep(.el-sub-menu__title .el-icon) {
  margin-right: 10px;
  font-size: 18px;
}

/* 激活项所在分组的表头同步高亮文字色 */
:deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #a5b4fc !important;
}

/* 二级菜单项略紧凑，与一级拉开层级 */
:deep(.el-sub-menu .el-menu-item) {
  height: 38px;
  line-height: 38px;
  font-size: 13px;
}
</style>
