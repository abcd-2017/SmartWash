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
import { computed, ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { SwitchButton } from '@element-plus/icons-vue';
import { getCurrentAdminUser } from '@/api/adminUser';
import { useAuthStore } from '@/stores/auth';
import { useConfirm } from '@/composables/useConfirm';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const currentRouteName = computed(() => route.meta.title || '');

const username = ref('');
const userInitial = computed(() => {
  return username.value ? username.value.charAt(0).toUpperCase() : '';
});

const fetchUserInfo = async () => {
  try {
    const userInfo = await getCurrentAdminUser();
    username.value = userInfo.username;
  } catch {
    // 请求失败由 http 拦截器统一弹出提示，这里保持用户名为空即可
  }
};

onMounted(() => {
  fetchUserInfo();
});

const handleLogout = async () => {
  // 确认弹窗统一走 useConfirm：取消/关闭静默返回 false（评审 #23）
  const confirmed = await useConfirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
  });
  if (!confirmed) return;
  // 登录态统一走 store 清理（内存 + localStorage 一并清空）
  authStore.clearLogin();
  router.push('/login');
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
