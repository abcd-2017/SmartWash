<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <div class="brand-name">SmartWash</div>
      <div class="brand-sub">管理系统</div>
    </div>
    <!-- 菜单搜索：菜单项多时快速直达；输入即平铺匹配结果，点击跳转 -->
    <div class="menu-search">
      <el-input
        v-model="menuQuery"
        placeholder="搜索菜单…"
        clearable
        :prefix-icon="Search"
        size="small"
        aria-label="搜索菜单"
      />
    </div>
    <el-menu
      v-show="!menuQuery"
      ref="menuRef"
      :default-active="activeMenu"
      :default-openeds="defaultOpeneds"
      router
    >
      <!-- 首页是唯一子项的「数据看板」，保持顶层不折叠，减少一次点击 -->
      <el-menu-item v-if="homeRoute" :index="homeRoute.path">
        <el-icon><component :is="resolveIcon(homeRoute.meta.icon)" /></el-icon>
        <span>{{ homeRoute.meta.title }}</span>
      </el-menu-item>

      <!-- index 用分组唯一 key（标题可能重名，用标题做 index 会导致同名组展开联动） -->
      <el-sub-menu v-for="(group, idx) in menuGroups" :key="group.title" :index="groupKey(idx)">
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
    <!-- 搜索态：平铺匹配结果（含所属分组），独立菜单实例避免与分组展开状态互相干扰 -->
    <el-menu v-show="menuQuery" :default-active="activeMenu" router class="search-menu">
      <el-menu-item v-if="searchResults.length === 0" disabled index="no-match">
        <span class="search-empty">无匹配菜单</span>
      </el-menu-item>
      <el-menu-item v-for="r in searchResults" :key="r.path" :index="r.path">
        <el-icon><component :is="resolveIcon(r.icon)" /></el-icon>
        <span class="search-title">{{ r.title }}</span>
        <span class="search-group-tag">{{ r.group }}</span>
      </el-menu-item>
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
  // 情绪与订阅
  AlarmClock,
  // 树形分组表头图标 + 短链管理
  Files,
  OfficeBuilding,
  User,
  MagicStick,
  Link,
  Grid,
  // 工作台配置
  Guide,
  // 游戏库
  VideoPlay,
  // 系统与安全
  Lock,
  DocumentChecked,
  // 消息中心（站内信）
  Bell,
  // 家庭点单
  Food,
  Dish,
  // 菜单搜索
  Search,
  // 参数设置
  Tools,
  // 菜谱管理（router meta 引用，漏登记会渲染为空图标）
  ForkSpoon,
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
  // 情绪与订阅
  AlarmClock: markRaw(AlarmClock),
  // 工具箱与分组表头
  Link: markRaw(Link),
  Files: markRaw(Files),
  OfficeBuilding: markRaw(OfficeBuilding),
  User: markRaw(User),
  MagicStick: markRaw(MagicStick),
  Grid: markRaw(Grid),
  // 工作台配置
  Guide: markRaw(Guide),
  // 游戏库
  VideoPlay: markRaw(VideoPlay),
  // 系统与安全
  Lock: markRaw(Lock),
  DocumentChecked: markRaw(DocumentChecked),
  // 消息中心（站内信）
  Bell: markRaw(Bell),
  // 家庭点单
  Food: markRaw(Food),
  Dish: markRaw(Dish),
  // 菜单搜索
  Search: markRaw(Search),
  // 参数设置
  Tools: markRaw(Tools),
  // 菜谱管理
  ForkSpoon: markRaw(ForkSpoon),
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
// 六组信息架构：基础数据 → 业务管理 → 用户运营 → 平台模块 → 观象台 → 系统与安全，
// 与 src/views/ 的目录划分一一对应；组间顺序即侧边栏展示顺序
// （家庭点单为独立业务域，单独成组挂末尾）
// ⚠ 一个 path 只允许出现在一个分组里，重复登记会导致菜单项在侧边栏重复出现
const MENU_GROUP_CONFIG = [
  { title: '基础数据', icon: 'OfficeBuilding', paths: ['/schools', '/lockers', '/laundry'] },
  { title: '业务管理', icon: 'Files', paths: ['/orders', '/payment', '/recharge'] },
  // 用户触达（站内信）与营销发券同属用户运营，不再各自成组
  { title: '用户运营', icon: 'User', paths: ['/users', '/coupon', '/userCoupon', '/notices'] },
  {
    // 短链/工作台/游戏/订阅等平台实验模块统一归口
    title: '平台模块',
    icon: 'Grid',
    paths: [
      '/toolbox/short-codes',
      '/workbench/entries',
      '/workbench/deeplink-routes',
      '/game/list',
      '/game/levels',
      '/game/data',
      '/subscriptions',
    ],
  },
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
  {
    title: '系统与安全',
    icon: 'Lock',
    paths: ['/roles', '/adminUsers', '/audit-logs', '/captcha', '/sysconfig'],
  },
  // 家庭点单为独立业务域，单独成组（后续批量导入挂同组）
  { title: '家庭点单', icon: 'Food', paths: ['/dining/categories', '/dining/recipes'] },
];

const homeRoute = computed(() => menuRoutes.value.find((r) => r.path === '/') || null);

// sub-menu index 用「标题 + 序号」组合键：标题可能重名（重名组会展开联动），序号保证唯一
const groupKey = (idx) => `group-${idx}`;

