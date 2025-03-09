<template>
  <el-container style="height: 100vh">
    <el-aside width="200px">
      <!-- 左侧菜单 -->
      <el-menu :default-active="activeMenu">
        <el-menu-item index="1">首页</el-menu-item>
        <el-menu-item index="2">用户管理</el-menu-item>
        <el-menu-item index="3">设置</el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header>
        <div class="header-content">
          <h2>后台管理系统</h2>
        </div>
      </el-header>

      <el-main>
        <router-view></router-view>
      </el-main>
    </el-container>
  </el-container>
</template>
  
  <script>
export default {
  name: "Layout",
  data() {
    return {
      activeMenu: "1",
    };
  },
};
</script>
  
  <style scoped>
.header-content {
  padding: 20px;
  font-size: 24px;
  font-weight: bold;
  color: #333;
}
</style>
  