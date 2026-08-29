<template>
  <!-- Element Plus 全局中文语言包：按需引入后由 ConfigProvider 下发（替代原 app.use(ElementPlus, { locale })） -->
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup>
// 中文语言包按需引入（配合 unplugin 按需模式，main.js 不再全量注册 Element Plus）
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<style>
body,
html {
  margin: 0;
  padding: 0;
  height: 100%;
}
</style>