const menuGroups = computed(() => {
  const byPath = new Map(menuRoutes.value.map((r) => [r.path, r]));
  const consumed = new Set();
  const groups = MENU_GROUP_CONFIG.map((cfg) => {
    // 同一 path 只渲染一次：重复登记的分组配置不再重复消费（防菜单项重复出现）
    const items = cfg.paths.map((p) => byPath.get(p)).filter((r) => r && !consumed.has(r.path));
    items.forEach((item) => consumed.add(item.path));
    return { title: cfg.title, icon: cfg.icon, items };
  });
  // 首页已单独渲染为顶层菜单项，兜底分组里不能再出现一次
  const rest = menuRoutes.value.filter((r) => !consumed.has(r.path) && r.path !== '/');
  if (rest.length) {
    groups.push({ title: '其他', icon: 'Grid', items: rest });
  }
  return groups.filter((g) => g.items.length > 0);
});

const activeMenu = computed(() => {
  const { meta, path } = route;
  return meta.activeMenu || path;
});

// 当前激活项所属分组的 index key（el-sub-menu 以此作为 index）
const activeGroupIndex = computed(() => {
  const active = activeMenu.value;
  const idx = menuGroups.value.findIndex((g) => g.items.some((item) => item.path === active));
  return idx >= 0 ? groupKey(idx) : null;
});

// 初次渲染（含刷新/直链进入）即展开当前所在分组
const defaultOpeneds = computed(() => (activeGroupIndex.value ? [activeGroupIndex.value] : []));

// ===== 菜单搜索 =====
const menuQuery = ref('');

// 平铺搜索结果：标题/路径匹配（不区分大小写），附带所属分组名便于区分重名页面
const searchResults = computed(() => {
  const q = menuQuery.value.trim().toLowerCase();
  if (!q) return [];
  const groupOf = new Map();
  menuGroups.value.forEach((g) => g.items.forEach((item) => groupOf.set(item.path, g.title)));
  const results = menuRoutes.value
    .filter(
      (r) => (r.meta?.title || '').toLowerCase().includes(q) || r.path.toLowerCase().includes(q)
    )
    .map((r) => ({
      path: r.path,
      title: r.meta?.title || r.path,
      icon: r.meta?.icon,
      group: r.path === '/' ? '首页' : groupOf.get(r.path) || '其他',
    }));
  return results;
});

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

// 从搜索结果跳转（或其他路由变化）后退出搜索态：恢复分组菜单，让目标项在常规结构中高亮
watch(
  () => route.path,
  () => {
    if (menuQuery.value) menuQuery.value = '';
  }
);
</script>

<style scoped>
.sidebar {
  width: 220px;
  background-color: var(--sidebar-bg);
  height: 100vh;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 24px 20px 20px;
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--sidebar-text-strong);
  letter-spacing: -0.3px;
}

.brand-sub {
  font-size: 12px;
  color: var(--sidebar-text-sub);
  margin-top: 4px;
}

/* 菜单三色改用暗侧 token 经 EP CSS 变量注入（替代原 background-color/text-color/active-text-color 硬编码 props） */
.sidebar :deep(.el-menu) {
  --el-menu-bg-color: var(--sidebar-bg);
  --el-menu-text-color: var(--sidebar-text);
  --el-menu-hover-bg-color: var(--sidebar-hover-bg);
  --el-menu-active-color: var(--sidebar-text-active);
  border-right: none;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  /* 菜单项文字 nowrap，长标题会横向溢出：隐藏横向滚动，交给下面的省略号兜住 */
  overflow-x: hidden;
  padding: 0 12px;
}

:deep(.el-menu-item) {
  border-radius: 8px;
  margin-bottom: 2px;
  height: 44px;
  line-height: 44px;
  color: var(--sidebar-text) !important;
  font-weight: 500;
  font-size: 14px;
}

:deep(.el-menu-item:hover) {
  background-color: var(--sidebar-hover-bg) !important;
  color: var(--sidebar-text-hover) !important;
}

:deep(.el-menu-item.is-active) {
  background-color: var(--sidebar-active-bg) !important;
  color: var(--sidebar-text-active) !important;
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
  color: var(--sidebar-text) !important;
  font-weight: 500;
  font-size: 14px;
}

:deep(.el-sub-menu__title:hover) {
  background-color: var(--sidebar-hover-bg) !important;
  color: var(--sidebar-text-hover) !important;
}

:deep(.el-sub-menu__title .el-icon) {
  margin-right: 10px;
  font-size: 18px;
}

/* 激活项所在分组的表头同步高亮文字色 */
:deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--sidebar-text-active) !important;
}

/* 二级菜单项略紧凑，与一级拉开层级 */
:deep(.el-sub-menu .el-menu-item) {
  height: 38px;
  line-height: 38px;
  font-size: 13px;
}

/* 菜单项/分组标题文字超宽时省略，防长标题撑出侧栏横向滚动 */
:deep(.el-menu-item > span),
:deep(.el-sub-menu__title > span) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

/* ===== 菜单搜索 ===== */
.menu-search {
  padding: 0 14px 12px;
}

.menu-search :deep(.el-input__wrapper) {
  background-color: var(--sidebar-hover-bg);
  box-shadow: none;
}

.search-menu {
  padding-bottom: 16px;
}

.search-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 结果右侧的所属分组小标签，帮助区分重名页面 */
.search-group-tag {
  font-size: 11px;
  color: var(--sidebar-text-sub);
  background-color: var(--sidebar-hover-bg);
  border-radius: 4px;
  padding: 1px 6px;
  margin-left: 6px;
  flex-shrink: 0;
}

.search-empty {
  color: var(--sidebar-text-sub);
  font-size: 13px;
}
</style>
