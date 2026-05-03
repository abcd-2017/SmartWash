# UI 重设计实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 SmartWash Web 后台管理系统从 Element Plus 默认风格升级为"深邃高级"设计风格（Indigo + Slate 配色）。

**Architecture:** 通过 Element Plus CSS 变量全局覆盖主题色，修改 3 个 Layout 组件 + 登录页的模板和样式。无需新增依赖，无需修改 CRUD 页面。

**Tech Stack:** Vue 3, Element Plus 2.9, CSS 变量

---

### Task 1: 添加全局主题 CSS 变量

**Files:**
- Modify: `src/assets/main.css`

- [ ] **Step 1: 写入 Element Plus 全局 CSS 变量覆盖**

将 `src/assets/main.css` 改为：

```css
@import './base.css';

/* Element Plus 全局主题变量覆盖 */
:root {
  --el-color-primary: #6366f1;
  --el-color-primary-light-3: #818cf8;
  --el-color-primary-light-5: #a5b4fc;
  --el-color-primary-light-7: #c7d2fe;
  --el-color-primary-light-9: #eef2ff;
  --el-color-success: #10b981;
  --el-color-danger: #ef4444;
  --el-border-radius-base: 8px;
  --el-border-radius-small: 6px;
  --el-bg-color-page: #f1f5f9;
}
```

- [ ] **Step 2: 验证开发服务器启动正常**

```sh
npm run dev
```

打开浏览器确认 Element Plus 组件的默认主色已变为 Indigo。

- [ ] **Step 3: 提交**

```bash
git add src/assets/main.css
git commit -m "feat(Web): 新增 Element Plus 全局主题变量覆盖，Indigo 配色"
```

---

### Task 2: 侧边栏升级 — 深色主题 + 图标

**Files:**
- Modify: `src/components/Layout/Sidebar.vue`

- [ ] **Step 1: 更新 Sidebar.vue 模板和样式**

将 `src/components/Layout/Sidebar.vue` 改为：

```vue
<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <div class="brand-name">SmartWash</div>
      <div class="brand-sub">管理系统</div>
    </div>
    <el-menu
      :default-active="activeMenu"
      background-color="#0f172a"
      text-color="#94a3b8"
      active-text-color="#a5b4fc"
      router
    >
      <template v-for="route in menuRoutes" :key="route.path">
        <el-menu-item :index="route.path">
          <el-icon><component :is="iconMap[route.name]" /></el-icon>
          <span>{{ route.meta.title }}</span>
        </el-menu-item>
      </template>
    </el-menu>
  </div>
</template>

<script setup>
import { computed, markRaw } from "vue";
import { useRoute, useRouter } from "vue-router";
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
} from "@element-plus/icons-vue";

const route = useRoute();
const router = useRouter();

const iconMap = {
  Home: markRaw(HomeFilled),
  SchoolList: markRaw(School),
  Users: markRaw(UserFilled),
  RechargeList: markRaw(Coin),
  LaundryList: markRaw(ShoppingBag),
  RoleList: markRaw(Key),
  AdminUserList: markRaw(Avatar),
  LockerList: markRaw(Box),
  PaymentList: markRaw(CreditCard),
  OrderList: markRaw(Document),
  CouponList: markRaw(Ticket),
  UserCouponList: markRaw(CollectionTag),
};

const menuRoutes = computed(() => {
  const layoutRoute = router.options.routes.find((r) => r.path === "/");
  return layoutRoute?.children?.filter((r) => r.meta?.showInMenu) || [];
});

const activeMenu = computed(() => {
  const { meta, path } = route;
  return meta.activeMenu || path;
});
</script>

<style scoped>
.sidebar {
  width: 220px;
  background-color: #0f172a;
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
  color: #e2e8f0;
  letter-spacing: -0.3px;
}

.brand-sub {
  font-size: 11px;
  color: #64748b;
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
}

:deep(.el-menu-item:hover) {
  background-color: rgba(99, 102, 241, 0.08) !important;
  color: #c7d2fe !important;
}

:deep(.el-menu-item.is-active) {
  background-color: rgba(99, 102, 241, 0.15) !important;
  color: #a5b4fc !important;
}

:deep(.el-menu-item .el-icon) {
  margin-right: 10px;
  font-size: 18px;
}
</style>
```

- [ ] **Step 2: 验证开发服务器热更新正常**

确认侧边栏显示深蓝黑底色、白色 logo、菜单项带图标。

- [ ] **Step 3: 提交**

```bash
git add src/components/Layout/Sidebar.vue
git commit -m "feat(Web): 侧边栏升级为深色主题，增加菜单图标"
```

---

### Task 3: 顶栏升级 — 首字母头像 + 样式细化

**Files:**
- Modify: `src/components/Layout/Navbar.vue`

- [ ] **Step 1: 更新 Navbar.vue 模板、脚本和样式**

将 `src/components/Layout/Navbar.vue` 的 template 中头像区域改为：

```vue
<el-avatar :size="32" class="user-avatar">
  {{ userInitial }}
</el-avatar>
```

脚本中删除 `userAvatar` computed，添加：

```js
const userInitial = computed(() => {
  return username.value ? username.value.charAt(0).toUpperCase() : "";
});
```

样式改为：

```css
.navbar {
  padding: 0 24px;
  background-color: #fff;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-avatar {
  background: #6366f1;
  color: #fff;
  font-weight: 600;
  font-size: 14px;
}
```

完整文件内容：

