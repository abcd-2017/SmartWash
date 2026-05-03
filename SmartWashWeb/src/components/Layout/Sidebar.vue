<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <div class="brand-name">SmartWash</div>
      <div class="brand-sub">管理系统</div>
    </div>
    <el-menu
      :default-active="activeMenu"
      background-color="#1e293b"
      text-color="#cbd5e1"
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
</style>
