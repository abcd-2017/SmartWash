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