```vue
<template>
  <div class="navbar">
    <div class="left">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>{{ currentRouteName }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="right">
      <el-dropdown>
        <span class="user-info">
          <el-avatar :size="32" class="user-avatar">
            {{ userInitial }}
          </el-avatar>
          <span class="username">{{ username }}</span>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { SwitchButton } from "@element-plus/icons-vue";
import { ElMessageBox } from "element-plus";
import { getCurrentAdminUser } from "@/api/adminUser";

const route = useRoute();
const router = useRouter();
const currentRouteName = computed(() => route.meta.title || "");

const username = ref("");
const userInitial = computed(() => {
  return username.value ? username.value.charAt(0).toUpperCase() : "";
});

const fetchUserInfo = async () => {
  try {
    const userInfo = await getCurrentAdminUser();
    username.value = userInfo.username;
  } catch (error) {
    console.error("获取用户信息失败:", error);
  }
};

onMounted(() => {
  fetchUserInfo();
});

const handleLogout = () => {
  ElMessageBox.confirm("确定要退出登录吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      localStorage.removeItem("token");
      router.push("/login");
    })
    .catch(() => {});
};
</script>

<style scoped>
.navbar {
  padding: 0 24px;
  background-color: #fff;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.left {
  display: flex;
  align-items: center;
}

.right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 0 8px;
  outline: none;
}

.user-info:hover {
  background-color: transparent !important;
}

.user-avatar {
  background: #6366f1;
  color: #fff;
  font-weight: 600;
  font-size: 14px;
}

.username {
  margin-left: 8px;
  font-size: 14px;
  color: #334155;
}

:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
```

- [ ] **Step 2: 验证顶栏效果**

确认顶栏高度 52px、底部细线、头像为首字母圆形 Indigo 底色。

- [ ] **Step 3: 提交**

```bash
git add src/components/Layout/Navbar.vue
git commit -m "feat(Web): 顶栏样式升级，首字母头像替代外部图片"
```

---

### Task 4: 布局背景色调整

**Files:**
- Modify: `src/components/Layout/Layout.vue`

- [ ] **Step 1: 更新内容区背景色**

将 `.app-main` 的背景色从 `#f0f2f5` 改为 `#f1f5f9`：

```vue
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
  min-width: 0;
}

.app-main {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background-color: #f1f5f9;
}
</style>
```

（模板和 script 不变）

- [ ] **Step 2: 提交**

```bash
git add src/components/Layout/Layout.vue
git commit -m "feat(Web): 内容区背景色更新为 Slate 100"
```

---

### Task 5: 登录页配色升级

**Files:**
- Modify: `src/views/LoginPage.vue`

- [ ] **Step 1: 更新登录页样式**

仅修改 `<style scoped>` 部分，模板和脚本保持不变：

```css
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  width: 100vw;
  background: #f1f5f9;
  position: fixed;
  top: 0;
  left: 0;
  margin: 0;
  padding: 0;
}

.login-content {
  display: flex;
  width: 1000px;
  height: 600px;
  background: #ffffff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.12);
}

.login-left {
  flex: 1;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #312e81 100%);
  padding: 60px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
}

.login-left::before {
  content: "";
  position: absolute;
  top: 40px;
  left: 40px;
  right: 40px;
  bottom: 40px;
  border: 1px solid rgba(99, 102, 241, 0.12);
  border-radius: 12px;
  pointer-events: none;
}

.brand {
  display: flex;
  align-items: center;
  margin-bottom: 40px;
}

.brand h1 {
  font-size: 36px;
  font-weight: 700;
  margin: 0;
  color: #e2e8f0;
  letter-spacing: -0.5px;
}

.slogan h2 {
  font-size: 28px;
  margin-bottom: 16px;
  font-weight: 500;
  color: #cbd5e1;
  letter-spacing: -0.3px;
}

.slogan p {
  font-size: 16px;
  color: #94a3b8;
  margin: 0;
  line-height: 1.6;
}

.login-right {
  flex: 1;
  padding: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
}

.login-box {
  width: 100%;
  max-width: 400px;
}

.login-title {
  margin-bottom: 32px;
  font-size: 24px;
  text-align: center;
  color: #0f172a;
  font-weight: 600;
  letter-spacing: -0.3px;
}

.login-form {
  margin-top: 20px;
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 500;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
  transition: all 0.3s ease;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.1);
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

:deep(.el-form-item__label) {
  color: #334155;
  font-weight: 500;
}

:deep(.el-input__inner) {
  color: #0f172a;
}

:deep(.el-input__inner::placeholder) {
  color: #94a3b8;
}
```

- [ ] **Step 2: 验证登录页效果**

打开 `/login` 页面确认：左深色渐变 + Indigo 细线框，右白色表单区，输入框带圆角。

- [ ] **Step 3: 提交**

```bash
git add src/views/LoginPage.vue
git commit -m "feat(Web): 登录页配色升级为深邃高级风格"
```

---

### Task 6: 全量验证

- [ ] **Step 1: 启动开发服务器**

```sh
npm run dev
```

- [ ] **Step 2: 检查所有页面**

- 登录页：渐变背景、圆角输入框、Indigo 按钮
- 登录后：深蓝黑侧边栏带图标、52px 顶栏、首字母头像
- 各管理页面：表格和按钮主色为 Indigo，卡片圆角 8px

- [ ] **Step 3: 验证响应式无异常**

缩小浏览器窗口，确认布局不破裂。

- [ ] **Step 4: 提交（如有微调）或确认完成**

```bash
git status
```
